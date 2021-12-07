/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.treenode;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.view.data.TreeNodeBean;

import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;

@org.kohsuke.MetaInfServices(org.jd.gui.spi.TreeNodeFactory.class)
public class EarFileTreeNodeFactoryProvider extends ZipFileTreeNodeFactoryProvider {
    protected static final ImageIcon ICON = new ImageIcon(JarFileTreeNodeFactoryProvider.class.getClassLoader().getResource("org/jd/gui/images/ear_obj.gif"));

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:*.ear"); }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf("/");
        String label = entry.getPath().substring(lastSlashIndex+1);
        String location = new File(entry.getUri()).getPath();
        T node = (T)new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, ICON));
        // Add dummy node
        node.add(new DefaultMutableTreeNode());
        return node;
    }
}
