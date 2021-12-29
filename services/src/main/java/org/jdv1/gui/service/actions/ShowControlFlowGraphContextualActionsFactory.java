/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.actions;

import org.jd.core.v1.cfg.ControlFlowGraphPlantUMLWriter;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphMaker;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ControlFlowGraphReducer;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Action;
import javax.swing.ImageIcon;

public class ShowControlFlowGraphContextualActionsFactory implements ContextualActionsFactory {

    @Override
    public Collection<Action> make(API api, Container.Entry entry, String fragment) {
        Collection<Action> actions = new ArrayList<>();
        if (entry.getPath().endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            actions.add(new ShowControlFlowGraphAction(entry, fragment, null));
            for (ControlFlowGraphReducer controlFlowGraphReducer : ControlFlowGraphReducer.getPreferredReducers()) {
                actions.add(new ShowControlFlowGraphAction(entry, fragment, controlFlowGraphReducer));
            }
        }
        return actions;
    }

    public static class ShowControlFlowGraphAction extends AbstractMethodAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/net/sourceforge/plantuml/version/favicon.png"));

        private final transient ControlFlowGraphReducer controlFlowGraphReducer;

        public ShowControlFlowGraphAction(Container.Entry entry, String fragment, ControlFlowGraphReducer controlFlowGraphReducer) {
            super(entry, fragment);
            this.controlFlowGraphReducer = controlFlowGraphReducer;
            if (controlFlowGraphReducer == null) {
                putValue(GROUP_NAME, "Edit > ShowInitialControlFlowGraph"); // used for sorting and grouping menus
                putValue(NAME, "Show Initial Control Flow Graph");
            } else {
                putValue(GROUP_NAME, "Edit > ShowReducedControlFlowGraph");
                putValue(NAME, controlFlowGraphReducer.getLabel());
            }
            putValue(SMALL_ICON, ICON);
        }

        protected void methodAction(Method method) {
            if (controlFlowGraphReducer == null) {
                ControlFlowGraphPlantUMLWriter.showGraph(new ControlFlowGraphMaker().make(method));
            } else {
                controlFlowGraphReducer.reduce(method);
                ControlFlowGraphPlantUMLWriter.showGraph(controlFlowGraphReducer.getControlFlowGraph());
            }
        }
    }
}
