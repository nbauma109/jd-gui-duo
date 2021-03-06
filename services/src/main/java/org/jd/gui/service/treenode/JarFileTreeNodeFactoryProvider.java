/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.entry.path.DirectoryEntryPath;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.container.JarContainerEntryUtil;
import org.jd.gui.view.data.TreeNodeBean;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;

public class JarFileTreeNodeFactoryProvider extends ZipFileTreeNodeFactoryProvider {

    protected static final ImageIcon JAR_FILE_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/jar_obj.png"));
    protected static final ImageIcon EJB_FILE_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/ejbmodule_obj.gif"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.jar");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf("/");
        String label = entry.getPath().substring(lastSlashIndex + 1);
        String location = new File(entry.getUri()).getPath();
        ImageIcon icon = isAEjbModule(entry) ? EJB_FILE_ICON : JAR_FILE_ICON;
        T node = (T) new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, icon));
        // Add dummy node
        node.add(new DefaultMutableTreeNode());
        return node;
    }

    protected static boolean isAEjbModule(Container.Entry entry) {
        Map<Container.EntryPath, Container.Entry> children = entry.getChildren();
        if (children != null) {
            Container.Entry metaInf = children.get(new DirectoryEntryPath("META-INF"));
            if (metaInf != null) {
                children = metaInf.getChildren();
                if (children.containsKey(new DirectoryEntryPath("META-INF/ejb-jar.xml"))) {
                    return true;
                }
            }
        }

        return false;
    }

    protected class TreeNode extends ZipFileTreeNodeFactoryProvider.TreeNode {

        private static final long serialVersionUID = 1L;

        public TreeNode(Container.Entry entry, Object userObject) {
            super(entry, userObject);
        }

        @Override
        public Collection<Container.Entry> getChildren() {
            return JarContainerEntryUtil.removeInnerTypeEntries(entry.getChildren());
        }
    }
}
