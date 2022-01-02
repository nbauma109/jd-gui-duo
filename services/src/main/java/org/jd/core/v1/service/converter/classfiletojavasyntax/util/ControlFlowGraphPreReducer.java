/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.util;

import org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.BasicBlock;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.ControlFlowGraph;

import static org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.BasicBlock.TYPE_CONDITIONAL_BRANCH;

public final class ControlFlowGraphPreReducer {

    private ControlFlowGraphPreReducer() {
        super();
    }

    public static void reduce(ControlFlowGraph cfg) {
        for (BasicBlock basicBlock : cfg.getBasicBlocks()) {
            if (basicBlock.getType() == TYPE_CONDITIONAL_BRANCH 
                && basicBlock.getNext().getType() == TYPE_CONDITIONAL_BRANCH 
                && basicBlock.getEnclosingLoop() != null
                && basicBlock.getBranch().getNext() == BasicBlock.LOOP_START
                && basicBlock.getNext().getPredecessors().size() >= 3
                && basicBlock.getNext().getPredecessors().stream().filter(b -> b != basicBlock).allMatch(b -> b.getBranch() == basicBlock.getNext())) {
                basicBlock.flip();
            }
        }
    }
}
