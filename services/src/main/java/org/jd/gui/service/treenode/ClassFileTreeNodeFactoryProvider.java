/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.apache.bcel.classfile.ClassFormatException;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContainerEntryGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.view.component.DynamicPage;
import org.jd.gui.view.data.ClassFileTreeNodeBean;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.tree.DefaultMutableTreeNode;

import static org.apache.bcel.Const.MAJOR_1_1;
import static org.apache.bcel.Const.MAJOR_1_5;

import jd.core.CoreConstants;

public class ClassFileTreeNodeFactoryProvider extends AbstractTypeFileTreeNodeFactoryProvider {

    public static final ImageIcon CLASS_FILE_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj.png"));
    public static final ImageIcon CLASS_FILE_ICON_ERROR = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj_error.png"));
    public static final ImageIcon CLASS_FILE_ICON_WARNING = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj_warning.png"));
    public static final Factory FACTORY = new Factory();

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.class");
    }

    @Override
    public Pattern getPathPattern() {
        if (externalPathPattern == null) {
            return Pattern.compile("^((?!module-info\\.class).)*$");
        }
        return externalPathPattern;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DefaultMutableTreeNode & ContainerEntryGettable & UriGettable> T make(API api, Container.Entry entry) {
        int lastSlashIndex = entry.getPath().lastIndexOf('/');
        String label = entry.getPath().substring(lastSlashIndex + 1);
        ClassFileTreeNodeBean treeNodeBean = new ClassFileTreeNodeBean(api, label, entry);
        FileTreeNode fileTreeNode = new FileTreeNode(entry, treeNodeBean, FACTORY);
        treeNodeBean.getWorker().execute();
        return (T) fileTreeNode;
    }

    protected static class Factory implements AbstractTypeFileTreeNodeFactoryProvider.PageAndTipFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends JComponent & UriGettable> T makePage(API a, Container.Entry e) {
            return (T) new DynamicPage(a, e);
        }

        @Override
        public String makeTip(API api, Container.Entry entry) {
            String location = new File(entry.getUri()).getPath();
            StringBuilder tip = new StringBuilder("<html>Location: ");

            tip.append(location);
            tip.append("<br>Java compiler version: ");

            try (DataInputStream is = new DataInputStream(entry.getInputStream())) {
                int magic = is.readInt();
                if (magic != CoreConstants.JAVA_MAGIC_NUMBER) {
                    throw new ClassFormatException("Invalid CLASS file");
                }
                int minorVersion = readUnsignedShort(is);
                int majorVersion = readUnsignedShort(is);

                if (majorVersion >= MAJOR_1_5) {
                    tip.append(majorVersion - (MAJOR_1_5 - 5));
                } else if (majorVersion >= MAJOR_1_1) {
                    tip.append("1.");
                    tip.append(majorVersion - (MAJOR_1_1 - 1));
                }
                tip.append(" (");
                tip.append(majorVersion);
                tip.append('.');
                tip.append(minorVersion);
                tip.append(')');
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }

            tip.append("</html>");

            return tip.toString();
        }

        /**
         * @see java.io.DataInputStream#readUnsignedShort()
         */
        protected int readUnsignedShort(InputStream is) throws IOException {
            int ch1 = is.read();
            int ch2 = is.read();
            if ((ch1 | ch2) < 0) {
                throw new EOFException();
            }
            return (ch1 << 8) + ch2;
        }
    }
}
