/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.service.type.ClassFileTypeFactoryProvider;
import org.jd.gui.spi.TypeFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Cursor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;

import static org.assertj.core.api.Assertions.assertThat;

/** Finding one's way in the page of a source: fragments, searches, links, line numbers and breakpoints. */
class PageNavigationTest {

    private static final String SOURCE = """
            package demo;

            public class Child extends Base {
                private int counter;
                private String label = "needle in a string";

                public Child() {
                    counter = 1;
                }

                public int count(int step) {
                    // a comment
                    inherited(step);
                    shared = label.length();
                    return counter + step;
                }

                public static class Inner {
                    void run() {
                        new Child().count(2);
                    }
                }
            }
            """;

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        classes = CompiledEntries.compile(directory, Map.of(
                "demo/Child.java", SOURCE,
                "demo/Base.java", "package demo; public class Base { protected int shared; protected void inherited(int value) {} }"));
    }

    private static final Map<String, String> PREFERENCES = new HashMap<>();

    private static ClassFilePage page() {
        PREFERENCES.clear();
        PREFERENCES.putAll(CompiledEntries.preferences());
        Container.Entry entry = CompiledEntries.archive(classes, "demo/Child.class");
        TypeFactory factory = new ClassFileTypeFactoryProvider();
        API api = (API) Proxy.newProxyInstance(PageNavigationTest.class.getClassLoader(), new Class<?>[] {API.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPreferences" -> PREFERENCES;
                    case "getTypeFactory" -> factory;
                    default -> null;
                });
        return new ClassFilePage(api, entry);
    }

    private static <T> T onSwing(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /** The texts that are highlighted once the page is opened at the URI (the fragment, or the query with its "?"). */
    private static List<String> open(ClassFilePage page, String suffix) throws Exception {
        onSwing(() -> {
            page.textArea.setSize(800, 600);
            return page.openUri(URI.create("file:/x.jar!/demo/Child.class" + suffix));
        });
        return onSwing(() -> page.textArea.getMarkAllHighlightRanges().stream().sorted()
                .map(range -> page.getText().substring(range.getStartOffset(), range.getEndOffset())).toList());
    }

    private static String lineOfCaret(ClassFilePage page) throws Exception {
        return onSwing(() -> {
            int line = page.textArea.getLineOfOffset(page.textArea.getCaretPosition());
            int start = page.textArea.getLineStartOffset(line);
            return page.textArea.getText(start, page.textArea.getLineEndOffset(line) - start).trim();
        });
    }

    @ParameterizedTest(name = "{0} highlights {1}")
    @CsvSource(delimiter = '|', value = {
            "#demo/Child                                              | Child",
            "#demo/Child-counter-I                                    | counter",
            "#demo/Child-count-(I)I                                   | count",
            "#*/Child-count-(?)?                                      | count",
            "?highlightFlags=td&highlightPattern=Inner                | Inner",
            "?highlightFlags=fd&highlightPattern=coun*                | counter",
            "?highlightFlags=md&highlightPattern=coun*                | count",
            "?highlightFlags=s&highlightPattern=needle                | needle",
            "?highlightFlags=cr&highlightPattern=Child                | Child",
            "?highlightFlags=mr&highlightPattern=inherited            | inherited",
            "?highlightFlags=fr&highlightPattern=shared               | shared",
            "?highlightFlags=md&highlightPattern=run&highlightScope=demo/Child$Inner | run",
            "?highlightFlags=md&highlightPattern=run&highlightScope=*/Child$Inner    | run"})
    void aFragmentOrASearchHighlightsWhatItNames(String suffix, String expected) throws Exception {
        assertThat(open(page(), suffix)).isNotEmpty().containsOnly(expected);
    }

    @ParameterizedTest
    @CsvSource({"#demo/Child-missing-I", "#demo/Nothing", "?highlightFlags=md&highlightPattern=run&highlightScope=demo/Child",
            "?highlightPattern=count", "?highlightFlags=md", "?position=100000"})
    void whatIsNotThereIsNotHighlighted(String suffix) throws Exception {
        assertThat(open(page(), suffix)).isEmpty();
    }

    @Test
    void aPageCanBeOpenedAtALineOrAtAPosition() throws Exception {
        ClassFilePage page = page();
        int offset = page.getText().indexOf("return ");

        open(page, "?position=" + offset);
        String returnLine = lineOfCaret(page);
        assertThat(returnLine).contains("return ");
        open(page, "?lineNumber=1");
        assertThat(lineOfCaret(page)).isNotEqualTo(returnLine);
        assertThat(onSwing(() -> page.openUri(URI.create("file:/x.jar!/demo/Child.class?lineNumber=x")))).isFalse();
        assertThat(onSwing(() -> page.openUri(URI.create("file:/x.jar!/demo/Child.class?position=x")))).isFalse();
    }

    @Test
    void theTypeThatHasTheCaretIsTheFocusedOne() throws Exception {
        ClassFilePage page = page();

        onSwing(() -> {
            page.textArea.setCaretPosition(page.getText().indexOf("void run"));
            return null;
        });

        assertThat(page.getFocusedTypeName()).isEqualTo("demo/Child$Inner");
        assertThat(page.getEntry().getPath()).isEqualTo("demo/Child.class");
        assertThat(page.getUri()).isEqualTo(URI.create("file:/x.jar!/demo/Child.class"));
    }

    @Test
    void theLinkToAnInheritedMemberLeadsToTheTypeThatDeclaresIt() {
        ClassFilePage page = page();
        // the entries of the index are the class files, read to look for the members
        page.indexesChanged(List.of(java.util.concurrent.CompletableFuture.completedFuture(name -> {
            Map<String, java.util.Collection> index = new HashMap<>();
            if ("typeDeclarations".equals(name)) {
                index.put("demo/Child", List.of(CompiledEntries.archive(classes, "demo/Child.class")));
                index.put("demo/Base", List.of(CompiledEntries.archive(classes, "demo/Base.class")));
            }
            return index;
        })));

        assertThat(page.hyperlinks.values()).map(link -> (HyperlinkReferenceData) link)
                .filteredOn(link -> "inherited".equals(link.getReference().getName()) || "shared".equals(link.getReference().getName()))
                .isNotEmpty()
                .allSatisfy(link -> {
                    assertThat(link.getReference().isEnabled()).isTrue();
                    assertThat(link.getReference().getTypeName()).isEqualTo("demo/Base");
                });
    }

    @Test
    void theTextCanBeSearchedForwardsAndBackwards() throws Exception {
        ClassFilePage page = page();
        org.jd.gui.api.feature.ContentSearchable.SearchType search = new org.jd.gui.api.feature.ContentSearchable.SearchType(false, false, false, true, true);

        assertThat(onSwing(() -> page.highlightText("counter", search))).isTrue();
        int first = onSwing(() -> page.textArea.getSelectionStart());
        onSwing(() -> {
            page.findNext("counter", search);
            return null;
        });
        int second = onSwing(() -> page.textArea.getSelectionStart());
        onSwing(() -> {
            page.findPrevious("counter", search);
            return null;
        });

        assertThat(second).isGreaterThan(first);
        assertThat(onSwing(() -> page.textArea.getSelectionStart())).isEqualTo(first);
        // before the first one, the search goes on from the end; after the last one, from the start
        onSwing(() -> {
            page.findPrevious("counter", search);
            return null;
        });
        assertThat(onSwing(() -> page.textArea.getSelectionStart())).isGreaterThan(second);
        onSwing(() -> {
            page.findNext("counter", search);
            return null;
        });
        assertThat(onSwing(() -> page.textArea.getSelectionStart())).isEqualTo(first);
        assertThat(onSwing(() -> page.highlightText("not there", search))).isFalse();
        assertThat(onSwing(() -> page.highlightText("c", search))).isTrue();
    }

    @Test
    void aDoubleClickHighlightsTheWordWhereverItIsUntilThisIsTurnedOff() throws Exception {
        ClassFilePage page = page();
        onSwing(() -> {
            int start = page.getText().indexOf("counter");
            page.textArea.select(start, start + "counter".length());
            page.textArea.dispatchEvent(new MouseEvent(page.textArea, MouseEvent.MOUSE_CLICKED, 0, 0, -10, -10, 2, false, MouseEvent.BUTTON1));
            return null;
        });

        assertThat(page.textArea.getMarkAllHighlightRanges()).hasSizeGreaterThan(1);
        Map<String, String> off = new HashMap<>(PREFERENCES);
        off.put(org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_ENABLED, "false");
        onSwing(() -> {
            page.preferencesChanged(off);
            return null;
        });
        assertThat(onSwing(() -> page.textArea.getMarkAllHighlightRanges())).isEmpty();
        onSwing(() -> {
            page.preferencesChanged(new HashMap<>(PREFERENCES));
            return null;
        });
        assertThat(onSwing(() -> page.textArea.getMarkAllHighlightRanges())).hasSizeGreaterThan(1);
    }

    @Test
    void theWheelWithTheControlKeyChangesTheSizeOfTheFont() throws Exception {
        ClassFilePage page = page();
        int size = page.textArea.getFont().getSize();

        wheel(page, InputEvent.CTRL_DOWN_MASK, -1);
        assertThat(page.textArea.getFont().getSize()).isGreaterThan(size);
        assertThat(PREFERENCES).containsEntry(org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY, String.valueOf(page.textArea.getFont().getSize()));
        wheel(page, InputEvent.CTRL_DOWN_MASK, 1);
        assertThat(page.textArea.getFont().getSize()).isEqualTo(size);
        wheel(page, 0, 1);
        assertThat(page.textArea.getFont().getSize()).isEqualTo(size);
    }

    private static void wheel(ClassFilePage page, int modifiers, int rotation) throws Exception {
        onSwing(() -> {
            page.textArea.setSize(800, 600);
            page.scrollPane.dispatchEvent(new MouseWheelEvent(page.scrollPane, MouseEvent.MOUSE_WHEEL, 0, modifiers, 20, 20, 0, false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
            return null;
        });
    }

    @Test
    void thePointerIsAHandOverALinkThatCanBeFollowed() throws Exception {
        ClassFilePage page = page();
        HyperlinkData link = page.hyperlinks.firstEntry().getValue();
        ((HyperlinkReferenceData) link).getReference().setEnabled(true);

        Cursor over = onSwing(() -> {
            page.textArea.setSize(800, 600);
            java.awt.geom.Rectangle2D box = page.textArea.modelToView2D(link.getStartPosition() + 1);
            move(page, (int) box.getCenterX(), (int) box.getCenterY());
            return page.textArea.getCursor();
        });
        Cursor elsewhere = onSwing(() -> {
            move(page, 1, 1);
            return page.textArea.getCursor();
        });

        assertThat(over.getType()).isEqualTo(Cursor.HAND_CURSOR);
        assertThat(elsewhere.getType()).isNotEqualTo(Cursor.HAND_CURSOR);
    }

    private static void move(ClassFilePage page, int x, int y) {
        page.textArea.dispatchEvent(new MouseEvent(page.textArea, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
    }

    @Test
    void aBreakpointIsSetAtTheCaretOnALineThatHasCode() throws Exception {
        ClassFilePage page = page();
        List<Integer> toggled = new ArrayList<>();
        page.setBreakpointToggleHandler((type, line) -> {
            toggled.add(line);
            return true;
        });

        int line = onSwing(() -> {
            page.textArea.setCaretPosition(page.getText().indexOf("return "));
            page.toggleBreakpointAtCaret();
            return page.getCaretSourceLine();
        });

        assertThat(line).isPositive();
        assertThat(toggled).containsExactly(line);
        assertThat(page.getBreakpointLines()).containsExactly(line);
        // the first line holds the package: no code is there
        assertThat(onSwing(() -> {
            page.textArea.setCaretPosition(0);
            page.toggleBreakpointAtCaret();
            return toggled.size();
        })).isEqualTo(1);
    }

    @ParameterizedTest(name = "\"{0}\" holds code: {1}")
    @CsvSource(delimiter = '|', value = {
            "/* 15 */     return value;      | true",
            "/*    */ /* b */ value++;       | true",
            "    value++; // done            | true",
            "/* 15 */                        | false",
            "/* 15 */     // only a comment  | false",
            "/* a comment that goes on       | false",
            "   * its next line              | false",
            "   its last line */             | true",
            "// return value;                | false"})
    void theCodeOfALineMayFollowItsComments(String text, boolean code) throws Exception {
        ClassFilePage page = page();

        int line = onSwing(() -> {
            page.textArea.setText(text);
            page.textArea.setCaretPosition(0);
            page.setLineNumber(1, 7);
            return page.getCaretSourceLine();
        });

        assertThat(line).isEqualTo(code ? 7 : 0);
    }

    @Test
    void theLineWhereTheExecutionStopsIsShownWithoutMovingTheCaret() throws Exception {
        ClassFilePage page = page();
        int line = onSwing(() -> {
            page.textArea.setCaretPosition(page.getText().indexOf("return "));
            int found = page.getCaretSourceLine();
            page.textArea.setCaretPosition(0);
            return found;
        });

        onSwing(() -> {
            page.textArea.setSize(800, 600);
            page.setCurrentExecutionLine(line);
            return null;
        });

        assertThat(page.textArea.getHighlightCurrentLine()).isFalse();
        assertThat(page.textArea.getCaretPosition()).isZero();
        onSwing(() -> {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setClip(0, 0, 800, 600);
            page.setSize(800, 600);
            page.scrollPane.setSize(800, 600);
            page.scrollPane.doLayout();
            page.scrollPane.getGutter().doLayout();
            java.awt.Component numbers = page.scrollPane.getRowHeader().getView();
            numbers.setSize(40, 600);
            numbers.paint(graphics);
            page.textArea.setLineWrap(true);
            numbers.paint(graphics);
            graphics.dispose();
            return null;
        });
        onSwing(() -> {
            page.setCurrentExecutionLine(0);
            return null;
        });
        assertThat(page.textArea.getHighlightCurrentLine()).isTrue();
    }

    @Test
    void nothingIsInspectedWhenNoProgramIsStopped() throws Exception {
        ClassFilePage page = page();
        List<String> inspected = new ArrayList<>();
        onSwing(() -> {
            page.inspectSelection();
            page.setDebugExpressionHandlers(() -> true, (expression, point) -> inspected.add(expression), inspected::add, line -> inspected.add("line " + line));
            page.textArea.setCaretPosition(0);
            // the page is not on screen: there is nowhere to show the value
            page.inspectSelection();
            return null;
        });

        assertThat(inspected).isEmpty();
    }
}
