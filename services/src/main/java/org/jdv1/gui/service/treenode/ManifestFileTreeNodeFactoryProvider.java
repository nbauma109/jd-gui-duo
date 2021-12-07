/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.treenode;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.*;
import org.jd.gui.api.model.Container;
import org.jd.gui.view.data.TreeNodeBean;
import org.jdv1.gui.view.component.ManifestFilePage;

import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

@org.kohsuke.MetaInfServices(org.jd.gui.spi.TreeNodeFactory.class)
public class ManifestFileTreeNodeFactoryProvider extends FileTreeNodeFactoryProvider {
    protected static final ImageIcon ICON = new ImageIcon(ManifestFileTreeNodeFactoryProvider.class.getClassLoader().getResource("org/jd/gui/images/manifest_obj.png"));

    @Override
    public String[] getSelectors() { return appendSelectors("*:file:META-INF/MANIFEST.MF"); }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        String location = new File(entry.getUri()).getPath();
        return (T)new TreeNode(entry, new TreeNodeBean("MANIFEST.MF", "Location: " + location, ICON));
    }

    protected static class TreeNode extends FileTreeNodeFactoryProvider.TreeNode implements PageCreator {

        private static final long serialVersionUID = 1L;

        public TreeNode(Container.Entry entry, Object userObject) { super(entry, userObject); }

        // --- PageCreator --- //
        @Override
        @SuppressWarnings("unchecked")
        public <T extends JComponent & UriGettable> T createPage(API api) {
            return (T)new ManifestFilePage(api, entry);
        }
    }
}
