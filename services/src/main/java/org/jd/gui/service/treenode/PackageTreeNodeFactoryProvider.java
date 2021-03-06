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
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.container.JarContainerEntryUtil;
import org.jd.gui.view.data.TreeNodeBean;

import java.io.File;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;

public class PackageTreeNodeFactoryProvider extends DirectoryTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/package_obj.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("jar:dir:*");
    }

    @Override
    public Pattern getPathPattern() {
        if (externalPathPattern == null) {
            return Pattern.compile("(META-INF\\/versions\\/.*)|(?!META-INF)..*");
        }
        return externalPathPattern;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf("/");
        Collection<Container.Entry> entries = entry.getChildren().values();

        // Aggregate directory names
        while (entries.size() == 1) {
            Container.Entry child = entries.iterator().next();
            if (!child.isDirectory() || api.getTreeNodeFactory(child) != this || entry.getContainer() != child.getContainer()) {
                break;
            }
            entry = child;
            entries = entry.getChildren().values();
        }

        String label = entry.getPath().substring(lastSlashIndex + 1).replace('/', '.');
        String location = new File(entry.getUri()).getPath();
        T node = (T) new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, getIcon(), getOpenIcon()));

        if (!entries.isEmpty()) {
            // Add dummy node
            node.add(new DefaultMutableTreeNode());
        }

        return node;
    }

    @Override
    public ImageIcon getIcon() {
        return ICON;
    }

    @Override
    public ImageIcon getOpenIcon() {
        return null;
    }

    protected class TreeNode extends DirectoryTreeNodeFactoryProvider.TreeNode {

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
