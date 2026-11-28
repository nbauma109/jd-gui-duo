package org.jd.gui.view.component;

import jd.core.links.HyperlinkData;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomLineNumbersPageTest {

    private static final String SOURCE = "first();\nsecond();\nthird();\nfourth();\nfifth();\nsixth();";
    private static final Font NUMBER_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 18);
    private static final Color BACKGROUND = Color.WHITE;
    private static final Color FOREGROUND = Color.BLACK;
    private static final int COLOR_TOLERANCE = 40;

    @Test
    void ordinaryTextUsesPhysicalLineNumbers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            for (int line = 1; line <= 6; line++) {
                assertThat(page.getSourceLineNumber(line)).isEqualTo(line);
            }

            page.initLineNumbers();
            page.setShowMisalignment(true);
            BufferedImage image = paint(page);
            for (int line = 1; line <= 6; line++) {
                assertThat(page.getSourceLineNumber(line)).isEqualTo(line);
                assertNumber(row(page, image, line), FOREGROUND, line);
            }
        });
    }

    @Test
    void misalignedRowsDisplayTheirOriginalSourceNumbers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            page.setShowMisalignment(false);
            BufferedImage identity = paint(page);
            int[] sourceTwo = row(page, identity, 2);
            int[] sourceFour = row(page, identity, 4);
            assertThat(sourceTwo).isNotEqualTo(sourceFour);

            setMisalignedLineNumbers(page);
            BufferedImage withoutWarning = paint(page);

            assertThat(row(page, withoutWarning, 2)).containsExactly(sourceFour);
            assertThat(row(page, withoutWarning, 4)).containsExactly(sourceTwo);
            assertThat(row(page, withoutWarning, 5)).containsExactly(sourceFour);
            assertBlank(row(page, withoutWarning, 3));
            assertThat(page.getSourceLineNumber(2)).isEqualTo(4);
            assertThat(page.getSourceLineNumber(3)).isZero();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyMisalignedSourceNumbersUseTheErrorColor(boolean showMisalignment) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            setMisalignedLineNumbers(page);
            page.setShowMisalignment(showMisalignment);
            BufferedImage image = paint(page);
            Color expected = FOREGROUND;
            if (showMisalignment) {
                expected = Color.RED;
            }

            assertNumber(row(page, image, 1), FOREGROUND, 1);
            assertNumber(row(page, image, 2), expected, 2);
            assertBlank(row(page, image, 3));
            assertNumber(row(page, image, 4), expected, 4);
            assertNumber(row(page, image, 5), expected, 5);
            assertNumber(row(page, image, 6), FOREGROUND, 6);
        });
    }

    private static void setMisalignedLineNumbers(TestPage page) {
        page.clearLineNumbers();
        page.setLineNumber(1, 1);
        page.setLineNumber(2, 4);
        page.setLineNumber(4, 2);
        page.setLineNumber(5, 4);
        page.setLineNumber(6, 6);
    }

    @Test
    void changingTheWarningColorAndDisablingWarningsPreservesTheNumbers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.setLineNumber(1, 1);
            page.setLineNumber(2, 7);
            page.setLineNumber(3, 3);
            page.setShowMisalignment(true);
            page.setErrorForeground(Color.BLUE);
            BufferedImage warning = paint(page);

            assertNumber(row(page, warning, 1), FOREGROUND, 1);
            assertNumber(row(page, warning, 2), Color.BLUE, 2);
            assertNumber(row(page, warning, 3), FOREGROUND, 3);
            assertThat(page.getSourceLineNumber(2)).isEqualTo(7);

            page.setShowMisalignment(false);
            BufferedImage normal = paint(page);
            assertNumber(row(page, normal, 2), FOREGROUND, 2);
            assertThat(page.getSourceLineNumber(2)).isEqualTo(7);
            page.setShowMisalignment(true);
            assertThat(row(page, paint(page), 2)).containsExactly(row(page, warning, 2));
        });
    }

    @Test
    void absentMappingsAndClearedMappingsDoNotDisplayPhysicalNumbers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.setLineNumber(2, 8);
            page.setLineNumber(4, 0);
            BufferedImage image = paint(page);

            assertBlank(row(page, image, 1));
            assertNumber(row(page, image, 2), Color.RED, 2);
            for (int line = 3; line <= 6; line++) {
                assertBlank(row(page, image, line));
                assertThat(page.getSourceLineNumber(line)).isZero();
            }

            page.clearLineNumbers();
            BufferedImage cleared = paint(page);
            for (int line = 1; line <= 6; line++) {
                assertBlank(row(page, cleared, line));
            }
            page.setLineNumber(2, 2);
            assertNumber(row(page, paint(page), 2), FOREGROUND, 2);
        });
    }

    @Test
    void aTrailingNewlineDoesNotCreateAnOriginalSourceNumber() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage("first();\nsecond();\n");
            page.initLineNumbers();
            BufferedImage image = paint(page);

            assertNumber(row(page, image, 1), FOREGROUND, 1);
            assertNumber(row(page, image, 2), FOREGROUND, 2);
            assertBlank(row(page, image, 3));
            assertThat(page.getSourceLineNumber(3)).isZero();
        });
    }

    @Test
    void wrappedLinesDisplayTheirSourceNumberOnlyOnTheFirstVisualRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage("first();\n" + "longStatement(); ".repeat(30) + "\nthird();");
            page.setLineNumber(1, 1);
            page.setLineNumber(2, 5);
            page.setLineNumber(3, 3);
            page.textArea.setLineWrap(true);
            page.textArea.setWrapStyleWord(true);
            BufferedImage image = paint(page);

            int height = page.textArea.getLineHeight();
            int second = rowTop(page, 2);
            int third = rowTop(page, 3);
            assertThat(third - second).isGreaterThan(height);
            assertNumber(row(page, image, 1), FOREGROUND, 1);
            assertNumber(row(page, image, 2), Color.RED, 2);
            for (int top = second + height; top < third; top += height) {
                assertBlank(pixels(image, top, height));
            }
            assertNumber(row(page, image, 3), FOREGROUND, 3);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCollapsedFoldKeepsTheNumbersAndColorsOfTheVisibleSourceRows(boolean wrap) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage("class Sample {\n    int field;\n    int other;\n}\n// after");
            page.textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            page.textArea.setCodeFoldingEnabled(true);
            page.textArea.setLineWrap(wrap);
            page.setLineNumber(1, 1);
            page.setLineNumber(2, 2);
            page.setLineNumber(3, 3);
            page.setLineNumber(4, 4);
            page.setLineNumber(5, 9);
            page.textArea.getFoldManager().reparse();
            Fold fold = page.textArea.getFoldManager().getFoldForLine(0);
            assertThat(fold).isNotNull();
            assertThat(fold.getEndLine()).isEqualTo(3);
            BufferedImage expanded = paint(page);
            int expandedTop = rowTop(page, 5);
            int[] lastNumber = row(page, expanded, 5);
            assertNumber(lastNumber, Color.RED, 5);

            fold.setCollapsed(true);
            BufferedImage collapsed = paint(page);
            int collapsedTop = rowTop(page, 5);
            assertThat(collapsedTop).isLessThan(expandedTop);
            assertThat(collapsedTop - rowTop(page, 1)).isEqualTo(page.textArea.getLineHeight());
            assertNumber(row(page, collapsed, 1), FOREGROUND, 1);
            assertThat(row(page, collapsed, 5)).containsExactly(lastNumber);

            fold.setCollapsed(false);
            BufferedImage restored = paint(page);
            assertThat(rowTop(page, 5)).isEqualTo(expandedTop);
            assertThat(row(page, restored, 5)).containsExactly(lastNumber);
        });
    }

    @Test
    void paintingAClippedRegionRetainsTheCorrectSourceNumberAndColor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            page.setLineNumber(4, 2);
            BufferedImage full = paint(page);
            int top = rowTop(page, 4);
            int height = page.textArea.getLineHeight();
            BufferedImage clipped = paint(page, new Rectangle(0, top, full.getWidth(), height));

            assertNumber(row(page, clipped, 4), Color.RED, 4);
            assertThat(row(page, clipped, 4)).containsExactly(row(page, full, 4));
        });
    }

    @Test
    void aBreakpointDoesNotReplaceTheMisalignedLineNumber() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.setLineNumber(1, 1);
            page.setLineNumber(2, 5);
            page.setLineNumber(3, 3);
            int[] before = row(page, paint(page), 2);
            assertNumber(before, Color.RED, 2);

            page.setBreakpointLineNumber(5, true);
            int[] withBreakpoint = row(page, paint(page), 2);
            assertThat(withBreakpoint).isNotEqualTo(before);
            assertThat(hasColor(withBreakpoint, Color.RED)).isTrue();
            assertThat(page.getBreakpointLines()).containsExactly(5);

            page.setBreakpointLineNumber(5, false);
            assertThat(row(page, paint(page), 2)).containsExactly(before);
        });
    }

    @Test
    void gutterClicksPreserveCaretNavigationAndOnlyLeftDoubleClicksToggleMappedBreakpoints() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.setLineNumber(2, 42);
            paint(page);
            CustomLineNumbersPage.SourceLineNumberList numbers = lineNumbers(page);

            pressLineNumber(page, numbers, 2, MouseEvent.BUTTON1, 1);
            assertThat(page.getCaretSourceLine()).isEqualTo(42);
            assertThat(page.getBreakpointLines()).isEmpty();

            pressLineNumber(page, numbers, 2, MouseEvent.BUTTON3, 2);
            assertThat(page.getBreakpointLines()).isEmpty();

            pressLineNumber(page, numbers, 1, MouseEvent.BUTTON1, 2);
            assertThat(page.getCaretSourceLine()).isZero();
            assertThat(page.getBreakpointLines()).isEmpty();

            pressLineNumber(page, numbers, 2, MouseEvent.BUTTON1, 2);
            assertThat(page.getBreakpointLines()).containsExactly(42);
            assertThat(page.getCaretSourceLine()).isEqualTo(42);

            pressLineNumber(page, numbers, 2, MouseEvent.BUTTON1, 2);
            assertThat(page.getBreakpointLines()).isEmpty();
        });
    }

    @Test
    void executionArrowAndBreakpointCanShareAMappedSourceRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.setLineNumber(1, 1);
            page.setLineNumber(2, 42);
            page.setLineNumber(3, 3);
            page.setBreakpointLineNumber(42, true);
            BufferedImage before = paint(page);

            page.setCurrentExecutionLine(42);
            BufferedImage executing = paint(page);
            Color executionArrow = new Color(0, 128, 0);
            assertThat(hasColor(row(page, executing, 2), executionArrow)).isTrue();
            assertThat(hasColor(row(page, executing, 2), Color.RED)).isTrue();
            assertThat(hasColor(row(page, executing, 1), executionArrow)).isFalse();
            assertThat(hasColor(row(page, executing, 3), executionArrow)).isFalse();
            assertThat(page.getBreakpointLines()).containsExactly(42);

            page.setCurrentExecutionLine(0);
            assertThat(row(page, paint(page), 2)).containsExactly(row(page, before, 2));
        });
    }

    @Test
    void gutterWidthWithoutMappingsTracksPhysicalLineCount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            BufferedImage before = paint(page);
            assertNumber(row(page, before, 2), FOREGROUND, 2);

            page.setText("statement();\n".repeat(15));
            BufferedImage expanded = paint(page);
            assertThat(expanded.getWidth()).isGreaterThan(before.getWidth());
            assertNumber(row(page, expanded, 10), FOREGROUND, 10);

            page.setText(SOURCE);
            assertThat(paint(page).getWidth()).isEqualTo(before.getWidth());
        });
    }

    @Test
    void detachedLineNumberListPaintsWithItsOwnBackgroundAndPreservesGraphicsState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            paint(page);
            CustomLineNumbersPage.SourceLineNumberList numbers = page.new SourceLineNumberList(page.textArea);
            numbers.setFont(NUMBER_FONT);
            numbers.setForeground(FOREGROUND);
            numbers.setBackground(BACKGROUND);
            numbers.setSize(numbers.getPreferredSize().width, page.textArea.getHeight());
            BufferedImage image = new BufferedImage(numbers.getWidth(), numbers.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.MAGENTA);
                Font originalFont = NUMBER_FONT.deriveFont(12f);
                graphics.setFont(originalFont);
                assertThat(graphics.getClip()).isNull();
                numbers.paintComponent(graphics);
                assertThat(graphics.getColor()).isEqualTo(Color.MAGENTA);
                assertThat(graphics.getFont()).isEqualTo(originalFont);
                assertThat(graphics.getClip()).isNull();
                assertThat(image.getRGB(0, 0)).isEqualTo(BACKGROUND.getRGB());
                assertThat(hasColor(pixels(image, 0, image.getHeight()), FOREGROUND)).isTrue();
            } finally {
                graphics.dispose();
            }
        });
    }

    @Test
    void paintingAndDoubleClickingBeforeTextAreaLayoutAreSafe() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            RSyntaxTextArea area = new RSyntaxTextArea(SOURCE);
            area.setSize(0, 0);
            CustomLineNumbersPage.SourceLineNumberList numbers = page.new SourceLineNumberList(area);
            numbers.setFont(NUMBER_FONT);
            numbers.setBackground(BACKGROUND);
            numbers.setSize(80, 80);

            BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setClip(0, 0, 80, 80);
                assertThat(area.viewToModel2D(new Point(0, 0))).isNegative();
                numbers.paintComponent(graphics);
                assertBlank(pixels(image, 0, image.getHeight()));
            } finally {
                graphics.dispose();
            }

            numbers.mousePressed(new MouseEvent(numbers, MouseEvent.MOUSE_PRESSED, 0L,
                    0, 2, 0, 2, false, MouseEvent.BUTTON1));
            assertThat(page.getBreakpointLines()).isEmpty();
        });
    }

    @Test
    void paintingWithAPlainTextAreaDoesNotAttemptSyntaxRowRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            RTextArea area = new RTextArea(SOURCE);
            CustomLineNumbersPage.SourceLineNumberList numbers = page.new SourceLineNumberList(area);
            numbers.setSize(80, 80);
            BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setClip(0, 0, 80, 80);
                numbers.paintComponent(graphics);
                assertThat(pixels(image, 0, 80)).containsOnly(0);
            } finally {
                graphics.dispose();
            }
        });
    }

    private static CustomLineNumbersPage.SourceLineNumberList lineNumbers(TestPage page) {
        for (Component child : page.scrollPane.getGutter().getComponents()) {
            if (child instanceof CustomLineNumbersPage.SourceLineNumberList numbers) {
                return numbers;
            }
        }
        throw new AssertionError("Source line number list not found");
    }

    private static void pressLineNumber(TestPage page, CustomLineNumbersPage.SourceLineNumberList numbers,
            int physicalLine, int button, int clickCount) {
        Point point = SwingUtilities.convertPoint(page.scrollPane.getGutter(),
                new Point(2, rowTop(page, physicalLine) + page.textArea.getLineHeight() / 2), numbers);
        numbers.mousePressed(new MouseEvent(numbers, MouseEvent.MOUSE_PRESSED, 0L,
                0, point.x, point.y, clickCount, false, button));
    }

    @Test
    void gutterWidthTracksChangesToOriginalSourceNumbers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            int initialWidth = paint(page).getWidth();

            page.setLineNumber(2, 12345);
            BufferedImage expanded = paint(page);
            assertThat(expanded.getWidth()).isGreaterThan(initialWidth);
            assertNumber(row(page, expanded, 2), Color.RED, 2);

            page.setLineNumber(2, 2);
            assertThat(paint(page).getWidth()).isEqualTo(initialWidth);

            page.setLineNumber(2, 12345);
            assertThat(paint(page).getWidth()).isGreaterThan(initialWidth);
            page.clearLineNumbers();
            assertThat(paint(page).getWidth()).isEqualTo(initialWidth);
        });
    }

    @Test
    void gutterWidthTracksLineNumberFontChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage(SOURCE);
            page.initLineNumbers();
            int initialWidth = paint(page).getWidth();

            Font largerFont = NUMBER_FONT.deriveFont(30f);
            page.textArea.setFont(largerFont);
            page.scrollPane.getGutter().setLineNumberFont(largerFont);
            assertThat(paint(page).getWidth()).isGreaterThan(initialWidth);

            page.textArea.setFont(NUMBER_FONT);
            page.scrollPane.getGutter().setLineNumberFont(NUMBER_FONT);
            assertThat(paint(page).getWidth()).isEqualTo(initialWidth);
        });
    }

    private static BufferedImage paint(TestPage page) {
        return paint(page, null);
    }

    private static BufferedImage paint(TestPage page, Rectangle clip) {
        page.setSize(640, 600);
        layout(page);
        Gutter gutter = page.scrollPane.getGutter();
        assertThat(gutter.getWidth()).isPositive();
        assertThat(gutter.getHeight()).isPositive();
        BufferedImage image = new BufferedImage(gutter.getWidth(), gutter.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setClip(0, 0, image.getWidth(), image.getHeight());
            if (clip != null) {
                graphics.clip(clip);
            }
            gutter.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }

    private static int rowTop(TestPage page, int physicalLine) {
        try {
            int offset = page.textArea.getLineStartOffset(physicalLine - 1);
            Rectangle2D bounds = page.textArea.modelToView2D(offset);
            assertThat(bounds).isNotNull();
            Point top = new Point(0, (int) bounds.getY());
            return SwingUtilities.convertPoint(page.textArea, top, page.scrollPane.getGutter()).y;
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private static int[] row(TestPage page, BufferedImage image, int physicalLine) {
        return pixels(image, rowTop(page, physicalLine), page.textArea.getLineHeight());
    }

    private static int[] pixels(BufferedImage image, int top, int height) {
        assertThat(top).isNotNegative();
        assertThat(top + height).isLessThanOrEqualTo(image.getHeight());
        return image.getRGB(0, top, image.getWidth(), height, null, 0, image.getWidth());
    }

    private static void assertBlank(int[] pixels) {
        assertThat(pixels).containsOnly(BACKGROUND.getRGB());
    }

    private static void assertNumber(int[] pixels, Color expected, int physicalLine) {
        assertThat(hasColor(pixels, expected)).as("line %s contains %s", physicalLine, expected).isTrue();
        for (Color unexpected : Arrays.asList(FOREGROUND, Color.RED, Color.BLUE)) {
            if (!expected.equals(unexpected)) {
                assertThat(hasColor(pixels, unexpected)).as("line %s does not contain %s", physicalLine, unexpected).isFalse();
            }
        }
    }

    private static boolean hasColor(int[] pixels, Color expected) {
        for (int pixel : pixels) {
            Color actual = new Color(pixel, true);
            if (actual.getAlpha() == 255 && isClose(actual, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClose(Color actual, Color expected) {
        return Math.abs(actual.getRed() - expected.getRed()) <= COLOR_TOLERANCE
                && Math.abs(actual.getGreen() - expected.getGreen()) <= COLOR_TOLERANCE
                && Math.abs(actual.getBlue() - expected.getBlue()) <= COLOR_TOLERANCE;
    }

    private static final class TestPage extends CustomLineNumbersPage {
        private static final long serialVersionUID = 1L;

        private TestPage(String source) {
            super(CompiledEntries.api(CompiledEntries.preferences()));
            textArea.setFont(NUMBER_FONT);
            setText(source);
            Gutter gutter = scrollPane.getGutter();
            gutter.setBackground(BACKGROUND);
            gutter.setBorderColor(BACKGROUND);
            gutter.setLineNumberColor(FOREGROUND);
            gutter.setCurrentLineNumberColor(FOREGROUND);
            gutter.setLineNumberFont(NUMBER_FONT);
            gutter.setFoldIndicatorEnabled(false);
            gutter.setIconRowHeaderEnabled(false);
            gutter.setLineNumbersEnabled(true);
        }

        @Override
        protected void toggleBreakpointLineNumber(int sourceLineNumber) {
            setBreakpointLineNumber(sourceLineNumber, !isBreakpointLineNumber(sourceLineNumber));
        }

        @Override
        protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) {
            return false;
        }

        @Override
        protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
            throw new AssertionError("No hyperlink is enabled in this fixture");
        }
    }
}
