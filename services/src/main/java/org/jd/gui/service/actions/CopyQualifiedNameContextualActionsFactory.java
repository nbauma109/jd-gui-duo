/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.actions;

import org.apache.bcel.classfile.Utility;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.util.ImageUtil;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;

public class CopyQualifiedNameContextualActionsFactory implements ContextualActionsFactory { // NO_UCD (unused code)

    @Override
    public Collection<Action> make(API api, Container.Entry entry, String fragment) {
        return Arrays.asList(new CopyQualifiedNameAction(api, entry, fragment, true), new CopyQualifiedNameAction(api, entry, fragment, false));
    }

    public static class CopyQualifiedNameAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/cpyqual_menu.png"));

        private final transient API api;
        private final transient Container.Entry entry;
        private final String fragment;
        private final boolean qualified;

        public CopyQualifiedNameAction(API api, Container.Entry entry, String fragment, boolean qualified) {
            this.api = api;
            this.entry = entry;
            this.fragment = fragment;
            this.qualified = qualified;

            putValue(GROUP_NAME, "Edit > CutCopyPaste");
            if (qualified) {
                putValue(NAME, "Copy Qualified Name");
            } else {
                putValue(NAME, "Copy Internal Name");
            }
            putValue(SMALL_ICON, ICON);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TypeFactory typeFactory = api.getTypeFactory(entry);

            if (typeFactory != null) {
                Type type = typeFactory.make(api, entry, fragment);

                if (type != null) {
                    String displayPackageName = type.getDisplayPackageName();
                    if (!qualified) {
                        displayPackageName = Utility.packageToPath(displayPackageName);
                    }
                    StringBuilder sb = new StringBuilder(displayPackageName);

                    if (sb.length() > 0) {
                        sb.append(qualified ? '.' : '/');
                    }

                    sb.append(type.getDisplayTypeName());

                    if (fragment != null) {
                        int dashIndex = fragment.indexOf('-');

                        if (dashIndex != -1) {
                            int lastDashIndex = fragment.lastIndexOf('-');

                            if (dashIndex == lastDashIndex) {
                                // See jd.gui.api.feature.UriOpenable
                                throw new InvalidFormatException("fragment: " + fragment);
                            }
                            String name = fragment.substring(dashIndex + 1, lastDashIndex);
                            String descriptor = fragment.substring(lastDashIndex + 1);

                            if (descriptor.startsWith("(")) {
                                for (Type.Method method : type.getMethods()) {
                                    if (method.getName().equals(name) && method.getDescriptor().equals(descriptor)) {
                                        sb.append('.').append(method.getDisplayName());
                                        break;
                                    }
                                }
                            } else {
                                for (Type.Field field : type.getFields()) {
                                    if (field.getName().equals(name) && field.getDescriptor().equals(descriptor)) {
                                        sb.append('.').append(field.getDisplayName());
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
                    return;
                }
            }

            // Create qualified name from URI
            String path = entry.getUri().getPath();
            String rootPath = entry.getContainer().getRoot().getUri().getPath();
            String qualifiedName = path.substring(rootPath.length());

            if (qualifiedName.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                qualifiedName = qualifiedName.substring(0, qualifiedName.length()-6);
            }

            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(qualifiedName), null);
        }
    }
}
