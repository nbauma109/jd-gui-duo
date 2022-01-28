/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.TreeNodeExpandable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.view.data.TreeNodeBean;

import java.io.File;
import java.net.URI;
import java.util.Collection;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;

public class DirectoryTreeNodeFactoryProvider extends AbstractTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/folder.gif"));
    protected static final ImageIcon OPEN_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/folder_open.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:dir:*");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf('/');
        Collection<Entry> entries = entry.getChildren().values();

        // Aggregate directory names
        while (entries.size() == 1) {
            Entry child = entries.iterator().next();
            if (!child.isDirectory() || api.getTreeNodeFactory(child) != this || entry.getContainer() != child.getContainer()) {
                break;
            }
            entry = child;
            entries = entry.getChildren().values();
        }

        String label = entry.getPath().substring(lastSlashIndex + 1);
        String location = new File(entry.getUri()).getPath();
        TreeNode node = new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, getIcon(), getOpenIcon()));

        if (!entries.isEmpty()) {
            // Add dummy node
            node.add(new DefaultMutableTreeNode());
        }

        return (T) node;
    }

    public ImageIcon getIcon() {
        return ICON;
    }

    public ImageIcon getOpenIcon() {
        return OPEN_ICON;
    }

    protected class TreeNode extends DefaultMutableTreeNode implements ContainerEntryGettable, UriGettable, TreeNodeExpandable {

        private static final long serialVersionUID = 1L;

        protected transient Container.Entry entry;
        protected boolean initialized;

        public TreeNode(Container.Entry entry, Object userObject) {
            super(userObject);
            this.entry = entry;
            this.initialized = false;
        }

        // --- ContainerEntryGettable --- //
        @Override
        public Container.Entry getEntry() {
            return entry;
        }

        // --- UriGettable --- //
        @Override
        public URI getUri() {
            return entry.getUri();
        }

        // --- TreeNodeExpandable --- //
        @Override
        public void populateTreeNode(API api) {
            if (!initialized) {
                removeAllChildren();

                Collection<Container.Entry> entries = getChildren();

                while (entries.size() == 1) {
                    Entry child = entries.iterator().next();
                    if (!child.isDirectory() || api.getTreeNodeFactory(child) != DirectoryTreeNodeFactoryProvider.this) {
                        break;
                    }
                    entries = child.getChildren().values();
                }

                for (Entry nextEntry : entries) {
                    TreeNodeFactory factory = api.getTreeNodeFactory(nextEntry);
                    if (factory != null) {
                        add(factory.make(api, nextEntry));
                    }
                }

                initialized = true;
            }
        }

        public Collection<Container.Entry> getChildren() {
            return entry.getChildren().values();
        }
    }
}
