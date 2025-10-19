/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.PageCreator;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.io.TextReader;
import org.jd.gui.view.component.TextPage;
import org.jd.gui.view.data.TreeNodeBean;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

public class TextFileTreeNodeFactoryProvider extends FileTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/ascii_obj.png"));

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.txt", "*:file:*.md", "*:file:*.SF", "*:file:*.policy", "*:file:*.yaml", "*:file:*.yml", "*:file:*/COPYRIGHT", "*:file:*/LICENSE", "*:file:*.cnd", "*:file:*.drl", "*:file:*.dsl");
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
            return (T) new Page(api, entry);
        }
    }

    protected static class Page extends TextPage implements UriGettable {

        private static final long serialVersionUID = 1L;
        private transient Container.Entry entry;

        public Page(API api, Container.Entry entry) {
        	super(api);
            this.entry = entry;
            try (InputStream inputStream = entry.getInputStream()) {
                setText(TextReader.getText(inputStream));
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        // --- UriGettable --- //
        @Override
        public URI getUri() {
            return entry.getUri();
        }

        // --- ContentSavable --- //
        @Override
        public String getFileName() {
            String path = entry.getPath();
            int index = path.lastIndexOf("/");
            return path.substring(index + 1);
        }
    }
}
