/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.actions;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JFrame;

public class ShowByteCodeContextualActionsFactory implements ContextualActionsFactory { // NO_UCD (unused code)

    @Override
    public Collection<Action> make(API api, Container.Entry entry, String fragment) {
        return Collections.singletonList(new ShowByteCodeAction(entry, fragment));
    }

    public static class ShowByteCodeAction extends AbstractMethodAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj.png"));

        public ShowByteCodeAction(Container.Entry entry, String fragment) {
            super(entry, fragment);
            putValue(GROUP_NAME, "Edit > ShowByteCode"); // used for sorting and grouping menus
            putValue(NAME, "Show Byte Code");
            putValue(SMALL_ICON, ICON);
        }

        @Override
        protected void methodAction(Method method) {
            String byteCode = ByteCodeWriter.write("    ", method);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            RSyntaxTextArea textArea = new RSyntaxTextArea(byteCode);
            textArea.setCaretPosition(0);
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            RTextScrollPane sp = new RTextScrollPane(textArea);
            JFrame frame = new JFrame("Byte Code Viewer for " + method);
            frame.getContentPane().add(sp);
            frame.setLocation(screenSize.width / 4, screenSize.height / 4);
            frame.setSize(screenSize.width / 2, screenSize.height / 2);
            frame.setIconImages(Stream.of(32, 64, 128).map(size -> "/org/jd/gui/images/jd_icon_" + size + ".png").map(ImageUtil::getImage).toList());
            frame.setVisible(true);
        }

    }
}
