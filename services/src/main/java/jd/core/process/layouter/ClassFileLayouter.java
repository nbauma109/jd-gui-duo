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
package jd.core.process.layouter;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;

import java.util.*;

import jd.core.model.classfile.*;
import jd.core.model.classfile.Field.ValueAndMethod;
import jd.core.model.classfile.attribute.AttributeSignature;
import jd.core.model.classfile.constant.ConstantFieldref;
import jd.core.model.classfile.constant.ConstantNameAndType;
import jd.core.model.instruction.bytecode.instruction.GetStatic;
import jd.core.model.instruction.bytecode.instruction.Instruction;
import jd.core.model.instruction.bytecode.instruction.InvokeNew;
import jd.core.model.layout.block.*;
import jd.core.model.layout.section.LayoutSection;
import jd.core.model.reference.Reference;
import jd.core.model.reference.ReferenceMap;
import jd.core.preferences.Preferences;
import jd.core.process.layouter.visitor.InstructionSplitterVisitor;
import jd.core.process.layouter.visitor.MaxLineNumberVisitor;
import jd.core.util.ClassFileUtil;
import jd.core.util.SignatureUtil;
import jd.core.util.TypeNameUtil;

public class ClassFileLayouter {
    private ClassFileLayouter() {
    }
        public static int Layout(
        Preferences preferences,
        ReferenceMap referenceMap,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        int maxLineNumber = CreateBlocks(
                preferences, referenceMap, classFile, layoutBlockList);

        // "layoutBlockList" contient une structure lineaire classee dans
        // l'ordre naturel sans prendre les contraintes d'alignement.

        if (maxLineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
            preferences.getRealignmentLineNumber())
        {
            LayoutBlocks(layoutBlockList);
        }

        return maxLineNumber;
    }

    private static int CreateBlocks(
        Preferences preferences,
        ReferenceMap referenceMap,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        boolean separator = true;

        // Layout package statement
        String internalPackageName = classFile.getInternalPackageName();
        if (internalPackageName != null && !internalPackageName.isEmpty())
        {
            layoutBlockList.add(new PackageLayoutBlock(classFile));
            layoutBlockList.add(
                new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 2));
            separator = false;
        }

        // Layout import statements
        int importCount = GetImportCount(referenceMap, classFile);
        if (importCount > 0)
        {
            layoutBlockList.add(new ImportsLayoutBlock(
                classFile, importCount-1));
            layoutBlockList.add(new SeparatorLayoutBlock(
                LayoutBlockConstants.SEPARATOR_AFTER_IMPORTS, 2));
            separator = false;
        }

        if (separator)
        {
            layoutBlockList.add(new SeparatorLayoutBlock(
                LayoutBlockConstants.SEPARATOR_AT_BEGINING, 0));
        }

        // Layout class
        return CreateBlocksForClass(preferences, classFile, layoutBlockList);
    }

    private static int GetImportCount(
        ReferenceMap referenceMap, ClassFile classFile)
    {
        Collection<Reference> collection = referenceMap.values();

        if (collection.isEmpty()) {
            return 0;
        }
        int importCount = 0;
        String internalPackageName = classFile.getInternalPackageName();
        Iterator<Reference> iterator = collection.iterator();
        String internalReferencePackageName;
        // Filtrage
        while (iterator.hasNext())
        {
            internalReferencePackageName = TypeNameUtil.InternalTypeNameToInternalPackageName(
                iterator.next().getInternalName());

            // No import for same package classes
            // No import for 'java/lang' classes
            if (internalReferencePackageName.equals(internalPackageName) || StringConstants.INTERNAL_JAVA_LANG_PACKAGE_NAME.equals(
                    internalReferencePackageName))
            {
                continue;
            }

            importCount++;
        }
        return importCount;
    }

    private static int CreateBlocksForClass(
        Preferences preferences,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        MarkerLayoutBlock tmslb = new MarkerLayoutBlock(
            LayoutBlockConstants.TYPE_MARKER_START, classFile);
        layoutBlockList.add(tmslb);

        boolean displayExtendsOrImplementsFlag =
            CreateBlocksForHeader(classFile, layoutBlockList);

        TypeBodyBlockStartLayoutBlock bbslb = new TypeBodyBlockStartLayoutBlock();
        layoutBlockList.add(bbslb);

        int layoutBlockListLength = layoutBlockList.size();

        int maxLineNumber = CreateBlocksForBody(
            preferences, classFile,
            layoutBlockList);

        if (layoutBlockListLength == layoutBlockList.size())
        {
            // Classe vide. Transformation du bloc 'BodyBlockStartLayoutBlock'
            if (displayExtendsOrImplementsFlag) {
                bbslb.transformToStartEndBlock(1);
            } else {
                bbslb.transformToStartEndBlock(0);
            }
        }
        else
        {
            TypeBodyBlockEndLayoutBlock bbelb = new TypeBodyBlockEndLayoutBlock();
            bbslb.other = bbelb;
            bbelb.other = bbslb;
            layoutBlockList.add(bbelb);
        }

        MarkerLayoutBlock tmelb = new MarkerLayoutBlock(
            LayoutBlockConstants.TYPE_MARKER_END, classFile);
        tmslb.other = tmelb;
        tmelb.other = tmslb;
        layoutBlockList.add(tmelb);

        return maxLineNumber;
    }

    private static boolean CreateBlocksForHeader(
        ClassFile classFile, List<LayoutBlock> layoutBlockList)
    {
        boolean displayExtendsOrImplementsFlag = false;

        if (classFile.containsAttributeDeprecated() &&
            !classFile.containsAnnotationDeprecated(classFile))
        {
            layoutBlockList.add(new CommentDeprecatedLayoutBlock());
        }

        // Affichage des attributs de la classe
        //LayoutAttributes(
        //	layoutBlockList, classFile, classFile.getAttributes());

        // Affichage des annotations de la classe
        AnnotationLayouter.CreateBlocksForAnnotations(
            classFile, classFile.getAttributes(), layoutBlockList);

        // Affichage de la classe, de l'interface, de l'enum ou de l'annotation
         // Check annotation
        AttributeSignature as = classFile.getAttributeSignature();
        if (as == null)
        {
            layoutBlockList.add(new TypeNameLayoutBlock(classFile));

            if ((classFile.access_flags & ClassFileConstants.ACC_ANNOTATION) == 0) {
                if ((classFile.access_flags & ClassFileConstants.ACC_ENUM) != 0)
                {
                    // Enum
                     // Interfaces
                    displayExtendsOrImplementsFlag =
                        CreateBlocksForInterfacesImplements(
                            classFile, layoutBlockList);
                }
                else if ((classFile.access_flags & ClassFileConstants.ACC_INTERFACE) != 0)
                {
                    // Interface
                     // Super interface
                    int[] interfaceIndexes = classFile.getInterfaces();
                    if (interfaceIndexes != null && interfaceIndexes.length > 0)
                    {
                        displayExtendsOrImplementsFlag = true;
                        layoutBlockList.add(
                            new ExtendsSuperInterfacesLayoutBlock(classFile));
                    }
                }
                else
                {
                    // Class
                     // Super class
                    String internalSuperClassName = classFile.getSuperClassName();
                    if (internalSuperClassName != null &&
                        !StringConstants.JAVA_LANG_OBJECT.equals(internalSuperClassName))
                    {
                        displayExtendsOrImplementsFlag = true;
                        layoutBlockList.add(
                            new ExtendsSuperTypeLayoutBlock(classFile));
                    }

                    // Interfaces
                    displayExtendsOrImplementsFlag |=
                        CreateBlocksForInterfacesImplements(
                            classFile, layoutBlockList);
                }
            }
        }
        else
        {
            // Signature contenant des notations generiques
            ConstantPool constants = classFile.getConstantPool();
            String signature = constants.getConstantUtf8(as.signature_index);
            displayExtendsOrImplementsFlag =
                SignatureLayouter.CreateLayoutBlocksForClassSignature(
                    classFile, signature, layoutBlockList);
        }

        return displayExtendsOrImplementsFlag;
    }

    private static boolean CreateBlocksForInterfacesImplements(
        ClassFile classFile, List<LayoutBlock> layoutBlockList)
    {
        int[] interfaceIndexes = classFile.getInterfaces();

        if (interfaceIndexes != null && interfaceIndexes.length > 0)
        {
            layoutBlockList.add(
                new ImplementsInterfacesLayoutBlock(classFile));

            return true;
        }
        return false;
    }

    public static int CreateBlocksForBodyOfAnonymousClass(
        Preferences preferences,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        InnerTypeBodyBlockStartLayoutBlock ibbslb =
            new InnerTypeBodyBlockStartLayoutBlock();
        layoutBlockList.add(ibbslb);

        int layoutBlockListLength = layoutBlockList.size();

        int maxLineNumber = CreateBlocksForBody(
                preferences, classFile, layoutBlockList);

        if (layoutBlockListLength == layoutBlockList.size())
        {
            // Classe vide. Transformation du bloc 'BodyBlockStartLayoutBlock'
            ibbslb.transformToStartEndBlock();
        }
        else
        {
            InnerTypeBodyBlockEndLayoutBlock ibbelb =
                new InnerTypeBodyBlockEndLayoutBlock();
            ibbslb.other = ibbelb;
            ibbelb.other = ibbslb;
            layoutBlockList.add(ibbelb);
        }

        return maxLineNumber;
    }

    private static int CreateBlocksForBody(
        Preferences preferences,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        CreateBlockForEnumValues(preferences, classFile, layoutBlockList);

        List<SubListLayoutBlock> sortedFieldBlockList =
            CreateSortedBlocksForFields(preferences, classFile);
        List<SubListLayoutBlock> sortedMethodBlockList =
            CreateSortedBlocksForMethods(preferences, classFile);
        List<SubListLayoutBlock> sortedInnerClassBlockList =
            CreateSortedBlocksForInnerClasses(preferences, classFile);

        return MergeBlocks(
            layoutBlockList, sortedFieldBlockList,
            sortedMethodBlockList, sortedInnerClassBlockList);
    }

    private static void CreateBlockForEnumValues(
        Preferences preferences,
        ClassFile classFile,
        List<LayoutBlock> layoutBlockList)
    {
        List<Instruction> values = classFile.getEnumValues();

        if (values != null)
        {
            int valuesLength = values.size();

            if (valuesLength > 0)
            {
                ConstantPool constants = classFile.getConstantPool();
                Field[] fields = classFile.getFields();
                int fieldsLength = fields.length;
                List<InvokeNew> enumValues =
                    new ArrayList<>(fieldsLength);

                InstructionSplitterVisitor visitor =
                    new InstructionSplitterVisitor();

                GetStatic getStatic;
                ConstantFieldref cfr;
                ConstantNameAndType cnat;
                int j;
                Field field;
                ValueAndMethod vam;
                InvokeNew invokeNew;
                // Pour chaque valeur, recherche du l'attribut d'instance, puis
                // du constructeur
                for (int i=0; i<valuesLength; i++)
                {
                    getStatic = (GetStatic)values.get(i);
                    cfr = constants.getConstantFieldref(getStatic.index);
                    cnat = constants.getConstantNameAndType(
                        cfr.name_and_type_index);

                    j = fields.length;

                    while (j-- > 0)
                    {
                        field = fields[j];

                        if (field.name_index != cnat.name_index ||
                            field.descriptor_index != cnat.descriptor_index) {
                            continue;
                        }

                        vam = field.getValueAndMethod();
                        invokeNew = (InvokeNew)vam.getValue();

                        invokeNew.transformToEnumValue(getStatic);

                        enumValues.add(invokeNew);
                        break;
                    }
                }

                int length = enumValues.size();

                if (length > 0)
                {
                    // Affichage des valeurs
                    InvokeNew enumValue = enumValues.get(0);

                    visitor.start(
                        preferences, layoutBlockList, classFile,
                        classFile.getStaticMethod(), enumValue);
                    visitor.visit(enumValue);
                    visitor.end();

                    for (int i=1; i<length; i++)
                    {
                        layoutBlockList.add(new FragmentLayoutBlock(
                            LayoutBlockConstants.FRAGMENT_COMA_SPACE));
                        layoutBlockList.add(new SeparatorLayoutBlock(
                            LayoutBlockConstants.SEPARATOR, 0));

                        enumValue = enumValues.get(i);

                        visitor.start(
                            preferences, layoutBlockList, classFile,
                            classFile.getStaticMethod(), enumValue);
                        visitor.visit(enumValue);
                        visitor.end();
                    }

                    layoutBlockList.add(new FragmentLayoutBlock(
                        LayoutBlockConstants.FRAGMENT_SEMICOLON));
                }
            }
        }
    }

    /**
     * @return liste de sequences de 'LayoutBlock'
     * Sequence produite pour chaque champ:
     *  - FieldBlockStartLayoutBlock
     *  -  CommentDeprecatedLayoutBlock ?
     *  -  AnnotationsLayoutBlock ?
     *  -  FieldLayoutBlock
     *  -  InstructionsLayoutBlock ?
     *  - FieldBlockEndLayoutBlock
     */
    private static List<SubListLayoutBlock> CreateSortedBlocksForFields(
        Preferences preferences, ClassFile classFile)
    {
        Field[] fields = classFile.getFields();

        if (fields == null)
        {
            return Collections.emptyList();
        }
        // Creation des 'FieldLayoutBlock'
        int length = fields.length;
        List<SubListLayoutBlock> sortedFieldBlockList =
            new ArrayList<>(length);
        InstructionSplitterVisitor visitor =
            new InstructionSplitterVisitor();
        Field field;
        List<LayoutBlock> subLayoutBlockList;
        MarkerLayoutBlock fmslb;
        int firstLineNumber;
        int lastLineNumber;
        int preferedLineNumber;
        MarkerLayoutBlock fmelb;
        for (int i=0; i<length; i++)
        {
            field = fields[i];

            if ((field.access_flags & (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_ENUM)) != 0) {
                continue;
            }

            subLayoutBlockList = new ArrayList<>(6);

            fmslb = new MarkerLayoutBlock(
                LayoutBlockConstants.FIELD_MARKER_START, classFile);
            subLayoutBlockList.add(fmslb);

//            	WriteAttributes(
//            		spw, referenceMap, classFile, field.getAttributes());

            if (field.containsAttributeDeprecated() &&
                !field.containsAnnotationDeprecated(classFile))
            {
                subLayoutBlockList.add(new CommentDeprecatedLayoutBlock());
            }

            AnnotationLayouter.CreateBlocksForAnnotations(
                classFile, field.getAttributes(), subLayoutBlockList);

            subLayoutBlockList.add(new FieldNameLayoutBlock(classFile, field));

            firstLineNumber = Instruction.UNKNOWN_LINE_NUMBER;
            lastLineNumber = Instruction.UNKNOWN_LINE_NUMBER;
            preferedLineNumber = LayoutBlockConstants.UNLIMITED_LINE_COUNT;

            if (field.getValueAndMethod() != null)
            {
                ValueAndMethod valueAndMethod = field.getValueAndMethod();
                Instruction value = valueAndMethod.getValue();
                Method method = valueAndMethod.getMethod();

                firstLineNumber = value.lineNumber;
                lastLineNumber = MaxLineNumberVisitor.visit(value);
                preferedLineNumber = lastLineNumber - firstLineNumber;

                // Affichage des instructions d'initialisation des valeurs
                visitor.start(
                    preferences, subLayoutBlockList, classFile, method, value);
                visitor.visit(value);
                visitor.end();

                subLayoutBlockList.add(new FragmentLayoutBlock(
                    LayoutBlockConstants.FRAGMENT_SEMICOLON));
            }

            fmelb = new MarkerLayoutBlock(
                LayoutBlockConstants.FIELD_MARKER_END, classFile);
            fmslb.other = fmelb;
            fmelb.other = fmslb;
            subLayoutBlockList.add(fmelb);

            sortedFieldBlockList.add(new SubListLayoutBlock(
                LayoutBlockConstants.SUBLIST_FIELD,
                subLayoutBlockList, firstLineNumber,
                lastLineNumber, preferedLineNumber));
        }
        return SortBlocks(sortedFieldBlockList);
    }

    /**
     * @return liste de sequences de 'LayoutBlock'
     * Sequence produite pour chaque méthode:
     *  - MethodBlockStartLayoutBlock
     *  -  CommentDeprecatedLayoutBlock ?
     *  -  AnnotationsLayoutBlock ?
     *  -  MethodLayoutBlock
     *  -  ThrowsLayoutBlock ?
     *  -  StatementsBlockStartLayoutBlock ?
     *  -   StatementsLayoutBlock *
     *  -  StatementsBlockEndLayoutBlock ?
     *  - MethodBlockEndLayoutBlock
     */
    private static List<SubListLayoutBlock> CreateSortedBlocksForMethods(
        Preferences preferences, ClassFile classFile)
    {
        Method[] methods = classFile.getMethods();

        if (methods == null)
        {
            return Collections.emptyList();
        }
        // Creation des 'MethodLayoutBlock'
        ConstantPool constants = classFile.getConstantPool();
        boolean multipleConstructorFlag =
            ClassFileUtil.ContainsMultipleConstructor(classFile);
        int length = methods.length;
        List<SubListLayoutBlock> sortedMethodBlockList =
            new ArrayList<>(length);
        boolean showDefaultConstructor =
            preferences.getShowDefaultConstructor();
        JavaSourceLayouter javaSourceLayouter = new JavaSourceLayouter();
        Method method;
        AttributeSignature as;
        int signatureIndex;
        String signature;
        List<LayoutBlock> subLayoutBlockList;
        MarkerLayoutBlock mmslb;
        boolean nullCodeFlag;
        boolean displayThrowsFlag;
        int firstLineNumber;
        int lastLineNumber;
        int preferedLineNumber;
        MarkerLayoutBlock mmelb;
        for (int i=0; i<length; i++)
        {
            method = methods[i];

            if ((method.access_flags &
                    (ClassFileConstants.ACC_SYNTHETIC|ClassFileConstants.ACC_BRIDGE)) != 0) {
                continue;
            }

            as = method.getAttributeSignature();

            // Le descripteur et la signature sont differentes pour les
            // constructeurs des Enums ! Cette information est passée à
            // "SignatureWriter.WriteMethodSignature(...)".
            signatureIndex = as == null ?
                    method.descriptor_index : as.signature_index;
            signature = constants.getConstantUtf8(signatureIndex);

            if ((classFile.access_flags & ClassFileConstants.ACC_ENUM) != 0 &&
                ClassFileUtil.IsAMethodOfEnum(classFile, method, signature)) {
                continue;
            }

            if (method.name_index == constants.instanceConstructorIndex)
            {
                if (classFile.getInternalAnonymousClassName() != null) {
                    // Ne pas afficher les constructeurs des classes anonymes.
                    continue;
                }

                if (!multipleConstructorFlag &&
                    (method.getFastNodes() == null ||
                     method.getFastNodes().isEmpty()))
                {
                    int[] exceptionIndexes = method.getExceptionIndexes();

                    if (exceptionIndexes == null ||
                        exceptionIndexes.length == 0)
                    {
                        if ((classFile.access_flags & ClassFileConstants.ACC_ENUM) != 0)
                        {
                            if (SignatureUtil.GetParameterSignatureCount(signature) == 2)
                            {
                                // Ne pas afficher le constructeur par defaut
                                // des Enum si il est vide et si c'est le seul
                                // constructeur.
                                continue;
                            }
                        } else if (!showDefaultConstructor && "()V".equals(signature))
                        {
                            // Ne pas afficher le constructeur par defaut si
                            // il est vide et si c'est le seul constructeur.
                            continue;
                        }
                    }
                }
            }

            if (method.name_index == constants.classConstructorIndex && (method.getFastNodes() == null ||
                method.getFastNodes().isEmpty())) {
                continue;
            }

            subLayoutBlockList = new ArrayList<>(30);

            mmslb = new MarkerLayoutBlock(
                LayoutBlockConstants.METHOD_MARKER_START, classFile);
            subLayoutBlockList.add(mmslb);

//					WriteAttributes(
//						spw, referenceMap, classFile, method.getAttributes());

            if (method.containsError())
            {
                subLayoutBlockList.add(new CommentErrorLayoutBlock());
            }

            if (method.containsAttributeDeprecated() &&
                !method.containsAnnotationDeprecated(classFile))
            {
                subLayoutBlockList.add(new CommentDeprecatedLayoutBlock());
            }

            AnnotationLayouter.CreateBlocksForAnnotations(
                classFile, method.getAttributes(), subLayoutBlockList);

            // Information utilisee par 'PrintWriter' pour afficher un ';'
            // après les méthodes sans code. Evite d'instancier un object
            // 'EmptyCodeLayoutBlock'.
            nullCodeFlag = method.getCode() == null;
            displayThrowsFlag = false;

            if (method.name_index == constants.classConstructorIndex)
            {
                subLayoutBlockList.add(new MethodStaticLayoutBlock(classFile));
            } else if (method.getExceptionIndexes() == null)
            {
                subLayoutBlockList.add(new MethodNameLayoutBlock(
                    classFile, method, signature,
                    as == null, nullCodeFlag));
            }
            else
            {
                subLayoutBlockList.add(new MethodNameLayoutBlock(
                    classFile, method, signature,
                    as == null, false));

                subLayoutBlockList.add(new ThrowsLayoutBlock(
                    classFile, method, nullCodeFlag));

                displayThrowsFlag = true;
            }

            firstLineNumber = Instruction.UNKNOWN_LINE_NUMBER;
            lastLineNumber = Instruction.UNKNOWN_LINE_NUMBER;
            preferedLineNumber = LayoutBlockConstants.UNLIMITED_LINE_COUNT;

            if (!nullCodeFlag)
            {
                // DEBUG //
                if (method.containsError())
                {
                    MethodBodyBlockStartLayoutBlock mbbslb =
                        new MethodBodyBlockStartLayoutBlock();
                    subLayoutBlockList.add(mbbslb);
                    subLayoutBlockList.add(
                        new ByteCodeLayoutBlock(classFile, method));
                    MethodBodyBlockEndLayoutBlock mbbelb =
                        new MethodBodyBlockEndLayoutBlock();
                    subLayoutBlockList.add(mbbelb);
                    mbbslb.other = mbbelb;
                    mbbelb.other = mbbslb;
                }
                // DEBUG //
                else
                {
                    List<Instruction> list = method.getFastNodes();

                    MethodBodyBlockStartLayoutBlock mbbslb =
                        new MethodBodyBlockStartLayoutBlock();
                    subLayoutBlockList.add(mbbslb);

                    int subLayoutBlockListLength = subLayoutBlockList.size();
                    boolean singleLine = false;

                    if (!list.isEmpty())
                    {
                        try
                        {
                            int beforeIndex = subLayoutBlockList.size();
                            singleLine = javaSourceLayouter.createBlocks(
                                preferences, subLayoutBlockList,
                                classFile, method, list);
                            int afterIndex = subLayoutBlockList.size();

                            firstLineNumber = SearchFirstLineNumber(
                                subLayoutBlockList, beforeIndex, afterIndex);
                            lastLineNumber = SearchLastLineNumber(
                                subLayoutBlockList, beforeIndex, afterIndex);
                        }
                        catch (Exception e)
                        {
                            assert ExceptionUtil.printStackTrace(e);
                            // Erreur durant l'affichage => Retrait de tous
                            // les blocs
                            int currentLength = subLayoutBlockList.size();
                            while (currentLength > subLayoutBlockListLength) {
                                currentLength--;
                                subLayoutBlockList.remove(currentLength);
                            }

                            subLayoutBlockList.add(
                                new ByteCodeLayoutBlock(classFile, method));
                        }
                    }

                    if (subLayoutBlockListLength == subLayoutBlockList.size())
                    {
                        // Bloc vide d'instructions. Transformation du bloc
                        // 'StatementBlockStartLayoutBlock'
                        if (displayThrowsFlag) {
                            mbbslb.transformToStartEndBlock(1);
                        } else {
                            mbbslb.transformToStartEndBlock(0);
                        }
                    }
                    else if (singleLine)
                    {
                        mbbslb.transformToSingleLineBlock();
                        MethodBodySingleLineBlockEndLayoutBlock mbssbelb =
                            new MethodBodySingleLineBlockEndLayoutBlock();
                        mbbslb.other   = mbssbelb;
                        mbssbelb.other = mbbslb;
                        subLayoutBlockList.add(mbssbelb);
                    }
                    else
                    {
                        MethodBodyBlockEndLayoutBlock mbbelb =
                            new MethodBodyBlockEndLayoutBlock();
                        mbbslb.other = mbbelb;
                        mbbelb.other = mbbslb;
                        subLayoutBlockList.add(mbbelb);
                    }
                } // if (method.containsError()) else
            } // if (nullCodeFlag == false)

            mmelb = new MarkerLayoutBlock(
                LayoutBlockConstants.METHOD_MARKER_END, classFile);
            mmslb.other = mmelb;
            mmelb.other = mmslb;
            subLayoutBlockList.add(mmelb);

            sortedMethodBlockList.add(new SubListLayoutBlock(
                LayoutBlockConstants.SUBLIST_METHOD,
                subLayoutBlockList, firstLineNumber,
                lastLineNumber, preferedLineNumber));
        }
        return SortBlocks(sortedMethodBlockList);
    }

    private static List<SubListLayoutBlock> CreateSortedBlocksForInnerClasses(
        Preferences preferences, ClassFile classFile)
    {
        List<ClassFile> innerClassFiles = classFile.getInnerClassFiles();

        if (innerClassFiles == null)
        {
            return Collections.emptyList();
        }
        int length = innerClassFiles.size();
        List<SubListLayoutBlock> sortedInnerClassBlockList =
            new ArrayList<>(length);
        ClassFile innerClassFile;
        List<LayoutBlock> innerClassLayoutBlockList;
        int afterIndex;
        int firstLineNumber;
        int lastLineNumber;
        int preferedLineCount;
        for (int i=0; i<length; i++)
        {
            innerClassFile = innerClassFiles.get(i);

            if ((innerClassFile.access_flags & ClassFileConstants.ACC_SYNTHETIC) != 0 ||
                innerClassFile.getInternalAnonymousClassName() != null) {
                continue;
            }

            innerClassLayoutBlockList = new ArrayList<>(100);

            CreateBlocksForClass(
                preferences, innerClassFile, innerClassLayoutBlockList);

            afterIndex = innerClassLayoutBlockList.size();

            firstLineNumber = SearchFirstLineNumber(
                innerClassLayoutBlockList, 0, afterIndex);
            lastLineNumber = SearchLastLineNumber(
                innerClassLayoutBlockList, 0, afterIndex);

            preferedLineCount = LayoutBlockConstants.UNLIMITED_LINE_COUNT;
            if (firstLineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER)
            {
                preferedLineCount = lastLineNumber-firstLineNumber;
            }

            sortedInnerClassBlockList.add(new SubListLayoutBlock(
                LayoutBlockConstants.SUBLIST_INNER_CLASS,
                innerClassLayoutBlockList, firstLineNumber,
                lastLineNumber, preferedLineCount));
        }
        return SortBlocks(sortedInnerClassBlockList);
    }

    private static int SearchFirstLineNumber(
        List<LayoutBlock> layoutBlockList, int firstIndex, int afterIndex)
    {
        int firstLineNumber;
        for (int index=firstIndex; index<afterIndex; index++)
        {
            firstLineNumber = layoutBlockList.get(index).firstLineNumber;
            if (firstLineNumber != Instruction.UNKNOWN_LINE_NUMBER) {
                return firstLineNumber;
            }
        }

        return Instruction.UNKNOWN_LINE_NUMBER;
    }

    private static int SearchLastLineNumber(
        List<LayoutBlock> layoutBlockList, int firstIndex, int afterIndex)
    {
        int lastLineNumber;
        while (afterIndex-- > firstIndex)
        {
            lastLineNumber = layoutBlockList.get(afterIndex).lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER) {
                return lastLineNumber;
            }
        }

        return Instruction.UNKNOWN_LINE_NUMBER;
    }

    private static List<SubListLayoutBlock> SortBlocks(
        List<SubListLayoutBlock> blockList)
    {
        // Detection de l'ordre de génération des champs par le compilateur:
        // ascendant (1), descendant (2) ou aleatoire (3)
        int length = blockList.size();
        int lineNumber = Instruction.UNKNOWN_LINE_NUMBER;
        int order = 0;

        SubListLayoutBlock layoutBlock;
        int newLineNumber;
        for (int i=0; i<length; i++)
        {
            layoutBlock = blockList.get(i);
            newLineNumber = layoutBlock.lastLineNumber;

            if (newLineNumber != Instruction.UNKNOWN_LINE_NUMBER)
            {
                if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                {
                    if (order == 0) // Unknown
                    {
                        order = (lineNumber < newLineNumber) ? 1 : 2;
                    }
                    else if (order == 1) // Asc
                    {
                        if (lineNumber > newLineNumber)
                        {
                            order = 3; // Aleatoire
                            break;
                        }
                    } else if (order == 2 && lineNumber < newLineNumber)
                    {
                        order = 3; // Aleatoire
                        break;
                    }
                }

                lineNumber = newLineNumber;
            }
        }

        // Trie
        if (order == 2) {
            Collections.reverse(blockList);
        } else if (order == 3) {
            for (int i=0; i<length; i++)
            {
                blockList.get(i).index = i;
            }
            // Tri par ordre croissant, les blocs sans numero de ligne
            // sont places a la fin.
            Collections.sort(blockList, new LayoutBlockComparator());
        }

        return blockList;
    }
    /* POURQUOI AVOIR UTILISE UNE SIGNATURE SI COMPLEXE A CONVERTIR EN C++ ?
     * private static <T extends LayoutBlock> List<T> SortBlocks(List<T> blockList)
    {
        // Detection de l'ordre de génération des champs par le compilateur:
        // ascendant (1), descendant (2) ou aleatoire (3)
        int length = blockList.size();
        int lineNumber = Instruction.UNKNOWN_LINE_NUMBER;
        int order = 0;

        for (int i=0; i<length; i++)
        {
            T layoutBlock = blockList.get(i);
            int newLineNumber = layoutBlock.lastLineNumber;

            if (newLineNumber != Instruction.UNKNOWN_LINE_NUMBER)
            {
                if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                {
                    if (order == 0) // Unknown
                    {
                        order = (lineNumber < newLineNumber) ? 1 : 2;
                    }
                    else if (order == 1) // Asc
                    {
                        if (lineNumber > newLineNumber)
                        {
                            order = 3; // Aleatoire
                            break;
                        }
                    }
                    else if (order == 2) // Desc
                    {
                        if (lineNumber < newLineNumber)
                        {
                            order = 3; // Aleatoire
                            break;
                        }
                    }
                }

                lineNumber = newLineNumber;
            }
        }

        // Trie
        switch (order)
        {
        case 2: // Desc
            Collections.reverse(blockList);
            break;
        case 3: // Aleatoire
            // Tri par ordre croissant, les blocs sans numero de ligne
            // sont places a la fin.
            Collections.sort(blockList, new LayoutBlockComparator());
            break;
        }

        return blockList;
    } */

    /** Premiere phase du realignement,
     * 3 jeux de cartes,
     * Conserver l'ordre naturel jusqu'à une impossibilite:
     * Copie des blocs sans numero de ligne des champs au plus tot
     * Copie des blocs sans numero de ligne des méthodes et des classes internes au plus tard
     */
    private static int MergeBlocks(
        List<LayoutBlock> layoutBlockList,
        List<SubListLayoutBlock> sortedFieldBlockList,
        List<SubListLayoutBlock> sortedMethodBlockList,
        List<SubListLayoutBlock> sortedInnerClassBlockList)
    {
        int maxLineNumber = Instruction.UNKNOWN_LINE_NUMBER;

        Collections.reverse(sortedFieldBlockList);
        Collections.reverse(sortedMethodBlockList);
        Collections.reverse(sortedInnerClassBlockList);

        // Recherche du bloc ayant un numero de ligne defini
        int minLineNumberMethod =
            SearchMinimalLineNumber(sortedMethodBlockList);
        int minLineNumberInnerClass =
            SearchMinimalLineNumber(sortedInnerClassBlockList);

        // Fusion des jeux de cartes
        // 1) Champs
        while (!sortedFieldBlockList.isEmpty())
        {
            if (minLineNumberMethod == Instruction.UNKNOWN_LINE_NUMBER)
            {
                if (minLineNumberInnerClass == Instruction.UNKNOWN_LINE_NUMBER)
                {
                    // Copie de tout dans l'ordre naturel
                    maxLineNumber = MergeFieldBlockList(
                        layoutBlockList, sortedFieldBlockList, maxLineNumber);
                    break;
                }
                // Copie des champs avec et sans numero de ligne
                maxLineNumber = ExclusiveMergeFieldBlockList(
                    layoutBlockList, sortedFieldBlockList,
                    minLineNumberInnerClass, maxLineNumber);
                // Copie de toutes les méthodes sans numero de ligne
                maxLineNumber = MergeBlockList(
                    layoutBlockList, sortedMethodBlockList, maxLineNumber);
                // Copie des classes internes jusqu'à l'inner classe ayant
                // le plus petit numero de ligne
                maxLineNumber = InclusiveMergeBlockList(
                    layoutBlockList, sortedInnerClassBlockList,
                    minLineNumberInnerClass, maxLineNumber);
                minLineNumberInnerClass =
                    SearchMinimalLineNumber(sortedInnerClassBlockList);
            } else if (minLineNumberInnerClass == Instruction.UNKNOWN_LINE_NUMBER ||
                minLineNumberMethod < minLineNumberInnerClass)
            {
                // Copie des champs avec et sans numero de ligne
                maxLineNumber = ExclusiveMergeFieldBlockList(
                    layoutBlockList, sortedFieldBlockList,
                    minLineNumberMethod, maxLineNumber);
                // Copie des méthodes jusqu'à la méthode ayant le plus
                // petit numero de ligne
                maxLineNumber = InclusiveMergeBlockList(
                    layoutBlockList, sortedMethodBlockList,
                    minLineNumberMethod, maxLineNumber);
                minLineNumberMethod =
                    SearchMinimalLineNumber(sortedMethodBlockList);
            }
            else
            {
                // Copie des champs avec et sans numero de ligne
                maxLineNumber = ExclusiveMergeFieldBlockList(
                    layoutBlockList, sortedFieldBlockList,
                    minLineNumberInnerClass, maxLineNumber);
                // Copie des méthodes avec et sans numero de ligne
                maxLineNumber = ExclusiveMergeMethodOrInnerClassBlockList(
                    layoutBlockList, sortedMethodBlockList,
                    minLineNumberInnerClass, maxLineNumber);
                // Copie des classes internes jusqu'à l'inner classe ayant
                // le plus petit numero de ligne
                maxLineNumber = InclusiveMergeBlockList(
                    layoutBlockList, sortedInnerClassBlockList,
                    minLineNumberInnerClass, maxLineNumber);
                minLineNumberInnerClass =
                    SearchMinimalLineNumber(sortedInnerClassBlockList);
            }
        }

        // 2) Methodes
        while (!sortedMethodBlockList.isEmpty())
        {
            if (minLineNumberInnerClass == Instruction.UNKNOWN_LINE_NUMBER)
            {
                maxLineNumber = MergeBlockList(
                    layoutBlockList, sortedMethodBlockList, maxLineNumber);
                break;
            }
            // Copie des méthodes avec et sans numero de ligne
            maxLineNumber = ExclusiveMergeMethodOrInnerClassBlockList(
                layoutBlockList, sortedMethodBlockList,
                minLineNumberInnerClass, maxLineNumber);
            // Copie des classes internes jusqu'à l'inner classe ayant le
            // plus petit numero de ligne
            maxLineNumber = InclusiveMergeBlockList(
                layoutBlockList, sortedInnerClassBlockList,
                minLineNumberInnerClass, maxLineNumber);
            minLineNumberInnerClass =
                SearchMinimalLineNumber(sortedInnerClassBlockList);
        }

        return MergeBlockList(
            layoutBlockList, sortedInnerClassBlockList, maxLineNumber);
    }

    private static int ExclusiveMergeMethodOrInnerClassBlockList(
        List<LayoutBlock> destination,
        List<SubListLayoutBlock> source,
        int minLineNumber, int maxLineNumber)
    {
        byte lastTag = destination.get(destination.size()-1).tag;
        int index = source.size();

        SubListLayoutBlock sllb;
        int lineNumber;
        int lastLineNumber;
        while (index > 0)
        {
            sllb = source.get(index-1);
            lineNumber = sllb.lastLineNumber;

            if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                lineNumber >= minLineNumber) {
                break;
            }

            // Add separator
            switch (lastTag)
            {
            case LayoutBlockConstants.FIELD_MARKER_END:
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 1));
                break;
//			case LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK:
            case LayoutBlockConstants.TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START:
            case LayoutBlockConstants.STATEMENTS_BLOCK_START:
            case LayoutBlockConstants.SWITCH_BLOCK_START:
            case LayoutBlockConstants.SEPARATOR:
                break;
            default:
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 2));
                break;
            }

            // Move item
            destination.addAll(sllb.subList);

            // Store last line number
            lastLineNumber = sllb.lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER && (maxLineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                maxLineNumber < lastLineNumber))
            {
                maxLineNumber = lastLineNumber;
            }

            index--;
            source.remove(index);

            // Store last tag
            lastTag = LayoutBlockConstants.UNDEFINED;
        }

        return maxLineNumber;
    }

    private static int ExclusiveMergeFieldBlockList(
        List<LayoutBlock> destination,
        List<SubListLayoutBlock> source,
        int minLineNumber, int maxLineNumber)
    {
        byte lastTag = destination.get(destination.size()-1).tag;
        int index = source.size();

        SubListLayoutBlock sllb;
        int lineNumber;
        int lastLineNumber;
        while (index > 0)
        {
            sllb = source.get(index-1);
            lineNumber = sllb.lastLineNumber;

            if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                lineNumber >= minLineNumber) {
                break;
            }

            // Add separator
            switch (lastTag)
            {
            case LayoutBlockConstants.FIELD_MARKER_END:
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 1));
                break;
//			case LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK:
            case LayoutBlockConstants.TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START:
            case LayoutBlockConstants.STATEMENTS_BLOCK_START:
            case LayoutBlockConstants.SWITCH_BLOCK_START:
            case LayoutBlockConstants.SEPARATOR:
                break;
            default:
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 2));
                break;
            }

            index--;
            // Move item
            source.remove(index);
            destination.addAll(sllb.subList);

            // Store last line number
            lastLineNumber = sllb.lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER && (maxLineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                maxLineNumber < lastLineNumber))
            {
                maxLineNumber = lastLineNumber;
            }

            // Store last tag
            lastTag = LayoutBlockConstants.FIELD_MARKER_END;
        }

        return maxLineNumber;
    }

    private static int InclusiveMergeBlockList(
        List<LayoutBlock> destination,
        List<SubListLayoutBlock> source,
        int minLineNumber, int maxLineNumber)
    {
        byte lastTag = destination.get(destination.size()-1).tag;
        int index = source.size();

        SubListLayoutBlock sllb;
        int lineNumber;
        int lastLineNumber;
        // Deplacement
        while (index > 0)
        {
            sllb = source.get(index-1);
            lineNumber = sllb.lastLineNumber;

            if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                lineNumber > minLineNumber) {
                break;
            }

            // Add separator
            if (lastTag != LayoutBlockConstants.TYPE_BODY_BLOCK_START && lastTag != LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START && lastTag != LayoutBlockConstants.METHOD_BODY_BLOCK_START
                    && lastTag != LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START && lastTag != LayoutBlockConstants.STATEMENTS_BLOCK_START && lastTag != LayoutBlockConstants.SWITCH_BLOCK_START
                    && lastTag != LayoutBlockConstants.SEPARATOR) {
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 2));
            }

            // Move item
            destination.addAll(sllb.subList);

            // Store last line number
            lastLineNumber = sllb.lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER && (maxLineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                maxLineNumber < lastLineNumber))
            {
                maxLineNumber = lastLineNumber;
            }

            index--;
            source.remove(index);

            if (lineNumber == minLineNumber) {
                break;
            }

            // Store last tag
            lastTag = LayoutBlockConstants.UNDEFINED;
        }

        return maxLineNumber;
    }

    private static int MergeBlockList(
        List<LayoutBlock> destination,
        List<SubListLayoutBlock> source,
        int maxLineNumber)
    {
        byte lastTag = destination.get(destination.size()-1).tag;
        int index = source.size();

        SubListLayoutBlock sllb;
        int lastLineNumber;
        while (index-- > 0)
        {
            // Add separator
            if (lastTag != LayoutBlockConstants.TYPE_BODY_BLOCK_START && lastTag != LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START && lastTag != LayoutBlockConstants.METHOD_BODY_BLOCK_START
                    && lastTag != LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START && lastTag != LayoutBlockConstants.STATEMENTS_BLOCK_START && lastTag != LayoutBlockConstants.SWITCH_BLOCK_START
                    && lastTag != LayoutBlockConstants.SEPARATOR) {
                destination.add(new SeparatorLayoutBlock(
                    LayoutBlockConstants.SEPARATOR, 2));
            }

            // Move item
            sllb = source.remove(index);
            destination.addAll(sllb.subList);

            // Store last line number
            lastLineNumber = sllb.lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER && (maxLineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                maxLineNumber < lastLineNumber))
            {
                maxLineNumber = lastLineNumber;
            }

            // Store last tag
            lastTag = LayoutBlockConstants.UNDEFINED;
        }

        return maxLineNumber;
    }

    private static int MergeFieldBlockList(
        List<LayoutBlock> destination,
        List<SubListLayoutBlock> source,
        int maxLineNumber)
    {
        byte lastTag = destination.get(destination.size()-1).tag;
        int index = source.size();

        SubListLayoutBlock sllb;
        int lastLineNumber;
        while (index-- > 0)
        {
            // Add separator
            switch (lastTag)
            {
            case LayoutBlockConstants.FIELD_MARKER_END:
                destination.add(
                    new SeparatorLayoutBlock(LayoutBlockConstants.SEPARATOR, 1));
                break;
//			case LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK:
            case LayoutBlockConstants.TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_BLOCK_START:
            case LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START:
            case LayoutBlockConstants.STATEMENTS_BLOCK_START:
            case LayoutBlockConstants.SWITCH_BLOCK_START:
            case LayoutBlockConstants.SEPARATOR:
                break;
            default:
                destination.add(new SeparatorLayoutBlock(
                    LayoutBlockConstants.SEPARATOR, 2));
                break;
            }

            // Move item
            sllb = source.remove(index);
            destination.addAll(sllb.subList);

            // Store last line number
            lastLineNumber = sllb.lastLineNumber;
            if (lastLineNumber != Instruction.UNKNOWN_LINE_NUMBER && (maxLineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                maxLineNumber < lastLineNumber))
            {
                maxLineNumber = lastLineNumber;
            }

            // Store last tag
            lastTag = LayoutBlockConstants.FIELD_MARKER_END;
        }

        return maxLineNumber;
    }

    /** La liste est classee en ordre inverse. */
    private static int SearchMinimalLineNumber(List<? extends LayoutBlock> list)
    {
        int index = list.size();

        int lineNumber;
        while (index-- > 0)
        {
            lineNumber = list.get(index).lastLineNumber;
            if (lineNumber != Instruction.UNKNOWN_LINE_NUMBER) {
                return lineNumber;
            }
        }

        return Instruction.UNKNOWN_LINE_NUMBER;
    }

    private static void LayoutBlocks(List<LayoutBlock> layoutBlockList)
    {
        // DEBUG // long time0 = System.currentTimeMillis();

        // Initialize
        List<LayoutSection> layoutSectionList =
            new ArrayList<>();

        CreateSections(layoutBlockList, layoutSectionList);
        InitializeBlocks(layoutBlockList, layoutSectionList);

        int layoutCount = 20;

        do
        {
            // Layout
            LayoutSections(layoutBlockList, layoutSectionList);

            // Score
            ScoreSections(layoutBlockList, layoutSectionList);

            // Slice
            if (!SliceDownBlocks(layoutBlockList, layoutSectionList)) {
                break;
            }

            ResetLineCounts(layoutBlockList, layoutSectionList);
        }
        while (layoutCount-- > 0);

        // DEBUG // System.err.println("LayoutBlocks: Nbr de boucles: " + (20-layoutCount));

        layoutCount = 20;

        do
        {
            // Layout
            LayoutSections(layoutBlockList, layoutSectionList);

            // Score
            ScoreSections(layoutBlockList, layoutSectionList);

            // Slice
            if (!SliceUpBlocks(layoutBlockList, layoutSectionList)) {
                break;
            }

            ResetLineCounts(layoutBlockList, layoutSectionList);
        }
        while (layoutCount-- > 0);

        // DEBUG // System.err.println("LayoutBlocks: Nbr de boucles: " + (20-layoutCount));

        // DEBUG // long time1 = System.currentTimeMillis();
        // DEBUG // System.err.println("LayoutBlocks: Temps: " + (time1-time0) + "ms");
    }

    private static void CreateSections(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        int blockLength = layoutBlockList.size();

        // Layout
        int layoutSectionListSize = 0;
        int firstBlockIndex = 0;
        int firstLineNumber = 1;
        boolean containsError = false;

        LayoutBlock lb;
        for (int blockIndex=1; blockIndex<blockLength; blockIndex++)
        {
            lb = layoutBlockList.get(blockIndex);

            if (lb.tag == LayoutBlockConstants.BYTE_CODE)
            {
                containsError = true;
            }

            if (lb.firstLineNumber != Instruction.UNKNOWN_LINE_NUMBER)
            {
                if (firstLineNumber > lb.firstLineNumber) {
                    containsError = true;
                }
                layoutSectionList.add(new LayoutSection(
                    layoutSectionListSize,
                    firstBlockIndex, blockIndex-1,
                    firstLineNumber, lb.firstLineNumber,
                    containsError));
                layoutSectionListSize++;
                firstBlockIndex = blockIndex+1;
                firstLineNumber = lb.lastLineNumber;
                containsError = false;
            }
        }

        if (firstBlockIndex < blockLength-1)
        {
            layoutSectionList.add(new LayoutSection(
                layoutSectionListSize,
                firstBlockIndex, blockLength-1,
                firstLineNumber, Instruction.UNKNOWN_LINE_NUMBER,
                containsError));
        }
    }

    private static void InitializeBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        // Initialize indexes & sections
        int blockIndex = 0;
        int sectionLength = layoutSectionList.size();

        LayoutSection section;
        int lastBlockIndex;
        LayoutBlock lb;
        for (int sectionIndex=0; sectionIndex<sectionLength; sectionIndex++)
        {
            section = layoutSectionList.get(sectionIndex);
            lastBlockIndex = section.lastBlockIndex;

            for (blockIndex = section.firstBlockIndex;
                 blockIndex <= lastBlockIndex;
                 blockIndex++)
            {
                lb = layoutBlockList.get(blockIndex);
                lb.index = blockIndex;
                lb.section = section;
            }
        }
    }

    private static void ResetLineCounts(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        // Initialize indexes & sections
        int sectionLength = layoutSectionList.size();

        LayoutSection section;
        for (int sectionIndex=0; sectionIndex<sectionLength; sectionIndex++)
        {
            section = layoutSectionList.get(sectionIndex);

            if (section.relayout)
            {
                int lastBlockIndex = section.lastBlockIndex;

                LayoutBlock lb;
                for (int blockIndex = section.firstBlockIndex;
                         blockIndex <= lastBlockIndex;
                         blockIndex++)
                {
                    lb = layoutBlockList.get(blockIndex);
                    lb.lineCount = lb.preferedLineCount;
                }
            }
        }
    }

    private static void LayoutSections(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        // Layout sections
        int sectionLength = layoutSectionList.size();

        if (sectionLength > 0)
        {
            sectionLength--;

            int layoutCount = 5;
            boolean redo;

            do
            {
                redo = false;

                LayoutSection section;
                // Mise en page avec heuristiques
                for (int sectionIndex=0; sectionIndex<sectionLength; sectionIndex++)
                {
                    section = layoutSectionList.get(sectionIndex);

                    if (section.relayout && !section.containsError)
                    {
                        section.relayout = false;

                        int originalLineCount = section.originalLineCount;
                        int currentLineCount = GetLineCount(
                            layoutBlockList, section.firstBlockIndex, section.lastBlockIndex);

                        if (originalLineCount > currentLineCount)
                        {
                            ExpandBlocksWithHeuristics(
                                layoutBlockList, section.firstBlockIndex, section.lastBlockIndex,
                                originalLineCount-currentLineCount);
                            redo = true;
                        }
                        else if (currentLineCount > originalLineCount)
                        {
                            CompactBlocksWithHeuristics(
                                layoutBlockList, section.firstBlockIndex, section.lastBlockIndex,
                                currentLineCount-originalLineCount);
                            redo = true;
                        }
                    }
                }

                // Pas de mise en page de la derniere section
                layoutSectionList.get(sectionLength).relayout = false;
            }
            while (redo && layoutCount-- > 0);

            // Derniere mise en page si les precedentes tentatives ont echouees
            if (redo)
            {
                LayoutSection section;
                for (int sectionIndex=0; sectionIndex<sectionLength; sectionIndex++)
                {
                    section = layoutSectionList.get(sectionIndex);

                    if (section.relayout && !section.containsError)
                    {
                        section.relayout = false;

                        int originalLineCount = section.originalLineCount;
                        int currentLineCount = GetLineCount(
                            layoutBlockList, section.firstBlockIndex, section.lastBlockIndex);

                        if (originalLineCount > currentLineCount)
                        {
                            ExpandBlocks(
                                layoutBlockList, section.firstBlockIndex, section.lastBlockIndex,
                                originalLineCount-currentLineCount);
                        }
                        else if (currentLineCount > originalLineCount)
                        {
                            CompactBlocks(
                                layoutBlockList, section.firstBlockIndex, section.lastBlockIndex,
                                currentLineCount-originalLineCount);
                        }
                    }
                }

                // Pas de mise en page de la derniere section
                layoutSectionList.get(sectionLength).relayout = false;
            }
        }
    }

    private static int GetLineCount(
        List<LayoutBlock> layoutBlockList, int firstIndex, int lastIndex)
    {
        int sum = 0;

        int lineCount;
        for (int index=firstIndex; index<=lastIndex; index++)
        {
            lineCount = layoutBlockList.get(index).lineCount;
            if (lineCount != LayoutBlockConstants.UNLIMITED_LINE_COUNT) {
                sum += lineCount;
            }
        }

        return sum;
    }

    private static void CompactBlocksWithHeuristics(
        List<LayoutBlock> layoutBlockList,
        int firstIndex, int lastIndex, int delta)
    {
        int oldDelta;

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.SEPARATOR || lb.tag == LayoutBlockConstants.SEPARATOR_OF_STATEMENTS) && lb.lineCount > 2)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            // Compact implements & throws
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                LayoutBlock lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.IMPLEMENTS_INTERFACES
                        || lb.tag == LayoutBlockConstants.GENERIC_IMPLEMENTS_INTERFACES
                        || lb.tag == LayoutBlockConstants.THROWS) && lb.lineCount > 0)				{
                    lb.lineCount--;
                    delta--;
                }
            }

            LayoutBlock lb;
            // Compact extends
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.EXTENDS_SUPER_TYPE
                        || lb.tag == LayoutBlockConstants.EXTENDS_SUPER_INTERFACES
                        || lb.tag == LayoutBlockConstants.GENERIC_EXTENDS_SUPER_TYPE
                        || lb.tag == LayoutBlockConstants.GENERIC_EXTENDS_SUPER_INTERFACES)
                        && lb.lineCount > 0)				{
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        // Compact imports
        for (int i=lastIndex; i>=firstIndex && delta>0; i--)
        {
            LayoutBlock lb = layoutBlockList.get(i);

            if (lb.tag == LayoutBlockConstants.IMPORTS && lb.lineCount > 0)
            {
                if (lb.lineCount >= delta)
                {
                    lb.lineCount -= delta;
                    delta = 0;
                }
                else
                {
                    delta -= lb.lineCount;
                    lb.lineCount = 0;
                }
            }
        }

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact début de bloc des méthodes
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START
                        || lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START) && lb.lineCount > 1
                        && lb.lineCount > lb.minimalLineCount)				{
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.SEPARATOR_OF_STATEMENTS && lb.lineCount > 1)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact fin de bloc des méthodes
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_END
                        || lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END) && lb.lineCount > 1
                        && lb.lineCount > lb.minimalLineCount)				{
                    // Compact end block
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.FRAGMENT_CASE
                        || lb.tag == LayoutBlockConstants.FRAGMENT_CASE_ENUM
                        || lb.tag == LayoutBlockConstants.FRAGMENT_CASE_STRING)
                        && lb.lineCount > 0)				{
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact fin de bloc des méthodes
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_END && lb.lineCount > lb.minimalLineCount)
                {
                    // Compact end block
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.CASE_BLOCK_START || lb.tag == LayoutBlockConstants.CASE_BLOCK_END) && lb.lineCount > 0)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact fin de bloc des méthodes
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START || lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_END) && lb.lineCount > lb.minimalLineCount)
                {
                    BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Compact end block
                    lb.lineCount--;
                    delta--;

                    if (lb.lineCount <= 1)
                    {
                        // Compact start block
                        if (blb.section == blb.other.section)
                        {
                            if (blb.other.lineCount > delta)
                            {
                                blb.other.lineCount -= delta;
                                delta = 0;
                            }
                            else
                            {
                                delta -= blb.other.lineCount;
                                blb.other.lineCount = 0;
                            }
                        }
                        else
                        {
                            blb.other.section.relayout = true;
                            blb.other.lineCount = 0;
                        }
                    }
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            // Compact début de bloc des méthodes
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                LayoutBlock lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START) {
                    if (lb.lineCount > lb.minimalLineCount)
                    {
                        // Compact start block
                        lb.lineCount--;
                        delta--;
                    }
                } else if (lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_START && lb.lineCount > lb.minimalLineCount)
                {
                    // Compact start block
                    lb.lineCount--;
                    delta--;

                    if (lb.lineCount == 0)
                    {
                        BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                        // Compact end block
                        if (blb.section == blb.other.section)
                        {
                            if (blb.other.lineCount > delta)
                            {
                                blb.other.lineCount -= delta;
                                delta = 0;
                            }
                            else
                            {
                                delta -= blb.other.lineCount;
                                blb.other.lineCount = 0;
                            }
                        }
                        else
                        {
                            blb.other.section.relayout = true;
                            blb.other.lineCount = 0;
                        }
                    }
                }
            }

            LayoutBlock lb;
            // Compact fin de bloc des méthodes
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END)
                        && lb.lineCount > lb.minimalLineCount)				{
//						BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Compact end block
                    lb.lineCount--;
                    delta--;

//						if (lb.lineCount <= 1)
//						{
//							// Compact start block
//							if (blb.section == blb.other.section)
//							{
//								if (blb.other.lineCount > delta)
//								{
//									blb.other.lineCount -= delta;
//									delta = 0;
//								}
//								else
//								{
//									delta -= blb.other.lineCount;
//									blb.other.lineCount = 0;
//								}
//							}
//							else
//							{
//								blb.other.section.relayout = true;
//								blb.other.lineCount = 0;
//							}
//						}
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.SEPARATOR || lb.tag == LayoutBlockConstants.SEPARATOR_OF_STATEMENTS) && lb.lineCount > 0)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        LayoutBlock lb;
        do
        {
            oldDelta = delta;

            // Compact separator
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.COMMENT_ERROR && lb.lineCount > 0)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

//		// Si les heuristiques n'ont pas ete suffisantes...
//		do
//		{
//			oldDelta = delta;
//			// Compact block
//			for (int i=lastIndex; (i>=firstIndex) && (delta>0); i--)
//			{
//				LayoutBlock lb = layoutBlockList.get(i);
//				if (lb.lineCount > lb.minimalLineCount)
//				{
//					lb.lineCount--;
//					delta--;
//				}
//			}
//		}
//		while ((delta>0) && (oldDelta>delta));
    }

    private static void ExpandBlocksWithHeuristics(
        List<LayoutBlock> layoutBlockList,
        int firstIndex, int lastIndex, int delta)
    {
        int oldDelta;

        do
        {
            oldDelta = delta;

            // Expand "implements types"
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                LayoutBlock lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.IMPLEMENTS_INTERFACES
                        || lb.tag == LayoutBlockConstants.EXTENDS_SUPER_INTERFACES
                        || lb.tag == LayoutBlockConstants.GENERIC_IMPLEMENTS_INTERFACES
                        || lb.tag == LayoutBlockConstants.GENERIC_EXTENDS_SUPER_INTERFACES)
                        && lb.lineCount < lb.maximalLineCount)				{
                    lb.lineCount++;
                    delta--;
                }
            }

            // Expand "extends super type"
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                LayoutBlock lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.EXTENDS_SUPER_TYPE || lb.tag == LayoutBlockConstants.GENERIC_EXTENDS_SUPER_TYPE) && lb.lineCount < lb.maximalLineCount)
                {
                    lb.lineCount++;
                    delta--;
                }
            }

            LayoutBlock lb;
            // Expand separator after imports
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.SEPARATOR_AT_BEGINING || lb.tag == LayoutBlockConstants.SEPARATOR_AFTER_IMPORTS) {
                    lb.lineCount += delta;
                    delta = 0;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.FOR_BLOCK_START && lb.lineCount < lb.maximalLineCount)
                {
                    lb.lineCount++;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.CASE_BLOCK_END && lb.lineCount == 0)
                {
                    lb.lineCount++;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            // Expand fin de bloc des méthodes
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                LayoutBlock lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_END) && lb.lineCount == 0)				{
                    BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Expand end block
                    lb.lineCount++;
                    delta--;

                    // Expand start block
                    if (blb.other.lineCount == 0)
                    {
                        if (blb.section == blb.other.section)
                        {
                            if (delta > 0)
                            {
                                blb.other.lineCount++;
                                delta--;
                            }
                        }
                        else
                        {
                            blb.other.section.relayout = true;
                            blb.other.lineCount = 1;
                        }
                    }
                }
            }

            LayoutBlock lb;
            // Expand début de bloc du corps des classes internes
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_START)
                        && lb.lineCount == 0)				{
                    BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Expand start block
                    lb.lineCount++;
                    delta--;
                    // Expand end block
                    if (blb.section == blb.other.section)
                    {
                        int d = 2 - blb.other.lineCount;

                        if (d > delta)
                        {
                            blb.other.lineCount += delta;
                            delta = 0;
                        }
                        else
                        {
                            delta -= d;
                            blb.other.lineCount = 2;
                        }
                    }
                    else
                    {
                        blb.other.section.relayout = true;
                        blb.other.lineCount = 2;
                    }
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            // Expand separator 1
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.SEPARATOR || lb.tag == LayoutBlockConstants.SEPARATOR_OF_STATEMENTS) {
                    lb.lineCount++;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        do
        {
            oldDelta = delta;

            LayoutBlock lb;
            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if (lb.tag == LayoutBlockConstants.CASE_BLOCK_END && lb.lineCount < lb.maximalLineCount)
                {
                    lb.lineCount++;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);

        LayoutBlock lb;
        do
        {
            oldDelta = delta;

            // Compact fin de bloc des méthodes
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_END
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_END
                        || lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_END
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_END)
                        && lb.lineCount < lb.maximalLineCount)				{
   //					BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Expand end block
                    lb.lineCount++;
                    delta--;
//						if (delta < 2)
//						{
//							lb.lineCount += delta;
//							delta = 0;
//						}
//						else
//						{
//							delta -= 2 - lb.lineCount;
//							lb.lineCount = 2;
//						}

   //					// Expand start block
   //					if (blb.other.lineCount == 0)
   //					{
   //						if (blb.section == blb.other.section)
   //						{
   //							if (delta > 0)
   //							{
   //								blb.other.lineCount++;
   //								delta--;
   //							}
   //						}
   //						else
   //						{
   //							blb.other.section.relayout = true;
   //							blb.other.lineCount = 1;
   //						}
   //					}
                }
            }

            // Expand début de bloc du corps des classes internes
            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if ((lb.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START
                        || lb.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_START
                        || lb.tag == LayoutBlockConstants.STATEMENTS_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_START
                        || lb.tag == LayoutBlockConstants.SWITCH_BLOCK_START)
                        && lb.lineCount < lb.maximalLineCount)				{
                    BlockLayoutBlock blb = (BlockLayoutBlock)lb;

                    // Expand start block
                    lb.lineCount++;
                    delta--;

                    if (lb.lineCount > 1 && blb.other.lineCount == 0)
                    {
                        // Expand end block
                        if (blb.section == blb.other.section) // yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
                        {
                            if (delta > 0)
                            {
                                blb.other.lineCount = 1;
                                delta--;
                            }
                        }
                        else
                        {
                            blb.other.section.relayout = true;
                            blb.other.lineCount = 1;
                        }
                    }
                }
            }
        }
        while (delta>0 && oldDelta>delta);

//		do
//		{
//			oldDelta = delta;
//			// Expand
//			for (int i=firstIndex; (i<=lastIndex) && (delta>0); i++)
//			{
//				LayoutBlock lb = layoutBlockList.get(i);
//				if (lb.lineCount < lb.maximalLineCount)
//				{
//					lb.lineCount++;
//					delta--;
//				}
//			}
//		}
//		while ((delta>0) && (oldDelta>delta));
    }

    private static void CompactBlocks(
        List<LayoutBlock> layoutBlockList,
        int firstIndex, int lastIndex, int delta)
    {
        int oldDelta;

        LayoutBlock lb;
        do
        {
            oldDelta = delta;

            for (int i=lastIndex; i>=firstIndex && delta>0; i--)
            {
                lb = layoutBlockList.get(i);

                if (lb.lineCount > lb.minimalLineCount)
                {
                    lb.lineCount--;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);
    }

    private static void ExpandBlocks(
        List<LayoutBlock> layoutBlockList,
        int firstIndex, int lastIndex, int delta)
    {
        int oldDelta;

        LayoutBlock lb;
        do
        {
            oldDelta = delta;

            for (int i=firstIndex; i<=lastIndex && delta>0; i++)
            {
                lb = layoutBlockList.get(i);

                if (lb.lineCount < lb.maximalLineCount)
                {
                    lb.lineCount++;
                    delta--;
                }
            }
        }
        while (delta>0 && oldDelta>delta);
    }

    /** Score = sum( - (separator.lineCount)^2 + (sum(block.lineCount==0)) ) */
    private static void ScoreSections(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        int sectionLength = layoutSectionList.size();

        if (sectionLength > 0)
        {
            sectionLength--;

            LayoutSection section;
            int lastBlockIndex;
            int score;
            int sumScore;
            LayoutBlock lb;
            for (int sectionIndex=0; sectionIndex<sectionLength; sectionIndex++)
            {
                section = layoutSectionList.get(sectionIndex);
                lastBlockIndex = section.lastBlockIndex;
                score = 0;
                sumScore = 0;

                for (int blockIndex = section.firstBlockIndex;
                     blockIndex <= lastBlockIndex;
                     blockIndex++)
                {
                    lb = layoutBlockList.get(blockIndex);

                    if (lb.tag == LayoutBlockConstants.SEPARATOR) {
                        if (lb.lineCount < lb.preferedLineCount)
                        {
                            sumScore += lb.preferedLineCount-lb.lineCount;

                            if (lb.lineCount > 0)
                            {
                                score += sumScore*sumScore;
                                sumScore = 0;
                            }
                        }
                        else if (lb.lineCount > lb.preferedLineCount)
                        {
                            int delta = lb.lineCount - lb.preferedLineCount;
                            score -= delta*delta;
                        }
                    }
                }

                score += sumScore*sumScore;

                // DEBUG // System.err.println("score = " + score);
                section.score = score;
            }
        }

        // DEBUG // System.err.println();
    }

    /**
     * @param layoutBlockList
     * @param layoutSectionList
     * @return true si des bloques ont ete deplaces
     */
    private static boolean SliceDownBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        // Identifier la section avec le plus haut score c.a.d. la section
        // sur laquelle il faut relacher des contraintes.
        int sectionLength = layoutSectionList.size();

        List<LayoutSection> sortedLayoutSectionList =
            new ArrayList<>(sectionLength);
        sortedLayoutSectionList.addAll(layoutSectionList);

        Collections.sort(sortedLayoutSectionList);

        LayoutSection lsSource;
        for (int sectionSourceIndex = 0;
             sectionSourceIndex < sectionLength;
             sectionSourceIndex++)
        {
            // Section source
            lsSource = sortedLayoutSectionList.get(sectionSourceIndex);

            if (lsSource.score <= 0) {
                break;
            }

            if (SliceDownBlocks(
                    layoutBlockList, layoutSectionList,
                    lsSource)) {
                return true;
            }
        }

        return false;
    }

    private static boolean SliceUpBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList)
    {
        // Identifier la section avec le plus haut score c.a.d. la section
        // sur laquelle il faut relacher des contraintes.
        int sectionLength = layoutSectionList.size();

        List<LayoutSection> sortedLayoutSectionList =
            new ArrayList<>(sectionLength);
        sortedLayoutSectionList.addAll(layoutSectionList);

        Collections.sort(sortedLayoutSectionList);

        LayoutSection lsSource;
        for (int sectionSourceIndex = 0;
             sectionSourceIndex < sectionLength;
             sectionSourceIndex++)
        {
            // Section source
            lsSource = sortedLayoutSectionList.get(sectionSourceIndex);

            if (lsSource.score <= 0) {
                break;
            }

            if (SliceUpBlocks(
                    layoutBlockList, layoutSectionList,
                    lsSource)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param layoutBlockList
     * @param layoutSectionList
     * @param lsSource
     * @return true si des bloques ont ete deplaces
     */
    private static boolean SliceDownBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList,
        LayoutSection lsSource)
    {
        // Slice down. Detect type of last block
        int firstBlockIndex = lsSource.firstBlockIndex;
        int blockIndex;

        LayoutBlock lb;
        for (blockIndex = lsSource.lastBlockIndex;
             blockIndex >= firstBlockIndex;
             blockIndex--)
        {
            lb = layoutBlockList.get(blockIndex);

            switch (lb.tag)
            {
            case LayoutBlockConstants.TYPE_MARKER_START:
                // Found
                // Slice last method block
                // Slice last field block
                if (SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.METHOD_MARKER_START,
                        LayoutBlockConstants.METHOD_MARKER_END)
                 || SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.FIELD_MARKER_START,
                        LayoutBlockConstants.FIELD_MARKER_END)) {
                    return true;
                }

                break;
            case LayoutBlockConstants.FIELD_MARKER_START:
                // Found
                // Slice last inner class block
                // Slice last method block
                if (SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.TYPE_MARKER_START,
                        LayoutBlockConstants.TYPE_MARKER_END)
                 || SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.METHOD_MARKER_START,
                        LayoutBlockConstants.METHOD_MARKER_END)) {
                    return true;
                }

                break;
            case LayoutBlockConstants.METHOD_MARKER_START:
                // Found
                // Slice last inner class block
                // Slice last field block
                if (SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.TYPE_MARKER_START,
                        LayoutBlockConstants.TYPE_MARKER_END)
                 || SliceDownBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.FIELD_MARKER_START,
                        LayoutBlockConstants.FIELD_MARKER_END)) {
                    return true;
                }

                break;
            }
        }

        return false;
    }

    /**
     * @param layoutBlockList
     * @param layoutSectionList
     * @param sectionSourceIndex
     * @param firstBlockIndex
     * @param blockIndex
     * @param markerStartTag
     * @param markerEndTag
     * @return true si des bloques ont ete deplaces
     */
    private static boolean SliceDownBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList,
        int blockIndex,
        LayoutSection lsSource, int markerStartTag, int markerEndTag)
    {
        // Rechercher le dernier block de type 'tag'
        int firstBlockIndex = lsSource.firstBlockIndex;

        LayoutBlock lb;
        while (firstBlockIndex < blockIndex)
        {
            blockIndex--;
            lb = layoutBlockList.get(blockIndex);

            if (lb.tag == markerEndTag)
            {
                // Tag de marqueur de fin trouvé.
                MarkerLayoutBlock mlb = (MarkerLayoutBlock)lb;

                if (mlb.section != mlb.other.section || mlb.other.index <= firstBlockIndex)
                {
                    // Le marqueur de début est avant la limite.
                    return false;
                }

                // Trouvé.

                // -- 1 ----------------------------------------------------- //
                int lastEndTagBlockIndex = blockIndex;

                // Rechercher du nombre de blocs a deplacer =>
                //  Trouver le premier block de type 'tag' sans numero de ligne
                int counter = 1;

                blockIndex = mlb.other.index;

                while (firstBlockIndex < blockIndex)
                {
                    blockIndex--;
                    lb = layoutBlockList.get(blockIndex);

                    if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
                    {
                        break;
                    }
                    if (lb.tag == markerEndTag)
                    {
                        // Tag de marqueur de fin trouvé.
                        mlb = (MarkerLayoutBlock)lb;

                        if (mlb.section != mlb.other.section || mlb.other.index <= firstBlockIndex)
                        {
                            // Le marqueur de début est avant la limite.
                            break;
                        }

                        counter++;
                        blockIndex = mlb.other.index;
                    }
                    else if (lb.tag == LayoutBlockConstants.FIELD_MARKER_END ||
                             lb.tag == LayoutBlockConstants.METHOD_MARKER_END ||
                             lb.tag == LayoutBlockConstants.TYPE_MARKER_END)
                    {
                        break;
                    }
                }

                // Un ou plusieurs blocs a deplacer trouvés.

                // Rechercher de l'index d'insertion =>
                //  Trouver la section ayant le score le plus bas jusqu'à la
                //  section contenant un block de type 'tag' ayant un numero
                //  de ligne defini
                int blockLength = layoutBlockList.size();
                blockIndex = lastEndTagBlockIndex;

                int lowerScore = lsSource.score;
                int lowerScoreBlockIndex = blockIndex;

                while (++blockIndex < blockLength)
                {
                    lb = layoutBlockList.get(blockIndex);

                    if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END ||
//						(lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) ||
                        lb.tag == markerStartTag)
                    {
                        // Fin de corps ou début d'un bloc
                        if (lowerScore > lb.section.score)
                        {
                            lowerScore = lb.section.score;
                            lowerScoreBlockIndex = blockIndex;
                        }

                        // Impossible de deplacer un bloc au dessus
                        // - d'une fin de corps
                        // - d'un autre du meme type
                        // => On s'arrete.
                        break;
                    }
                    if (lb.tag == LayoutBlockConstants.FIELD_MARKER_START ||
                             lb.tag == LayoutBlockConstants.METHOD_MARKER_START ||
                             lb.tag == LayoutBlockConstants.TYPE_MARKER_START)
                    {
                        // Debut d'un bloc d'un type different
                        if (lb.section != null &&
                            lowerScore > lb.section.score)
                        {
                            lowerScore = lb.section.score;
                            lowerScoreBlockIndex = blockIndex;
                        }

                        blockIndex = ((MarkerLayoutBlock)lb).other.index;
                    }
                }

                if (lowerScore != lsSource.score)
                {
                    // Trouvé.

                    // -- 2 ------------------------------------------------- //

                    // Rechercher de l'index de début du bloc de type 'tag'
                    // counter/2 en partant de 'lastEndTagBlockIndex'
                    counter = (counter + 1) / 2;
                    int firstStartTagBlockIndex =
                            blockIndex = lastEndTagBlockIndex;

                    while (firstBlockIndex < blockIndex)
                    {
                        lb = layoutBlockList.get(blockIndex);

                        if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
                        {
                            break;
                        }
                        if (lb.tag == markerEndTag)
                        {
                            firstStartTagBlockIndex = blockIndex =
                                ((MarkerLayoutBlock)lb).other.index;

                            counter--;
                            if (counter == 0) {
                                break;
                            }
                        }

                        blockIndex--;
                    }

                    // Trouvé.

                    // -- 3 ------------------------------------------------- //

                    // Deplacer la moitier des blocks de type 'tag' de
                    // 'firstStartTagBlockIndex' a 'lastEndTagBlockIndex'
                    // vers 'lowerScoreBlockIndex'

                    LayoutBlock insertionLayoutBlock =
                        layoutBlockList.get(lowerScoreBlockIndex);
                    LayoutSection lsTarget = insertionLayoutBlock.section;

                    // Remove blocks
                    int sourceDeltaIndex =
                        lastEndTagBlockIndex - firstStartTagBlockIndex + 1;
                    List<LayoutBlock> layoutBlockListToMove =
                        new ArrayList<>(sourceDeltaIndex);

                    for (blockIndex=lastEndTagBlockIndex;
                         blockIndex>=firstStartTagBlockIndex;
                         blockIndex--)
                    {
                        lb = layoutBlockList.remove(blockIndex);

                        // Update section attribute
                        lb.section = lsTarget;

                        layoutBlockListToMove.add(lb);
                    }

                    Collections.reverse(layoutBlockListToMove);

                    // Remove separator after blocks if exists
                    if (layoutBlockList.get(blockIndex+1).tag ==
                        LayoutBlockConstants.SEPARATOR)
                    {
                        layoutBlockList.remove(blockIndex+1);
                        sourceDeltaIndex++;
                    }

                    // Modify separator brefore blocks if exists
                    if (layoutBlockList.get(blockIndex).tag ==
                        LayoutBlockConstants.SEPARATOR)
                    {
                        layoutBlockList.get(blockIndex).preferedLineCount = 2;
                    }

                    // Blocs pas encore inserés.
                    lowerScoreBlockIndex -= sourceDeltaIndex;

                    int targetDeltaIndex = 0;

                    if (insertionLayoutBlock.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END /*||
                        (insertionLayoutBlock.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK)*/)
                    {
                        // Insert new separator before blocks
                        int preferedLineCount = 2;

                        if (markerEndTag == LayoutBlockConstants.FIELD_MARKER_END &&
                            layoutBlockList.get(lowerScoreBlockIndex-1).tag ==
                                LayoutBlockConstants.FIELD_MARKER_END)
                        {
                            preferedLineCount = 1;
                        }

                        layoutBlockList.add(
                            lowerScoreBlockIndex,
                            new SeparatorLayoutBlock(
                                LayoutBlockConstants.SEPARATOR,
                                preferedLineCount));

                        targetDeltaIndex++;
                    }
                    else
                    {
                        // Update separator before blocks
                        LayoutBlock beforeLayoutBlock =
                            layoutBlockList.get(lowerScoreBlockIndex-1);

                        int preferedLineCount = 2;

                        if (markerEndTag == LayoutBlockConstants.FIELD_MARKER_END &&
                            layoutBlockList.get(lowerScoreBlockIndex-2).tag ==
                                LayoutBlockConstants.FIELD_MARKER_END)
                        {
                            preferedLineCount = 1;
                        }

                        beforeLayoutBlock.preferedLineCount = preferedLineCount;
                    }

                    // Insert blocks
                    int layoutBlockListToMoveSize = layoutBlockListToMove.size();

                    layoutBlockList.addAll(
                        lowerScoreBlockIndex+targetDeltaIndex,
                        layoutBlockListToMove);

                    targetDeltaIndex += layoutBlockListToMoveSize;

                    // Add separator after blocks
                    if (insertionLayoutBlock.tag != LayoutBlockConstants.TYPE_BODY_BLOCK_END /*&&
                        (insertionLayoutBlock.tag != LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK)*/)
                    {
                        int preferedLineCount = 2;

                        if (markerStartTag == LayoutBlockConstants.FIELD_MARKER_START)
                        {
                            preferedLineCount = 1;
                        }

                        layoutBlockList.add(
                            lowerScoreBlockIndex+targetDeltaIndex,
                            new SeparatorLayoutBlock(
                                LayoutBlockConstants.SEPARATOR,
                                preferedLineCount));

                        targetDeltaIndex++;
                    }

                    // -- 4 ------------------------------------------------- //

                    // Update indexes of sections
                    lsSource.lastBlockIndex -= sourceDeltaIndex;

                    for (int sectionIndex=lsSource.index+1;
                             sectionIndex<=lsTarget.index-1;
                             sectionIndex++)
                    {
                        LayoutSection ls = layoutSectionList.get(sectionIndex);
                        ls.firstBlockIndex -= sourceDeltaIndex;
                        ls.lastBlockIndex  -= sourceDeltaIndex;
                    }

                    lsTarget.firstBlockIndex -= sourceDeltaIndex;

                    int delta = sourceDeltaIndex - targetDeltaIndex;

                    if (delta != 0)
                    {
                        lsTarget.lastBlockIndex -= delta;

                        LayoutSection ls;
                        // Update indexes of last sections
                        for (int sectionIndex=layoutSectionList.size()-1;
                                 sectionIndex>lsTarget.index;
                                 sectionIndex--)
                        {
                            ls = layoutSectionList.get(sectionIndex);
                            ls.firstBlockIndex -= delta;
                            ls.lastBlockIndex  -= delta;
                        }
                    }

                    // Update index of blocks
                    blockLength = layoutBlockList.size();

                    for (blockIndex=firstStartTagBlockIndex;
                         blockIndex<blockLength;
                         blockIndex++)
                    {
                        layoutBlockList.get(blockIndex).index = blockIndex;
                    }

                    // Update relayout flag of sections
                    UpdateRelayoutFlag(layoutBlockList, lsSource);
                    UpdateRelayoutFlag(layoutBlockList, lsTarget);

                    return true;
                }

                break;
            }
            if (lb.tag == LayoutBlockConstants.FIELD_MARKER_END ||
                     lb.tag == LayoutBlockConstants.METHOD_MARKER_END ||
                     lb.tag == LayoutBlockConstants.TYPE_MARKER_END)
            {
                blockIndex = ((MarkerLayoutBlock)lb).other.index;
            }
            else if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
            {
                break;
            }
        }

        return false;
    }

    private static boolean SliceUpBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList,
        LayoutSection lsSource)
    {
        // Slice up. Detect type of last block
        int lastBlockIndex = lsSource.lastBlockIndex;
        int blockIndex;

        LayoutBlock lb;
        for (blockIndex = lsSource.firstBlockIndex;
             blockIndex <= lastBlockIndex;
             blockIndex++)
        {
            lb = layoutBlockList.get(blockIndex);

            switch (lb.tag)
            {
            case LayoutBlockConstants.TYPE_MARKER_END:
                // Found
                // Slice last method block
                // Slice last field block
                return SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.FIELD_MARKER_START,
                        LayoutBlockConstants.FIELD_MARKER_END)
                    || SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.METHOD_MARKER_START,
                        LayoutBlockConstants.METHOD_MARKER_END);
            case LayoutBlockConstants.FIELD_MARKER_END:
                // Found
                // Slice last method block
                return SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.METHOD_MARKER_START,
                        LayoutBlockConstants.METHOD_MARKER_END)
                    || SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.TYPE_MARKER_START,
                        LayoutBlockConstants.TYPE_MARKER_END);
            case LayoutBlockConstants.METHOD_MARKER_END:
                // Found
                // Slice last field block
                return SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.FIELD_MARKER_START,
                        LayoutBlockConstants.FIELD_MARKER_END)
                    || SliceUpBlocks(
                        layoutBlockList, layoutSectionList,
                        blockIndex, lsSource,
                        LayoutBlockConstants.TYPE_MARKER_START,
                        LayoutBlockConstants.TYPE_MARKER_END);
            }
        }

        return false;
    }

    /**
     * @param layoutBlockList
     * @param layoutSectionList
     * @param sectionSourceIndex
     * @param firstBlockIndex
     * @param blockIndex
     * @param markerStartTag
     * @param markerEndTag
     * @return true si des bloques ont ete deplaces
     */
    private static boolean SliceUpBlocks(
        List<LayoutBlock> layoutBlockList,
        List<LayoutSection> layoutSectionList,
        int blockIndex,
        LayoutSection lsSource, int markerStartTag, int markerEndTag)
    {
        // Rechercher le premier block de type 'tag'
        int lastBlockIndex = lsSource.lastBlockIndex;

        LayoutBlock lb;
        while (blockIndex < lastBlockIndex)
        {
            blockIndex++;
            lb = layoutBlockList.get(blockIndex);

            if (lb.tag == markerStartTag)
            {
                // Tag de marqueur de début trouvé.
                MarkerLayoutBlock mlb = (MarkerLayoutBlock)lb;

                if (mlb.section != mlb.other.section || mlb.other.index >= lastBlockIndex)
                {
                    // Le marqueur de fin est après la limite.
                    return false;
                }

                // Trouvé.

                // -- 1 ----------------------------------------------------- //
                int firstStartTagBlockIndex = blockIndex;

                // Rechercher du nombre de blocs a deplacer =>
                //  Trouver le dernier block de type 'tag' sans numero de ligne
                int counter = 1;

                blockIndex = mlb.other.index;

                while (blockIndex < lastBlockIndex)
                {
                    blockIndex++;
                    lb = layoutBlockList.get(blockIndex);

                    if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
                    {
                        break;
                    }
                    if (lb.tag == markerStartTag)
                    {
                        // Tag de marqueur de fin trouvé.
                        mlb = (MarkerLayoutBlock)lb;

                        if (mlb.section != mlb.other.section || mlb.other.index >= lastBlockIndex)
                        {
                            // Le marqueur de début est avant la limite.
                            break;
                        }

                        counter++;
                        blockIndex = mlb.other.index;
                    }
                    else if (lb.tag == LayoutBlockConstants.FIELD_MARKER_START ||
                             lb.tag == LayoutBlockConstants.METHOD_MARKER_START ||
                             lb.tag == LayoutBlockConstants.TYPE_MARKER_START)
                    {
                        break;
                    }
                }

                // Un ou plusieurs blocs a deplacer trouvés.

                // Rechercher de l'index d'insertion =>
                //  Trouver la section ayant le score le plus bas jusqu'à la
                //  section contenant un block de type 'tag' ayant un numero
                //  de ligne defini
                blockIndex = firstStartTagBlockIndex;

                int lowerScore = lsSource.score;
                int lowerScoreBlockIndex = blockIndex;

                while (blockIndex-- > 0)
                {
                    lb = layoutBlockList.get(blockIndex);

                    if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START ||
//						(lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) ||
                        lb.tag == markerEndTag)
                    {
                        // début de corps ou fin d'un bloc
                        if (lowerScore > lb.section.score)
                        {
                            lowerScore = lb.section.score;
                            lowerScoreBlockIndex = blockIndex;
                        }

                        // Impossible de deplacer un bloc au dessus
                        // - d'un début de corps
                        // - d'un autre du meme type
                        // => On s'arrete.
                        break;
                    }
                    if (lb.tag == LayoutBlockConstants.FIELD_MARKER_END ||
                             lb.tag == LayoutBlockConstants.METHOD_MARKER_END ||
                             lb.tag == LayoutBlockConstants.TYPE_MARKER_END)
                    {
                        // Fin d'un bloc d'un type different
                        if (lb.section != null &&
                            lowerScore > lb.section.score)
                        {
                            lowerScore = lb.section.score;
                            lowerScoreBlockIndex = blockIndex;
                        }

                        blockIndex = ((MarkerLayoutBlock)lb).other.index;
                    }
                }

                if (lowerScore != lsSource.score)
                {
                    // Trouvé.

                    // -- 2 ------------------------------------------------- //

                    // Rechercher de l'index de début du bloc de type 'tag'
                    // counter/2 en partant de 'lastEndTagBlockIndex'
                    counter = (counter + 1) / 2;
                    int lastEndTagBlockIndex =
                            blockIndex = firstStartTagBlockIndex;

                    while (blockIndex > 0)
                    {
                        lb = layoutBlockList.get(blockIndex);

                        if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
                        {
                            break;
                        }
                        if (lb.tag == markerStartTag)
                        {
                            lastEndTagBlockIndex = blockIndex =
                                ((MarkerLayoutBlock)lb).other.index;

                            counter--;
                            if (counter == 0) {
                                break;
                            }
                        }

                        blockIndex++;
                    }

                    // Trouvé.

                    // -- 3 ------------------------------------------------- //

                    // Deplacer la moitier des blocks de type 'tag' de
                    // 'firstStartTagBlockIndex' a 'lastEndTagBlockIndex'
                    // vers 'lowerScoreBlockIndex'

                    LayoutBlock insertionLayoutBlock =
                        layoutBlockList.get(lowerScoreBlockIndex);
                    LayoutSection lsTarget = insertionLayoutBlock.section;

                    // Remove blocks
                    int sourceDeltaIndex =
                        lastEndTagBlockIndex - firstStartTagBlockIndex + 1;
                    List<LayoutBlock> layoutBlockListToMove =
                        new ArrayList<>(sourceDeltaIndex);

                    for (blockIndex=lastEndTagBlockIndex;
                         blockIndex>=firstStartTagBlockIndex;
                         blockIndex--)
                    {
                        lb = layoutBlockList.remove(blockIndex);

                        // Update section attribute
                        lb.section = lsTarget;

                        layoutBlockListToMove.add(lb);
                    }

                    Collections.reverse(layoutBlockListToMove);

                    // Remove separator after blocks if exists
                    if (layoutBlockList.get(blockIndex+1).tag ==
                        LayoutBlockConstants.SEPARATOR)
                    {
                        layoutBlockList.remove(blockIndex+1);
                        sourceDeltaIndex++;
                    }

                    // Modify separator brefore blocks if exists
                    if (layoutBlockList.get(blockIndex).tag ==
                        LayoutBlockConstants.SEPARATOR)
                    {
                        layoutBlockList.get(blockIndex).preferedLineCount = 2;
                    }

                    // Blocs pas encore inserés.
                    lowerScoreBlockIndex++;

                    int targetDeltaIndex = 0;

                    if (insertionLayoutBlock.tag != LayoutBlockConstants.TYPE_BODY_BLOCK_START /*&&
                        (insertionLayoutBlock.tag != LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK)*/)
                    {
                        // Insert new separator before blocks
                        int preferedLineCount = 2;

                        if (markerEndTag == LayoutBlockConstants.FIELD_MARKER_END)
                        {
                            preferedLineCount = 1;
                        }

                        layoutBlockList.add(
                            lowerScoreBlockIndex,
                            new SeparatorLayoutBlock(
                                LayoutBlockConstants.SEPARATOR,
                                preferedLineCount));

                        targetDeltaIndex++;
                    }

                    // Insert blocks
                    int layoutBlockListToMoveSize = layoutBlockListToMove.size();

                    layoutBlockList.addAll(
                        lowerScoreBlockIndex+targetDeltaIndex,
                        layoutBlockListToMove);

                    targetDeltaIndex += layoutBlockListToMoveSize;

                    // Update separator after blocks
                    if (insertionLayoutBlock.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START /*||
                        (insertionLayoutBlock.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK)*/)
                    {
                        // Insert new separator after blocks
                        int preferedLineCount = 2;

                        if (markerEndTag == LayoutBlockConstants.FIELD_MARKER_END &&
                            layoutBlockList.get(lowerScoreBlockIndex+targetDeltaIndex).tag ==
                                LayoutBlockConstants.FIELD_MARKER_END)
                        {
                            preferedLineCount = 1;
                        }

                        layoutBlockList.add(
                            lowerScoreBlockIndex+targetDeltaIndex,
                            new SeparatorLayoutBlock(
                                LayoutBlockConstants.SEPARATOR,
                                preferedLineCount));

                        targetDeltaIndex++;
                    }
                    else
                    {
                        // Update separator after blocks
                        LayoutBlock afterLayoutBlock =
                            layoutBlockList.get(lowerScoreBlockIndex+targetDeltaIndex);

                        int preferedLineCount = 2;

                        if (markerStartTag == LayoutBlockConstants.FIELD_MARKER_START &&
                            layoutBlockList.get(lowerScoreBlockIndex+targetDeltaIndex+1).tag ==
                                LayoutBlockConstants.FIELD_MARKER_START)
                        {
                            preferedLineCount = 1;
                        }

                        afterLayoutBlock.preferedLineCount = preferedLineCount;
                    }

                    // -- 4 ------------------------------------------------- //

                    // Update indexes of sections
                    lsTarget.lastBlockIndex += targetDeltaIndex;

                    for (int sectionIndex=lsTarget.index+1;
                             sectionIndex<=lsSource.index-1;
                             sectionIndex++)
                    {
                        LayoutSection ls = layoutSectionList.get(sectionIndex);
                        ls.firstBlockIndex += targetDeltaIndex;
                        ls.lastBlockIndex  += targetDeltaIndex;
                    }

                    lsSource.firstBlockIndex += targetDeltaIndex;

                    int delta = sourceDeltaIndex - targetDeltaIndex;

                    if (delta != 0)
                    {
                        lsSource.lastBlockIndex -= delta;

                        LayoutSection ls;
                        // Update indexes of last sections
                        for (int sectionIndex=layoutSectionList.size()-1;
                                 sectionIndex>lsSource.index;
                                 sectionIndex--)
                        {
                            ls = layoutSectionList.get(sectionIndex);
                            ls.firstBlockIndex -= delta;
                            ls.lastBlockIndex  -= delta;
                        }
                    }

                    // Update index of blocks
                    int blockLength = layoutBlockList.size();

                    for (blockIndex=lowerScoreBlockIndex;
                         blockIndex<blockLength;
                         blockIndex++)
                    {
                        layoutBlockList.get(blockIndex).index = blockIndex;
                    }

                    // Update relayout flag of sections
                    UpdateRelayoutFlag(layoutBlockList, lsSource);
                    UpdateRelayoutFlag(layoutBlockList, lsTarget);

                    return true;
                }

                break;
            }
            if (lb.tag == LayoutBlockConstants.FIELD_MARKER_START ||
                     lb.tag == LayoutBlockConstants.METHOD_MARKER_START ||
                     lb.tag == LayoutBlockConstants.TYPE_MARKER_START)
            {
                blockIndex = ((MarkerLayoutBlock)lb).other.index;
            }
            else if (lb.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END /* || (lb.tag == LayoutBlockConstants.SEPARATOR_BEFORE_OR_AFTER_BLOCK) */)
            {
                break;
            }
        }

        return false;
    }

    private static void UpdateRelayoutFlag(
        List<LayoutBlock> layoutBlockList, LayoutSection section)
    {
        section.relayout = true;

        int lastBlockIndex = section.lastBlockIndex;

        LayoutBlock block;
        for (int blockIndex=section.firstBlockIndex;
                 blockIndex<lastBlockIndex;
                 blockIndex++)
        {
            block = layoutBlockList.get(blockIndex);

            if (block.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_START
                    || block.tag == LayoutBlockConstants.TYPE_BODY_BLOCK_END
                    || block.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_START
                    || block.tag == LayoutBlockConstants.INNER_TYPE_BODY_BLOCK_END
                    || block.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_START
                    || block.tag == LayoutBlockConstants.METHOD_BODY_BLOCK_END
                    || block.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_START
                    || block.tag == LayoutBlockConstants.METHOD_BODY_SINGLE_LINE_BLOCK_END
                    || block.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_START
                    || block.tag == LayoutBlockConstants.SINGLE_STATEMENT_BLOCK_END
                    || block.tag == LayoutBlockConstants.STATEMENTS_BLOCK_START
                    || block.tag == LayoutBlockConstants.STATEMENTS_BLOCK_END
                    || block.tag == LayoutBlockConstants.SWITCH_BLOCK_START
                    || block.tag == LayoutBlockConstants.SWITCH_BLOCK_END) {
                BlockLayoutBlock blb = (BlockLayoutBlock) block;
                LayoutSection otherSection = blb.other.section;
                if (!otherSection.relayout) {
                    UpdateRelayoutFlag(layoutBlockList, otherSection);
                }
            }
        }
    }
}
