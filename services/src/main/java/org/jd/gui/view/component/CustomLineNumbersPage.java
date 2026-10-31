/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2025 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldManager;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RTextAreaUI;
import org.jd.gui.api.API;
import org.jd.gui.util.KeyBindings;
import org.jd.gui.util.ThemeUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;

public abstract class CustomLineNumbersPage extends HyperlinkPage {
    private static final long serialVersionUID = 1L;
    private static final Image BREAKPOINT_ICON = loadBreakpointIcon();
    private static final Color EXECUTION_LINE_COLOR = new Color(0xC6, 0xDB, 0xAE); // Eclipse execution line #C6DBAE
    private Color errorForeground = Color.RED;
    private boolean showMisalignment = true;
    private int currentExecutionLine;       // source line number, 0 = none
    private int currentExecutionTextAreaLine; // text-area line number, 0 = none
    private transient Object lineHighlightTag;

    protected void setErrorForeground(Color color) {
        errorForeground = color;
    }

    protected void setShowMisalignment(boolean b) {
        showMisalignment = b;
    }

    /** Map[textarea line number] = original line number. */
    private int[] lineNumberMap;
    private int maxLineNumber;
    private final Set<Integer> breakpointLineNumbers = new HashSet<>();

    private final transient API debugApi;
    private transient java.util.function.BooleanSupplier debugSuspended;
    private transient java.util.function.BiConsumer<String, java.awt.Point> inspectHandler;
    private transient java.util.function.Consumer<String> watchHandler;
    private transient java.util.function.IntConsumer runToLineHandler;

    protected CustomLineNumbersPage(API api) {
    	super(api);
        this.debugApi = api;
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showDebugContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showDebugContextMenu(e);
            }
        });
    }

    public void setDebugExpressionHandlers(java.util.function.BooleanSupplier suspended,
            java.util.function.BiConsumer<String, java.awt.Point> inspect, java.util.function.Consumer<String> watch,
            java.util.function.IntConsumer runToLine) {
        this.debugSuspended = suspended;
        this.inspectHandler = inspect;
        this.watchHandler = watch;
        this.runToLineHandler = runToLine;
    }

    private static javax.swing.Icon debugMenuIcon(String name) {
        URL url = CustomLineNumbersPage.class.getResource("/org/jd/gui/images/" + name);
        return url == null ? null : new ImageIcon(url);
    }

    private boolean isDebugSuspended() {
        return debugSuspended != null && debugSuspended.getAsBoolean() && inspectHandler != null && watchHandler != null;
    }

    /** Right-click menu of a suspended debuggee: Inspect and Watch the selected expression. */
    private void showDebugContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger() || !isDebugSuspended()) {
            return;
        }
        String expression = selectedExpression();
        if (expression == null) {
            expression = identifierAt(textArea.viewToModel2D(e.getPoint()));
        }
        String finalExpression = expression;
        java.awt.Point screenLocation = e.getLocationOnScreen();
        // same entries, order, icons and shortcuts as the debug section of Eclipse's editor menu
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem watch = new javax.swing.JMenuItem("Watch", debugMenuIcon("watch_exp.png"));
        watch.addActionListener(a -> watchHandler.accept(finalExpression));
        javax.swing.JMenuItem inspect = new javax.swing.JMenuItem("Inspect", debugMenuIcon("insp_sbook.png"));
        inspect.setAccelerator(KeyBindings.getConfiguredKeyStroke(debugApi.getPreferences(), KeyBindings.Binding.INSPECT));
        inspect.addActionListener(a -> inspectHandler.accept(finalExpression, screenLocation));
        javax.swing.JMenuItem runToLine = new javax.swing.JMenuItem("Run to Line", debugMenuIcon("runtoline_co.png"));
        runToLine.setAccelerator(KeyBindings.getConfiguredKeyStroke(debugApi.getPreferences(), KeyBindings.Binding.RUN_TO_LINE));
        int clickedLine = 0;
        try {
            clickedLine = getSourceLineNumber(textArea.getLineOfOffset(textArea.viewToModel2D(e.getPoint())) + 1);
        } catch (BadLocationException _) {
            // click below the last line
        }
        int finalLine = clickedLine;
        runToLine.addActionListener(a -> runToLineHandler.accept(finalLine));
        inspect.setEnabled(expression != null);
        watch.setEnabled(expression != null);
        runToLine.setEnabled(finalLine > 0 && runToLineHandler != null);
        menu.add(watch);
        menu.add(inspect);
        menu.add(runToLine);
        menu.show(textArea, e.getX(), e.getY());
    }

    public void inspectSelection() {
        if (!isDebugSuspended()) {
            return;
        }
        String expression = selectedExpression();
        if (expression == null) {
            expression = identifierAt(textArea.getCaretPosition());
        }
        if (expression == null) {
            return;
        }
        java.awt.Point screenLocation;
        try {
            java.awt.geom.Rectangle2D caret = textArea.modelToView2D(textArea.getCaretPosition());
            java.awt.Point origin = textArea.getLocationOnScreen();
            screenLocation = new java.awt.Point(origin.x + (int) caret.getX(), origin.y + (int) (caret.getY() + caret.getHeight()));
        } catch (BadLocationException | java.awt.IllegalComponentStateException _) {
            return;
        }
        inspectHandler.accept(expression, screenLocation);
    }

    private String selectedExpression() {
        String selected = textArea.getSelectedText();
        // keep the text as selected: collapsing spaces would change string literals such as "a  b"
        return selected == null || selected.isBlank() ? null : selected.trim();
    }

    private String identifierAt(int offset) {
        String text = textArea.getText();
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        return end > start ? text.substring(start, end) : null;
    }

    private static Image loadBreakpointIcon() {
        URL resource = CustomLineNumbersPage.class.getResource("/org/jd/gui/images/brkp_obj.png");
        return resource == null ? null : new ImageIcon(resource).getImage();
    }

    protected void setMaxLineNumber(int maxLineNumber) {
        if (maxLineNumber > 0) {
            if (lineNumberMap == null) {
                lineNumberMap = new int[maxLineNumber+1];
            } else if (lineNumberMap.length <= maxLineNumber) {
                int[] tmp = new int[maxLineNumber+1];
                System.arraycopy(lineNumberMap, 0, tmp, 0, lineNumberMap.length);
                lineNumberMap = tmp;
            }

            this.maxLineNumber = maxLineNumber;
        }
    }

    protected void initLineNumbers() {
        String text = getText();
        int len = text.length();

        if (len == 0) {
            setMaxLineNumber(0);
        } else {
            int mln = len - text.replace("\n", "").length();

            if (text.charAt(len-1) != '\n') {
                mln++;
            }

            setMaxLineNumber(mln);

            for (int i=1; i<=maxLineNumber; i++) {
                lineNumberMap[i] = i;
            }
        }
    }

    protected void setLineNumber(int textAreaLineNumber, int originalLineNumber) {
        if (originalLineNumber > 0) {
            setMaxLineNumber(textAreaLineNumber);
            lineNumberMap[textAreaLineNumber] = originalLineNumber;
        }
    }

    protected void clearLineNumbers() {
        if (lineNumberMap != null) {
            Arrays.fill(lineNumberMap, 0);
        }
    }

    public int getMaximumSourceLineNumber() { return maxLineNumber; }

    protected int getTextAreaLineNumber(int originalLineNumber) {
        if (lineNumberMap == null) {
            return originalLineNumber;
        }
        int textAreaLineNumber = 0;
        int greatestLowerSourceLineNumber = 0;
        int i = lineNumberMap.length;

        int sln;
        while (i-- > 0) {
            sln = lineNumberMap[i];
            if (sln <= originalLineNumber && greatestLowerSourceLineNumber < sln) {
                greatestLowerSourceLineNumber = sln;
                textAreaLineNumber = i;
            }
        }

        // No mapping found — fall back to the raw (bytecode) line number so the
        // execution-line highlight still appears even when line-number info is sparse.
        return textAreaLineNumber > 0 ? textAreaLineNumber : originalLineNumber;
    }

    protected void setBreakpointLineNumber(int sourceLineNumber, boolean enabled) {
        if (enabled) {
            breakpointLineNumbers.add(sourceLineNumber);
        } else {
            breakpointLineNumbers.remove(sourceLineNumber);
        }
        scrollPane.getGutter().repaint();
    }

    protected boolean isBreakpointLineNumber(int sourceLineNumber) {
        return breakpointLineNumbers.contains(sourceLineNumber);
    }

    public Set<Integer> getBreakpointLines() {
        return java.util.Collections.unmodifiableSet(breakpointLineNumbers);
    }

    public void setCurrentExecutionLine(int sourceLineNumber) {
        currentExecutionLine = sourceLineNumber;
        // Remove previous line highlight
        if (lineHighlightTag != null) {
            textArea.removeLineHighlight(lineHighlightTag);
            lineHighlightTag = null;
        }
        currentExecutionTextAreaLine = 0;
        if (sourceLineNumber > 0) {
            // Disable the current-line (caret-line) highlight while an execution line is
            // shown.  RSyntaxTextArea paints it AFTER addLineHighlight colours, which
            // would cover our green.  Disabling it lets addLineHighlight win.
            textArea.setHighlightCurrentLine(false);
            int taLine = getTextAreaLineNumber(sourceLineNumber);
            if (taLine > 0) {
                currentExecutionTextAreaLine = taLine;
                try {
                    lineHighlightTag = textArea.addLineHighlight(taLine - 1, EXECUTION_LINE_COLOR);
                    int offset = textArea.getLineStartOffset(taLine - 1);
                    scrollToLineCentered(offset);
                } catch (BadLocationException _) {
                    // line not in this view
                }
            }
        } else {
            // Restore the current-line highlight when no execution line is active
            textArea.setHighlightCurrentLine(true);
        }
        scrollPane.getGutter().repaint();
    }

    /**
     * Scrolls the viewport so that {@code offset} is vertically centred,
     * without moving the caret.
     */
    private void scrollToLineCentered(int offset) {
        try {
            java.awt.geom.Rectangle2D rec = textArea.modelToView2D(offset);
            if (rec != null) {
                doScrollCentered(rec);
            } else {
                // Component not laid out yet — defer one EDT cycle.
                SwingUtilities.invokeLater(() -> {
                    try {
                        java.awt.geom.Rectangle2D r = textArea.modelToView2D(offset);
                        if (r != null) {
                            doScrollCentered(r);
                        }
                    } catch (BadLocationException _) {
                        // ignore
                    }
                });
            }
        } catch (BadLocationException _) {
            // ignore
        }
    }

    private void doScrollCentered(java.awt.geom.Rectangle2D rec) {
        Rectangle visible = textArea.getVisibleRect();
        visible.y = (int) Math.round(rec.getY() - (visible.height - rec.getHeight()) / 2.0);
        Rectangle bounds = textArea.getBounds();
        Insets insets = textArea.getInsets();
        bounds.y = insets.top;
        bounds.height -= insets.top + insets.bottom;
        if (visible.y < bounds.y) {
            visible.y = bounds.y;
        }
        if (visible.y + visible.height > bounds.y + bounds.height) {
            visible.y = bounds.y + bounds.height - visible.height;
        }
        textArea.scrollRectToVisible(visible);
        // Deliberately NOT calling textArea.setCaretPosition() — the caret stays
        // where it is so the current-line highlight stays off the execution line.
    }

    protected void toggleBreakpointLineNumber(int sourceLineNumber) {
    }

    public int getCaretSourceLine() {
        try {
            return getSourceLineNumber(textArea.getLineOfOffset(textArea.getCaretPosition()) + 1);
        } catch (BadLocationException _) {
            return 0;
        }
    }

    public void toggleBreakpointAtCaret() {
        try {
            int textAreaLineNumber = textArea.getLineOfOffset(textArea.getCaretPosition()) + 1;
            int sourceLineNumber = getSourceLineNumber(textAreaLineNumber);
            if (sourceLineNumber > 0) {
                toggleBreakpointLineNumber(sourceLineNumber);
            }
        } catch (BadLocationException _) {
            // caret outside the document
        }
    }

    protected int getSourceLineNumber(int textAreaLineNumber) {
        if (lineNumberMap != null) {
            // Blank lines, braces and generated declarations have no original line: 0, not their row number,
            // which would point at an unrelated statement.
            return textAreaLineNumber < lineNumberMap.length ? lineNumberMap[textAreaLineNumber] : 0;
        }
        return textAreaLineNumber;
    }

    @Override
    protected RSyntaxTextArea newSyntaxTextArea(API api) { return ThemeUtil.applyTheme(api, new SourceSyntaxTextArea()); }

    public class SourceSyntaxTextArea extends HyperlinkSyntaxTextArea {
        private static final long serialVersionUID = 1L;

        @Override
        protected RTextAreaUI createRTextAreaUI() { return new SourceSyntaxTextAreaUI(this); }
    }

    /** A lot of code to replace the default LineNumberList... */
    public class SourceSyntaxTextAreaUI extends RSyntaxTextAreaUI {
        public SourceSyntaxTextAreaUI(JComponent rSyntaxTextArea) { super(rSyntaxTextArea); }
        @Override
        public EditorKit getEditorKit(JTextComponent tc) { return new SourceSyntaxTextAreaEditorKit(); }
        @Override
        public Rectangle getVisibleEditorRect() { return super.getVisibleEditorRect(); }

        /**
         * Paint the execution-line highlight AFTER super.paintBackground() so it
         * lands on top of RSyntaxTextArea's own current-line highlight.
         *
         * RSyntaxTextArea.paintBackground() order (confirmed from bytecode):
         *   1. fill background
         *   2. paintLineHighlights()        ← addLineHighlight() colours
         *   3. paintCurrentLineHighlight()  ← caret-line colour (#E8F2FE)
         * Step 3 always paints over step 2, so any addLineHighlight() colour on the
         * caret line is invisible.  By painting here, after super.paintBackground(),
         * our #C6DBAE always wins.
         */
        @Override
        protected void paintBackground(Graphics g) {
            super.paintBackground(g);
            if (currentExecutionTextAreaLine > 0) {
                RSyntaxTextArea rsta = (RSyntaxTextArea) getComponent();
                try {
                    int lineStart = rsta.getLineStartOffset(currentExecutionTextAreaLine - 1);
                    java.awt.geom.Rectangle2D r = rsta.modelToView2D(lineStart);
                    if (r != null) {
                        g.setColor(EXECUTION_LINE_COLOR);
                        g.fillRect(0, (int) r.getY(), rsta.getWidth(), rsta.getLineHeight());
                    }
                } catch (BadLocationException _) {
                    // line out of range in this view — ignore
                }
            }
        }
    }

    public class SourceSyntaxTextAreaEditorKit extends RSyntaxTextAreaEditorKit {
        private static final long serialVersionUID = 1L;

        @Override
        public LineNumberList createLineNumberList(RTextArea textArea) { return new SourceLineNumberList(textArea); }
    }

    /**
     * Why 'LineNumberList' is so unexpandable ? Too many private fields & methods and too many package scope.
     */
    public class SourceLineNumberList extends LineNumberList {
        private static final long serialVersionUID = 1L;
        private final RTextArea rTextArea;
        private transient Map<?,?> aaHints;
        private Rectangle visibleRect;
        private Insets textAreaInsets;
        private Dimension preferredSize;

        public SourceLineNumberList(RTextArea textArea) {
            super(textArea, null);
            this.rTextArea = textArea;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        toggleBreakpointAt(e);
                    }
                }
            });
        }

        private void toggleBreakpointAt(MouseEvent e) {
            try {
                int offset = rTextArea.viewToModel2D(new java.awt.Point(0, e.getY()));
                int textAreaLineNumber = rTextArea.getLineOfOffset(offset) + 1;
                int sourceLineNumber = getSourceLineNumber(textAreaLineNumber);
                if (sourceLineNumber > 0) {
                    toggleBreakpointLineNumber(sourceLineNumber);
                }
            } catch (Exception _) {
                // Ignore clicks outside valid text rows.
            }
        }

        @Override
        protected void init() {
            super.init();
            visibleRect = new Rectangle();
            aaHints = RSyntaxUtilities.getDesktopAntiAliasHints();
            textAreaInsets = null;
        }

        /**
         * @see org.fife.ui.rtextarea.LineNumberList#paintComponent(java.awt.Graphics)
         */
        @Override
        protected void paintComponent(Graphics g) {
            visibleRect = g.getClipBounds(visibleRect);

            if (visibleRect == null) {
                visibleRect = getVisibleRect();
            }
            if (visibleRect == null) {
                return;
            }

            int cellWidth = getPreferredSize().width;
            int cellHeight = rTextArea.getLineHeight();
            int ascent = rTextArea.getMaxAscent();
            FoldManager fm = ((RSyntaxTextArea)rTextArea).getFoldManager();
            int rhsBorderWidth = ((RSyntaxTextArea)rTextArea).isCodeFoldingEnabled() ? 0 : 4;
            FontMetrics metrics = g.getFontMetrics();
            int rhs = getWidth() - rhsBorderWidth;

            if (getParent() instanceof Gutter) { // Should always be true
                g.setColor(getParent().getBackground());
            } else {
                g.setColor(getBackground());
            }

            g.fillRect(0, visibleRect.y, cellWidth, visibleRect.height);
            g.setFont(getFont());

            if (aaHints != null) {
                ((Graphics2D)g).addRenderingHints(aaHints);
            }

            if (rTextArea.getLineWrap()) {
                SourceSyntaxTextAreaUI ui = (SourceSyntaxTextAreaUI)rTextArea.getUI();
                View v = ui.getRootView(rTextArea).getView(0);
                Element root = rTextArea.getDocument().getDefaultRootElement();
                int lineCount = root.getElementCount();
                int topPosition = rTextArea.viewToModel2D(visibleRect.getLocation());
                int topLine = root.getElementIndex(topPosition);
                Rectangle visibleEditorRect = ui.getVisibleEditorRect();
                @SuppressWarnings("all")
                Rectangle r = LineNumberList.getChildViewBounds(v, topLine, visibleEditorRect);
                int y = r.y;

                int visibleBottom =  visibleRect.y + visibleRect.height;

                // Keep painting lines until our y-coordinate is past the visible
                // end of the text area.

                while (y < visibleBottom) {
                    r = getChildViewBounds(v, topLine, visibleEditorRect);

                    // Paint the line number.
                    paintLineNumber(g, metrics, rhs, y+ascent, topLine + 1);

                    // The next possible y-coordinate is just after the last line
                    // painted.
                    y += r.height;

                    // Update topLine (we're actually using it for our "current line"
                    // variable now).
                    if (fm != null) {
                        Fold fold = fm.getFoldForLine(topLine);
                        if (fold != null && fold.isCollapsed()) {
                            topLine += fold.getCollapsedLineCount();
                        }
                    }

                    topLine++;
                    if (topLine >= lineCount) {
                        break;
                    }
                }
            } else {
                textAreaInsets = rTextArea.getInsets(textAreaInsets);

                if (visibleRect.y < textAreaInsets.top) {
                    visibleRect.height -= textAreaInsets.top - visibleRect.y;
                    visibleRect.y = textAreaInsets.top;
                }

                int topLine = (visibleRect.y - textAreaInsets.top) / cellHeight;
                int actualTopY = topLine * cellHeight + textAreaInsets.top;
                int y = actualTopY + ascent;

                // Get the actual first line to paint, taking into account folding.
                topLine += fm.getHiddenLineCountAbove(topLine, true);

                // Paint line numbers
                g.setColor(getForeground());

                int line = topLine + 1;

                while (y < visibleRect.y + visibleRect.height + ascent && line <= rTextArea.getLineCount()) {
                    paintLineNumber(g, metrics, rhs, y, line);

                    y += cellHeight;

                    Fold fold = fm.getFoldForLine(line - 1);
                    int hiddenLineCount;
                    // Skip to next line to paint, taking extra care for lines with
                    // block ends and begins together, e.g. "} else {"
                    while (fold != null && fold.isCollapsed()) {
                        hiddenLineCount = fold.getLineCount();
                        if (hiddenLineCount == 0) {
                            // Fold parser identified a 0-line fold region... This
                            // is really a bug, but we'll handle it gracefully.
                            break;
                        }
                        line += hiddenLineCount;
                        fold = fm.getFoldForLine(line - 1);
                    }

                    line++;
                }
            }
        }

        protected void paintLineNumber(Graphics g, FontMetrics metrics, int x, int y, int lineNumber) {
            int originalLineNumber = originalLineNumberOf(lineNumber);

            if (originalLineNumber != 0) {
                String number = Integer.toString(originalLineNumber);
                int strWidth = metrics.stringWidth(number);
                if (isBreakpointLineNumber(originalLineNumber)) {
                    paintBreakpointMarker(g, metrics, y);
                }
                if (currentExecutionTextAreaLine > 0 && lineNumber == currentExecutionTextAreaLine) {
                    paintExecutionArrow(g, metrics, x - strWidth, y);
                }
                g.setColor(showMisalignment && lineNumber != originalLineNumber ? errorForeground : getForeground());
                g.drawString(number, x-strWidth, y);
            }
        }

        private int originalLineNumberOf(int lineNumber) {
            if (lineNumberMap == null) {
                return lineNumber;
            }
            int originalLineNumber = lineNumber < lineNumberMap.length ? lineNumberMap[lineNumber] : 0;
            return originalLineNumber == 0 && isBreakpointLineNumber(lineNumber) ? lineNumber : originalLineNumber;
        }

        private void paintBreakpointMarker(Graphics g, FontMetrics metrics, int y) {
            if (BREAKPOINT_ICON != null) {
                int markerY = y - metrics.getAscent() + (metrics.getHeight() - 16) / 2;
                g.drawImage(BREAKPOINT_ICON, 1, markerY, null);
                return;
            }
            int markerSize = Math.clamp((long) metrics.getHeight() - 2, 8, 12);
            int markerY = y - metrics.getAscent() + (metrics.getHeight() - markerSize) / 2;
            g.setColor(new Color(42, 122, 198));
            g.fillOval(3, markerY, markerSize, markerSize);
            g.setColor(new Color(16, 78, 156));
            g.drawOval(3, markerY, markerSize, markerSize);
            g.setColor(new Color(126, 190, 235));
            g.fillOval(5, markerY + 2, Math.max(2, markerSize / 3), Math.max(2, markerSize / 3));
        }

        /** A solid right-pointing arrow (Eclipse-style execution pointer) that ends at {@code right}. */
        private void paintExecutionArrow(Graphics g, FontMetrics metrics, int right, int y) {
            g.setColor(new Color(0, 128, 0));
            int mid = y - metrics.getAscent() + metrics.getHeight() / 2;
            int ah = Math.max(4, metrics.getHeight() / 3);
            int ax = right - ah - 3;
            int[] px = {ax, ax + ah, ax};
            int[] py = {mid - ah / 2, mid, mid + ah / 2};
            g.fillPolygon(px, py, 3);
        }

        @Override
        public Dimension getPreferredSize() {
            if (preferredSize == null) {
                int lineCount = getMaximumSourceLineNumber();

                if (lineCount > 0) {
                    Font font = getFont();
                    FontMetrics fontMetrics = getFontMetrics(font);
                    int count = 1;

                    while (lineCount >= 10) {
                        lineCount = lineCount / 10;
                        count++;
                    }

                    int preferredWidth = fontMetrics.charWidth('9') * count + 24;
                    preferredSize = new Dimension(preferredWidth, 0);
                } else {
                    preferredSize = new Dimension(0, 0);
                }
            }

            return preferredSize;
        }
    }
}
