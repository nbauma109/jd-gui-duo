/**
 * Copyright (C) 2007-2019 Emmanuel Dupuy GPLv3
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jd.core.process.analyzer.classfile;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;

import java.util.*;

import jd.core.model.classfile.*;
import jd.core.model.classfile.attribute.AttributeSignature;
import jd.core.model.classfile.constant.*;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.FastLabel;
import jd.core.model.reference.ReferenceMap;
import jd.core.process.analyzer.classfile.reconstructor.*;
import jd.core.process.analyzer.classfile.visitor.CheckCastAndConvertInstructionVisitor;
import jd.core.process.analyzer.classfile.visitor.ReplaceStringBuxxxerVisitor;
import jd.core.process.analyzer.classfile.visitor.SetConstantTypeInStringIndexOfMethodsVisitor;
import jd.core.process.analyzer.instruction.bytecode.InstructionListBuilder;
import jd.core.process.analyzer.instruction.fast.DupLocalVariableAnalyzer;
import jd.core.process.analyzer.instruction.fast.FastInstructionListBuilder;
import jd.core.process.analyzer.instruction.fast.ReturnLineNumberAnalyzer;
import jd.core.process.analyzer.variable.DefaultVariableNameGenerator;
import jd.core.util.SignatureUtil;

public class ClassFileAnalyzer
{
    private ClassFileAnalyzer() {
    }
        public static void Analyze(ReferenceMap referenceMap, ClassFile classFile)
    {
        // Creation du tableau associatif [nom de classe interne, objet class].
        // Ce tableau est utilisé pour la suppression des accesseurs des
        // classes internes.
        Map<String, ClassFile> innerClassesMap;
        if (classFile.getInnerClassFiles() != null)
        {
            innerClassesMap = new HashMap<>(10);
            innerClassesMap.put(classFile.getThisClassName(), classFile);
            PopulateInnerClassMap(innerClassesMap, classFile);
        }
        else
        {
            innerClassesMap = null;
        }

        // Generation des listes d'instructions
        // Creation du tableau des variables locales si necessaire
        AnalyzeClass(referenceMap, innerClassesMap, classFile);
    }

    private static void PopulateInnerClassMap(
        Map<String, ClassFile> innerClassesMap, ClassFile classFile)
    {
        List<ClassFile> innerClassFiles = classFile.getInnerClassFiles();

        if (innerClassFiles != null)
        {
            int length = innerClassFiles.size();

            ClassFile innerClassFile;
            for (int i=0; i<length; ++i)
            {
                innerClassFile = innerClassFiles.get(i);
                innerClassesMap.put(
                    innerClassFile.getThisClassName(), innerClassFile);
                PopulateInnerClassMap(innerClassesMap, innerClassFile);
            }
        }
    }

    private static void AnalyzeClass(
        ReferenceMap referenceMap,
        Map<String, ClassFile> innerClassesMap,
        ClassFile classFile)
    {
        if ((classFile.access_flags & ClassFileConstants.ACC_SYNTHETIC) != 0)
        {
            AnalyzeSyntheticClass(classFile);
        }
        else
        {
            // L'analyse preliminaire permet d'identifier l'attribut de chaque
            // classe interne non statique portant la reference vers la classe
            // externe. 'PreAnalyzeMethods' doit être execute avant l'analyse
            // des classes internes. Elle permet egalement de construire la liste
            // des accesseurs et de parser les tableaux "SwitchMap" produit par le
            // compilateur d'Eclipse et utilisé pour le Switch+Enum.
            PreAnalyzeMethods(classFile);

            // Analyse des classes internes avant l'analyse de la classe pour
            // afficher correctement des classes anonymes.
            List<ClassFile> innerClassFiles = classFile.getInnerClassFiles();
            if (innerClassFiles != null)
            {
                int length = innerClassFiles.size();
                for (int i=0; i<length; i++) {
                    AnalyzeClass(referenceMap, innerClassesMap, innerClassFiles.get(i));
                }
            }

            // Analyse de la classe
            CheckUnicityOfFieldNames(classFile);
            CheckUnicityOfFieldrefNames(classFile);
            AnalyzeMethods(referenceMap, innerClassesMap, classFile);
            CheckAssertionsDisabledField(classFile);

            if ((classFile.access_flags & ClassFileConstants.ACC_ENUM) != 0) {
                AnalyzeEnum(classFile);
            }
        }
    }

    private static void AnalyzeSyntheticClass(ClassFile classFile)
    {
        // Recherche des classes internes utilisees par les instructions
        // Switch+Enum generees par les compilateurs autre qu'Eclipse.

        if ((classFile.access_flags & ClassFileConstants.ACC_STATIC) != 0 &&
            classFile.getOuterClass() != null &&
            classFile.getInternalAnonymousClassName() != null &&
            classFile.getFields() != null &&
            classFile.getMethods() != null &&
            classFile.getFields().length > 0 &&
            classFile.getMethods().length == 1 &&
            (classFile.getMethods()[0].access_flags &
                    (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED|ClassFileConstants.ACC_PRIVATE|ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_FINAL|ClassFileConstants.ACC_SYNTHETIC)) ==
                        ClassFileConstants.ACC_STATIC)
        {
            ClassFile outerClassFile = classFile.getOuterClass();
            ConstantPool outerConstants = outerClassFile.getConstantPool();
            ConstantPool constants = classFile.getConstantPool();
            Method method = classFile.getMethods()[0];

            try
            {
                AnalyzeMethodref(classFile);

                // Build instructions
                List<Instruction> list = new ArrayList<>();
                List<Instruction> listForAnalyze = new ArrayList<>();

                InstructionListBuilder.Build(
                    classFile, method, list, listForAnalyze);

                /* Parse static method
                 * static {
                 *  $SwitchMap$basic$data$TestEnum$enum2 = new int[enum2.values().length];
                 *  try { $SwitchMap$basic$data$TestEnum$enum2[enum2.E.ordinal()] = 1; } catch(NoSuchFieldError ex) { }
                 *  try { $SwitchMap$basic$data$TestEnum$enum2[enum2.F.ordinal()] = 2; } catch(NoSuchFieldError ex) { }
                 *  $SwitchMap$basic$data$TestEnum$enum1 = new int[enum1.values().length];
                 *  try { $SwitchMap$basic$data$TestEnum$enum1[enum1.A.ordinal()] = 1; } catch(NoSuchFieldError ex) { }
                 *  try { $SwitchMap$basic$data$TestEnum$enum1[enum1.B.ordinal()] = 2; } catch(NoSuchFieldError ex) { }
                 * }
                 */
                int length = list.size();

                PutStatic ps;
                ConstantFieldref cfr;
                ConstantNameAndType cnat;
                Field field;
                String fieldName;
                List<Integer> enumNameIndexes;
                int outerFieldNameIndex;
                Instruction instruction;
                String enumName;
                int outerEnumNameIndex;
                for (int index=0; index<length; index++)
                {
                    if (list.get(index).opcode != ByteCodeConstants.PUTSTATIC) {
                        break;
                    }

                    ps = (PutStatic)list.get(index);
                    cfr = constants.getConstantFieldref(ps.index);
                    if (cfr.class_index != classFile.getThisClassIndex()) {
                        break;
                    }

                    cnat = constants.getConstantNameAndType(cfr.name_and_type_index);

                    // Search field
                    field = SearchField(classFile, cnat);
                    if (field == null || (field.access_flags &
                            (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED|ClassFileConstants.ACC_PRIVATE|ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_FINAL|ClassFileConstants.ACC_SYNTHETIC)) !=
                                (ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_FINAL)) {
                        break;
                    }

                    fieldName = constants.getConstantUtf8(cnat.name_index);
                    if (! fieldName.startsWith("$SwitchMap$")) {
                        break;
                    }

                    enumNameIndexes = new ArrayList<>();

                    for (index+=3; index<length; index+=3)
                    {
                        instruction = list.get(index-2);

                        if (instruction.opcode != ByteCodeConstants.ARRAYSTORE ||
                            list.get(index-1).opcode != ByteCodeConstants.GOTO ||
                            list.get(index).opcode != ByteCodeConstants.ASTORE) {
                            break;
                        }

                        instruction = ((ArrayStoreInstruction)instruction).indexref;

                        if (instruction.opcode != ByteCodeConstants.INVOKEVIRTUAL) {
                            break;
                        }

                        instruction = ((Invokevirtual)instruction).objectref;

                        if (instruction.opcode != ByteCodeConstants.GETSTATIC) {
                            break;
                        }

                        cfr = constants.getConstantFieldref(
                            ((GetStatic)instruction).index);
                        cnat = constants.getConstantNameAndType(
                            cfr.name_and_type_index);
                        enumName = constants.getConstantUtf8(cnat.name_index);
                        outerEnumNameIndex = outerConstants.addConstantUtf8(enumName);

                        // Add enum name index
                        enumNameIndexes.add(outerEnumNameIndex);
                    }

                    outerFieldNameIndex = outerConstants.addConstantUtf8(fieldName);

                    // Key = indexe du nom de na classe interne dans le
                    // pool de constantes de la classe externe
                    outerClassFile.getSwitchMaps().put(
                        Integer.valueOf(outerFieldNameIndex), enumNameIndexes);

                    index -= 3;
                }
            }
            catch (Exception e)
            {
                assert ExceptionUtil.printStackTrace(e);
                method.setContainsError(true);
            }
        }
    }

    private static Field SearchField(
        ClassFile classFile, ConstantNameAndType cnat)
    {
        Field[] fields = classFile.getFields();
        int i = fields.length;

        Field field;
        while (i-- > 0)
        {
            field = fields[i];

            if (field.name_index == cnat.name_index &&
                field.descriptor_index == cnat.descriptor_index) {
                return field;
            }
        }

        return null;
    }

    private static void AnalyzeMethodref(ClassFile classFile)
    {
        ConstantPool constants = classFile.getConstantPool();

        Constant constant;
        for (int i=constants.size()-1; i>=0; --i)
        {
            constant = constants.get(i);

            if (constant != null &&
                (constant.tag == ConstantConstant.CONSTANT_METHODREF ||
                 constant.tag == ConstantConstant.CONSTANT_INTERFACEMETHODREF))
            {
                ConstantMethodref cmr = (ConstantMethodref)constant;
                ConstantNameAndType cnat =
                    constants.getConstantNameAndType(cmr.name_and_type_index);

                if (cnat != null)
                {
                    String signature = constants.getConstantUtf8(
                        cnat.descriptor_index);
                    cmr.setParameterSignatures(
                        SignatureUtil.GetParameterSignatures(signature));
                    cmr.setReturnedSignature(
                        SignatureUtil.GetMethodReturnedSignature(signature));
                }
            }
        }
    }

    private static void CheckUnicityOfFieldNames(ClassFile classFile)
    {
        Field[] fields = classFile.getFields();
        if (fields == null) {
            return;
        }

        ConstantPool constants = classFile.getConstantPool();
        Map<String, List<Field>> map =
            new HashMap<>();

        // Populate map
        int i = fields.length;
        while (i-- > 0)
        {
            Field field = fields[i];

            if ((field.access_flags & (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED)) != 0) {
                continue;
            }

            String name = constants.getConstantUtf8(field.name_index);

            map.computeIfAbsent(name, k -> new ArrayList<>(5)).add(field);
        }

        // Check unicity
        Iterator<String> iteratorName = map.keySet().iterator();
        String name;
        List<Field> list;
        int j;
        Field field;
        String newName;
        int newNameIndex;
        while (iteratorName.hasNext())
        {
            name = iteratorName.next();
            list = map.get(name);

            j = list.size();
            if (j < 2) {
                continue;
            }

            // Change attribute names;
            while (j-- > 0)
            {
                field = list.get(j);

                // Generate new attribute names
                newName = FieldNameGenerator.GenerateName(
                        constants.getConstantUtf8(field.descriptor_index),
                        constants.getConstantUtf8(field.name_index));
                // Add new constant string
                newNameIndex = constants.addConstantUtf8(newName);
                // Update name index
                field.name_index = newNameIndex;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void CheckUnicityOfFieldrefNames(ClassFile classFile)
    {
        ConstantPool constants = classFile.getConstantPool();

        // Popuplate array
        int i = constants.size();
        Object[] array = new Object[i];

        Constant constant;
        ConstantFieldref cfr;
        while (i-- > 0)
        {
            constant = constants.get(i);

            if (constant == null ||
                constant.tag != ConstantConstant.CONSTANT_FIELDREF) {
                continue;
            }

            cfr = (ConstantFieldref)constant;
            Map<String, List<ConstantNameAndType>> map =
                (Map<String, List<ConstantNameAndType>>)array[cfr.class_index];

            if (map == null)
            {
                map = new HashMap<>();
                array[cfr.class_index] = map;
            }

            ConstantNameAndType cnat =
                constants.getConstantNameAndType(cfr.name_and_type_index);
            String name = constants.getConstantUtf8(cnat.name_index);
            List<ConstantNameAndType> list = map.get(name);

            if (list != null)
            {
                if (list.get(0).descriptor_index != cnat.descriptor_index)
                {
                    // Same name and different signature
                    list.add(cnat);
                }
            }
            else
            {
                list = new ArrayList<>(5);
                map.put(name, list);
                list.add(cnat);
            }
        }

        // For each class in constant pool, check unicity of name of 'Fieldref'
        i = array.length;
        Map<String, List<ConstantNameAndType>> map;
        Iterator<String> iterator;
        String name;
        List<ConstantNameAndType> list;
        int k;
        ConstantNameAndType cnat;
        String signature;
        String newName;
        while (i-- > 0)
        {
            if (array[i] == null) {
                continue;
            }

            map = (Map<String, List<ConstantNameAndType>>)array[i];

            iterator = map.keySet().iterator();
            while (iterator.hasNext())
            {
                name = iterator.next();
                list = map.get(name);

                k = list.size();
                if (k < 2) {
                    continue;
                }

                while (k-- > 0)
                {
                    cnat = list.get(k);
                    signature = constants.getConstantUtf8(cnat.descriptor_index);
                    newName = FieldNameGenerator.GenerateName(signature, name);
                    cnat.name_index = constants.addConstantUtf8(newName);
                }
            }
        }
    }

    private static void CheckAssertionsDisabledField(ClassFile classFile)
    {
        ConstantPool constants = classFile.getConstantPool();
        Field[] fields = classFile.getFields();

        if (fields == null) {
            return;
        }

        int i = fields.length;
        Field field;
        String name;
        while (i-- > 0)
        {
            field = fields[i];

            if ((field.access_flags &
                    (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED|
                     ClassFileConstants.ACC_PRIVATE|ClassFileConstants.ACC_SYNTHETIC|
                     ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_FINAL))
                         != (ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_FINAL) || field.getValueAndMethod() == null) {
                continue;
            }

            name = constants.getConstantUtf8(field.name_index);
            if (! "$assertionsDisabled".equals(name)) {
                continue;
            }

            field.access_flags |= ClassFileConstants.ACC_SYNTHETIC;
        }
    }

    private static boolean HasAAccessorMethodName(ClassFile classFile, Method method)
    {
        String methodName =
            classFile.getConstantPool().getConstantUtf8(method.name_index);

        if (! methodName.startsWith("access$")) {
            return false;
        }

        int i = methodName.length();

        while (i-- > "access$".length())
        {
            if (! Character.isDigit(methodName.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static boolean HasAEclipseSwitchTableMethodName(
        ClassFile classFile, Method method)
    {
        String methodName =
            classFile.getConstantPool().getConstantUtf8(method.name_index);

        if (! methodName.startsWith("$SWITCH_TABLE$")) {
            return false;
        }

        String methodDescriptor =
            classFile.getConstantPool().getConstantUtf8(method.descriptor_index);

        return "()[I".equals(methodDescriptor);
    }

    /** Parse Eclipse SwitchTable method
     * static int[] $SWITCH_TABLE$basic$data$TestEnum$enum1()
     * {
     *   if($SWITCH_TABLE$basic$data$TestEnum$enum1 != null)
     *     return $SWITCH_TABLE$basic$data$TestEnum$enum1;
     *   int ai[] = new int[enum1.values().length];
     *   try { ai[enum1.A.ordinal()] = 1; } catch(NoSuchFieldError _ex) { }
     *   try { ai[enum1.B.ordinal()] = 2; } catch(NoSuchFieldError _ex) { }
     *   return $SWITCH_TABLE$basic$data$TestEnum$enum1 = ai;
     * }
     *
     * and parse DEX SwitchTable method
     * static int[] $SWITCH_TABLE$gr$androiddev$FuelPrices$StaticTools$LocationProvider()
     * {
     *   int[] local0 = $SWITCH_TABLE$gr$androiddev$FuelPrices$StaticTools$LocationProvider;
     *   if (local0 != null)
     *     return local0;
     *   int array = new int[StaticTools$LocationProvider.values().length()];
     *   try { array[StaticTools$LocationProvider.ANY.ordinal()] = 2; } catch (java/lang/NoSuchFieldError) --> 73
     *   try { array[StaticTools$LocationProvider.BESTOFBOTH.ordinal()] = 1; } catch (java/lang/NoSuchFieldError) --> 69
     *   try { array[StaticTools$LocationProvider.GPS.ordinal()] = 3; } catch (java/lang/NoSuchFieldError) --> 64
     *   try { array[StaticTools$LocationProvider.NETWORK.ordinal()] = 4; } catch (java/lang/NoSuchFieldError) --> 59
     *   $SWITCH_TABLE$gr$androiddev$FuelPrices$StaticTools$LocationProvider = array;
     *   return array;
     *   59: catch (java/lang/NoSuchFieldError) {}
     *   64: catch (java/lang/NoSuchFieldError) {}
     *   69: catch (java/lang/NoSuchFieldError) {}
     *   73: catch (java/lang/NoSuchFieldError) {}
     * }
     */
    private static void ParseEclipseOrDexSwitchTableMethod(
        ClassFile classFile, Method method)
    {
        List<Instruction> list = method.getInstructions();
        int length = list.size();

        if (length >= 6 &&
            list.get(0).opcode == ByteCodeConstants.DUPSTORE &&
            list.get(1).opcode == ByteCodeConstants.IFXNULL &&
            list.get(2).opcode == ByteCodeConstants.XRETURN &&
            list.get(3).opcode == ByteCodeConstants.POP &&
            list.get(4).opcode == ByteCodeConstants.ASTORE)
        {
            // Eclipse pattern
            ConstantPool constants = classFile.getConstantPool();
            List<Integer> enumNameIndexes = new ArrayList<>();

            Instruction instruction;
            ConstantFieldref cfr;
            ConstantNameAndType cnat;
            for (int index=5+2; index<length; index+=3)
            {
                instruction = list.get(index-2);

                if (instruction.opcode != ByteCodeConstants.ARRAYSTORE ||
                    list.get(index-1).opcode != ByteCodeConstants.GOTO ||
                    list.get(index).opcode != ByteCodeConstants.POP) {
                    break;
                }

                instruction = ((ArrayStoreInstruction)instruction).indexref;

                if (instruction.opcode != ByteCodeConstants.INVOKEVIRTUAL) {
                    break;
                }

                instruction = ((Invokevirtual)instruction).objectref;

                if (instruction.opcode != ByteCodeConstants.GETSTATIC) {
                    break;
                }

                cfr = constants.getConstantFieldref(((GetStatic)instruction).index);
                cnat = constants.getConstantNameAndType(
                    cfr.name_and_type_index);

                // Add enum name index
                enumNameIndexes.add(cnat.name_index);
            }

            classFile.getSwitchMaps().put(
                Integer.valueOf(method.name_index), enumNameIndexes);
        }
        else if (length >= 7 &&
                list.get(0).opcode == ByteCodeConstants.ASTORE &&
                list.get(1).opcode == ByteCodeConstants.IFXNULL &&
                list.get(2).opcode == ByteCodeConstants.XRETURN &&
                list.get(3).opcode == ByteCodeConstants.ASTORE &&
                list.get(4).opcode == ByteCodeConstants.ARRAYSTORE)
        {
            // Dalvik pattern
            ConstantPool constants = classFile.getConstantPool();
            List<Integer> enumNameIndexes = new ArrayList<>();

            Instruction instruction;
            ConstantFieldref cfr;
            ConstantNameAndType cnat;
            for (int index=4; index<length; index++)
            {
                instruction = list.get(index);

                if (instruction.opcode != ByteCodeConstants.ARRAYSTORE) {
                    break;
                }

                instruction = ((ArrayStoreInstruction)instruction).indexref;

                if (instruction.opcode != ByteCodeConstants.INVOKEVIRTUAL) {
                    break;
                }

                instruction = ((Invokevirtual)instruction).objectref;

                if (instruction.opcode != ByteCodeConstants.GETSTATIC) {
                    break;
                }

                cfr = constants.getConstantFieldref(((GetStatic)instruction).index);
                cnat = constants.getConstantNameAndType(
                    cfr.name_and_type_index);

                // Add enum name index
                enumNameIndexes.add(cnat.name_index);
            }

            classFile.getSwitchMaps().put(
                Integer.valueOf(method.name_index), enumNameIndexes);
        }
    }

    private static void PreAnalyzeMethods(ClassFile classFile)
    {
        AnalyzeMethodref(classFile);

        Method[] methods = classFile.getMethods();

        if (methods == null) {
            return;
        }

        int length = methods.length;
        DefaultVariableNameGenerator variableNameGenerator =
            new DefaultVariableNameGenerator(classFile);
        int outerThisFieldrefIndex = 0;

        for (int i=0; i<length; i++)
        {
            final Method method = methods[i];

            try
            {
                if (method.getCode() == null)
                {
                    if ((method.access_flags &
                            (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_BRIDGE)) == 0)
                    {
                        // Create missing local variable table
                        LocalVariableAnalyzer.Analyze(
                            classFile, method, variableNameGenerator, null, null);
                    }
                }
                else
                {
                    // Build instructions
                    List<Instruction> list = new ArrayList<>();
                    List<Instruction> listForAnalyze = new ArrayList<>();

                    InstructionListBuilder.Build(
                        classFile, method, list, listForAnalyze);
                    method.setInstructions(list);

                    if ((method.access_flags & (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED|ClassFileConstants.ACC_PRIVATE|ClassFileConstants.ACC_STATIC)) == ClassFileConstants.ACC_STATIC &&
                        HasAAccessorMethodName(classFile, method))
                    {
                        // Recherche des accesseurs
                        AccessorAnalyzer.Analyze(classFile, method);
                        // Setup access flag : JDK 1.4 not set synthetic flag...
                        method.access_flags |= ClassFileConstants.ACC_SYNTHETIC;
                    }
                    else if ((method.access_flags &
                            (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_BRIDGE)) == 0)
                    {
                        // Create missing local variable table
                        LocalVariableAnalyzer.Analyze(
                            classFile, method, variableNameGenerator, list, listForAnalyze);

                        // Recherche du numero de l'attribut contenant la reference
                        // de la classe externe
                        outerThisFieldrefIndex = SearchOuterThisFieldrefIndex(
                            classFile, method, list, outerThisFieldrefIndex);
                    }
                    else if ((method.access_flags & (ClassFileConstants.ACC_PUBLIC|ClassFileConstants.ACC_PROTECTED|ClassFileConstants.ACC_PRIVATE|ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_SYNTHETIC))
                                    == (ClassFileConstants.ACC_STATIC|ClassFileConstants.ACC_SYNTHETIC) &&
                                HasAEclipseSwitchTableMethodName(classFile, method))
                    {
                        // Parse "static int[] $SWITCH_TABLE$...()" method
                        ParseEclipseOrDexSwitchTableMethod(classFile, method);
                    }
                }
            }
            catch (Exception e)
            {
                assert ExceptionUtil.printStackTrace(e);
                method.setContainsError(true);
            }
        }

        if (outerThisFieldrefIndex != 0) {
            AnalyzeOuterReferences(classFile, outerThisFieldrefIndex);
        }
    }

    private static void AnalyzeMethods(
        ReferenceMap referenceMap,
        Map<String, ClassFile> innerClassesMap,
        ClassFile classFile)
    {
        Method[] methods = classFile.getMethods();

        if (methods == null) {
            return;
        }

        int length = methods.length;

        // Initialisation du reconstructeur traitant l'acces des champs et
        // méthodes externes si la classe courante est une classe interne ou
        // si elle contient des classes internes
        OuterReferenceReconstructor outerReferenceReconstructor =
            (innerClassesMap != null) ?
                new OuterReferenceReconstructor(innerClassesMap, classFile) : null;

        for (int i=0; i<length; i++)
        {
            final Method method = methods[i];

            if ((method.access_flags &
                (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_BRIDGE)) != 0 ||
                method.getCode() == null ||
                method.containsError()) {
                continue;
            }

            try
            {
                List<Instruction> list = method.getInstructions();

                // Recontruct access to outer fields and methods
                if (outerReferenceReconstructor != null) {
                    outerReferenceReconstructor.reconstruct(method, list);
                }
                // Re-construct 'new' intruction
                NewInstructionReconstructor.Reconstruct(classFile, method, list);
                SimpleNewInstructionReconstructor.Reconstruct(classFile, method, list);
                // Recontruction des instructions de pre-incrementation non entier
                PreIncReconstructor.Reconstruct(list);
                // Recontruction des instructions de post-incrementation non entier
                PostIncReconstructor.Reconstruct(list);
                // Recontruction du mot clé '.class' pour le JDK 1.1.8 - A
                DotClass118AReconstructor.Reconstruct(
                    referenceMap, classFile, list);
                // Recontruction du mot clé '.class' pour le JDK 1.4
                DotClass14Reconstructor.Reconstruct(
                    referenceMap, classFile, list);
                // Replace StringBuffer and StringBuilder in java source line
                ReplaceStringBufferAndStringBuilder(classFile, list);
                // Remove unused pop instruction
                RemoveUnusedPopInstruction(list);
                // Transformation des tests sur des types 'long' et 'double'
                TransformTestOnLongOrDouble(list);
                // Set constant type of "String.indexOf(...)" methods
                SetConstantTypeInStringIndexOfMethods(classFile, list);
                // Elimine la séquence DupStore(this) ... DupLoad() ... DupLoad().
                // Cette operation doit être executee avant
                // 'AssignmentInstructionReconstructor'.
                DupStoreThisReconstructor.Reconstruct(list);
                // Recontruction des affectations multiples
                // Cette operation doit être executee avant
                // 'InitArrayInstructionReconstructor', 'TernaryOpReconstructor'
                // et la construction des instructions try-catch et finally.
                // Cette operation doit être executee après 'DupStoreThisReconstructor'.
                AssignmentInstructionReconstructor.Reconstruct(list);
                // Elimine les doubles casts et ajoute des casts devant les
                // constantes numeriques si necessaire.
                CheckCastAndConvertInstructionVisitor.visit(
                    classFile.getConstantPool(), list);

                // Build fast instructions
                List<Instruction> fastList =
                    new ArrayList<>(list);
                method.setFastNodes(fastList);

                // DEBUG //
                //ConstantPool debugConstants = classFile.getConstantPool();
                //String debugMethodName = debugConstants.getConstantUtf8(method.name_index);
                // DEBUG //
                FastInstructionListBuilder.Build(
                    referenceMap, classFile, method, fastList);

                // Ajout des déclarations des variables locales temporaires
                DupLocalVariableAnalyzer.Declare(classFile, method, fastList);
            }
            catch (Exception e)
            {
                assert ExceptionUtil.printStackTrace(e);
                method.setContainsError(true);
            }
        }

        // Recherche des initialisations des attributs statiques Enum
        InitDexEnumFieldsReconstructor.Reconstruct(classFile);
        // Recherche des initialisations des attributs statiques
        InitStaticFieldsReconstructor.Reconstruct(classFile);
        // Recherche des initialisations des attributs d'instance
        InitInstanceFieldsReconstructor.Reconstruct(classFile);

        for (int i=0; i<length; i++)
        {
            final Method method = methods[i];

            if ((method.access_flags &
                (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_BRIDGE)) != 0 ||
                method.getCode() == null ||
                method.getFastNodes() == null ||
                method.containsError()) {
                continue;
            }

            try
            {
                // Remove empty and enum super call
                AnalyseAndModifyConstructors(classFile, method);
                // Check line number of 'return'
                ReturnLineNumberAnalyzer.Check(method);
                // Remove last instruction 'return'
                RemoveLastReturnInstruction(method);
            }
            catch (Exception e)
            {
                assert ExceptionUtil.printStackTrace(e);
                method.setContainsError(true);
            }
        }
    }

    private static int SearchOuterThisFieldrefIndex(
        ClassFile classFile, Method method,
        List<Instruction> list, int outerThisFieldrefIndex)
    {
        // Is classFile an inner class ?
        if (!classFile.isAInnerClass() ||
            (classFile.access_flags & ClassFileConstants.ACC_STATIC) != 0) {
            return 0;
        }

        ConstantPool constants = classFile.getConstantPool();

        // Is method a constructor ?
        if (method.name_index != constants.instanceConstructorIndex) {
            return outerThisFieldrefIndex;
        }

        // Is parameters counter greater than 0 ?
        AttributeSignature as = method.getAttributeSignature();
        String methodSignature = constants.getConstantUtf8(
            (as==null) ? method.descriptor_index : as.signature_index);

        if (methodSignature.charAt(1) == ')') {
            return 0;
        }

        // Search instruction 'PutField(#, ALoad(1))' before super <init>
        // method call.
        int length = list.size();

        Instruction instruction;
        for (int i=0; i<length; i++)
        {
            instruction = list.get(i);

            if (instruction.opcode == ByteCodeConstants.PUTFIELD)
            {
                // Is '#' equals to 'outerThisFieldIndex' ?
                PutField pf = (PutField)instruction;

                if (pf.objectref.opcode == ByteCodeConstants.ALOAD &&
                    pf.valueref.opcode == ByteCodeConstants.ALOAD &&
                    ((ALoad)pf.objectref).index == 0 &&
                    ((ALoad)pf.valueref).index == 1 && (outerThisFieldrefIndex == 0 ||
                    pf.index == outerThisFieldrefIndex)) {
                    return pf.index;
                }
            }
            else if (instruction.opcode == ByteCodeConstants.INVOKESPECIAL)
            {
                // Is a call to "this()" in constructor ?
                Invokespecial is = (Invokespecial)instruction;
                ConstantMethodref cmr = constants.getConstantMethodref(is.index);
                if (cmr.class_index == classFile.getThisClassIndex())
                {
                    ConstantNameAndType cnat =
                        constants.getConstantNameAndType(cmr.name_and_type_index);
                    if (cnat.name_index == constants.instanceConstructorIndex)
                    {
                        return outerThisFieldrefIndex;
                    }
                }
            }
        }

        // Instruction 'PutField' not found
        return 0;
    }

    /** Traitement des references externes des classes internes. */
    private static void AnalyzeOuterReferences(
        ClassFile classFile, int outerThisFieldrefIndex)
    {
        Method[] methods = classFile.getMethods();

        if (methods == null) {
            return;
        }

        int length = methods.length;

        // Recherche de l'attribut portant la reference vers la classe
        // externe.
        ConstantPool constants = classFile.getConstantPool();
        ConstantFieldref cfr =
            constants.getConstantFieldref(outerThisFieldrefIndex);

        if (cfr.class_index == classFile.getThisClassIndex())
        {
            ConstantNameAndType cnat =
                constants.getConstantNameAndType(cfr.name_and_type_index);
            Field[] fields = classFile.getFields();

            if (fields != null)
            {
                Field field;
                for (int i=fields.length-1; i>=0; --i)
                {
                    field = fields[i];

                    if (field.name_index == cnat.name_index &&
                        field.descriptor_index == cnat.descriptor_index)
                    {
                        classFile.setOuterThisField(field);
                        // Ensure outer this field is a synthetic field.
                        field.access_flags |= ClassFileConstants.ACC_SYNTHETIC;
                        break;
                    }
                }
            }
        }

        List<Instruction> list;
        int listLength;
        for (int i=0; i<length; i++)
        {
            final Method method = methods[i];

            if (method.getCode() == null || method.containsError()) {
                continue;
            }

            list = method.getInstructions();

            if (list == null) {
                continue;
            }

            listLength = list.size();

            if (method.name_index == constants.instanceConstructorIndex)
            {
                Instruction instruction;
                // Remove PutField instruction with index = outerThisFieldrefIndex
                // in constructors
                for (int index=0; index<listLength; index++)
                {
                    instruction = list.get(index);

                    if (instruction.opcode == ByteCodeConstants.PUTFIELD &&
                        ((PutField)instruction).index == outerThisFieldrefIndex)
                    {
                        list.remove(index);
                        break;
                    }
                }
            }
            else if ((method.access_flags &
                        (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_STATIC)) == ClassFileConstants.ACC_STATIC &&
                     method.name_index != constants.classConstructorIndex &&
                     listLength == 1 &&
                     classFile.isAInnerClass())
            {
                // Search accessor method:
                //   static TestInnerClass.InnerClass.InnerInnerClass basic/data/TestInnerClass$InnerClass$InnerInnerClass$InnerInnerInnerClass.access$100(InnerInnerInnerClass x0)
                //   {
                //      Byte code:
                //        0: aload_0
                //        1: getfield 1	basic/data/TestInnerClass$InnerClass$InnerInnerClass$InnerInnerInnerClass:this$1	Lbasic/data/TestInnerClass$InnerClass$InnerInnerClass;
                //        4: areturn
                //   }
                Instruction instruction = list.get(0);
                if (instruction.opcode != ByteCodeConstants.XRETURN) {
                    continue;
                }

                instruction = ((ReturnInstruction)instruction).valueref;
                if (instruction.opcode != ByteCodeConstants.GETFIELD) {
                    continue;
                }

                GetField gf = (GetField)instruction;
                if (gf.objectref.opcode != ByteCodeConstants.ALOAD ||
                    ((ALoad)gf.objectref).index != 0) {
                    continue;
                }

                cfr = constants.getConstantFieldref(gf.index);
                if (cfr.class_index != classFile.getThisClassIndex()) {
                    continue;
                }

                ConstantNameAndType cnat =
                    constants.getConstantNameAndType(cfr.name_and_type_index);
                Field outerField = classFile.getOuterThisField();
                if (cnat.descriptor_index != outerField.descriptor_index) {
                    continue;
                }

                if (cnat.name_index == outerField.name_index)
                {
                    // Ensure accessor method is a synthetic method
                    method.access_flags |= ClassFileConstants.ACC_SYNTHETIC;
                }
            }
        }
    }

    /**
     * 1) Retrait de la sequence suivante pour les contructeurs :
     *    Invokespecial(ALoad 0, <init>, [ ])
     * 2) Store super constructor parameter count to display anonymous
     *    class instanciation
     * 3) Store outer parameter position on field for inner and anonymous classes
     */
    private static void AnalyseAndModifyConstructors(
        ClassFile classFile, Method method)
    {
        ConstantPool constants = classFile.getConstantPool();

        if (method.name_index == constants.instanceConstructorIndex)
        {
            List<Instruction> list = method.getFastNodes();

            Instruction instruction;
            while (!list.isEmpty())
            {
                instruction = list.get(0);

                if (instruction.opcode == ByteCodeConstants.INVOKESPECIAL)
                {
                    Invokespecial is = (Invokespecial)instruction;

                    if (is.objectref.opcode == ByteCodeConstants.ALOAD &&
                        ((ALoad)is.objectref).index == 0)
                    {
                        ConstantMethodref cmr = constants.getConstantMethodref(is.index);
                        ConstantNameAndType cnat =
                            constants.getConstantNameAndType(cmr.name_and_type_index);

                        if (cnat.name_index == constants.instanceConstructorIndex)
                        {
                            if (cmr.class_index == classFile.getSuperClassIndex())
                            {
                                int count = is.args.size();

                                method.setSuperConstructorParameterCount(count);

                                if ((classFile.access_flags & ClassFileConstants.ACC_ENUM) != 0)
                                {
                                    if (count == 2)
                                    {
                                        // Retrait de l'appel du constructeur s'il
                                        // n'a que les deux paramètres standard.
                                        list.remove(0);
                                    }
                                } else if (count == 0)
                                {
                                    // Retrait de l'appel du constructeur s'il
                                    // n'a aucun parametre.
                                    list.remove(0);
                                }
                            }

                            break;
                        }
                    }
                }
                else if (instruction.opcode == ByteCodeConstants.PUTFIELD)
                {
                    PutField pf = (PutField)instruction;

                    if (pf.valueref.opcode == ByteCodeConstants.LOAD || pf.valueref.opcode == ByteCodeConstants.ALOAD
                            || pf.valueref.opcode == ByteCodeConstants.ILOAD) {
                        IndexInstruction ii = (IndexInstruction)pf.valueref;
                        // Rappel sur l'ordre des parametres passes aux constructeurs:
                        //  <init>(outer this, p1, p2, ..., outer local var1, ...)
                        // Rappel sur l'organisation des variables locales:
                        //  0: this
                        //  1: outer this
                        //  2: p1
                        //  ...
                        if (ii.index > 1)
                        {
                            // Stockage de la position du parametre du
                            // constructeur initialisant le champs
                            ConstantFieldref cfr =
                                constants.getConstantFieldref(pf.index);
                            ConstantNameAndType cnat =
                                constants.getConstantNameAndType(cfr.name_and_type_index);
                            Field field =
                                classFile.getField(cnat.name_index, cnat.descriptor_index);
                            field.anonymousClassConstructorParameterIndex = ii.index - 1;
                            // Ensure this field is a synthetic field.
                            field.access_flags |= ClassFileConstants.ACC_SYNTHETIC;
                        }
                    }
                }

                // Retrait des instructions precedents l'appel au constructeur
                // de la classe mere.
                list.remove(0);
            }
        }
    }

    private static void RemoveLastReturnInstruction(Method method)
    {
        List<Instruction> list = method.getFastNodes();

        if (list != null)
        {
            int length = list.size();

            if (length > 0)
            {
                switch (list.get(length-1).opcode)
                {
                case ByteCodeConstants.RETURN:
                    list.remove(length-1);
                    break;
                case FastConstants.LABEL:
                    FastLabel fl = (FastLabel)list.get(length-1);
                    if (fl.instruction.opcode == ByteCodeConstants.RETURN) {
                        fl.instruction = null;
                    }
                }
            }
        }
    }

    private static void ReplaceStringBufferAndStringBuilder(
        ClassFile classFile, List<Instruction> list)
    {
        ReplaceStringBuxxxerVisitor visitor = new ReplaceStringBuxxxerVisitor(
                classFile.getConstantPool());

        int length = list.size();

        for (int i=0; i<length; i++) {
            visitor.visit(list.get(i));
        }
    }

    private static void RemoveUnusedPopInstruction(List<Instruction> list)
    {
        int index = list.size();

        Instruction instruction;
        while (index-- > 0)
        {
            instruction = list.get(index);

            if (instruction.opcode == ByteCodeConstants.POP &&
                          (((Pop)instruction).objectref.opcode == ByteCodeConstants.GETFIELD
                        || ((Pop)instruction).objectref.opcode == ByteCodeConstants.GETSTATIC
                        || ((Pop)instruction).objectref.opcode == ByteCodeConstants.OUTERTHIS
                        || ((Pop)instruction).objectref.opcode == ByteCodeConstants.ALOAD
                        || ((Pop)instruction).objectref.opcode == ByteCodeConstants.ILOAD
                        || ((Pop)instruction).objectref.opcode == ByteCodeConstants.LOAD)) {
                list.remove(index);
            }
        }
    }

    private static void TransformTestOnLongOrDouble(List<Instruction> list)
    {
        int index = list.size();

        Instruction instruction;
        while (index-- > 0)
        {
            instruction = list.get(index);

            if (instruction.opcode == ByteCodeConstants.IF)
            {
                IfInstruction ii = (IfInstruction)instruction;

                if ((ii.cmp == ByteCodeConstants.CMP_EQ
                  || ii.cmp == ByteCodeConstants.CMP_NE
                  || ii.cmp == ByteCodeConstants.CMP_LT
                  || ii.cmp == ByteCodeConstants.CMP_GE
                  || ii.cmp == ByteCodeConstants.CMP_GT
                  || ii.cmp == ByteCodeConstants.CMP_LE)
                  && ii.value.opcode == ByteCodeConstants.BINARYOP)
                    {
                        BinaryOperatorInstruction boi =
                            (BinaryOperatorInstruction)ii.value;
                        if ("<".equals(boi.operator))
                        {
                            // Instruction 'boi' = ?CMP, ?CMPL or ?CMPG
                            list.set(index, new IfCmp(
                                ByteCodeConstants.IFCMP, ii.offset,
                                ii.lineNumber, ii.cmp,
                                boi.value1, boi.value2, ii.branch));
                        }
                    }
            }
        }
    }

    private static void SetConstantTypeInStringIndexOfMethods(
        ClassFile classFile, List<Instruction> list)
    {
        SetConstantTypeInStringIndexOfMethodsVisitor visitor =
            new SetConstantTypeInStringIndexOfMethodsVisitor(
                classFile.getConstantPool());

        visitor.visit(list);
    }

    private static void AnalyzeEnum(ClassFile classFile)
    {
        if (classFile.getFields() == null) {
            return;
        }

        ConstantPool constants = classFile.getConstantPool();
        String enumArraySignature = "[" + classFile.getInternalClassName();

        // Recherche du champ statique possedant un acces ACC_ENUM et un
        // type '[LenumXXXX;'
        Field[] fields = classFile.getFields();
        Field field;
        Instruction instruction;
        String fieldName;
        for (int i=fields.length-1; i>=0; --i)
        {
            field = fields[i];

            if ((field.access_flags & (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_ENUM)) == 0 ||
                field.getValueAndMethod() == null) {
                continue;
            }

            instruction = field.getValueAndMethod().getValue();

            if ((instruction.opcode != ByteCodeConstants.INITARRAY &&
                 instruction.opcode != ByteCodeConstants.NEWANDINITARRAY) ||
                !constants.getConstantUtf8(field.descriptor_index).equals(enumArraySignature)) {
                continue;
            }

            fieldName = constants.getConstantUtf8(field.name_index);
            if (! StringConstants.ENUM_VALUES_ARRAY_NAME.equals(fieldName) &&
                ! StringConstants.ENUM_VALUES_ARRAY_NAME_ECLIPSE.equals(fieldName)) {
                continue;
            }

            // Stockage des valeurs de l'enumeration
            classFile.setEnumValues(((InitArrayInstruction)instruction).values);
            break;
        }
    }
}
