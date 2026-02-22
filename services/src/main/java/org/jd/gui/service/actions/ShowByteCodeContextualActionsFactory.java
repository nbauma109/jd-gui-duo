/*
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.actions;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.LineNumber;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.LocalVariableTypeTable;
import org.apache.bcel.classfile.Method;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ByteCodeWriter;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.spi.ContextualActionsFactory;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.ThemeUtil;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
        if (entry.getPath().endsWith(StringConstants.CLASS_FILE_SUFFIX) && isMethod(fragment)) {
            actions.add(new ShowByteCodeAction(api, entry, fragment));
        }
        return actions;
    }

    public static class ShowByteCodeAction extends AbstractMethodAction {

        private static final long serialVersionUID = 1L;

        protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/classf_obj.png"));
        protected static final ImageIcon NEXT_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/forward_nav.png"));
        protected static final ImageIcon PREV_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/backward_nav.png"));

        public ShowByteCodeAction(API api, Container.Entry entry, String fragment) {
            super(api, entry, fragment);
            putValue(GROUP_NAME, "Edit > ShowByteCode"); // used for sorting and grouping menus
            putValue(NAME, "Show Byte Code");
            putValue(SMALL_ICON, ICON);
        }

        @Override
        protected void methodAction(API api, Method method, String className) {
            String byteCode = new AsciiTableByteCodeWriter().write("    ", method);
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            RSyntaxTextArea textArea = ThemeUtil.applyTheme(api, new RSyntaxTextArea(byteCode));
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

        private static class AsciiTableByteCodeWriter extends ByteCodeWriter {

            @Override
            protected void writeLineNumberTable(String linePrefix, StringBuilder sb, Code attributeCode) {
                LineNumberTable lineNumberTable = attributeCode.getLineNumberTable();
                if (lineNumberTable != null) {
                    sb.append("\n\n").append(linePrefix).append("Line number table:\n\n").append(linePrefix);
                    List<LineNumber> lineNumbers = Arrays.asList(lineNumberTable.getLineNumberTable());
                    List<ColumnData<LineNumber>> columns = new ArrayList<>();
                    columns.add(new Column().header("Java source line number").with(lineNumber -> String.valueOf(lineNumber.getLineNumber())));
                    columns.add(new Column().header("Byte code offset").with(lineNumber -> String.valueOf(lineNumber.getStartPC())));
                    sb.append(AsciiTable.builder().lineSeparator("\n" + linePrefix).data(lineNumbers, columns).asString());
                }
            }

            @Override
            protected void writeExceptionTable(String linePrefix, StringBuilder sb, ConstantPool constants, Code attributeCode) {
                CodeException[] codeExceptions = attributeCode.getExceptionTable();
                if (codeExceptions != null) {
                    sb.append("\n\n").append(linePrefix).append("Exception table:\n\n").append(linePrefix);
                    List<CodeException> codeExceptionList = Arrays.asList(codeExceptions);
                    List<ColumnData<CodeException>> columns = new ArrayList<>();
                    columns.add(new Column().header("From").with(ce -> String.valueOf(ce.getStartPC())));
                    columns.add(new Column().header("To").with(ce -> String.valueOf(ce.getEndPC())));
                    columns.add(new Column().header("Target").with(ce -> String.valueOf(ce.getHandlerPC())));
                    columns.add(new Column().header("Type").with(ce -> ce.getCatchType() == 0 ? "finally" : constants.getConstantString(ce.getCatchType(), Const.CONSTANT_Class)));
                    sb.append(AsciiTable.builder().lineSeparator("\n" + linePrefix).data(codeExceptionList, columns).asString());
                }
            }

            @Override
            protected void writeLocalVariableTable(String linePrefix, StringBuilder sb, Code attributeCode) {
                LocalVariableTable localVariableTable = attributeCode.getLocalVariableTable();
                if (localVariableTable != null) {
                    sb.append("\n\n").append(linePrefix).append("Local variable table:\n\n").append(linePrefix);
                    List<LocalVariable> localVariableList = Arrays.asList(localVariableTable.getLocalVariableTable());
                    List<ColumnData<LocalVariable>> columns = new ArrayList<>();
                    columns.add(new Column().header("Start").with(lv -> String.valueOf(lv.getStartPC())));
                    columns.add(new Column().header("Length").with(lv -> String.valueOf(lv.getLength())));
                    columns.add(new Column().header("Slot").with(lv -> String.valueOf(lv.getIndex())));
                    columns.add(new Column().header("Name").with(LocalVariable::getName));
                    columns.add(new Column().header("Descriptor").with(LocalVariable::getSignature));
                    sb.append(AsciiTable.builder().lineSeparator("\n" + linePrefix).data(localVariableList, columns).asString());
                }

                LocalVariableTypeTable localVariableTypeTable = (LocalVariableTypeTable) Optional.ofNullable(attributeCode.getAttributes())
                        .map(Stream::of).orElseGet(Stream::empty).filter(LocalVariableTypeTable.class::isInstance).findAny().orElse(null);

                if (localVariableTypeTable != null) {
                    sb.append("\n\n").append(linePrefix).append("Local variable type table:\n\n").append(linePrefix);
                    List<LocalVariable> localVariableList = Arrays.asList(localVariableTypeTable.getLocalVariableTypeTable());
                    List<ColumnData<LocalVariable>> columns = new ArrayList<>();
                    columns.add(new Column().header("Start").with(lv -> String.valueOf(lv.getStartPC())));
                    columns.add(new Column().header("Length").with(lv -> String.valueOf(lv.getLength())));
                    columns.add(new Column().header("Slot").with(lv -> String.valueOf(lv.getIndex())));
                    columns.add(new Column().header("Name").with(LocalVariable::getName));
                    columns.add(new Column().header("Descriptor").with(LocalVariable::getSignature));
                    sb.append(AsciiTable.builder().lineSeparator("\n" + linePrefix).data(localVariableList, columns).asString());
                }
            }
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
