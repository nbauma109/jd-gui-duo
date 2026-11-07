package org.jd.gui.view.component;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.BreakpointCapablePage;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.ContainerFactory;
import org.jd.gui.spi.FileLoader;
import org.jd.gui.spi.Indexer;
import org.jd.gui.spi.PanelFactory;
import org.jd.gui.spi.SourceSaver;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.spi.UriLoader;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import jd.core.links.HyperlinkData;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that the execution-line highlight drawn by {@link CustomLineNumbersPage}
 * actually renders with the Eclipse green color {@code #C6DBAE}.
 *
 * <p>This test creates a concrete {@link CustomLineNumbersPage} subclass, sets a
 * multi-line text body, calls {@code setCurrentExecutionLine}, renders the component
 * off-screen into a {@link BufferedImage}, and samples the pixel colour at the
 * target line row to assert it matches the expected green.
 */
class ExecutionLineHighlightTest {

    private static final Color EXPECTED_COLOR = new Color(0xC6, 0xDB, 0xAE);
    private static final int TARGET_SOURCE_LINE = 5;   // the source line we will highlight
    private static final int COLOR_TOLERANCE = 20;      // per-channel tolerance

    // ---------------------------------------------------------------------------
    // Minimal no-op API
    // ---------------------------------------------------------------------------
    private static final API MOCK_API = new API() {
        private final Map<String, String> prefs = new HashMap<>();

        @Override public boolean openURI(URI uri) { return false; }
        @Override public boolean openURI(int x, int y, Collection<Container.Entry> entries, String query, String fragment) { return false; }
        @Override public void addURI(URI uri) { /* no-op */ }
        @Override public void compareFiles(URI file1, URI file2) { /* no-op */ }
        @Override public <T extends JComponent & org.jd.gui.api.feature.UriGettable> void addPanel(File file, String title, Supplier<Icon> iconSupplier, String tip, T component) { /* no-op */ }
        @Override public Collection<Action> getContextualActions(Container.Entry entry, String fragment) { return Collections.emptyList(); }
        @Override public UriLoader getUriLoader(URI uri) { return null; }
        @Override public FileLoader getFileLoader(File file) { return null; }
        @Override public ContainerFactory getContainerFactory(Path rootPath) { return null; }
        @Override public PanelFactory getMainPanelFactory(Container container) { return null; }
        @Override public TreeNodeFactory getTreeNodeFactory(Container.Entry entry) { return null; }
        @Override public TypeFactory getTypeFactory(Container.Entry entry) { return null; }
        @Override public Indexer getIndexer(Container.Entry entry) { return null; }
        @Override public SourceSaver getSourceSaver(Container.Entry entry) { return null; }
        @Override public Map<String, String> getPreferences() { return prefs; }
        @Override public Collection<Indexes> getCollectionOfIndexes() { return Collections.emptyList(); }
        @Override public Collection<Future<Indexes>> getCollectionOfFutureIndexes() { return Collections.emptyList(); }
        @Override public String getSource(Container.Entry entry) { return null; }
        @Override public void loadSource(Container.Entry entry, LoadSourceListener listener) { /* no-op */ }
        @Override public File loadSourceFile(Container.Entry entry) { return null; }
        @Override public void repaint() { /* no-op */ }
        @Override public boolean isDarkMode() { return false; }
        @Override public void syncBreakpointPage(BreakpointCapablePage page) { /* no-op */ }
    };

    // ---------------------------------------------------------------------------
    // Minimal concrete subclass (CustomLineNumbersPage is abstract)
    // ---------------------------------------------------------------------------
    private static class TestPage extends CustomLineNumbersPage {
        private static final long serialVersionUID = 1L;

        TestPage() {
            super(MOCK_API);
        }

        /** Exposes the protected {@code textArea} field for inspection. */
        org.fife.ui.rsyntaxtextarea.RSyntaxTextArea getTextArea() {
            return textArea;
        }

        void setTextContent(String text) {
            setText(text);
            initLineNumbers();
        }

        @Override
        protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) { return false; }

        @Override
        protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) { /* no-op */ }
    }

    /** Lets the events that are already queued (layout, repaint, scroll) run: the EDT is flushed a few times. */
    private static void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            SwingUtilities.invokeAndWait(() -> { /* flush */ });
        }
    }

    // ---------------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------------
    @Test
    void testExecutionLineColorIsGreen() throws Exception {
        runScenario(false, false);
    }

    /** Real flow: openURI puts the caret on the target line before the highlight is set. */
    @Test
    void testGreenWhenCaretMovedBeforeHighlight() throws Exception {
        runScenario(true, false);
    }

    /** Caret lands on the target line after the highlight is set (invokeLater race). */
    @Test
    void testGreenWhenCaretMovedAfterHighlight() throws Exception {
        runScenario(false, true);
    }

    private void runScenario(boolean caretBefore, boolean caretAfter) throws Exception {
        Assumptions.assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "Skipping GUI rendering test in headless environment"
        );

        // Build 20-line sample text
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            sb.append("// Line ").append(i).append("\n");
        }
        final String sampleText = sb.toString();

        final TestPage[] pageHolder   = new TestPage[1];
        final JFrame[]   frameHolder  = new JFrame[1];

        // ── Step 1: create + show the frame on the EDT ────────────────────────
        SwingUtilities.invokeAndWait(() -> {
            TestPage page = new TestPage();
            page.setTextContent(sampleText);

            JFrame frame = new JFrame("ExecutionLineHighlightTest");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setSize(800, 400);
            frame.add(page);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            pageHolder[0]  = page;
            frameHolder[0] = frame;
        });

        // Give the component time to be laid out and painted initially.
        settle();

        // ── Step 2: set the execution line ────────────────────────────────────
        SwingUtilities.invokeAndWait(() -> {
            TestPage p = pageHolder[0];
            try {
                int off = p.getTextArea().getLineStartOffset(TARGET_SOURCE_LINE - 1);
                if (caretBefore) {
                    p.getTextArea().setCaretPosition(off);
                }
                p.setCurrentExecutionLine(TARGET_SOURCE_LINE);
                if (caretAfter) {
                    SwingUtilities.invokeLater(() -> p.getTextArea().setCaretPosition(off));
                }
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });

        // Let any invokeLater scroll/repaint tasks settle.
        settle();

        // ── Step 3: render to BufferedImage and sample ────────────────────────
        final int[]         lineYHolder = new int[1];
        final int[]         lineHHolder = new int[1];
        final BufferedImage[] imgHolder = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() -> {
            TestPage page = pageHolder[0];
            org.fife.ui.rsyntaxtextarea.RSyntaxTextArea ta = page.getTextArea();

            // Locate the row bounds for TARGET_SOURCE_LINE (1-based) using the
            // text-area's document model.  The target line maps 1:1 (initLineNumbers).
            try {
                int offset = ta.getLineStartOffset(TARGET_SOURCE_LINE - 1); // 0-based API
                Rectangle2D r = ta.modelToView2D(offset);
                if (r != null) {
                    lineYHolder[0] = (int) r.getY() + ta.getY(); // relative to page
                    lineHHolder[0] = (int) r.getHeight();
                }
            } catch (javax.swing.text.BadLocationException ex) {
                ex.printStackTrace();
            }

            // Render the whole page panel into an image
            Rectangle bounds = page.getBounds();
            BufferedImage img = new BufferedImage(
                Math.max(bounds.width,  1),
                Math.max(bounds.height, 1),
                BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g2 = img.createGraphics();
            page.paintAll(g2);
            g2.dispose();
            imgHolder[0] = img;
        });

        // Dispose the frame
        SwingUtilities.invokeLater(() -> frameHolder[0].dispose());

        // ── Step 4: assert ────────────────────────────────────────────────────
        BufferedImage img    = imgHolder[0];
        int lineY            = lineYHolder[0];
        int lineH            = lineHHolder[0];

        if (img == null || lineH == 0) {
            fail("Component was not rendered (modelToView2D returned null). " +
                 "Check that the frame was shown before sampling.");
        }

        // Sample the pixel in the middle of the execution line row,
        // at x = 200 (well inside the text area, past the gutter).
        int sampleX = Math.min(200, img.getWidth() - 1);
        int sampleY = lineY + lineH / 2;
        sampleY = Math.max(0, Math.min(sampleY, img.getHeight() - 1));

        Color sampled = new Color(img.getRGB(sampleX, sampleY));

        System.out.printf("ExecutionLineHighlightTest: sampled (#%02X%02X%02X) at (%d,%d), " +
                          "expected (#%02X%02X%02X)%n",
            sampled.getRed(),   sampled.getGreen(),   sampled.getBlue(),
            sampleX, sampleY,
            EXPECTED_COLOR.getRed(), EXPECTED_COLOR.getGreen(), EXPECTED_COLOR.getBlue());

        // Also dump a horizontal slice so we can see what the full row looks like
        System.out.print("Row color sample (x=50..400, step=50): ");
        for (int x = 50; x <= Math.min(400, img.getWidth() - 1); x += 50) {
            Color c = new Color(img.getRGB(x, sampleY));
            System.out.printf("[%d=#%02X%02X%02X] ", x, c.getRed(), c.getGreen(), c.getBlue());
        }
        System.out.println();

        int dr = Math.abs(sampled.getRed()   - EXPECTED_COLOR.getRed());
        int dg = Math.abs(sampled.getGreen() - EXPECTED_COLOR.getGreen());
        int db = Math.abs(sampled.getBlue()  - EXPECTED_COLOR.getBlue());

        if (dr > COLOR_TOLERANCE || dg > COLOR_TOLERANCE || db > COLOR_TOLERANCE) {
            fail(String.format(
                "Execution-line color is WRONG!%n" +
                "  Sampled : #%02X%02X%02X (R=%d G=%d B=%d)%n" +
                "  Expected: #%02X%02X%02X (R=%d G=%d B=%d)%n" +
                "  Δ: R=%d G=%d B=%d (tolerance=%d)%n" +
                "  Sampled at pixel (%d, %d) in the rendered image (size %dx%d).%n" +
                "  lineY=%d lineH=%d",
                sampled.getRed(),        sampled.getGreen(),        sampled.getBlue(),
                sampled.getRed(),        sampled.getGreen(),        sampled.getBlue(),
                EXPECTED_COLOR.getRed(), EXPECTED_COLOR.getGreen(), EXPECTED_COLOR.getBlue(),
                EXPECTED_COLOR.getRed(), EXPECTED_COLOR.getGreen(), EXPECTED_COLOR.getBlue(),
                dr, dg, db, COLOR_TOLERANCE,
                sampleX, sampleY, img.getWidth(), img.getHeight(),
                lineY, lineH
            ));
        }
    }
}
