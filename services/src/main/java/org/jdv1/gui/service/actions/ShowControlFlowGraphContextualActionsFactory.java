/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.actions;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.cfg.ControlFlowGraphPlantUMLWriter;
import org.jd.core.v1.cfg.MethodUtil;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.*;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.actions.InvalidFormatException;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.decompiler.ContainerLoader;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.*;

public class ShowControlFlowGraphContextualActionsFactory implements ContextualActionsFactory {

    @Override
    public Collection<Action> make(org.jd.gui.api.API api, Container.Entry entry, String fragment) {
        Collection<Action> actions = new ArrayList<>();
        if (entry.getPath().endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            actions.add(new ShowControlFlowGraphAction(entry, fragment, null));
            for (ControlFlowGraphReducer controlFlowGraphReducer : ControlFlowGraphReducer.getPreferredReducers()) {
                actions.add(new ShowControlFlowGraphAction(entry, fragment, controlFlowGraphReducer));
            }
        }
        return actions;
    }

    public static class ShowControlFlowGraphAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/net/sourceforge/plantuml/version/favicon.png"));

        private transient Container.Entry entry;
        private String fragment;
        private transient ControlFlowGraphReducer controlFlowGraphReducer;

        public ShowControlFlowGraphAction(Container.Entry entry, String fragment, ControlFlowGraphReducer controlFlowGraphReducer) {
            this.entry = entry;
            this.fragment = fragment;
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

        @Override
        public void actionPerformed(ActionEvent e) {
            Loader loader = new ContainerLoader(entry);
            TypeMaker typeMaker = new TypeMaker(loader);
            String entryPath = entry.getPath();
            String internalTypeName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()
            if (fragment != null) {
                int dashIndex = fragment.indexOf('-');
                if (dashIndex != -1) {
                    int lastDashIndex = fragment.lastIndexOf('-');
                    if (dashIndex == lastDashIndex) {
                        // See jd.gui.api.feature.UriOpenable
                        throw new InvalidFormatException("fragment: " + fragment);
                    }
                    String methodName = fragment.substring(dashIndex + 1, lastDashIndex);
                    String descriptor = fragment.substring(lastDashIndex + 1);
                    try {
                        Method method = MethodUtil.searchMethod(loader, typeMaker, internalTypeName, methodName, descriptor);
                        if (controlFlowGraphReducer == null) {
                            ControlFlowGraphPlantUMLWriter.showGraph(new ControlFlowGraphMaker().make(method));
                        } else {
                            controlFlowGraphReducer.reduce(method);
                            ControlFlowGraphPlantUMLWriter.showGraph(controlFlowGraphReducer.getControlFlowGraph());
                        }
                    } catch (LoaderException | IOException ex) {
                        assert ExceptionUtil.printStackTrace(ex);
                    }
                }
            }
        }
    }
}
