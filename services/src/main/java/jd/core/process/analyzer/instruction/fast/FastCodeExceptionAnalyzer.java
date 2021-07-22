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
package jd.core.process.analyzer.instruction.fast;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.CodeException;

import java.util.*;
import java.util.Map.Entry;

import jd.core.model.classfile.LocalVariable;
import jd.core.model.classfile.LocalVariables;
import jd.core.model.classfile.Method;
import jd.core.model.instruction.bytecode.ByteCodeConstants;
import jd.core.model.instruction.bytecode.instruction.*;
import jd.core.model.instruction.fast.FastConstants;
import jd.core.model.instruction.fast.instruction.FastSynchronized;
import jd.core.model.instruction.fast.instruction.FastTry;
import jd.core.model.instruction.fast.instruction.FastTry.FastCatch;
import jd.core.process.analyzer.instruction.bytecode.ComparisonInstructionAnalyzer;
import jd.core.process.analyzer.instruction.fast.visitor.CheckLocalVariableUsedVisitor;
import jd.core.process.analyzer.instruction.fast.visitor.FastCompareInstructionVisitor;
import jd.core.process.analyzer.util.InstructionUtil;
import jd.core.util.IntSet;
import jd.core.util.UtilConstants;

/** Aglomeration des informations 'CodeException'. */
public class FastCodeExceptionAnalyzer
{
    private FastCodeExceptionAnalyzer() {
    }

    public static List<FastCodeExcepcion> aggregateCodeExceptions(
            Method method, List<Instruction> list)
    {
        List<Entry<Integer, CodeException>> arrayOfCodeException = method.getCodeExceptions();

        if (arrayOfCodeException == null || arrayOfCodeException.isEmpty()) {
            return null;
        }

        // Aggregation des 'finally' et des 'catch' executant le meme bloc
        List<FastAggregatedCodeExcepcion> fastAggregatedCodeExceptions =
            new ArrayList<>(
                arrayOfCodeException.size());
        populateListOfFastAggregatedCodeException(
            method, list, fastAggregatedCodeExceptions);

        int length = fastAggregatedCodeExceptions.size();
        List<FastCodeExcepcion> fastCodeExceptions =
            new ArrayList<>(length);

        // Aggregation des blocs 'finally' aux blocs 'catch'
        // Add first
        fastCodeExceptions.add(newFastCodeException(
            list, fastAggregatedCodeExceptions.get(0)));

        FastAggregatedCodeExcepcion fastAggregatedCodeException;
        // Add or update
        for (int i=1; i<length; ++i)
        {
            fastAggregatedCodeException = fastAggregatedCodeExceptions.get(i);

            // Update 'FastCodeException' for 'codeException'
            if (!updateFastCodeException(
                    fastCodeExceptions, fastAggregatedCodeException)) {
                // Not found -> Add new entry
                fastCodeExceptions.add(newFastCodeException(
                    list, fastAggregatedCodeException));
            }
        }

        // Sort by 1)tryFromOffset 2)maxOffset 3)tryToOffset
        // Necessaire pour le calcul de 'afterOffset' des structures try-catch
        // par 'ComputeAfterOffset'
        Collections.sort(fastCodeExceptions);

        FastCodeExcepcion fce1;
        FastCodeExcepcion fce2;
        // Aggregation des blocs 'catch'
        // Reduce of FastCodeException after UpdateFastCodeException(...)
        for (int i=fastCodeExceptions.size()-1; i>=1; --i)
        {
            fce1 = fastCodeExceptions.get(i);
            fce2 = fastCodeExceptions.get(i-1);

            if (fce1.tryFromOffset == fce2.tryFromOffset &&
                fce1.tryToOffset == fce2.tryToOffset &&
                fce1.synchronizedFlag == fce2.synchronizedFlag &&
                (fce1.afterOffset == UtilConstants.INVALID_OFFSET || fce1.afterOffset > fce2.maxOffset) &&
                (fce2.afterOffset == UtilConstants.INVALID_OFFSET || fce2.afterOffset > fce1.maxOffset))
            {
                // Append catches
                fce2.catches.addAll(fce1.catches);
                Collections.sort(fce2.catches);
                // Append finally
                if (fce2.nbrFinally == 0)
                {
                    fce2.finallyFromOffset = fce1.finallyFromOffset;
                    fce2.nbrFinally        = fce1.nbrFinally;
                }
                // Update 'maxOffset'
                if (fce2.maxOffset < fce1.maxOffset) {
                    fce2.maxOffset = fce1.maxOffset;
                }
                // Update 'afterOffset'
                if (fce2.afterOffset == UtilConstants.INVALID_OFFSET ||
                    (fce1.afterOffset != UtilConstants.INVALID_OFFSET &&
                     fce1.afterOffset < fce2.afterOffset)) {
                    fce2.afterOffset = fce1.afterOffset;
                }
                // Remove last FastCodeException
                fastCodeExceptions.remove(i);
            }
        }

        // Search 'switch' instructions, sort case offset
        List<int[]> switchCaseOffsets = searchSwitchCaseOffsets(list);

        FastCodeExcepcion fce;
        for (int i=fastCodeExceptions.size()-1; i>=0; --i)
        {
            fce = fastCodeExceptions.get(i);

            // Determine type
            defineType(list, fce);

            if (fce.type == FastConstants.TYPE_UNDEFINED) {
                System.err.println("Undefined type catch");
            }

            // Compute afterOffset
            computeAfterOffset(
                method, list, switchCaseOffsets, fastCodeExceptions, fce, i);

            length = list.size();
            if (fce.afterOffset == UtilConstants.INVALID_OFFSET && length > 0)
            {
                Instruction lastInstruction = list.get(length-1);
                fce.afterOffset = lastInstruction.offset;

                if (lastInstruction.opcode != Const.RETURN &&
                    lastInstruction.opcode != ByteCodeConstants.XRETURN) {
                    // Set afterOffset to a virtual instruction after list.
                    fce.afterOffset++;
                }
            }
        }

        // Sort by 1)tryFromOffset 2)maxOffset 3)tryToOffset
        Collections.sort(fastCodeExceptions);

        return fastCodeExceptions;
    }

    private static void populateListOfFastAggregatedCodeException(
        Method method, List<Instruction> list,
        List<FastAggregatedCodeExcepcion> fastAggregatedCodeExceptions)
    {
        int length = method.getCode().length;
        if (length == 0) {
            return;
        }

        FastAggregatedCodeExcepcion[] array =
            new FastAggregatedCodeExcepcion[length];

        List<Entry<Integer, CodeException>> arrayOfCodeException = method.getCodeExceptions();
        length = arrayOfCodeException.size();
        CodeException codeException;
        for (int i=0; i<length; i++)
        {
            codeException = arrayOfCodeException.get(i).getValue();

            if (array[codeException.getHandlerPC()] == null)
            {
                FastAggregatedCodeExcepcion face =
                    new FastAggregatedCodeExcepcion(
                        i, codeException.getStartPC(), codeException.getEndPC(),
                        codeException.getHandlerPC(), codeException.getCatchType());
                fastAggregatedCodeExceptions.add(face);
                array[codeException.getHandlerPC()] = face;
            }
            else
            {
                FastAggregatedCodeExcepcion face = array[codeException.getHandlerPC()];
                // ATTENTION: la modification de 'endPc' implique la
                //            reecriture de 'defineType(...) !!
                if (face.getCatchType() == 0)
                {
                    face.nbrFinally++;
                } else // Ce type d'exception a-t-il deja ete ajoute ?
                if (isNotAlreadyStored(face, codeException.getCatchType()))
                {
                    // Non
                    if (face.otherCatchTypes == null) {
                        face.otherCatchTypes = new int[length];
                    }
                    face.otherCatchTypes[i] = codeException.getCatchType();
                }
            }
        }

        int i = fastAggregatedCodeExceptions.size();
        FastAggregatedCodeExcepcion face;
        while (i-- > 0)
        {
            face = fastAggregatedCodeExceptions.get(i);

            if (face.getCatchType() == 0 && isASynchronizedBlock(list, face))
            {
                face.synchronizedFlag = true;
            }
        }
    }

    private static boolean isNotAlreadyStored(
        FastAggregatedCodeExcepcion face, int catchType)
    {
        if (face.getCatchType() == catchType) {
            return false;
        }

        if (face.otherCatchTypes != null)
        {
            int i = face.otherCatchTypes.length;

            while (i-- > 0)
            {
                if (face.otherCatchTypes[i] == catchType) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isASynchronizedBlock(
        List<Instruction> list, FastAggregatedCodeExcepcion face)
    {
        int index = InstructionUtil.getIndexForOffset(list, face.getStartPC());

        if (index == -1) {
            return false;
        }

        if (list.get(index).opcode == Const.MONITOREXIT)
        {
            // Cas particulier Jikes 1.2.2
            return true;
        }

        if (index < 1) {
            return false;
        }

        /* Recherche si le bloc finally contient une instruction
         * monitorexit ayant le meme index que l'instruction
         * monitorenter avant le bloc try.
         * Byte code++:
         *  5: System.out.println("start");
         *  8: localTestSynchronize = this
         *  11: monitorenter (localTestSynchronize);        <----
         *  17: System.out.println("in synchronized");
         *  21: monitorexit localTestSynchronize;
         *  22: goto 30;
         *  25: localObject2 = finally;
         *  27: monitorexit localTestSynchronize;           <====
         *  29: throw localObject1;
         *  35: System.out.println("end");
         *  38: return;
         */
        Instruction instruction = list.get(index-1);

        if (instruction.opcode != Const.MONITORENTER) {
            return false;
        }

        int varMonitorIndex;
        MonitorEnter monitorEnter = (MonitorEnter)instruction;

        switch (monitorEnter.objectref.opcode)
        {
        case Const.ALOAD:
            {
                if (index < 2) {
                    return false;
                }
                instruction = list.get(index-2);
                if (instruction.opcode != Const.ASTORE) {
                    return false;
                }
                AStore astore = (AStore)instruction;
                varMonitorIndex = astore.index;
            }
            break;
        case ByteCodeConstants.ASSIGNMENT:
            {
                AssignmentInstruction ai =
                    (AssignmentInstruction)monitorEnter.objectref;
                if (ai.value1.opcode != Const.ALOAD) {
                    return false;
                }
                ALoad aload = (ALoad)ai.value1;
                varMonitorIndex = aload.index;
            }
            break;
        default:
            return false;
        }

        boolean checkMonitorExit = false;
        int length = list.size();
        index = InstructionUtil.getIndexForOffset(list, face.getHandlerPC());

        while (index < length)
        {
            instruction = list.get(index);
            index++;

            if (instruction.opcode == Const.MONITOREXIT) {
                checkMonitorExit = true;
                MonitorExit monitorExit = (MonitorExit)instruction;

                if (monitorExit.objectref.opcode == Const.ALOAD &&
                    ((ALoad)monitorExit.objectref).index == varMonitorIndex) {
                    return true;
                }
            } else if (instruction.opcode == Const.RETURN || instruction.opcode == ByteCodeConstants.XRETURN
                    || instruction.opcode == Const.ATHROW) {
                return false;
            }
        }
        // Si l'expression ci-dessous est vraie, aucune instruction 'MonitorExit' n'a ete trouvée. Cas de la
        // double instruction 'synchronized' imbriquée pour le JDK 1.1.8
        return !checkMonitorExit && index == length;
    }

    private static boolean updateFastCodeException(
            List<FastCodeExcepcion> fastCodeExceptions,
            FastAggregatedCodeExcepcion fastAggregatedCodeException)
    {
        int length = fastCodeExceptions.size();

        if (fastAggregatedCodeException.getCatchType() == 0)
        {
            // Finally

            // Same start and end offsets
            for (int i=0; i<length; ++i)
            {
                FastCodeExcepcion fce = fastCodeExceptions.get(i);

                if (fce.finallyFromOffset == UtilConstants.INVALID_OFFSET &&
                    fastAggregatedCodeException.getStartPC() == fce.tryFromOffset &&
                    fastAggregatedCodeException.getEndPC() == fce.tryToOffset &&
                    fastAggregatedCodeException.getHandlerPC() > fce.maxOffset &&
                    !fastAggregatedCodeException.synchronizedFlag && (fce.afterOffset == UtilConstants.INVALID_OFFSET ||
                    (fastAggregatedCodeException.getEndPC() < fce.afterOffset &&
                     fastAggregatedCodeException.getHandlerPC() < fce.afterOffset)))
                {
                    fce.maxOffset = fastAggregatedCodeException.getHandlerPC();
                    fce.finallyFromOffset = fastAggregatedCodeException.getHandlerPC();
                    fce.nbrFinally += fastAggregatedCodeException.nbrFinally;
                    return true;
                }
            }

            FastCodeExcepcion fce;
            // Old algo
            for (int i=0; i<length; ++i)
            {
                fce = fastCodeExceptions.get(i);

                if (fce.finallyFromOffset == UtilConstants.INVALID_OFFSET &&
                    fastAggregatedCodeException.getStartPC() == fce.tryFromOffset &&
                    fastAggregatedCodeException.getEndPC() >= fce.tryToOffset &&
                    fastAggregatedCodeException.getHandlerPC() > fce.maxOffset &&
                    !fastAggregatedCodeException.synchronizedFlag && (fce.afterOffset == UtilConstants.INVALID_OFFSET ||
                    (fastAggregatedCodeException.getEndPC() < fce.afterOffset &&
                     fastAggregatedCodeException.getHandlerPC() < fce.afterOffset)))
                {
                    fce.maxOffset = fastAggregatedCodeException.getHandlerPC();
                    fce.finallyFromOffset = fastAggregatedCodeException.getHandlerPC();
                    fce.nbrFinally += fastAggregatedCodeException.nbrFinally;
                    return true;
                }
                /* Mis en commentaire a cause d'erreurs pour le jdk1.5.0 dans
                 * TryCatchFinallyClass.complexMethodTryCatchCatchFinally()
                 *
                 * else if ((fce.catches != null) &&
                         (fce.afterOffset == fastAggregatedCodeException.endPc))
                {
                    fce.finallyFromOffset = fastAggregatedCodeException.handlerPc;
                    fce.nbrFinally += fastAggregatedCodeException.nbrFinally;
                    return true;
                } */
            }
        }

        return false;
    }

    private static FastCodeExcepcion newFastCodeException(
        List<Instruction> list, FastAggregatedCodeExcepcion fastCodeException)
    {
        FastCodeExcepcion fce = new FastCodeExcepcion(
            fastCodeException.getStartPC(),
            fastCodeException.getEndPC(),
            fastCodeException.getHandlerPC(),
            fastCodeException.synchronizedFlag);

        if (fastCodeException.getCatchType() == 0)
        {
            fce.finallyFromOffset = fastCodeException.getHandlerPC();
            fce.nbrFinally += fastCodeException.nbrFinally;
        }
        else
        {
            fce.catches.add(new FastCodeExceptionCatch(
                fastCodeException.getCatchType(),
                fastCodeException.otherCatchTypes,
                fastCodeException.getHandlerPC()));
        }

        // Approximation a affinée par la méthode 'ComputeAfterOffset'
        fce.afterOffset = searchAfterOffset(list, fastCodeException.getHandlerPC());

        return fce;
    }

    /** Recherche l'offset après le bloc try-catch-finally. */
    private static int searchAfterOffset(List<Instruction> list, int offset)
    {
        // Search instruction at 'offset'
        int index = InstructionUtil.getIndexForOffset(list, offset);

        if (index <= 0) {
            return offset;
        }

        index--;
        // Search previous 'goto' instruction
        Instruction i = list.get(index);

        switch (i.opcode)
        {
        case Const.GOTO:
            int branch = ((Goto)i).branch;
            if (branch < 0) {
                return UtilConstants.INVALID_OFFSET;
            }
            int jumpOffset = i.offset + branch;
            index = InstructionUtil.getIndexForOffset(list, jumpOffset);
            if (index <= 0) {
                return UtilConstants.INVALID_OFFSET;
            }
            i = list.get(index);
            if (i.opcode != Const.JSR) {
                return jumpOffset;
            }
            branch = ((Jsr)i).branch;
            if (branch > 0) {
                return i.offset + branch;
            }
            return jumpOffset+1;

        case Const.RET:
            // Particularite de la structure try-catch-finally du JDK 1.1.8:
            // une sous routine termine le bloc precedent 'offset'.
            // Strategie : recheche de l'instruction goto, sautant après
            // 'offset', et suivie par le sequence d'instructions suivante :
            //  30: goto +105 -> 135
            //  33: astore_3
            //  34: jsr +5 -> 39
            //  37: aload_3
            //  38: athrow
            //  39: astore 4
            //  41: ...
            //  45: ret 4
            while (--index >= 3)
            {
                if (list.get(index).opcode == Const.ATHROW &&
                    list.get(index-1).opcode == Const.JSR &&
                    list.get(index-2).opcode == Const.ASTORE &&
                    list.get(index-3).opcode == Const.GOTO)
                {
                    Goto g = (Goto)list.get(index-3);
                    return g.getJumpOffset();
                }
            }
            // intended fall through
        default:
            return UtilConstants.INVALID_OFFSET;
        }
    }

    private static List<int[]> searchSwitchCaseOffsets(
            List<Instruction> list)
    {
        List<int[]> switchCaseOffsets = new ArrayList<>();

        int i = list.size();
        Instruction instruction;
        while (i-- > 0)
        {
            instruction = list.get(i);

            if (instruction.opcode == Const.TABLESWITCH || instruction.opcode == Const.LOOKUPSWITCH) {
                Switch s = (Switch)instruction;
                int j = s.offsets.length;
                int[] offsets = new int[j+1];

                offsets[j] = s.offset + s.defaultOffset;
                while (j-- > 0) {
                    offsets[j] = s.offset + s.offsets[j];
                }

                Arrays.sort(offsets);
                switchCaseOffsets.add(offsets);
            }
        }

        return switchCaseOffsets;
    }

    private static void defineType(
            List<Instruction> list, FastCodeExcepcion fastCodeException)
    {
        // Contains finally ?
        switch (fastCodeException.nbrFinally)
        {
        case 0:
            // No
            fastCodeException.type = FastConstants.TYPE_CATCH;
            break;
        case 1:
            // 1.1.8, 1.3.1, 1.4.2 or eclipse 677
            // Yes, contains catch ?
            if (fastCodeException.catches == null	||
                fastCodeException.catches.isEmpty())
            {
                // No
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index < 0) {
                    return;
                }

                // Search 'goto' instruction
                Instruction instruction = list.get(index-1);
                switch (instruction.opcode)
                {
                case Const.GOTO:
                    if (tryBlockContainsJsr(list, fastCodeException))
                    {
                        fastCodeException.type = FastConstants.TYPE_118_FINALLY;
                    } else // Search previous 'goto' instruction
                    if (list.get(index-2).opcode == Const.MONITOREXIT) {
                        fastCodeException.type = FastConstants.TYPE_118_SYNCHRONIZED;
                    } else {
                        // TYPE_ECLIPSE_677_FINALLY or TYPE_118_FINALLY_2 ?
                        int jumpOffset = ((Goto)instruction).getJumpOffset();
                        instruction =
                            InstructionUtil.getInstructionAt(list, jumpOffset);

                        if (instruction.opcode == Const.JSR) {
                            fastCodeException.type =
                                FastConstants.TYPE_118_FINALLY_2;
                        } else {
                            fastCodeException.type =
                                FastConstants.TYPE_ECLIPSE_677_FINALLY;
                        }
                    }
                    break;
                case Const.RETURN:
                case ByteCodeConstants.XRETURN:
                    if (tryBlockContainsJsr(list, fastCodeException))
                    {
                        fastCodeException.type = FastConstants.TYPE_118_FINALLY;
                    } else // Search previous 'return' instruction
                    if (list.get(index-2).opcode == Const.MONITOREXIT) {
                        fastCodeException.type = FastConstants.TYPE_118_SYNCHRONIZED;
                    } else {
                        // TYPE_ECLIPSE_677_FINALLY or TYPE_142 ?
                        Instruction firstFinallyInstruction = list.get(index+1);
                        int exceptionIndex = ((AStore)list.get(index)).index;
                        int length = list.size();

                        // Search throw instruction
                        while (++index < length)
                        {
                            instruction = list.get(index);
                            if (instruction.opcode == Const.ATHROW)
                            {
                                AThrow athrow = (AThrow)instruction;
                                if (athrow.value.opcode == Const.ALOAD &&
                                    ((ALoad)athrow.value).index == exceptionIndex) {
                                    break;
                                }
                            }
                        }

                        if (++index >= length)
                        {
                            fastCodeException.type = FastConstants.TYPE_142;
                        }
                        else
                        {
                            instruction = list.get(index);

                            fastCodeException.type =
                                (instruction.opcode != firstFinallyInstruction.opcode ||
                                 firstFinallyInstruction.lineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                                 firstFinallyInstruction.lineNumber != instruction.lineNumber) ?
                                         FastConstants.TYPE_142 :
                                         FastConstants.TYPE_ECLIPSE_677_FINALLY;
                        }
                    }
                    break;
                case Const.ATHROW:
                    // Search 'jsr' instruction after 'astore' instruction
                    if (list.get(index+1).opcode == Const.JSR) {
                        fastCodeException.type =
                            FastConstants.TYPE_118_FINALLY_THROW;
                    } else if (list.get(index).opcode ==
                                    Const.MONITOREXIT) {
                        fastCodeException.type =
                            FastConstants.TYPE_118_FINALLY;
                    } else {
                        fastCodeException.type =
                            FastConstants.TYPE_142_FINALLY_THROW;
                    }
                    break;
                case Const.RET:
                    // Double synchronized blocks compiled with the JDK 1.1.8
                    fastCodeException.type =
                        FastConstants.TYPE_118_SYNCHRONIZED_DOUBLE;
                    break;
                }
            }
            else
            {
                // Yes, contains catch(s) & finally
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.catches.get(0).fromOffset);
                if (index < 0) {
                    return;
                }

                index--;
                // Search 'goto' instruction in try block
                Instruction instruction = list.get(index);
                if (instruction.opcode == Const.GOTO)
                {
                    Goto g = (Goto)instruction;

                    index--;
                    // Search previous 'goto' instruction
                    instruction = list.get(index);
                    if (instruction.opcode == Const.JSR)
                    {
                        fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    }
                    else
                    {
                        // Search jump 'goto' instruction
                        index = InstructionUtil.getIndexForOffset(
                            list, g.getJumpOffset());
                        instruction = list.get(index);

                        if (instruction.opcode == Const.JSR)
                        {
                            fastCodeException.type =
                                FastConstants.TYPE_118_CATCH_FINALLY;
                        }
                        else
                        {
                            instruction = list.get(index - 1);

                            if (instruction.opcode == Const.ATHROW) {
                                fastCodeException.type =
                                    FastConstants.TYPE_ECLIPSE_677_CATCH_FINALLY;
                            } else {
                                fastCodeException.type =
                                    FastConstants.TYPE_118_CATCH_FINALLY_2;
                            }
                        }
                    }
                } else if (instruction.opcode == Const.RET)
                {
                    fastCodeException.type =
                        FastConstants.TYPE_118_CATCH_FINALLY;
                }
                else
                {
                    index--;
                    // Search previous instruction
                    instruction = list.get(index);
                    if (instruction.opcode == Const.JSR)
                    {
                        fastCodeException.type =
                            FastConstants.TYPE_131_CATCH_FINALLY;
                    }
                }
            }
            break;
        default:
            // 1.3.1, 1.4.2, 1.5.0, jikes 1.2.2 or eclipse 677
            // Yes, contains catch ?
            if (fastCodeException.catches == null	||
                fastCodeException.catches.isEmpty())
            {
                // No, 1.4.2 or jikes 1.2.2 ?
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.tryToOffset);
                if (index < 0) {
                    return;
                }

                Instruction instruction = list.get(index);

                switch (instruction.opcode)
                {
                case Const.JSR:
                    fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    break;
                case Const.ATHROW:
                    fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    break;
                case Const.GOTO:
                    Goto g = (Goto)instruction;

                    // Search previous 'goto' instruction
                    instruction = InstructionUtil.getInstructionAt(
                        list, g.getJumpOffset());
                    if (instruction == null) {
                        return;
                    }

                    if (instruction.opcode == Const.JSR &&
                        ((Jsr)instruction).branch < 0)
                    {
                        fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    } else if (index > 0 && list.get(index-1).opcode == Const.JSR) {
                        fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    } else {
                        fastCodeException.type = FastConstants.TYPE_142;
                    }
                    break;
                case Const.POP:
                    defineTypeJikes122Or142(
                        list, fastCodeException, ((Pop)instruction).objectref, index);
                    break;
                case Const.ASTORE:
                    defineTypeJikes122Or142(
                        list, fastCodeException, ((AStore)instruction).valueref, index);
                    break;
                case Const.RETURN:
                case ByteCodeConstants.XRETURN:
                    // 1.3.1, 1.4.2 or jikes 1.2.2 ?
                    if (index > 0 && list.get(index-1).opcode == Const.JSR) {
                        fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    } else {
                        fastCodeException.type = FastConstants.TYPE_142;
                    }
                    break;
                default:
                    fastCodeException.type = FastConstants.TYPE_142;
                }
            }
            else
            {
                // Yes, contains catch(s) & multiple finally
                // Control que toutes les instructions 'goto' sautent sur la
                // meme instruction.
                boolean uniqueJumpAddressFlag = true;
                int uniqueJumpAddress = -1;

                if (fastCodeException.catches != null)
                {
                    FastCodeExceptionCatch fcec;
                    int index;
                    for (int i=fastCodeException.catches.size()-1; i>=0; --i)
                    {
                        fcec = fastCodeException.catches.get(i);
                        index = InstructionUtil.getIndexForOffset(
                                list, fcec.fromOffset);
                        if (index != -1)
                        {
                            Instruction instruction = list.get(index-1);
                            if (instruction.opcode == Const.GOTO)
                            {
                                int branch  = ((Goto)instruction).branch;
                                if (branch > 0)
                                {
                                    int jumpAddress = instruction.offset + branch;
                                    if (uniqueJumpAddress == -1)
                                    {
                                        uniqueJumpAddress = jumpAddress;
                                    }
                                    else if (uniqueJumpAddress != jumpAddress)
                                    {
                                        uniqueJumpAddressFlag = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index < 0) {
                    return;
                }

                index--;
                Instruction instruction = list.get(index);

                if (uniqueJumpAddressFlag &&
                    instruction.opcode == Const.GOTO)
                {
                    int branch  = ((Goto)instruction).branch;
                    if (branch > 0)
                    {
                        int jumpAddress = instruction.offset + branch;
                        if (uniqueJumpAddress == -1) {
                            uniqueJumpAddress = jumpAddress;
                        } else if (uniqueJumpAddress != jumpAddress) {
                            uniqueJumpAddressFlag = false;
                        }
                    }
                }

                if (!uniqueJumpAddressFlag)
                {
                    fastCodeException.type = FastConstants.TYPE_ECLIPSE_677_CATCH_FINALLY;
                    return;
                }

                index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.tryToOffset);
                if (index < 0) {
                    return;
                }

                instruction = list.get(index);

                switch (instruction.opcode)
                {
                case Const.JSR:
                    fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    break;
                case Const.ATHROW:
                    fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    break;
                case Const.GOTO:
                    Goto g = (Goto)instruction;

                    // Search previous 'goto' instruction
                    instruction = InstructionUtil.getInstructionAt(
                        list, g.getJumpOffset());
                    if (instruction == null) {
                        return;
                    }

                    if (instruction.opcode == Const.JSR &&
                        ((Jsr)instruction).branch < 0)
                    {
                        fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    } else if (index > 0 && list.get(index-1).opcode == Const.JSR) {
                        fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    } else {
                        fastCodeException.type = FastConstants.TYPE_142;
                    }
                    break;
                case Const.POP:
                    defineTypeJikes122Or142(
                        list, fastCodeException, ((Pop)instruction).objectref, index);
                    break;
                case Const.ASTORE:
                    defineTypeJikes122Or142(
                        list, fastCodeException, ((AStore)instruction).valueref, index);
                    break;
                case Const.RETURN:
                case ByteCodeConstants.XRETURN:
                    // 1.3.1, 1.4.2 or jikes 1.2.2 ?
                    instruction = InstructionUtil.getInstructionAt(
                        list, uniqueJumpAddress);
                    if (instruction != null &&
                        instruction.opcode == Const.JSR &&
                        ((Jsr)instruction).branch < 0) {
                        fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    } else if (index > 0 && list.get(index-1).opcode == Const.JSR) {
                        fastCodeException.type = FastConstants.TYPE_131_CATCH_FINALLY;
                    } else {
                        fastCodeException.type = FastConstants.TYPE_142;
                    }
                    break;
                default:
                    // TYPE_ECLIPSE_677_FINALLY or TYPE_142 ?
                    index = InstructionUtil.getIndexForOffset(
                            list, fastCodeException.finallyFromOffset);
                    Instruction firstFinallyInstruction = list.get(index+1);

                    if (firstFinallyInstruction.opcode != Const.ASTORE)
                    {
                        fastCodeException.type = FastConstants.TYPE_142;
                    }
                    else
                    {
                        int exceptionIndex = ((AStore)list.get(index)).index;
                        int length = list.size();

                        // Search throw instruction
                        while (++index < length)
                        {
                            instruction = list.get(index);
                            if (instruction.opcode == Const.ATHROW)
                            {
                                AThrow athrow = (AThrow)instruction;
                                if (athrow.value.opcode == Const.ALOAD &&
                                    ((ALoad)athrow.value).index == exceptionIndex) {
                                    break;
                                }
                            }
                        }

                        if (++index >= length)
                        {
                            fastCodeException.type = FastConstants.TYPE_142;
                        }
                        else
                        {
                            instruction = list.get(index);

                            fastCodeException.type =
                                (instruction.opcode != firstFinallyInstruction.opcode ||
                                 firstFinallyInstruction.lineNumber == Instruction.UNKNOWN_LINE_NUMBER ||
                                 firstFinallyInstruction.lineNumber != instruction.lineNumber) ?
                                         FastConstants.TYPE_142 :
                                         FastConstants.TYPE_ECLIPSE_677_CATCH_FINALLY;
                        }
                    }
                }
            }
        }
    }

    private static boolean tryBlockContainsJsr(
        List<Instruction> list, FastCodeExcepcion fastCodeException)
    {
        int index = InstructionUtil.getIndexForOffset(
                list, fastCodeException.tryToOffset);

        if (index != -1)
        {
            int tryFromOffset = fastCodeException.tryFromOffset;

            Instruction instruction;
            for (;;)
            {
                instruction = list.get(index);

                if (instruction.offset <= tryFromOffset)
                {
                    break;
                }

                if (instruction.opcode == Const.JSR && ((Jsr)instruction).getJumpOffset() >
                    fastCodeException.finallyFromOffset)
                {
                    return true;
                }

                if (index == 0)
                {
                    break;
                }

                index--;
            }
        }

        return false;
    }

    private static void defineTypeJikes122Or142(
            List<Instruction> list, FastCodeExcepcion fastCodeException,
            Instruction instruction, int index)
    {
        if (instruction.opcode == ByteCodeConstants.EXCEPTIONLOAD)
        {
            index--;
            instruction = list.get(index);

            if (instruction.opcode == Const.GOTO)
            {
                int jumpAddress = ((Goto)instruction).getJumpOffset();

                instruction = InstructionUtil.getInstructionAt(list, jumpAddress);

                if (instruction != null &&
                    instruction.opcode == Const.JSR)
                {
                    fastCodeException.type = FastConstants.TYPE_JIKES_122;
                    return;
                }
            }
        }

        fastCodeException.type = FastConstants.TYPE_142;
    }

    private static void computeAfterOffset(
            Method method, List<Instruction> list,
            List<int[]> switchCaseOffsets,
            List<FastCodeExcepcion> fastCodeExceptions,
            FastCodeExcepcion fastCodeException, int fastCodeExceptionIndex)
    {
        switch (fastCodeException.type)
        {
        case FastConstants.TYPE_118_CATCH_FINALLY:
            {
                // Strategie : Trouver l'instruction suivant 'ret' de la sous
                // routine 'finally'.
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.afterOffset);
                if (index < 0 || index >= list.size()) {
                    return;
                }

                int length = list.size();
                IntSet offsetSet = new IntSet();
                int retCounter = 0;

                Instruction i;
                // Search 'ret' instruction
                // Permet de prendre en compte les sous routines imbriquées
                while (++index < length)
                {
                    i = list.get(index);

                    if (i.opcode == Const.JSR) {
                        offsetSet.add(((Jsr)i).getJumpOffset());
                    } else if (i.opcode == Const.RET) {
                        if (offsetSet.size() == retCounter)
                        {
                            fastCodeException.afterOffset = i.offset + 1;
                            return;
                        }
                        retCounter++;
                    }
                }
            }
            break;
        case FastConstants.TYPE_118_CATCH_FINALLY_2:
            {
                Instruction instruction = InstructionUtil.getInstructionAt(
                        list, fastCodeException.afterOffset);
                if (instruction == null) {
                    return;
                }

                fastCodeException.afterOffset = instruction.offset + 1;
            }
            break;
        case FastConstants.TYPE_118_FINALLY_2:
            {
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.afterOffset);
                if (index < 0 || index >= list.size()) {
                    return;
                }

                index++;
                Instruction i = list.get(index);
                if (i.opcode != Const.GOTO) {
                    return;
                }

                fastCodeException.afterOffset = ((Goto)i).getJumpOffset();
            }
            break;
        case FastConstants.TYPE_JIKES_122:
            // Le traitement suivant etait faux pour reconstruire la méthode
            // "basic.data.TestTryCatchFinally .methodTryFinally1()" compile
            // par "Eclipse Java Compiler v_677_R32x, 3.2.1 release".
//			{
//				int index = InstructionUtil.getIndexForOffset(
//						list, fastCodeException.afterOffset);
//				if ((index < 0) || (index >= list.size()))
//					return;
//				Instruction i = list.get(++index);
//				fastCodeException.afterOffset = i.offset;
//			}
            break;
        case FastConstants.TYPE_ECLIPSE_677_FINALLY:
            {
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index < 0) {
                    return;
                }

                int length = list.size();
                Instruction instruction = list.get(index);

                if (instruction.opcode == Const.POP) {
                    // Search the first throw instruction
                    while (++index < length)
                    {
                        instruction = list.get(index);
                        if (instruction.opcode == Const.ATHROW)
                        {
                            fastCodeException.afterOffset =
                                instruction.offset + 1;
                            break;
                        }
                    }
                } else if (instruction.opcode == Const.ASTORE) {
                    // L'un des deux cas les plus complexes :
                    // - le bloc 'finally' est dupliqué deux fois.
                    // - aucun 'goto' ne saute après le dernier bloc finally.
                    // Methode de calcul de 'afterOffset' :
                    // - compter le nombre d'instructions entre le début du 1er bloc
                    //   'finally' et le saut du goto en fin de bloc 'try'.
                    // - Ajouter ce nombre à l'index de l'instruction vers laquelle
                    //   saute le 'goto' precedent le 1er bloc 'finally'.
                    int finallyStartIndex = index+1;
                    int exceptionIndex = ((AStore)instruction).index;

                    // Search throw instruction
                    while (++index < length)
                    {
                        instruction = list.get(index);
                        if (instruction.opcode == Const.ATHROW)
                        {
                            AThrow athrow = (AThrow)instruction;
                            if (athrow.value.opcode == Const.ALOAD &&
                                ((ALoad)athrow.value).index == exceptionIndex) {
                                break;
                            }
                        }
                    }

                    index += index - finallyStartIndex + 1;

                    if (index < length) {
                        fastCodeException.afterOffset = list.get(index).offset;
                    }
                }
            }
            break;
        case FastConstants.TYPE_ECLIPSE_677_CATCH_FINALLY:
            {
                // L'un des deux cas les plus complexes :
                // - le bloc 'finally' est dupliqué deux ou trois fois.
                // - aucun 'goto' ne saute après le dernier bloc finally.
                // Methode de calcul de 'afterOffset' :
                // - compter le nombre d'instructions entre le début du 1er bloc
                //   'finally' et le saut du goto en fin de bloc 'try'.
                // - Ajouter ce nombre à l'index de l'instruction vers laquelle
                //   saute le 'goto' precedent le 1er bloc 'finally'.
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index < 0) {
                    return;
                }

                Instruction instruction = list.get(index);

                if (instruction.opcode != Const.ASTORE) {
                    return;
                }

                int finallyStartIndex = index+1;
                int exceptionIndex = ((AStore)instruction).index;
                int length = list.size();

                // Search throw instruction
                while (++index < length)
                {
                    instruction = list.get(index);
                    if (instruction.opcode == Const.ATHROW)
                    {
                        AThrow athrow = (AThrow)instruction;
                        if (findAloadForAThrow(exceptionIndex, athrow))
                        {
                            break;
                        }
                    }
                }

                int delta = index - finallyStartIndex;
                index += delta + 1;

                if (index < list.size()) {
                    int afterOffset = list.get(index).offset;

                    // Verification de la presence d'un bloc 'finally' pour les blocs
                    // 'catch'.
                    if (index < length &&
                        list.get(index).opcode == Const.GOTO)
                    {
                        Goto g = (Goto)list.get(index);
                        int jumpOffset = g.getJumpOffset();
                        int indexTmp = index + delta + 1;

                        if (indexTmp < length &&
                            list.get(indexTmp-1).offset < jumpOffset &&
                            jumpOffset <= list.get(indexTmp).offset)
                        {
                            // Reduction de 'afterOffset' a l'aide des 'Branch Instructions'
                            afterOffset = reduceAfterOffsetWithBranchInstructions(
                                list, fastCodeException,
                                fastCodeException.finallyFromOffset,
                                list.get(indexTmp).offset);

                            // Reduction de 'afterOffset' a l'aide des numeros de ligne
                            if (! fastCodeException.synchronizedFlag)
                            {
                                afterOffset = reduceAfterOffsetWithLineNumbers(
                                    list, fastCodeException, afterOffset);
                            }

                            // Reduction de 'afterOffset' a l'aide des instructions de
                            // gestion des exceptions englobantes
                            afterOffset = reduceAfterOffsetWithExceptions(
                                fastCodeExceptions, fastCodeException.tryFromOffset,
                                fastCodeException.finallyFromOffset, afterOffset);
                        }
                }

                fastCodeException.afterOffset = afterOffset;
            }
            }
            break;
        case FastConstants.TYPE_118_FINALLY:
            {
                // Re-estimation de la valeur de l'attribut 'afterOffset'.
                // Strategie : le bon offset, après le bloc 'try-finally', se
                // trouve après l'instruction 'ret' de la sous procedure du
                // bloc 'finally'.
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index <= 0) {
                    return;
                }

                int length = list.size();

                // Gestion des instructions JSR imbriquees
                int offsetOfJsrsLength = list.get(length-1).offset + 1;
                boolean[] offsetOfJsrs = new boolean[offsetOfJsrsLength];
                int level = 0;

                Instruction i;
                while (++index < length)
                {
                    i = list.get(index);

                    if (offsetOfJsrs[i.offset]) {
                        level++;
                    }

                    if (i.opcode == Const.JSR)
                    {
                        int jumpOffset = ((Jsr)i).getJumpOffset();
                        if (jumpOffset < offsetOfJsrsLength) {
                            offsetOfJsrs[jumpOffset] = true;
                        }
                    }
                    else if (i.opcode == Const.RET)
                    {
                        if (level <= 1)
                        {
                            fastCodeException.afterOffset = i.offset+1;
                            break;
                        }
                        level--;
                    }
                }
            }
            break;
        case FastConstants.TYPE_118_FINALLY_THROW:
        case FastConstants.TYPE_131_CATCH_FINALLY:
            {
                int index = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.finallyFromOffset);
                if (index <= 0) {
                    return;
                }

                // Search last 'ret' instruction of the finally block
                int length = list.size();

                Instruction i;
                while (++index < length)
                {
                    i = list.get(index);
                    if (i.opcode == Const.RET)
                    {
                        fastCodeException.afterOffset = (++index < length) ?
                            list.get(index).offset :
                            i.offset+1;
                        break;
                    }
                }
            }
            break;
        default:
            {
                int length = list.size();

                // Re-estimation de la valeur de l'attribut 'afterOffset'.
                // Strategie : parcours du bytecode jusqu'à trouver une
                // instruction de saut vers la derniere instruction 'return',
                // ou une instruction 'athrow' ou une instruction de saut
                // négatif allant en deca du début du dernier block. Le parcours
                // du bytecode doit prendre en compte les sauts positifs.

                // Calcul de l'offset après la structure try-catch
                int afterOffset = fastCodeException.afterOffset;
                if (afterOffset == -1) {
                    afterOffset = list.get(length-1).offset + 1;
                }

                // Reduction de 'afterOffset' a l'aide des 'Branch Instructions'
                afterOffset = reduceAfterOffsetWithBranchInstructions(
                        list, fastCodeException, fastCodeException.maxOffset,
                        afterOffset);

                // Reduction de 'afterOffset' a l'aide des numeros de ligne
                if (! fastCodeException.synchronizedFlag)
                {
                    afterOffset = reduceAfterOffsetWithLineNumbers(
                            list, fastCodeException, afterOffset);
                }

                // Reduction de 'afterOffset' a l'aide des instructions 'switch'
                afterOffset = reduceAfterOffsetWithSwitchInstructions(
                        switchCaseOffsets, fastCodeException.tryFromOffset,
                        fastCodeException.maxOffset, afterOffset);

                // Reduction de 'afterOffset' a l'aide des instructions de gestion
                // des exceptions englobantes
                fastCodeException.afterOffset = afterOffset =
                    reduceAfterOffsetWithExceptions(
                        fastCodeExceptions, fastCodeException.tryFromOffset,
                        fastCodeException.maxOffset, afterOffset);

                // Recherche de la 1ere exception débutant après 'maxOffset'
                int tryFromOffset = Integer.MAX_VALUE;
                int tryIndex = fastCodeExceptionIndex + 1;
                while (tryIndex < fastCodeExceptions.size())
                {
                    int tryFromOffsetTmp =
                        fastCodeExceptions.get(tryIndex).tryFromOffset;
                    if (tryFromOffsetTmp > fastCodeException.maxOffset)
                    {
                        tryFromOffset = tryFromOffsetTmp;
                        break;
                    }
                    tryIndex++;
                }

                // Parcours
                int maxIndex = InstructionUtil.getIndexForOffset(
                        list, fastCodeException.maxOffset);
                int index = maxIndex;
                Instruction instruction;
                while (index < length)
                {
                    instruction = list.get(index);

                    if (instruction.offset >= afterOffset) {
                        break;
                    }

                    if (instruction.offset > tryFromOffset)
                    {
                        // Saut des blocs try-catch-finally
                        FastCodeExcepcion fce =
                            fastCodeExceptions.get(tryIndex);
                        int afterOffsetTmp = fce.afterOffset;

                        int tryFromOffsetTmp;
                        FastCodeExcepcion fceTmp;
                        // Recherche du plus grand offset de fin parmi toutes
                        // les exceptions débutant à l'offset 'tryFromOffset'
                        for (;;)
                        {
                            tryIndex++;
                            if (tryIndex >= fastCodeExceptions.size())
                            {
                                tryFromOffset = Integer.MAX_VALUE;
                                break;
                            }
                            tryFromOffsetTmp = fastCodeExceptions.get(tryIndex).tryFromOffset;
                            if (fce.tryFromOffset != tryFromOffsetTmp)
                            {
                                tryFromOffset = tryFromOffsetTmp;
                                break;
                            }
                            fceTmp = fastCodeExceptions.get(tryIndex);
                            if (afterOffsetTmp < fceTmp.afterOffset) {
                                afterOffsetTmp = fceTmp.afterOffset;
                            }
                        }

                        while (index < length &&
                               list.get(index).offset < afterOffsetTmp) {
                            index++;
                        }
                    }
                    else
                    {
                        switch (instruction.opcode)
                        {
                        case Const.ATHROW:
                        case Const.RETURN:
                        case ByteCodeConstants.XRETURN:
                            // Verification que toutes les variables
                            // locales utilisees sont definies dans le
                            // bloc du dernier catch ou de finally
                            // OU que l'instruction participe a un
                            // operateur ternaire
                            if (CheckLocalVariableUsedVisitor.visit(
                                    method.getLocalVariables(),
                                    fastCodeException.maxOffset,
                                    instruction) || checkTernaryOperator(list, index))
                            {
                                // => Instruction incluse au bloc
                                fastCodeException.afterOffset = instruction.offset+1;
                            } else if (index+1 >= length)
                            {
                                // Derniere instruction de la liste
                                if (instruction.opcode == Const.ATHROW)
                                {
                                    // Dernier 'throw'
                                    // => Instruction incluse au bloc
                                    fastCodeException.afterOffset = instruction.offset+1;
                                }
                                else
                                {
                                    // Dernier 'return'
                                    // => Instruction placee après le bloc
                                    fastCodeException.afterOffset = instruction.offset;
                                }
                            }
                            else
                            {
                                // Une instruction du bloc 'try-catch-finally'
                                // saute-t-elle vers l'instuction qui suit
                                // cette instruction ?
                                int tryFromIndex =
                                    InstructionUtil.getIndexForOffset(
                                        list, fastCodeException.tryFromOffset);
                                int beforeInstructionOffset = (index==0) ?
                                    0 : list.get(index-1).offset;

                                if (InstructionUtil.checkNoJumpToInterval(
                                    list, tryFromIndex, maxIndex,
                                    beforeInstructionOffset, instruction.offset))
                                {
                                    // Aucune instruction du bloc
                                    // 'try-catch-finally' ne saute vers
                                    // cette instruction.
                                    // => Instruction incluse au bloc
                                    fastCodeException.afterOffset = instruction.offset+1;
                                }
                                else
                                {
                                    // Une instruction du bloc
                                    // 'try-catch-finally' saute vers
                                    // cette instruction.
                                    // => Instruction placee après le bloc
                                    fastCodeException.afterOffset = instruction.offset;
                                }
                            }
                            return;
                        case Const.GOTO:
                        case ByteCodeConstants.IFCMP:
                        case ByteCodeConstants.IF:
                        case ByteCodeConstants.IFXNULL:
                            int jumpOffsetTmp;

                            if (instruction.opcode == Const.GOTO)
                            {
                                jumpOffsetTmp =
                                    ((BranchInstruction)instruction).getJumpOffset();
                            }
                            else
                            {
                                // L'aggregation des instructions 'if' n'a pas
                                // encore ete executee. Recherche du plus petit
                                // offset de saut parmi toutes les instructions
                                // 'if' qui suivent.
                                index = ComparisonInstructionAnalyzer.getLastIndex(
                                    list, index);
                                BranchInstruction lastBi =
                                    (BranchInstruction)list.get(index);
                                jumpOffsetTmp = lastBi.getJumpOffset();
                            }

                            if (jumpOffsetTmp > instruction.offset)
                            {
                                // Saut positif
                                if (jumpOffsetTmp >= afterOffset) {
                                    if (instruction.opcode == Const.GOTO ||
                                        jumpOffsetTmp != afterOffset)
                                    {
                                        // Une instruction du bloc 'try-catch-finally'
                                        // saute-t-elle vers cett instuction ?
                                        int tryFromIndex =
                                            InstructionUtil.getIndexForOffset(
                                                list, fastCodeException.tryFromOffset);
                                        int beforeInstructionOffset = (index==0) ?
                                                0 : list.get(index-1).offset;

                                        if (InstructionUtil.checkNoJumpToInterval(
                                                list, tryFromIndex, maxIndex,
                                                beforeInstructionOffset, instruction.offset))
                                        {
                                            // Aucune instruction du bloc
                                            // 'try-catch-finally' ne saute vers
                                            // cette instuction
                                            // => Instruction incluse au bloc
                                            fastCodeException.afterOffset = instruction.offset+1;
                                        }
                                        else
                                        {
                                            // Une instruction du bloc
                                            // 'try-catch-finally' saute vers
                                            // cette instuction
                                            // => Instruction placée après le bloc
                                            fastCodeException.afterOffset = instruction.offset;
                                        }
                                    }
                                    //else
                                    //{
                                        // Si l'instruction est un saut conditionnel
                                        // et si l'offset de saut est le meme que 'afterOffset',
                                        // alors l'instruction fait partie du dernier bloc.
                                    //}
                                    return;
                                }
                                while (++index < length)
                                {
                                    if (list.get(index).offset >= jumpOffsetTmp)
                                    {
                                        --index;
                                        break;
                                    }
                                }
                            }
                            else if (jumpOffsetTmp <= fastCodeException.tryFromOffset)
                            {
                                // Saut négatif
                                if (index > 0 &&
                                    instruction.lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                                {
                                    Instruction beforeInstruction = list.get(index-1);
                                    if (instruction.lineNumber ==
                                            beforeInstruction.lineNumber)
                                    {
                                        // For instruction ?
                                        if ((beforeInstruction.opcode ==
                                                Const.ASTORE &&
                                            ((AStore)beforeInstruction).valueref.opcode ==
                                                ByteCodeConstants.EXCEPTIONLOAD) || (beforeInstruction.opcode ==
                                                Const.POP &&
                                            ((Pop)beforeInstruction).objectref.opcode ==
                                                ByteCodeConstants.EXCEPTIONLOAD))
                                        {
                                            // Non
                                            fastCodeException.afterOffset =
                                                instruction.offset;
                                        } else {
                                            // Oui
                                            fastCodeException.afterOffset =
                                                beforeInstruction.offset;
                                        }
                                        return;
                                    }
                                }
                                fastCodeException.afterOffset =
                                    instruction.offset;
                                return;
                            }
                            break;
                        case Const.LOOKUPSWITCH:
                        case Const.TABLESWITCH:
                            Switch s = (Switch)instruction;

                            // Search max offset
                            int maxOffset = s.defaultOffset;
                            int i = s.offsets.length;
                            while (i-- > 0)
                            {
                                int offset = s.offsets[i];
                                if (maxOffset < offset) {
                                    maxOffset = offset;
                                }
                            }

                            if (maxOffset < afterOffset)
                            {
                                while (++index < length)
                                {
                                    if (list.get(index).offset >= maxOffset)
                                    {
                                        --index;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                        index++;
                    }
                }
            }
        }
    }

    private static boolean checkTernaryOperator(List<Instruction> list, int index)
    {
        // Motif des operateurs ternaires :
        //  index-3) If instruction (IF || IFCMP || IFXNULL || COMPLEXIF)
        //  index-2) TernaryOpStore
        //  index-1) Goto
        //    index) (X)Return
        if (index > 2 &&
            list.get(index-1).opcode == Const.GOTO &&
            list.get(index-2).opcode == ByteCodeConstants.TERNARYOPSTORE)
        {
            Goto g = (Goto)list.get(index-1);
            int jumpOffset = g.getJumpOffset();
            int returnOffset = list.get(index).offset;
            if (g.offset < jumpOffset && jumpOffset < returnOffset)
            {
                return true;
            }
        }

        return false;
    }

    private static int reduceAfterOffsetWithBranchInstructions(
        List<Instruction> list, FastCodeExcepcion fastCodeException,
        int firstOffset, int afterOffset)
    {
        Instruction instruction;

        // Check previous instructions
        int index = InstructionUtil.getIndexForOffset(
                list, fastCodeException.tryFromOffset);

        if (index != -1)
        {
            while (index-- > 0)
            {
                instruction = list.get(index);

                if (instruction.opcode == ByteCodeConstants.IF || instruction.opcode == ByteCodeConstants.IFCMP
                        || instruction.opcode == ByteCodeConstants.IFXNULL || instruction.opcode == Const.GOTO) {
                    int jumpOffset = ((BranchInstruction)instruction).getJumpOffset();
                    if (firstOffset < jumpOffset && jumpOffset < afterOffset) {
                        afterOffset = jumpOffset;
                    }
                }
            }
        }

        // Check next instructions
        index = list.size();
        do
        {
            index--;
            instruction = list.get(index);

            if (instruction.opcode == ByteCodeConstants.IF || instruction.opcode == ByteCodeConstants.IFCMP
                    || instruction.opcode == ByteCodeConstants.IFXNULL || instruction.opcode == Const.GOTO) {
                int jumpOffset = ((BranchInstruction)instruction).getJumpOffset();
                if (firstOffset < jumpOffset && jumpOffset < afterOffset) {
                    afterOffset = jumpOffset;
                }
            }
        }
        while (instruction.offset > afterOffset);

        return afterOffset;
    }

    private static int reduceAfterOffsetWithLineNumbers(
        List<Instruction> list, FastCodeExcepcion fastCodeException,
        int afterOffset)
    {
        int fromIndex = InstructionUtil.getIndexForOffset(
                list, fastCodeException.tryFromOffset);
        int index = fromIndex;

        if (index != -1)
        {
            // Search first line number
            int length = list.size();
            int firstLineNumber = Instruction.UNKNOWN_LINE_NUMBER;
            Instruction instruction;

            do
            {
                instruction = list.get(index);
                index++;

                if (instruction.lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                {
                    firstLineNumber = instruction.lineNumber;
                    break;
                }
            }
            while (instruction.offset < afterOffset && index < length);

            if (firstLineNumber != Instruction.UNKNOWN_LINE_NUMBER)
            {
                // Exclude instruction with a smaller line number
                int maxOffset = fastCodeException.maxOffset;
                index = InstructionUtil.getIndexForOffset(list, afterOffset);

                if (index != -1)
                {
                    while (index-- > 0)
                    {
                        instruction = list.get(index);

                        if (instruction.offset <= maxOffset || (instruction.lineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                            instruction.lineNumber >= firstLineNumber))
                        {
                            break;
                        }

                        // L'instruction a un numero de ligne inferieur aux
                        // instructions du bloc 'try'. A priori, elle doit être
                        // place après le bloc 'catch'.

                        // Est-ce une instruction de saut ? Si oui, est-ce que
                        // la placer hors du bloc 'catch' genererait deux points
                        // de sortie du bloc ?
                        if (instruction.opcode == Const.GOTO)
                        {
                            int jumpOffset = ((Goto)instruction).getJumpOffset();

                            if (! InstructionUtil.checkNoJumpToInterval(
                                    list,
                                    fromIndex, index,
                                    jumpOffset-1, jumpOffset))
                            {
                                break;
                            }
                        }

                        // Est-ce une instruction 'return' ? Si oui, est-ce que
                        // la placer hors du bloc 'catch' genererait deux points
                        // de sortie du bloc ?
                        if (instruction.opcode == Const.RETURN)
                        {
                            int maxIndex = InstructionUtil.getIndexForOffset(
                                    list, maxOffset);

                            if (list.get(maxIndex-1).opcode == instruction.opcode)
                            {
                                break;
                            }
                        }

                        /*
                         * A QUOI SERT CE BLOC ? A QUEL CAS D'UTILISATION
                         * CORRESPOND T IL ?
                         * /
                        if (instruction.opcode != Const.IINC)
                        {
                            if (// Check previous instructions
                                InstructionUtil.CheckNoJumpToInterval(
                                    list,
                                    0, index,
                                    maxOffset, instruction.offset) &&
                                // Check next instructions
                                InstructionUtil.CheckNoJumpToInterval(
                                    list,
                                    index+1, length,
                                    maxOffset, instruction.offset))
                            {
                                break;
                            }
                        }
                        / */

                        afterOffset = instruction.offset;
                    }
                }
            }
        }

        return afterOffset;
    }

    private static int reduceAfterOffsetWithSwitchInstructions(
        List<int[]> switchCaseOffsets,
        int firstOffset, int lastOffset, int afterOffset)
    {
        int i = switchCaseOffsets.size();
        int[] offsets;
        int j;
        while (i-- > 0)
        {
            offsets = switchCaseOffsets.get(i);

            j = offsets.length;
            if (j > 1)
            {
                j--;
                int offset2 = offsets[j];

                int offset1;
                while (j-- > 0)
                {
                    offset1 = offsets[j];

                    if (offset1 != -1 &&
                        offset1 <= firstOffset && lastOffset < offset2 && (afterOffset == -1 || afterOffset > offset2)) {
                        afterOffset = offset2;
                    }

                    offset2 = offset1;
                }
            }
        }

        return afterOffset;
    }

    private static int reduceAfterOffsetWithExceptions(
        List<FastCodeExcepcion> fastCodeExceptions,
        int fromOffset, int maxOffset, int afterOffset)
    {
        int i = fastCodeExceptions.size();
        FastCodeExcepcion fastCodeException;
        int toOffset;
        while (i-- > 0)
        {
            fastCodeException = fastCodeExceptions.get(i);

            toOffset = fastCodeException.finallyFromOffset;

            if (fastCodeException.catches != null)
            {
                int j = fastCodeException.catches.size();
                FastCodeExceptionCatch fcec;
                while (j-- > 0)
                {
                    fcec = fastCodeException.catches.get(j);

                    if (toOffset != -1 &&
                        fcec.fromOffset <= fromOffset &&
                        maxOffset < toOffset && (afterOffset == -1 || afterOffset > toOffset)) {
                        afterOffset = toOffset;
                    }

                    toOffset = fcec.fromOffset;
                }
            }

            if (fastCodeException.tryFromOffset <= fromOffset &&
                maxOffset < toOffset && (afterOffset == -1 || afterOffset > toOffset)) {
                afterOffset = toOffset;
            }
        }

        return afterOffset;
    }

    public static void formatFastTry(
        LocalVariables localVariables, FastCodeExcepcion fce,
        FastTry fastTry, int returnOffset)
    {
        switch (fce.type)
        {
        case FastConstants.TYPE_CATCH:
            formatCatch(localVariables, fce, fastTry);
            break;
        case FastConstants.TYPE_118_FINALLY:
            format118Finally(localVariables, fce, fastTry);
            break;
        case FastConstants.TYPE_118_FINALLY_2:
            format118Finally2(fce, fastTry);
            break;
        case FastConstants.TYPE_118_FINALLY_THROW:
            format118FinallyThrow(fastTry);
            break;
        case FastConstants.TYPE_118_CATCH_FINALLY:
            format118CatchFinally(fce, fastTry);
            break;
        case FastConstants.TYPE_118_CATCH_FINALLY_2:
            format118CatchFinally2(fce, fastTry);
            break;
        case FastConstants.TYPE_131_CATCH_FINALLY:
            format131CatchFinally(localVariables, fce, fastTry);
            break;
        case FastConstants.TYPE_142:
            format142(localVariables, fce, fastTry);
            break;
        case FastConstants.TYPE_142_FINALLY_THROW:
            format142FinallyThrow(fastTry);
            break;
        case FastConstants.TYPE_JIKES_122:
            formatJikes122(localVariables, fce, fastTry, returnOffset);
            break;
        case FastConstants.TYPE_ECLIPSE_677_FINALLY:
            formatEclipse677Finally(fce, fastTry);
            break;
        case FastConstants.TYPE_ECLIPSE_677_CATCH_FINALLY:
            formatEclipse677CatchFinally(fce, fastTry, returnOffset);
        }
    }

    private static void formatCatch(
        LocalVariables localVariables, FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int jumpOffset = -1;

        // Remove last 'goto' instruction in try block
        if (!tryInstructions.isEmpty())
        {
            int lastIndex = tryInstructions.size() - 1;
            Instruction instruction = tryInstructions.get(lastIndex);

            if (instruction.opcode == Const.GOTO)
            {
                int tmpJumpOffset = ((Goto)instruction).getJumpOffset();

                if (tmpJumpOffset < fce.tryFromOffset ||
                    instruction.offset < tmpJumpOffset)
                {
                    jumpOffset = tmpJumpOffset;
                    fce.tryToOffset = instruction.offset;
                    tryInstructions.remove(lastIndex);
                }
            }
        }

        // Remove JSR instruction in try block before 'return' instruction
        formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
            tryInstructions, localVariables, Instruction.UNKNOWN_LINE_NUMBER);

        int i = fastTry.catches.size();
        List<Instruction> catchInstructions;
        while (i-- > 0)
        {
            catchInstructions = fastTry.catches.get(i).instructions;

            // Remove first catch instruction in each catch block
            if (formatCatchRemoveFirstCatchInstruction(catchInstructions.get(0))) {
                catchInstructions.remove(0);
            }

            // Remove last 'goto' instruction
            if (!catchInstructions.isEmpty())
            {
                int lastIndex = catchInstructions.size() - 1;
                Instruction instruction = catchInstructions.get(lastIndex);

                if (instruction.opcode == Const.GOTO)
                {
                    int tmpJumpOffset = ((Goto)instruction).getJumpOffset();

                    if (tmpJumpOffset < fce.tryFromOffset ||
                        instruction.offset < tmpJumpOffset)
                    {
                        if (jumpOffset == -1)
                        {
                            jumpOffset = tmpJumpOffset;
                            fce.catches.get(i).toOffset = instruction.offset;
                            catchInstructions.remove(lastIndex);
                        }
                        else if (jumpOffset == tmpJumpOffset)
                        {
                            fce.catches.get(i).toOffset = instruction.offset;
                            catchInstructions.remove(lastIndex);
                        }
                    }
                }

                // Remove JSR instruction in try block before 'return' instruction
                formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
                    catchInstructions, localVariables,
                    Instruction.UNKNOWN_LINE_NUMBER);
            }
        }
    }

    private static boolean formatCatchRemoveFirstCatchInstruction(
        Instruction instruction)
    {
        switch (instruction.opcode)
        {
        case Const.POP:
            return
                ((Pop)instruction).objectref.opcode ==
                ByteCodeConstants.EXCEPTIONLOAD;

        case Const.ASTORE:
            return
                ((AStore)instruction).valueref.opcode ==
                ByteCodeConstants.EXCEPTIONLOAD;

        default:
            return false;
        }
    }

    private static void format118Finally(
        LocalVariables localVariables, FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int length = tryInstructions.size();

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(--length).opcode == Const.GOTO)
        {
            Goto g = (Goto)tryInstructions.remove(length);
            fce.tryToOffset = g.offset;
        }
        length--;
        // Remove last 'jsr' instruction in try block
        if (tryInstructions.get(length).opcode != Const.JSR) {
            throw new UnexpectedInstructionException();
        }
        tryInstructions.remove(length);

        // Remove JSR instruction in try block before 'return' instruction
        int finallyInstructionsLineNumber =
                fastTry.finallyInstructions.get(0).lineNumber;
        formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
            tryInstructions, localVariables, finallyInstructionsLineNumber);

        format118FinallyThrow(fastTry);
    }

    private static void format118Finally2(
        FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int tryInstructionsLength = tryInstructions.size();

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(tryInstructionsLength-1).opcode ==
                Const.GOTO)
        {
            tryInstructionsLength--;
            Goto g = (Goto)tryInstructions.remove(tryInstructionsLength);
            fce.tryToOffset = g.offset;
        }

        List<Instruction> finallyInstructions = fastTry.finallyInstructions;
        int finallyInstructionsLength = finallyInstructions.size();

        // Update all offset of instructions 'goto' and 'ifxxx' if
        // (finallyInstructions.gt(0).offset) < (jump offset) &&
        // (jump offset) < (finallyInstructions.gt(5).offset)
        if (finallyInstructionsLength > 5)
        {
            int firstFinallyOffset = finallyInstructions.get(0).offset;
            int lastFinallyOffset = finallyInstructions.get(5).offset;

            Instruction instruction;
            int jumpOffset;
            while (tryInstructionsLength-- > 0)
            {
                instruction = tryInstructions.get(tryInstructionsLength);
                switch (instruction.opcode)
                {
                case ByteCodeConstants.IFCMP:
                    {
                        jumpOffset = ((IfCmp)instruction).getJumpOffset();

                        if (firstFinallyOffset < jumpOffset &&
                            jumpOffset <= lastFinallyOffset) {
                            ((IfCmp)instruction).branch =
                                firstFinallyOffset - instruction.offset;
                        }
                    }
                    break;
                case ByteCodeConstants.IF:
                case ByteCodeConstants.IFXNULL:
                    {
                        jumpOffset =
                            ((IfInstruction)instruction).getJumpOffset();

                        if (firstFinallyOffset < jumpOffset &&
                            jumpOffset <= lastFinallyOffset) {
                            ((IfInstruction)instruction).branch =
                                firstFinallyOffset - instruction.offset;
                        }
                    }
                    break;
                case ByteCodeConstants.COMPLEXIF:
                    {
                        jumpOffset =
                            ((BranchInstruction)instruction).getJumpOffset();

                        if (firstFinallyOffset < jumpOffset &&
                            jumpOffset <= lastFinallyOffset) {
                            ((ComplexConditionalBranchInstruction)instruction).branch =
                                firstFinallyOffset - instruction.offset;
                        }
                    }
                    break;
                case Const.GOTO:
                    {
                        jumpOffset = ((Goto)instruction).getJumpOffset();

                        if (firstFinallyOffset < jumpOffset &&
                            jumpOffset <= lastFinallyOffset) {
                            ((Goto)instruction).branch =
                                firstFinallyOffset - instruction.offset;
                        }
                    }
                    break;
                }
            }
        }

        // Remove last 'ret' instruction in finally block
        finallyInstructions.remove(finallyInstructionsLength - 1);
        // Remove 'AStore ExceptionLoad' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'jsr' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'athrow' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'jsr' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'goto' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'AStore ReturnAddressLoad' instruction in finally block
        finallyInstructions.remove(0);
    }

    private static void format118FinallyThrow(FastTry fastTry)
    {
        List<Instruction> finallyInstructions = fastTry.finallyInstructions;
        int length = finallyInstructions.size();

        length--;
        // Remove last 'ret' instruction in finally block
        Instruction i = finallyInstructions.get(length);
        if (i.opcode != Const.RET) {
            throw new UnexpectedInstructionException();
        }
        finallyInstructions.remove(length);
        // Remove 'AStore ExceptionLoad' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'jsr' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'athrow' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'astore' instruction (returnAddress) in finally block
        finallyInstructions.remove(0);
    }

    private static void format118CatchFinally(
            FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int tryInstructionsLength = tryInstructions.size();

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(--tryInstructionsLength).opcode ==
            Const.GOTO)
        {
            Goto g = (Goto)tryInstructions.remove(tryInstructionsLength);
            fce.tryToOffset = g.offset;
        }

        // Format catch blocks
        int i = fastTry.catches.size()-1;
        if (i >= 0)
        {
            List<Instruction>  catchInstructions =
                fastTry.catches.get(i).instructions;
            int catchInstructionsLength = catchInstructions.size();

            switch (catchInstructions.get(--catchInstructionsLength).opcode)
            {
            case Const.GOTO:
                // Remove 'goto' instruction in catch block
                catchInstructions.remove(catchInstructionsLength);
                catchInstructionsLength--;
                // Remove 'jsr' instruction in catch block
                catchInstructions.remove(catchInstructionsLength);
                break;
            case Const.RETURN:
            case ByteCodeConstants.XRETURN:
                catchInstructionsLength--;
                // Remove 'jsr' instruction in catch block
                catchInstructions.remove(catchInstructionsLength);

                if (catchInstructionsLength > 0 &&
                    catchInstructions.get(catchInstructionsLength-1).opcode == Const.ATHROW)
                {
                    // Remove 'return' instruction after a 'throw' instruction
                    catchInstructions.remove(catchInstructionsLength);
                }

                break;
            }

            // Remove first catch instruction in each catch block
            catchInstructions.remove(0);

            while (i-- > 0)
            {
                catchInstructions = fastTry.catches.get(i).instructions;
                catchInstructionsLength = catchInstructions.size();

                switch (catchInstructions.get(--catchInstructionsLength).opcode)
                {
                case Const.GOTO:
                    // Remove 'goto' instruction in catch block
                    Instruction in =
                        catchInstructions.remove(catchInstructionsLength);
                    fce.catches.get(i).toOffset = in.offset;
                    break;
                case Const.RETURN:
                case ByteCodeConstants.XRETURN:
                    catchInstructionsLength--;
                    // Remove 'jsr' instruction in catch block
                    catchInstructions.remove(catchInstructionsLength);
                    break;
                }

                // Remove first catch instruction in each catch block
                catchInstructions.remove(0);
            }
        }

        List<Instruction>  finallyInstructions = fastTry.finallyInstructions;
        int finallyInstructionsLength = finallyInstructions.size();

        finallyInstructionsLength--;
        // Remove last 'ret' instruction in finally block
        finallyInstructions.remove(finallyInstructionsLength);
        // Remove 'AStore ExceptionLoad' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'jsr' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'athrow' instruction in finally block
        finallyInstructions.remove(0);
        // Remove 'AStore ExceptionLoad' instruction in finally block
        finallyInstructions.remove(0);
    }

    private static void format118CatchFinally2(
        FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int tryInstructionsLength = tryInstructions.size();

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(--tryInstructionsLength).opcode ==
            Const.GOTO)
        {
            Goto g = (Goto)tryInstructions.remove(tryInstructionsLength);
            fce.tryToOffset = g.offset;
        }

        // Format catch blocks
        int i = fastTry.catches.size();
        List<Instruction> catchInstructions;
        int catchInstructionsLength;
        Instruction in;
        while (i-- > 0)
        {
            catchInstructions = fastTry.catches.get(i).instructions;
            catchInstructionsLength = catchInstructions.size();
            // Remove 'goto' instruction in catch block
            in = catchInstructions.remove(catchInstructionsLength - 1);
            fce.catches.get(i).toOffset = in.offset;
            // Remove first catch instruction in each catch block
            catchInstructions.remove(0);
        }

        // Remove 'Pop ExceptionLoad' instruction in finally block
        List<Instruction>  finallyInstructions = fastTry.finallyInstructions;
        finallyInstructions.remove(0);
    }

    /** Deux variantes existent. La sous procedure [finally] ne se trouve pas
     * toujours dans le block 'finally'.
     */
    private static void format131CatchFinally(
        LocalVariables localVariables, FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int length = tryInstructions.size();

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(--length).opcode == Const.GOTO)
        {
            Goto g = (Goto)tryInstructions.remove(length);
            fce.tryToOffset = g.offset;
        }
        // Remove JSR instruction in try block before 'return' instruction
        int finallyInstructionsLineNumber =
                fastTry.finallyInstructions.get(0).lineNumber;
        int jumpOffset = formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
                tryInstructions, localVariables, finallyInstructionsLineNumber);
        // Remove last 'jsr' instruction in try block
        length = tryInstructions.size();
        if (tryInstructions.get(--length).opcode == Const.JSR)
        {
            Jsr jsr = (Jsr)tryInstructions.remove(length);
            jumpOffset = jsr.getJumpOffset();
        }
        if (jumpOffset == -1) {
            throw new UnexpectedInstructionException();
        }

        List<Instruction> finallyInstructions = fastTry.finallyInstructions;

        if (jumpOffset < finallyInstructions.get(0).offset)
        {
            // La sous procedure [finally] se trouve dans l'un des blocs 'catch'.

            // Recherche et extraction de la sous procedure
            int i = fastTry.catches.size();
            int index;
            while (i-- > 0)
            {
                List<Instruction> catchInstructions =
                    fastTry.catches.get(i).instructions;

                if (catchInstructions.isEmpty() ||
                    catchInstructions.get(0).offset > jumpOffset) {
                    continue;
                }

                // Extract
                index = InstructionUtil.getIndexForOffset(catchInstructions, jumpOffset);
                finallyInstructions.clear();

                while (catchInstructions.get(index).opcode != Const.RET) {
                    finallyInstructions.add(catchInstructions.remove(index));
                }
                if (catchInstructions.get(index).opcode == Const.RET) {
                    finallyInstructions.add(catchInstructions.remove(index));
                }

                break;
            }

            // Format catch blocks
            i = fastTry.catches.size();
            List<Instruction> catchInstructions;
            while (i-- > 0)
            {
                catchInstructions = fastTry.catches.get(i).instructions;
                length = catchInstructions.size();

                // Remove last 'goto' instruction
                if (catchInstructions.get(--length).opcode == Const.GOTO)
                {
                    Goto g = (Goto)catchInstructions.remove(length);
                    fce.catches.get(i).toOffset = g.offset;
                }
                // Remove last 'jsr' instruction
                if (catchInstructions.get(--length).opcode == Const.JSR) {
                    catchInstructions.remove(length);
                }
                // Remove JSR instruction in try block before 'return' instruction
                formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
                    catchInstructions, localVariables,
                    finallyInstructionsLineNumber);
                // Remove first catch instruction in each catch block
                catchInstructions.remove(0);
            }

            // Format finally block
            length = finallyInstructions.size();

            length--;
            // Remove last 'ret' instruction in finally block
            finallyInstructions.remove(length);
        }
        else
        {
            // La sous procedure [finally] se trouve dans le bloc 'finally'.

            // Format catch blocks
            int i = fastTry.catches.size();
            List<Instruction> catchInstructions;
            while (i-- > 0)
            {
                catchInstructions = fastTry.catches.get(i).instructions;
                length = catchInstructions.size();

                // Remove last 'goto' instruction
                if (catchInstructions.get(--length).opcode == Const.GOTO)
                {
                    Goto g = (Goto)catchInstructions.remove(length);
                    fce.catches.get(i).toOffset = g.offset;
                }
                // Remove last 'jsr' instruction
                if (catchInstructions.get(--length).opcode == Const.JSR) {
                    catchInstructions.remove(length);
                }
                // Remove JSR instruction in try block before 'return' instruction
                formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
                    catchInstructions, localVariables,
                    finallyInstructionsLineNumber);
                // Remove first catch instruction in each catch block
                catchInstructions.remove(0);
            }

            // Format finally block
            length = finallyInstructions.size();

            length--;
            // Remove last 'ret' instruction in finally block
            finallyInstructions.remove(length);
            // Remove 'AStore ExceptionLoad' instruction in finally block
            finallyInstructions.remove(0);
            // Remove 'jsr' instruction in finally block
            finallyInstructions.remove(0);
            // Remove 'athrow' instruction in finally block
            finallyInstructions.remove(0);
        }
        // Remove 'AStore ReturnAddressLoad' instruction in finally block
        finallyInstructions.remove(0);
    }

    private static void format142(
        LocalVariables localVariables, FastCodeExcepcion fce, FastTry fastTry)
    {
        List<Instruction> finallyInstructions = fastTry.finallyInstructions;
        int finallyInstructionsSize = finallyInstructions.size();

        // Remove last 'athrow' instruction in finally block
        if (finallyInstructions.get(finallyInstructionsSize-1).opcode ==
                Const.ATHROW)
        {
            finallyInstructions.remove(finallyInstructionsSize-1);
        }
        // Remove 'astore' or 'monitorexit' instruction in finally block
        switch (finallyInstructions.get(0).opcode)
        {
        case Const.ASTORE:
        case Const.POP:
            finallyInstructions.remove(0);
        }
        finallyInstructionsSize = finallyInstructions.size();

        if (finallyInstructionsSize > 0)
        {
            FastCompareInstructionVisitor visitor =
                new FastCompareInstructionVisitor();

            List<Instruction> tryInstructions = fastTry.instructions;
            int length = tryInstructions.size();

            switch (tryInstructions.get(length-1).opcode)
            {
            case Const.GOTO:
                length--;
                // Remove last 'goto' instruction in try block
                Goto g = (Goto)tryInstructions.get(length);
                if (g.branch > 0)
                {
                    tryInstructions.remove(length);
                    fce.tryToOffset = g.offset;
                }
                break;
            }

            // Remove finally instructions in try block before 'return' instruction
            format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                localVariables, visitor, tryInstructions, finallyInstructions);

            if (fastTry.catches != null)
            {
                // Format catch blocks
                int i = fastTry.catches.size();
                List<Instruction> catchInstructions;
                while (i-- > 0)
                {
                    catchInstructions = fastTry.catches.get(i).instructions;
                    
                    length = catchInstructions.size();

                    switch (catchInstructions.get(length-1).opcode)
                    {
                    case Const.GOTO:
                        length--;
                        // Remove last 'goto' instruction in try block
                        Goto g = (Goto)catchInstructions.get(length);
                        if (g.branch > 0)
                        {
                            catchInstructions.remove(length);
                            fce.catches.get(i).toOffset = g.offset;
                        }
                        break;
                    }

                    // Remove finally instructions before 'return' instruction
                    format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                        localVariables, visitor, catchInstructions, finallyInstructions);
                    // Remove first catch instruction in each catch block
                    if (!catchInstructions.isEmpty()) {
                        catchInstructions.remove(0);
                    }
                }
            }
        }
        if (fastTry.catches != null && !fastTry.catches.isEmpty())
        {
            // Format catch blocks
            int i = fastTry.catches.size();
            List<Instruction> catchInstructions;
            while (i-- > 0)
            {
                catchInstructions = fastTry.catches.get(i).instructions;
                
		        // Remove first catch instruction in each catch block
		        if (!catchInstructions.isEmpty() && formatCatchRemoveFirstCatchInstruction(catchInstructions.get(0))) {
		            catchInstructions.remove(0);
		        }
            }
        }
    }

    private static void format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
        LocalVariables localVariables,
        FastCompareInstructionVisitor visitor,
        List<Instruction> instructions,
        List<Instruction> finallyInstructions)
    {
        int index = instructions.size();
        int finallyInstructionsSize = finallyInstructions.size();
        int finallyInstructionsLineNumber = finallyInstructions.get(0).lineNumber;

        boolean match = index >= finallyInstructionsSize && visitor.visit(
            instructions, finallyInstructions,
            index-finallyInstructionsSize, 0, finallyInstructionsSize);

        // Remove last finally instructions
        if (match)
        {
            for (int j=0; j<finallyInstructionsSize && index>0; index--,j++) {
                instructions.remove(index-1);
            }
        }

        Instruction instruction;
        while (index-- > 0)
        {
            instruction = instructions.get(index);

            switch (instruction.opcode)
            {
            case Const.RETURN:
            case Const.ATHROW:
                {
                    match = index >= finallyInstructionsSize && visitor.visit(
                        instructions, finallyInstructions,
                        index-finallyInstructionsSize, 0, finallyInstructionsSize);

                    if (match)
                    {
                        // Remove finally instructions
                        for (int j=0; j<finallyInstructionsSize && index>0; index--,++j) {
                        	Instruction instr = instructions.get(index-1);
                        	if (instr.lineNumber >= finallyInstructionsLineNumber) {
                        		instructions.remove(index-1);
                        	}
                        }
                    }

                    if (instruction.lineNumber != Instruction.UNKNOWN_LINE_NUMBER &&
                        instruction.lineNumber >= finallyInstructionsLineNumber)
                    {
                        instruction.lineNumber = Instruction.UNKNOWN_LINE_NUMBER;
                    }
                }
                break;
            case ByteCodeConstants.XRETURN:
                {
                    match = index >= finallyInstructionsSize && visitor.visit(
                        instructions, finallyInstructions,
                        index-finallyInstructionsSize, 0, finallyInstructionsSize);

                    if (match)
                    {
                        // Remove finally instructions
                        for (int j=0; j<finallyInstructionsSize && index>0; index--,j++) {
                            instructions.remove(index-1);
                        }
                    }

                    // Compact AStore + Return
                    ReturnInstruction ri = (ReturnInstruction)instruction;

                    if (ri.lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                    {
                        switch (ri.valueref.opcode)
                        {
                        case Const.ALOAD:
                            if (instructions.get(index-1).opcode == Const.ASTORE) {
                                index = compactStoreReturn(
                                        instructions, localVariables, ri,
                                        index, finallyInstructionsLineNumber);
                            }
                            break;
                        case ByteCodeConstants.LOAD:
                            if (instructions.get(index-1).opcode == ByteCodeConstants.STORE) {
                                index = compactStoreReturn(
                                        instructions, localVariables, ri,
                                        index, finallyInstructionsLineNumber);
                            }
                            break;
                        case Const.ILOAD:
                            if (instructions.get(index-1).opcode == Const.ISTORE) {
                                index = compactStoreReturn(
                                        instructions, localVariables, ri,
                                        index, finallyInstructionsLineNumber);
                            }
                            break;
                        }
                    }
                }
                break;
            case FastConstants.TRY:
                {
                    // Recursive calls
                    FastTry ft = (FastTry)instruction;

                    format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                        localVariables, visitor,
                        ft.instructions, finallyInstructions);

                    if (ft.catches != null)
                    {
                        int i = ft.catches.size();
                        while (i-- > 0)
                        {
                            format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                                localVariables, visitor,
                                ft.catches.get(i).instructions, finallyInstructions);
                        }
                    }

                    if (ft.finallyInstructions != null)
                    {
                        format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                            localVariables, visitor,
                            ft.finallyInstructions, finallyInstructions);
                    }
                }
                break;
            case FastConstants.SYNCHRONIZED:
                {
                    // Recursive calls
                    FastSynchronized fs = (FastSynchronized)instruction;

                    format142RemoveFinallyInstructionsBeforeReturnAndCompactStoreReturn(
                        localVariables, visitor,
                        fs.instructions, finallyInstructions);
                }
                break;
            }
        }
    }

    private static int compactStoreReturn(
        List<Instruction> instructions, LocalVariables localVariables,
        ReturnInstruction ri, int index, int finallyInstructionsLineNumber)
    {
        IndexInstruction load = (IndexInstruction)ri.valueref;
        StoreInstruction store = (StoreInstruction)instructions.get(index-1);

        if (load.index == store.index &&
            (load.lineNumber <= store.lineNumber ||
             load.lineNumber >= finallyInstructionsLineNumber))
        {
            // TODO A ameliorer !!
            // Remove local variable
            LocalVariable lv = localVariables.
                getLocalVariableWithIndexAndOffset(
                        store.index, store.offset);

            if (lv != null && lv.startPc == store.offset &&
                lv.startPc + lv.length <= ri.offset) {
                localVariables.
                    removeLocalVariableWithIndexAndOffset(
                            store.index, store.offset);
            }
            // Replace returned instruction
            ri.valueref = store.valueref;
            if (ri.lineNumber > store.lineNumber) {
                ri.lineNumber = store.lineNumber;
            }
            index--;
            // Remove 'store' instruction
            instructions.remove(index);
        }

        return index;
    }

    private static void format142FinallyThrow(FastTry fastTry)
    {
        // Remove last 'athrow' instruction in finally block
        fastTry.finallyInstructions.remove(fastTry.finallyInstructions.size()-1);
        // Remove 'astore' instruction in finally block
        fastTry.finallyInstructions.remove(0);
    }

    private static void formatJikes122(
        LocalVariables localVariables, FastCodeExcepcion fce,
        FastTry fastTry, int returnOffset)
    {
        List<Instruction> tryInstructions = fastTry.instructions;
        int lastIndex = tryInstructions.size()-1;
        Instruction lastTryInstruction = tryInstructions.get(lastIndex);
        int lastTryInstructionOffset = lastTryInstruction.offset;

        // Remove last 'goto' instruction in try block
        if (tryInstructions.get(lastIndex).opcode == Const.GOTO)
        {
            Goto g = (Goto)tryInstructions.remove(lastIndex);
            fce.tryToOffset = g.offset;
        }
        // Remove Jsr instruction before return instructions
        int finallyInstructionsLineNumber;
        if (fastTry.finallyInstructions.isEmpty()) {
            finallyInstructionsLineNumber = -1;
        } else {
            finallyInstructionsLineNumber = fastTry.finallyInstructions.get(0).lineNumber;
        }
        formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
            tryInstructions, localVariables, finallyInstructionsLineNumber);

        // Format catch blocks
        int i = fastTry.catches.size();
        List<Instruction> catchInstructions;
        while (i-- > 0)
        {
            catchInstructions = fastTry.catches.get(i).instructions;
            lastIndex = catchInstructions.size()-1;

            // Remove last 'goto' instruction in try block
            if (catchInstructions.get(lastIndex).opcode == Const.GOTO)
            {
                Goto g = (Goto)catchInstructions.remove(lastIndex);
                fce.catches.get(i).toOffset = g.offset;
            }
            // Remove Jsr instruction before return instructions
            if (finallyInstructionsLineNumber != -1) {
                formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
                    catchInstructions, localVariables,
                    finallyInstructionsLineNumber);
            }
            // Change negative jump goto to return offset
            formatFastTryFormatNegativeJumpOffset(
                catchInstructions, lastTryInstructionOffset, returnOffset);
            // Remove first catch instruction in each catch block
            catchInstructions.remove(0);
        }

        List<Instruction> finallyInstructions = fastTry.finallyInstructions;
        int length = finallyInstructions.size();

        // Remove last 'jsr' instruction in finally block
        if (!finallyInstructions.isEmpty()) {
            length--;
            finallyInstructions.remove(length);
        }
        // Remove last 'ret' or 'athrow' instruction in finally block
        if (!finallyInstructions.isEmpty()) {
            length--;
            finallyInstructions.remove(length);
        }
        // Remove 'AStore ExceptionLoad' instruction in finally block
        if (!finallyInstructions.isEmpty()) {
            finallyInstructions.remove(0);
        }
        // Remove 'jsr' instruction in finally block
        if (!finallyInstructions.isEmpty() && finallyInstructions.get(0).opcode == Const.JSR) {
            finallyInstructions.remove(0);
        }
        // Remove 'athrow' instruction in finally block
        if (!finallyInstructions.isEmpty() && finallyInstructions.get(0).opcode == Const.ATHROW) {
            finallyInstructions.remove(0);
        }
        // Remove 'astore' instruction in finally block
        if (!finallyInstructions.isEmpty() && finallyInstructions.get(0).opcode == Const.ASTORE) {
            finallyInstructions.remove(0);
        }
    }

    private static int formatFastTryRemoveJsrInstructionAndCompactStoreReturn(
        List<Instruction> instructions, LocalVariables localVariables,
        int finallyInstructionsLineNumber)
    {
        int jumpOffset = UtilConstants.INVALID_OFFSET;
        int index = instructions.size();

        while (index-- > 1)
        {
            if (instructions.get(index).opcode == Const.JSR)
            {
                // Remove Jsr instruction
                Jsr jsr = (Jsr)instructions.remove(index);
                jumpOffset = jsr.getJumpOffset();
            }
        }

        index = instructions.size();

        Instruction instruction;
        while (index-- > 1)
        {
            instruction = instructions.get(index);

            if (instruction.opcode == ByteCodeConstants.XRETURN)
            {
                // Compact AStore + Return
                ReturnInstruction ri = (ReturnInstruction)instruction;

                if (ri.lineNumber != Instruction.UNKNOWN_LINE_NUMBER)
                {
                    switch (ri.valueref.opcode)
                    {
                    case Const.ALOAD:
                        if (instructions.get(index-1).opcode == Const.ASTORE) {
                            index = compactStoreReturn(
                                    instructions, localVariables, ri,
                                    index, finallyInstructionsLineNumber);
                        }
                        break;
                    case ByteCodeConstants.LOAD:
                        if (instructions.get(index-1).opcode == ByteCodeConstants.STORE) {
                            index = compactStoreReturn(
                                    instructions, localVariables, ri,
                                    index, finallyInstructionsLineNumber);
                        }
                        break;
                    case Const.ILOAD:
                        if (instructions.get(index-1).opcode == Const.ISTORE) {
                            index = compactStoreReturn(
                                    instructions, localVariables, ri,
                                    index, finallyInstructionsLineNumber);
                        }
                        break;
                    }
                }
            }
        }

        return jumpOffset;
    }

    private static void formatFastTryFormatNegativeJumpOffset(
        List<Instruction> instructions,
        int lastTryInstructionOffset, int returnOffset)
    {
        int i = instructions.size();

        Instruction instruction;
        while (i-- > 0)
        {
            instruction = instructions.get(i);

            if (instruction.opcode == Const.GOTO) {
                Goto g = (Goto)instruction;
                int jumpOffset = g.getJumpOffset();

                if (jumpOffset < lastTryInstructionOffset)
                {
                    // Change jump offset
                    g.branch = returnOffset - g.offset;
                }
            }
        }
    }

    private static void formatEclipse677Finally(
        FastCodeExcepcion fce, FastTry fastTry)
    {
        // Remove instructions in finally block
        List<Instruction> finallyInstructions = fastTry.finallyInstructions;

        Instruction instruction = finallyInstructions.get(0);

        if (instruction.opcode == Const.POP) {
            // Remove 'pop' instruction in finally block
            finallyInstructions.remove(0);

            List<Instruction> tryInstructions = fastTry.instructions;
            int lastIndex = tryInstructions.size()-1;

            // Remove last 'goto' instruction in try block
            if (tryInstructions.get(lastIndex).opcode == Const.GOTO)
            {
                Goto g = (Goto)tryInstructions.remove(lastIndex);
                fce.tryToOffset = g.offset;
            }
        } else if (instruction.opcode == Const.ASTORE) {
            int exceptionIndex = ((AStore)instruction).index;
            int index = finallyInstructions.size();
            int athrowOffset = -1;
            int afterAthrowOffset = -1;

            // Search throw instruction
            while (index-- > 0)
            {
                instruction = finallyInstructions.get(index);
                if (instruction.opcode == Const.ATHROW)
                {
                    AThrow athrow = (AThrow)instruction;
                    if (findAloadForAThrow(exceptionIndex, athrow))
                    {
                        // Remove last 'athrow' instruction in finally block
                        athrowOffset = instruction.offset;
                        finallyInstructions.remove(index);
                        break;
                    }
                }
                afterAthrowOffset = instruction.offset;
                finallyInstructions.remove(index);
            }

            if (!finallyInstructions.isEmpty()) {
                // Remove 'astore' instruction in finally block
                Instruction astore = finallyInstructions.remove(0);

                List<Instruction> tryInstructions = fastTry.instructions;
                int lastIndex = tryInstructions.size()-1;

                // Remove last 'goto' instruction in try block
                if (tryInstructions.get(lastIndex).opcode == Const.GOTO)
                {
                    Goto g = (Goto)tryInstructions.remove(lastIndex);
                    fce.tryToOffset = g.offset;
                }

                removeOutOfBoundsInstructions(fastTry, astore);
                                    // Remove finally instructions before 'return' instruction
                int finallyInstructionsSize = finallyInstructions.size();
                formatEclipse677FinallyRemoveFinallyInstructionsBeforeReturn(
                    tryInstructions, finallyInstructionsSize);

                // Format 'ifxxx' instruction jumping to finally block
                formatEclipse677FinallyFormatIfInstruction(
                    tryInstructions, athrowOffset, afterAthrowOffset, astore.offset);
            }
        }
    }

    private static void removeOutOfBoundsInstructions(FastTry fastTry, Instruction astore) {
        Instruction tryInstr;
        // Remove try instructions that are out of bounds and should be found in finally instructions
        for (Iterator<Instruction> tryIter = fastTry.instructions.iterator(); tryIter.hasNext();) {
            tryInstr = tryIter.next();
            if (tryInstr.lineNumber >= astore.lineNumber) {
                tryIter.remove();
            }
        }
        Instruction catchInstr;
        // Remove catch instructions that are out of bounds and should be found in finally instructions
        for (FastCatch fastCatch : fastTry.catches) {
            for (Iterator<Instruction> catchIter = fastCatch.instructions.iterator(); catchIter.hasNext();) {
                catchInstr = catchIter.next();
                if (catchInstr.lineNumber >= astore.lineNumber) {
                    catchIter.remove();
                }
            }
        }
    }

    private static void formatEclipse677FinallyFormatIfInstruction(
        List<Instruction> instructions, int athrowOffset,
        int afterAthrowOffset, int afterTryOffset)
    {
        int i = instructions.size();

        Instruction instruction;
        while (i-- > 0)
        {
            instruction = instructions.get(i);

            if (instruction.opcode == ByteCodeConstants.IF || instruction.opcode == ByteCodeConstants.IFXNULL
                    || instruction.opcode == ByteCodeConstants.COMPLEXIF) {
                IfInstruction ifi = (IfInstruction)instruction;
                int jumpOffset = ifi.getJumpOffset();

                if (athrowOffset < jumpOffset && jumpOffset <= afterAthrowOffset)
                {
                    // Change jump offset
                    ifi.branch = afterTryOffset - ifi.offset;
                }
            }
        }
    }

    private static void formatEclipse677FinallyRemoveFinallyInstructionsBeforeReturn(
            List<Instruction> instructions, int finallyInstructionsSize)
    {
        int i = instructions.size();

        while (i-- > 0)
        {
            switch (instructions.get(i).opcode)
            {
            case Const.RETURN:
            case ByteCodeConstants.XRETURN:
                // Remove finally instructions
                for (int j=0; j<finallyInstructionsSize && i>0; i--,j++) {
                    instructions.remove(i-1);
                }
                break;
            }
        }
    }

    private static void formatEclipse677CatchFinally(
        FastCodeExcepcion fce, FastTry fastTry, int returnOffset)
    {
        // Remove instructions in finally block
        List<Instruction> finallyInstructions = fastTry.finallyInstructions;

        int exceptionIndex = ((AStore)finallyInstructions.get(0)).index;
        int index = finallyInstructions.size();
        int athrowOffset = -1;
        int afterAthrowOffset = -1;

        Instruction instruction;
        // Search throw instruction
        while (index-- > 0)
        {
            instruction = finallyInstructions.get(index);
            if (instruction.opcode == Const.ATHROW)
            {
                AThrow athrow = (AThrow)instruction;
                if (findAloadForAThrow(exceptionIndex, athrow))
                {
                    // Remove last 'athrow' instruction in finally block
                    athrowOffset = finallyInstructions.remove(index).offset;
                    break;
                }
            }
            afterAthrowOffset = instruction.offset;
            finallyInstructions.remove(index);
        }

        if (!finallyInstructions.isEmpty()) {
            // Remove 'astore' instruction in finally block
            Instruction astore = finallyInstructions.remove(0);

            List<Instruction> tryInstructions = fastTry.instructions;
            int lastIndex = tryInstructions.size()-1;
            Instruction lastTryInstruction = tryInstructions.get(lastIndex);
            int lastTryInstructionOffset = lastTryInstruction.offset;

            // Remove last 'goto' instruction in try block
            if (lastTryInstruction.opcode == Const.GOTO)
            {
                Goto g = (Goto)tryInstructions.remove(lastIndex);
                fce.tryToOffset = g.offset;
            }

            removeOutOfBoundsInstructions(fastTry, astore);

            // Remove finally instructions before 'return' instruction
            int finallyInstructionsSize = finallyInstructions.size();
            formatEclipse677FinallyRemoveFinallyInstructionsBeforeReturn(
                tryInstructions, finallyInstructionsSize);

            // Format 'ifxxx' instruction jumping to finally block
            formatEclipse677FinallyFormatIfInstruction(
                tryInstructions, athrowOffset,
                afterAthrowOffset, lastTryInstructionOffset+1);

            // Format catch blocks
            int i = fastTry.catches.size();
            FastCatch fastCatch;
            List<Instruction> catchInstructions;
            Instruction lastInstruction;
            int lastInstructionOffset;
            while (i-- > 0)
            {
                fastCatch = fastTry.catches.get(i);
                catchInstructions = fastCatch.instructions;
                index = catchInstructions.size();

                lastInstruction = catchInstructions.get(index-1);
                lastInstructionOffset = lastInstruction.offset;

                if (lastInstruction.opcode == Const.GOTO)
                {
                    index--;
                    // Remove last 'goto' instruction
                    Goto g = (Goto)catchInstructions.remove(index);
                    fce.catches.get(i).toOffset = g.offset;
                    int jumpOffset = g.getJumpOffset();

                    if (jumpOffset > fastTry.offset)
                    {
                        // Remove finally block instructions
                        for (int j=finallyInstructionsSize; j>0; --j) {
                            index--;
                            catchInstructions.remove(index);
                        }
                    }
                }

                // Remove finally instructions before 'return' instruction
                formatEclipse677FinallyRemoveFinallyInstructionsBeforeReturn(
                    catchInstructions, finallyInstructionsSize);

                // Format 'ifxxx' instruction jumping to finally block
                formatEclipse677FinallyFormatIfInstruction(
                    catchInstructions, athrowOffset,
                    afterAthrowOffset, lastInstructionOffset+1);

                // Change negative jump goto to return offset
                formatFastTryFormatNegativeJumpOffset(
                    catchInstructions, lastTryInstructionOffset, returnOffset);

                // Remove first catch instruction in each catch block
                catchInstructions.remove(0);
            }
        }
    }

	private static boolean findAloadForAThrow(int exceptionIndex, AThrow athrow) {
		return (athrow.value.opcode == Const.ALOAD &&
		        ((ALoad)athrow.value).index == exceptionIndex) 
            || (athrow.value.opcode == Const.CHECKCAST &&
		        ((CheckCast)athrow.value).objectref.opcode == Const.ALOAD &&
		        ((ALoad)((CheckCast)athrow.value).objectref).index == exceptionIndex);
	}

    public static class FastCodeExcepcion
        implements Comparable<FastCodeExcepcion>
    {
        public int tryFromOffset;
        public int tryToOffset;
        public List<FastCodeExceptionCatch> catches;
        public int finallyFromOffset;
        public int nbrFinally;
        public int maxOffset;
        public int afterOffset;
        public int type;
        public boolean synchronizedFlag;

        FastCodeExcepcion(
            int tryFromOffset, int tryToOffset,
            int maxOffset, boolean synchronizedFlag)
        {
            this.tryFromOffset = tryFromOffset;
            this.tryToOffset = tryToOffset;
            this.catches = new ArrayList<>();
            this.finallyFromOffset = UtilConstants.INVALID_OFFSET;
            this.nbrFinally = 0;
            this.maxOffset = maxOffset;
            this.afterOffset = UtilConstants.INVALID_OFFSET;
            this.type = FastConstants.TYPE_UNDEFINED;
            this.synchronizedFlag = synchronizedFlag;
        }

        @Override
        public int compareTo(FastCodeExcepcion other)
        {
            // Sort by 1)tryFromOffset 2)maxOffset 3)tryToOffset
            if (this.tryFromOffset != other.tryFromOffset) {
                return this.tryFromOffset - other.tryFromOffset;
            }

            if (this.maxOffset != other.maxOffset) {
                return other.maxOffset - this.maxOffset;
            }

            return other.tryToOffset - this.tryToOffset;
        }
    }

    public static class FastCodeExceptionCatch
        implements Comparable<FastCodeExceptionCatch>
    {
        public int type;
        public int otherTypes[];
        public int fromOffset;
        public int toOffset;

        public FastCodeExceptionCatch(
            int type, int otherCatchTypes[], int fromOffset)
        {
            this.type = type;
            this.otherTypes = otherCatchTypes;
            this.fromOffset = fromOffset;
            this.toOffset = UtilConstants.INVALID_OFFSET;
        }

        @Override
        public int compareTo(FastCodeExceptionCatch other)
        {
            return this.fromOffset - other.fromOffset;
        }
    }

    public static class FastAggregatedCodeExcepcion
    {
        public int           otherCatchTypes[];
        public int           nbrFinally;
        public boolean       synchronizedFlag;
        public int           index;
        public CodeException codeException;

        public FastAggregatedCodeExcepcion(
            int index, int startPc, int endPc, int handlerPc, int catchType)
        {
        	this.index = index;
            this.codeException = new CodeException(startPc, endPc, handlerPc, catchType);
            this.otherCatchTypes = null;
            this.nbrFinally = (catchType == 0) ? 1 : 0;
        }
        
        public int getCatchType() {
        	return codeException.getCatchType();
        }
        
        public int getStartPC() {
        	return codeException.getStartPC();
        }
        
        public int getEndPC() {
        	return codeException.getEndPC();
        }
        
        public int getHandlerPC() {
        	return codeException.getHandlerPC();
        }
    }

    public static int computeTryToIndex(
        List<Instruction> instructions, FastCodeExcepcion fce,
        int lastIndex, int maxOffset)
    {
        // Parcours
        int beforeMaxOffset = fce.tryFromOffset;
        int index = InstructionUtil.getIndexForOffset(
                instructions, fce.tryFromOffset);

        Instruction instruction;
        while (index <= lastIndex)
        {
            instruction = instructions.get(index);

            if (instruction.offset > maxOffset) {
                return index-1;
            }

            switch (instruction.opcode)
            {
            case Const.ATHROW:
            case Const.RETURN:
            case ByteCodeConstants.XRETURN:
                {
                    if (instruction.offset >= beforeMaxOffset) {
                        return index;
                    }	// Inclus au bloc 'try'
                }
                break;
            case Const.GOTO:
                {
                    int jumpOffset = ((BranchInstruction)instruction).getJumpOffset();

                    if (jumpOffset > instruction.offset)
                    {
                        // Saut positif
                        if (jumpOffset < maxOffset)
                        {
                            // Saut dans les limites
                            if (beforeMaxOffset < jumpOffset) {
                                beforeMaxOffset = jumpOffset;
                            }
                        } else // Saut au-delà des limites
                        if (instruction.offset >= beforeMaxOffset) {
                            return index;
                        }	// Inclus au bloc 'try'
                    } else // Saut au-delà des limites
                    if (jumpOffset < fce.tryFromOffset && instruction.offset >= beforeMaxOffset) {
                        return index;
                    }	// Inclus au bloc 'try'
                }
                break;
            case ByteCodeConstants.IFCMP:
            case ByteCodeConstants.IF:
            case ByteCodeConstants.IFXNULL:
                {
                    // L'aggregation des instructions 'if' n'a pas
                    // encore ete executee. Recherche du plus petit
                    // offset de saut parmi toutes les instructions
                    // 'if' qui suivent.
                    index = ComparisonInstructionAnalyzer.getLastIndex(instructions, index);
                    BranchInstruction lastBi = (BranchInstruction)instructions.get(index);
                    int jumpOffset = lastBi.getJumpOffset();

                    // Saut positif dans les limites
                    if (jumpOffset > instruction.offset && jumpOffset < maxOffset && beforeMaxOffset < jumpOffset) {
                        beforeMaxOffset = jumpOffset;
                    }
                    // else
                    // {
                    // 	// Saut au-delà des limites, 'break' ?
                    // }
                    // else
                    // {
                    // 	// Saut négatif, 'continue' ?
                    //}
                }
                break;
            case Const.LOOKUPSWITCH:
            case Const.TABLESWITCH:
                {
                    Switch s = (Switch)instruction;

                    // Search max offset
                    int maxSitchOffset = s.defaultOffset;
                    int i = s.offsets.length;
                    int offset;
                    while (i-- > 0)
                    {
                        offset = s.offsets[i];
                        if (maxSitchOffset < offset) {
                            maxSitchOffset = offset;
                        }
                    }
                    maxSitchOffset += s.offset;

                    // Saut positif dans les limites
                    if (maxSitchOffset > instruction.offset && maxSitchOffset < maxOffset && beforeMaxOffset < maxSitchOffset) {
                        beforeMaxOffset = maxSitchOffset;
                    }
                    // else
                    // {
                    // 	// Saut au-delà des limites, 'break' ?
                    // }
                    // else
                    // {
                    // 	// Saut négatif, 'continue' ?
                    //}
                    break;
                }
            }

            index++;
        }

        if (index == instructions.size()) {
            return index - 1;
        }

        return index;
    }
}
