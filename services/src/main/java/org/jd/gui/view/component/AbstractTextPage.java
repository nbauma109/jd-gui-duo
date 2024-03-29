/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.folding.FoldManager;
import org.fife.ui.rtextarea.FoldIndicatorStyle;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.feature.ContentSearchable;
import org.jd.gui.api.feature.LineNumberNavigable;
import org.jd.gui.api.feature.PreferencesChangeListener;
import org.jd.gui.api.feature.UriOpenable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;

public class AbstractTextPage extends JPanel implements LineNumberNavigable, ContentSearchable, UriOpenable, PreferencesChangeListener {

    private static final long serialVersionUID = 1L;

    protected static final Color DOUBLE_CLICK_HIGHLIGHT_COLOR = new Color(0x66ff66);
    protected static final Color SEARCH_HIGHLIGHT_COLOR = new Color(0xffff66);
    protected static final Color SELECT_HIGHLIGHT_COLOR = new Color(0xF49810);

    protected static final RSyntaxTextAreaEditorKit.DecreaseFontSizeAction DECREASE_FONT_SIZE_ACTION = new RSyntaxTextAreaEditorKit.DecreaseFontSizeAction();
    protected static final RSyntaxTextAreaEditorKit.IncreaseFontSizeAction INCREASE_FONT_SIZE_ACTION = new RSyntaxTextAreaEditorKit.IncreaseFontSizeAction();

    protected final RSyntaxTextArea textArea;
    protected final RTextScrollPane scrollPane;

    private Map<String, String> preferences;

    public AbstractTextPage() {
        super(new BorderLayout());

        textArea = newSyntaxTextArea();
        textArea.setSyntaxEditingStyle(getSyntaxStyle());
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setCaretPosition(0);
        textArea.setEditable(false);
        textArea.setDropTarget(null);
        textArea.setPopupMenu(null);
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    textArea.setMarkAllHighlightColor(DOUBLE_CLICK_HIGHLIGHT_COLOR);
                    SearchEngine.markAll(textArea, newSearchContext(textArea.getSelectedText(), true, true, true, false));
                }
            }
        });

        KeyStroke ctrlA = KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        KeyStroke ctrlC = KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        KeyStroke ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        InputMap inputMap = textArea.getInputMap();
        inputMap.put(ctrlA, "none");
        inputMap.put(ctrlC, "none");
        inputMap.put(ctrlV, "none");

        try (InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("rsyntaxtextarea/themes/eclipse.xml")) {
            Theme theme = Theme.load(resourceAsStream);
            theme.apply(textArea);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setFont(textArea.getFont());

        final MouseWheelListener[] mouseWheelListeners = scrollPane.getMouseWheelListeners();

        // Remove default listeners
        for (MouseWheelListener listener : mouseWheelListeners) {
            scrollPane.removeMouseWheelListener(listener);
        }

        scrollPane.addMouseWheelListener(e -> {
            if ((e.getModifiersEx() & (InputEvent.META_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) != 0) {
                int x = e.getX() + scrollPane.getX() - textArea.getX();
                int y = e.getY() + scrollPane.getY() - textArea.getY();
                int offset = textArea.viewToModel2D(new Point(x, y));

                // Update font size
                if (e.getWheelRotation() > 0) {
                    DECREASE_FONT_SIZE_ACTION.actionPerformedImpl(null, textArea);
                } else {
                    INCREASE_FONT_SIZE_ACTION.actionPerformedImpl(null, textArea);
                }

                // Save preferences
                if (preferences != null) {
                    preferences.put(FONT_SIZE_KEY, String.valueOf(textArea.getFont().getSize()));
                }

                try {
                    Rectangle2D newRectangle = textArea.modelToView2D(offset);
                    int newY = (int) Math.round(newRectangle.getY() + newRectangle.getHeight() / 2D);

                    // Scroll
                    Point viewPosition = scrollPane.getViewport().getViewPosition();
                    viewPosition.y = Math.max(viewPosition.y + newY - y, 0);
                    scrollPane.getViewport().setViewPosition(viewPosition);
                } catch (BadLocationException ee) {
                    assert ExceptionUtil.printStackTrace(ee);
                }
            } else {
                // Call default listeners
                for (MouseWheelListener listener : mouseWheelListeners) {
                    listener.mouseWheelMoved(e);
                }
            }
        });

        Gutter gutter = scrollPane.getGutter();
        gutter.setFoldIndicatorStyle(FoldIndicatorStyle.CLASSIC);
        gutter.setFoldIndicatorForeground(gutter.getBorderColor());

        add(scrollPane, BorderLayout.CENTER);
        add(new RoundMarkErrorStrip(textArea), BorderLayout.LINE_END);
    }

    protected RSyntaxTextArea newSyntaxTextArea() {
        return new RSyntaxTextArea();
    }

    public String getText() {
        return textArea.getText();
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    protected void setText(String text) {
        textArea.setText(text);
        textArea.setCaretPosition(0);
    }

    public String getSyntaxStyle() {
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }

    /**
     * @see org.fife.ui.rsyntaxtextarea.RSyntaxUtilities#selectAndPossiblyCenter
     *      Force center and do not select
     */
    protected void setCaretPositionAndCenter(DocumentRange range) {
        final int start = range.getStartOffset();
        final int end = range.getEndOffset();
        boolean foldsExpanded = false;
        FoldManager fm = textArea.getFoldManager();

        if (fm.isCodeFoldingSupportedAndEnabled()) {
            foldsExpanded = fm.ensureOffsetNotInClosedFold(start);
            foldsExpanded |= fm.ensureOffsetNotInClosedFold(end);
        }

        if (!foldsExpanded) {
            try {
                Rectangle2D rec = textArea.modelToView2D(start);

                if (rec != null) {
                    // Visible
                    setCaretPositionAndCenter(start, end, rec);
                } else {
                    // Not visible yet
                    SwingUtilities.invokeLater(() -> {
                        try {
                            Rectangle2D r = textArea.modelToView2D(start);
                            if (r != null) {
                                setCaretPositionAndCenter(start, end, r);
                            }
                        } catch (BadLocationException e) {
                            assert ExceptionUtil.printStackTrace(e);
                        }
                    });
                }
            } catch (BadLocationException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }

    protected void setCaretPositionAndCenter(int start, int end, Rectangle2D rec) {
        if (end != start) {
            try {
                rec = rec.createUnion(textArea.modelToView2D(end));
            } catch (BadLocationException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        Rectangle visible = textArea.getVisibleRect();

        // visible.x = r.x - (visible.width - r.width) / 2;
        visible.y = (int) Math.round(rec.getY() - (visible.height - rec.getHeight()) / 2);

        Rectangle bounds = textArea.getBounds();
        Insets i = textArea.getInsets();
        // bounds.x = i.left;
        bounds.y = i.top;
        // bounds.width -= i.left + i.right;
        bounds.height -= i.top + i.bottom;

        // if (visible.x < bounds.x) {
        // visible.x = bounds.x;
        // }
        // if (visible.x + visible.width > bounds.x + bounds.width) {
        // visible.x = bounds.x + bounds.width - visible.width;
        // }
        if (visible.y < bounds.y) {
            visible.y = bounds.y;
        }
        if (visible.y + visible.height > bounds.y + bounds.height) {
            visible.y = bounds.y + bounds.height - visible.height;
        }

        textArea.scrollRectToVisible(visible);
        textArea.setCaretPosition(start);
    }

    // --- LineNumberNavigable --- //
    @Override
    public int getMaximumLineNumber() {
        try {
            return textArea.getLineOfOffset(textArea.getDocument().getLength()) + 1;
        } catch (BadLocationException e) {
            assert ExceptionUtil.printStackTrace(e);
            return 0;
        }
    }

    @Override
    public void goToLineNumber(int lineNumber) {
        try {
            textArea.setCaretPosition(textArea.getLineStartOffset(lineNumber - 1));
        } catch (BadLocationException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    @Override
    public boolean checkLineNumber(int lineNumber) {
        return true;
    }

    // --- ContentSearchable --- //
    @Override
    public boolean highlightText(String text, boolean caseSensitive) {
        if (text.length() > 1) {
            textArea.setMarkAllHighlightColor(SEARCH_HIGHLIGHT_COLOR);
            textArea.setCaretPosition(textArea.getSelectionStart());

            SearchContext context = newSearchContext(text, caseSensitive, false, true, false);
            SearchResult result = SearchEngine.find(textArea, context);

            if (!result.wasFound()) {
                textArea.setCaretPosition(0);
                result = SearchEngine.find(textArea, context);
            }

            return result.wasFound();
        }
        return true;
    }

    @Override
    public void findNext(String text, boolean caseSensitive) {
        if (text.length() > 1) {
            textArea.setMarkAllHighlightColor(SEARCH_HIGHLIGHT_COLOR);

            SearchContext context = newSearchContext(text, caseSensitive, false, true, false);
            SearchResult result = SearchEngine.find(textArea, context);

            if (!result.wasFound()) {
                textArea.setCaretPosition(0);
                SearchEngine.find(textArea, context);
            }
        }
    }

    @Override
    public void findPrevious(String text, boolean caseSensitive) {
        if (text.length() > 1) {
            textArea.setMarkAllHighlightColor(SEARCH_HIGHLIGHT_COLOR);

            SearchContext context = newSearchContext(text, caseSensitive, false, false, false);
            SearchResult result = SearchEngine.find(textArea, context);

            if (!result.wasFound()) {
                textArea.setCaretPosition(textArea.getDocument().getLength());
                SearchEngine.find(textArea, context);
            }
        }
    }

    protected SearchContext newSearchContext(String searchFor, boolean matchCase, boolean wholeWord, boolean searchForward, boolean regexp) {
        SearchContext context = new SearchContext(searchFor, matchCase);
        context.setMarkAll(true);
        context.setWholeWord(wholeWord);
        context.setSearchForward(searchForward);
        context.setRegularExpression(regexp);
        return context;
    }

    // --- UriOpenable --- //
    @Override
    public boolean openUri(URI uri) {
        String query = uri.getQuery();

        if (query != null) {
            Map<String, String> parameters = parseQuery(query);

            if (parameters.containsKey("lineNumber")) {
                String lineNumber = parameters.get("lineNumber");

                try {
                    goToLineNumber(Integer.parseInt(lineNumber));
                    return true;
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else if (parameters.containsKey("position")) {
                String position = parameters.get("position");

                try {
                    int pos = Integer.parseInt(position);
                    if (textArea.getDocument().getLength() > pos) {
                        setCaretPositionAndCenter(new DocumentRange(pos, pos));
                        return true;
                    }
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else if (parameters.containsKey("highlightFlags")) {
                String highlightFlags = parameters.get("highlightFlags");

                if (highlightFlags.indexOf('s') != -1 && parameters.containsKey("highlightPattern")) {
                    textArea.setMarkAllHighlightColor(SELECT_HIGHLIGHT_COLOR);
                    textArea.setCaretPosition(0);

                    // Highlight all
                    String searchFor = createRegExp(parameters.get("highlightPattern"));
                    SearchContext context = newSearchContext(searchFor, true, false, true, true);
                    SearchResult result = SearchEngine.find(textArea, context);

                    if (result.getMatchRange() != null) {
                        textArea.setCaretPosition(result.getMatchRange().getStartOffset());
                    }

                    return true;
                }
            }
        }

        return false;
    }

    protected Map<String, String> parseQuery(String query) {
        Map<String, String> parameters = new HashMap<>();

        // Parse parameters
        try {
            for (String param : query.split("&")) {
                int index = param.indexOf('=');

                String enc = StandardCharsets.UTF_8.name();
                if (index == -1) {
                    parameters.put(URLDecoder.decode(param, enc), "");
                } else {
                    String key = param.substring(0, index);
                    String value = param.substring(index + 1);
                    parameters.put(URLDecoder.decode(key, enc), URLDecoder.decode(value, enc));
                }
            }
        } catch (UnsupportedEncodingException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        return parameters;
    }

    /**
     * Create a simple regular expression
     *
     * Rules: '*' matchTypeEntries 0 ou N characters '?' matchTypeEntries 1
     * character
     */
    public static String createRegExp(String pattern) {
        int patternLength = pattern.length();
        StringBuilder sbPattern = new StringBuilder(patternLength * 2);

        for (int i = 0; i < patternLength; i++) {
            char c = pattern.charAt(i);

            switch (c) {
            case '*':
                sbPattern.append(".*");
                break;
            case '?':
                sbPattern.append('.');
                break;
            case '.':
                sbPattern.append("\\.");
                break;
            default:
                sbPattern.append(c);
                break;
            }
        }

        return sbPattern.toString();
    }

    // --- PreferencesChangeListener --- //
    @Override
    public void preferencesChanged(Map<String, String> preferences) {
        String fontSize = preferences.get(FONT_SIZE_KEY);

        if (fontSize != null) {
            try {
                textArea.setFont(textArea.getFont().deriveFont(Float.parseFloat(fontSize)));
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        this.preferences = preferences;
    }
}
