/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.actions;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;

public class ShowByteCodeContextualActionsFactory implements ContextualActionsFactory { // NO_UCD (unused code)

    @Override
    public Collection<Action> make(API api, Container.Entry entry, String fragment) {
        Collection<Action> actions = new ArrayList<>();
        if (entry.getPath().endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            actions.add(new ShowByteCodeAction(entry, fragment));
        }
        return actions;
    }

    public static class ShowByteCodeAction extends AbstractMethodAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj.png"));
        protected static final ImageIcon NEXT_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/forward_nav.png"));
        protected static final ImageIcon PREV_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/backward_nav.png"));

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
            
            // Create a toolbar with searching options.
            JToolBar toolBar = new JToolBar();
            JTextField searchField = new JTextField(30);
            toolBar.add(searchField);
            final JButton nextButton = new JButton("Find Next", NEXT_ICON);
            nextButton.setActionCommand("FindNext");
            JButton prevButton = new JButton("Find Previous", PREV_ICON);
            prevButton.setActionCommand("FindPrev");
            toolBar.add(prevButton);
            toolBar.add(nextButton);
            JCheckBox regexCB = new JCheckBox("Regex");
            toolBar.add(regexCB);
            JCheckBox matchCaseCB = new JCheckBox("Match Case");
            toolBar.add(matchCaseCB);

            SearchAction searchAction = new SearchAction(matchCaseCB, regexCB, textArea, searchField);
            nextButton.addActionListener(searchAction);
            prevButton.addActionListener(searchAction);

            
            // Make Enter and Shift + Enter search forward and backward,
            // respectively, when the search field is focused.
            InputMap im = searchField.getInputMap();
            ActionMap am = searchField.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "searchForward");
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "searchBackward");
            am.put("searchForward", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    nextButton.doClick(0);
                }
            });
            am.put("searchBackward", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    prevButton.doClick(0);
                }
            });

            // Make Ctrl+F/Cmd+F focus the search field
            int defaultMod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            im = textArea.getInputMap();
            am = textArea.getActionMap();
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, defaultMod), "doSearch");
            am.put("doSearch", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    searchField.requestFocusInWindow();
                }
            });
            
            JFrame frame = new JFrame("Byte Code Viewer for " + method);
            java.awt.Container cp = frame.getContentPane();
            cp.add(toolBar, BorderLayout.NORTH);
            cp.add(sp);
            frame.setLocation(screenSize.width / 4, screenSize.height / 4);
            frame.setSize(screenSize.width / 2, screenSize.height / 2);
            ImageUtil.addJDIconsToFrame(frame);
            frame.setVisible(true);
        }

        private static class SearchAction extends AbstractAction {

            private static final long serialVersionUID = 1L;

            private JCheckBox matchCaseCB;
            private JCheckBox regexCB;
            private JTextArea textArea;
            private JTextField searchField;
            
            public SearchAction(JCheckBox matchCaseCB, JCheckBox regexCB, JTextArea textArea, JTextField searchField) {
                this.matchCaseCB = matchCaseCB;
                this.regexCB = regexCB;
                this.textArea = textArea;
                this.searchField = searchField;
            }

            @Override
            public void actionPerformed(ActionEvent e) {

                // "FindNext" => search forward, "FindPrev" => search backward
                String command = e.getActionCommand();
                boolean forward = "FindNext".equals(command);

                // Create an object defining our search parameters.
                SearchContext context = new SearchContext();
                String text = searchField.getText();
                if (text.length() == 0) {
                    return;
                }
                context.setSearchFor(text);
                context.setMatchCase(matchCaseCB.isSelected());
                context.setRegularExpression(regexCB.isSelected());
                context.setSearchForward(forward);
                context.setWholeWord(false);

                SearchEngine.find(textArea, context);
            }
        }
    }
}
