/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.data;

import org.jd.gui.api.API;

import javax.swing.Icon;

import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON;
import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON_ERROR;
import static org.jd.gui.service.treenode.ClassFileTreeNodeFactoryProvider.CLASS_FILE_ICON_WARNING;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;

public class ClassFileTreeNodeBean extends TreeNodeBean {
    private final API api;
    private Icon classIcon;

    public ClassFileTreeNodeBean(API api, String label) {
        super(label, CLASS_FILE_ICON);
        this.api = api;
    }

    @Override
    public Icon getIcon() {
        if (("true".equals(api.getPreferences().get(SHOW_COMPILER_ERRORS)) && classIcon == CLASS_FILE_ICON_ERROR)
         || ("true".equals(api.getPreferences().get(SHOW_COMPILER_WARNINGS)) && classIcon == CLASS_FILE_ICON_WARNING)) {
            return classIcon;
        }
        return CLASS_FILE_ICON;
        
    }

    @Override
    public Icon getOpenIcon() {
        return getIcon();
    }

    public void setClassIcon(Icon classIcon) {
        this.classIcon = classIcon;
    }
}
