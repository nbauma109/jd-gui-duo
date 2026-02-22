/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.actions;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.cfg.ControlFlowGraph;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphGotoReducer;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphLoopReducer;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphMaker;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphPreReducer;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphReducer;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;
import org.jd.util.CFGViewer;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

public class ShowControlFlowGraphContextualActionsFactory implements ContextualActionsFactory { // NO_UCD (unused code)

    public static final int MODE_RAW = 0;
    public static final int MODE_GOTO_ONLY = 1;
    public static final int MODE_GOTO_AND_LOOP = 2;
    public static final int MODE_PRE_REDUCE = 3;

    @Override
    public Collection<Action> make(API api, Container.Entry entry, String fragment) {
        Collection<Action> actions = new ArrayList<>();
        if (entry.getPath().endsWith(StringConstants.CLASS_FILE_SUFFIX) && isMethod(fragment)) {
            actions.add(new ShowControlFlowGraphAction(api, entry, fragment, null, MODE_RAW));
            actions.add(new ShowControlFlowGraphAction(api, entry, fragment, null, MODE_GOTO_ONLY));
            actions.add(new ShowControlFlowGraphAction(api, entry, fragment, null, MODE_GOTO_AND_LOOP));
            actions.add(new ShowControlFlowGraphAction(api, entry, fragment, null, MODE_PRE_REDUCE));
            for (ControlFlowGraphReducer controlFlowGraphReducer : ControlFlowGraphReducer.getPreferredReducers()) {
                actions.add(new ShowControlFlowGraphAction(api, entry, fragment, controlFlowGraphReducer, MODE_GOTO_AND_LOOP));
            }
        }
        return actions;
    }

    public static class ShowControlFlowGraphAction extends AbstractMethodAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/net/sourceforge/plantuml/version/favicon.png"));

        private final transient ControlFlowGraphReducer controlFlowGraphReducer;

        private int mode;

        public ShowControlFlowGraphAction(API api, Container.Entry entry, String fragment, ControlFlowGraphReducer controlFlowGraphReducer, int mode) {
            super(api, entry, fragment);
            this.controlFlowGraphReducer = controlFlowGraphReducer;
            this.mode = mode;
            if (controlFlowGraphReducer == null) {
                putValue(GROUP_NAME, "Edit > ShowInitialControlFlowGraph"); // used for sorting and grouping menus
                putValue(NAME, "Show Initial Control Flow Graph " + getModeAsString());
            } else {
                String suffix = controlFlowGraphReducer.doPreReduce() ? " (with pre-reduce)" : " (no pre-reduce)";
                putValue(GROUP_NAME, "Edit > ShowReducedControlFlowGraph" + suffix);
                putValue(NAME, controlFlowGraphReducer.getLabel() + suffix);
            }
            putValue(SMALL_ICON, ICON);
        }

        private String getModeAsString() {
            return switch (mode) {
                case MODE_RAW -> "";
                case MODE_GOTO_ONLY -> "(Goto)";
                case MODE_GOTO_AND_LOOP -> "(Goto, Loop)";
                case MODE_PRE_REDUCE -> "(Goto, Loop, Pre-Reduce)";
                default -> throw new IllegalArgumentException("Unexpected value: " + mode);
            };
        }

        @Override
        protected void methodAction(API api, Method method, String className) {
            if ((method.getAccessFlags() & Const.ACC_ABSTRACT) != 0) {
                JOptionPane.showMessageDialog(null, "Method is abstract !", "ERROR", JOptionPane.ERROR_MESSAGE, ICON);
                return;
            }
            if (controlFlowGraphReducer == null) {
                ControlFlowGraph controlFlowGraph = new ControlFlowGraphMaker().make(method);
                switch (mode) {
                    case MODE_GOTO_ONLY:
                        ControlFlowGraphGotoReducer.reduce(controlFlowGraph);
                        break;
                    case MODE_GOTO_AND_LOOP:
                        ControlFlowGraphGotoReducer.reduce(controlFlowGraph);
                        ControlFlowGraphLoopReducer.reduce(controlFlowGraph);
                        break;
                    case MODE_PRE_REDUCE:
                        ControlFlowGraphGotoReducer.reduce(controlFlowGraph);
                        ControlFlowGraphLoopReducer.reduce(controlFlowGraph);
                        ControlFlowGraphPreReducer.reduce(controlFlowGraph);
                        break;
                    default:
                        break;
                }
                CFGViewer.showGraph(controlFlowGraph, className);
            } else {
                controlFlowGraphReducer.reduce(method);
                CFGViewer.showGraph(controlFlowGraphReducer.getControlFlowGraph(), className);
            }
        }
    }
}
