package org.jd.core.v1.service.converter.classfiletojavasyntax.util.cfg;

import org.apache.bcel.classfile.CodeException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.BasicBlock;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CmpDepthCFGReducer extends ControlFlowGraphReducer {

    @Override
    protected boolean needToUpdateConditionTernaryOperator(BasicBlock basicBlock, BasicBlock nextNext) {
        return ByteCodeUtil.evalStackDepth(basicBlock) + 1 == -ByteCodeUtil.evalStackDepth(nextNext);
    }

    @Override
    protected boolean needToUpdateCondition(BasicBlock basicBlock, BasicBlock nextNext) {
        return false;
    }

    @Override
    protected boolean needToCreateIf(BasicBlock branch, BasicBlock nextNext, int maxOffset) {
        return nextNext.getFromOffset() < maxOffset && nextNext.getPredecessors().size() == 1;
    }

    @Override
    protected boolean needToCreateIfElse(BasicBlock branch, BasicBlock nextNext, BasicBlock branchNext) {
        return true;
    }

    @Override
    public String makeKey(CodeException ce) {
       return Stream.of(ce.getStartPC(), ce.getEndPC()).map(String::valueOf).collect(Collectors.joining("-"));
    }
}
