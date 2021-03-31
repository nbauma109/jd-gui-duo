/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.treenode;

import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.view.data.TreeNodeBean;

public class SqlFileTreeNodeFactoryProvider extends TextFileTreeNodeFactoryProvider {
    protected static final ImageIcon ICON = new ImageIcon(SqlFileTreeNodeFactoryProvider.class.getClassLoader().getResource("org/jd/gui/images/sql_obj.png"));

    @Override public String[] getSelectors() { return appendSelectors("*:file:*.sql"); }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf("/");
        String label = entry.getPath().substring(lastSlashIndex+1);
        String location = new File(entry.getUri()).getPath();
        return (T)new TreeNode(entry, new TreeNodeBean(label, "Location: " + location, ICON));
    }

    protected static class TreeNode extends TextFileTreeNodeFactoryProvider.TreeNode {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public TreeNode(Container.Entry entry, Object userObject) { super(entry, userObject); }

        // --- PageCreator --- //
        @Override
        @SuppressWarnings("unchecked")
        public <T extends JComponent & UriGettable> T createPage(API api) {
            return (T)new TextFileTreeNodeFactoryProvider.Page(entry) {
                /**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				@Override public String getSyntaxStyle() { return SyntaxConstants.SYNTAX_STYLE_SQL; }
            };
        }
    }
}