/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.PageCreator;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.view.data.TreeNodeBean;
import org.jd.gui.view.component.OneTypeReferencePerLinePage;

import java.io.File;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

public class MetainfServiceFileTreeNodeFactoryProvider extends FileTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/ascii_obj.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*");
    }

    @Override
    public Pattern getPathPattern() {
        if (externalPathPattern == null) {
            return Pattern.compile("META-INF\\/services\\/[^\\/]+");
        }
        return externalPathPattern;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf("/");
        String label = entry.getPath().substring(lastSlashIndex + 1);
        String location = new File(entry.getUri()).getPath();
        return (T) new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, ICON));
    }

    protected static class TreeNode extends FileTreeNodeFactoryProvider.TreeNode implements PageCreator {

        private static final long serialVersionUID = 1L;

        public TreeNode(Container.Entry entry, Object userObject) {
            super(entry, userObject);
        }

        // --- PageCreator --- //
        @Override
        @SuppressWarnings("unchecked")
        public <T extends JComponent & UriGettable> T createPage(API api) {
            return (T) new OneTypeReferencePerLinePage(api, entry);
        }
    }
}
