/*
 * Copyright (c) 2021-2026 Nicolas Baumann (@nbauma109).
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
import org.jd.gui.util.KeyBindings;
import org.jd.gui.util.KeyBindings.Binding;
import org.jd.gui.util.ThemeUtil;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.view.SearchUIOptions;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;

import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SEARCH_HIGHLIGHT_COLOR;

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
        protected static final ImageIcon NEXT_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/next_nav.png"));
        protected static final ImageIcon PREV_ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/prev_nav.png"));

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
            textArea.setMarkAllHighlightColor(getSearchHighlightColor(api));
            RTextScrollPane sp = new RTextScrollPane(textArea);

            // Create a toolbar with searching options.
            JToolBar toolBar = new JToolBar();
            JTextField searchField = new JTextField(30);
            SearchUIOptions.configureIncrementalSearchField(searchField, api.getPreferences());
            toolBar.add(searchField);
            final JButton nextButton = new JButton(SearchUIOptions.getFindNextLabel(), NEXT_ICON);
            nextButton.setActionCommand("FindNext");
            SearchUIOptions.configureFindButton(nextButton, api.getPreferences());
            JButton prevButton = new JButton(SearchUIOptions.getFindPreviousLabel(), PREV_ICON);
            prevButton.setActionCommand("FindPrev");
            SearchUIOptions.configureFindButton(prevButton, api.getPreferences());
            toolBar.add(nextButton);
            toolBar.add(prevButton);

            SearchUIOptions searchUIOptions = new SearchUIOptions(api.getPreferences(), null);
            searchUIOptions.attachTo(toolBar);
            searchUIOptions.bindOptionKeyStrokes(searchField, api.getPreferences());

            SearchAction searchAction = new SearchAction(searchUIOptions, textArea, searchField);
            nextButton.addActionListener(searchAction);
            prevButton.addActionListener(searchAction);
            Color searchBackgroundColor = searchField.getBackground();
            Color searchErrorBackgroundColor = Color.decode(api.getPreferences().get(GuiPreferences.ERROR_BACKGROUND_COLOR));
            Runnable updateSearchFeedback = () -> searchField.setBackground(searchAction.highlightText() ? searchBackgroundColor : searchErrorBackgroundColor);
            SearchUIOptions.installCriteriaListener(searchField, updateSearchFeedback);
            searchUIOptions.addOptionsChangeListener(_ -> updateSearchFeedback.run());

            SearchUIOptions.bindSearchNavigationKeyStrokes(searchField, api.getPreferences(), () -> nextButton.doClick(0), () -> prevButton.doClick(0));

            // Honor the same configurable Find shortcut used by the main window.
            InputMap im = textArea.getInputMap();
            ActionMap am = textArea.getActionMap();
            KeyStroke findKeyStroke = KeyBindings.getConfiguredKeyStroke(api.getPreferences(), Binding.FIND);
            if (findKeyStroke != null) {
                im.put(findKeyStroke, "doSearch");
            }
            am.put("doSearch", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    String selectedText = textArea.getSelectedText();
                    if (selectedText != null && !selectedText.isEmpty()) {
                        searchField.setText(selectedText);
                    }
                    searchField.requestFocusInWindow();
                    searchField.selectAll();
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

        private Color getSearchHighlightColor(API api) {
            String configuredColor = api.getPreferences().get(SEARCH_HIGHLIGHT_COLOR);

            try {
                return Color.decode(configuredColor != null ? configuredColor : DEFAULT_SEARCH_HIGHLIGHT_COLOR);
            } catch (RuntimeException e) {
                return Color.decode(DEFAULT_SEARCH_HIGHLIGHT_COLOR);
            }
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

            private final SearchUIOptions searchUIOptions;
            private final JTextArea textArea;
            private final JTextField searchField;

            public SearchAction(SearchUIOptions searchUIOptions, JTextArea textArea, JTextField searchField) {
                this.searchUIOptions = searchUIOptions;
                this.textArea = textArea;
                this.searchField = searchField;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                // "FindNext" => search forward, "FindPrev" => search backward
                String command = e.getActionCommand();
                boolean forward = "FindNext".equals(command);
                search(forward);
            }

            public boolean highlightText() {
                String text = searchField.getText();

                if (text.isEmpty()) {
                    return true;
                }

                textArea.setCaretPosition(textArea.getSelectionStart());
                SearchContext context = createSearchContext(text, true);
                boolean found = SearchEngine.find(textArea, context).wasFound();

                if (!found) {
                    textArea.setCaretPosition(0);
                    found = SearchEngine.find(textArea, context).wasFound();
                }

                return found;
            }

            private void search(boolean forward) {
                String text = searchField.getText();
                if (text.isEmpty()) {
                    return;
                }

                SearchEngine.find(textArea, createSearchContext(text, forward));
            }

            private SearchContext createSearchContext(String text, boolean forward) {
                SearchContext context = new SearchContext();
                context.setSearchFor(text);
                context.setMatchCase(searchUIOptions.isMatchCaseButtonSelected());
                context.setRegularExpression(searchUIOptions.isRegexButtonSelected());
                context.setSearchForward(forward);
                context.setWholeWord(searchUIOptions.isWholeWordButtonSelected());
                context.setMarkAll(searchUIOptions.isMarkAllButtonSelected());
                context.setSearchWrap(searchUIOptions.isWrapButtonSelected());
                return context;
            }
        }
    }
}
