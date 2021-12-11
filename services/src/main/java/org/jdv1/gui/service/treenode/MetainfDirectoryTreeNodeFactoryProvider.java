/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.treenode;

import javax.swing.ImageIcon;

import org.jd.gui.util.ImageUtil;

public class MetainfDirectoryTreeNodeFactoryProvider extends DirectoryTreeNodeFactoryProvider {
    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/inf_obj.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors(
                "jar:dir:META-INF",
                "war:dir:WEB-INF",
                "war:dir:WEB-INF/classes/META-INF",
                "ear:dir:META-INF",
                "jmod:dir:classes/META-INF");
    }

    @Override
    public ImageIcon getIcon() { return ICON; }
    @Override
    public ImageIcon getOpenIcon() { return null; }
}
