/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.gui.util.decompiler.GuiPreferences;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

/** The page of a class file: the source that each decompiler gives, its line numbers, breakpoints and preferences. */
class ClassFilePagesTest {

    private static final String SOURCE = """
            package demo;

            public class Sample {
                static final java.util.List<String> NAMES = new java.util.ArrayList<>();
                static {
                    NAMES.add("first");
                }
                private int count;
                public Sample(int count) {
                    this.count = count;
                }
                public int twice(int[] values, String name) {
                    int total = count * 2;
                    for (int value : values) {
                        total += value;
                    }
                    return total + name.length();
                }
                public Runnable task() {
                    return () -> {
                        System.out.println(count);
                    };
                }
            }
            """;

    @TempDir
    static Path directory;

    private static Map<String, byte[]> classes;

    @BeforeAll
    static void compile() throws IOException {
        classes = CompiledEntries.compile(directory, Map.of("demo/Sample.java", SOURCE));
    }

    /** The line of the source, from 1, that holds the text. */
    private static int line(String text) {
        List<String> lines = SOURCE.lines().toList();
        return 1 + IntStream.range(0, lines.size()).filter(i -> lines.get(i).contains(text)).findFirst().orElseThrow();
    }

    private static ClassFilePage page(Map<String, String> preferences) {
        return new ClassFilePage(CompiledEntries.api(preferences), CompiledEntries.archive(classes, "demo/Sample.class"));
    }

    private static Map<String, String> preferences(String... keysAndValues) {
        Map<String, String> preferences = CompiledEntries.preferences();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            preferences.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return preferences;
    }

    private static String saved(ClassFilePage page, Map<String, String> preferences) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        page.save(CompiledEntries.api(preferences), out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENGINE_JD_CORE_V1", "ENGINE_JD_CORE_V0", "ENGINE_CFR", "ENGINE_PROCYON", "ENGINE_FERNFLOWER", "ENGINE_VINEFLOWER", "ENGINE_JADX"})
    void everyDecompilerShowsAndSavesTheClassWithOrWithoutRealignedLines(String engineField) throws ReflectiveOperationException {
        String engine = (String) com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.class.getField(engineField).get(null);
        for (String realign : List.of("false", "true")) {
            Map<String, String> preferences = preferences(GuiPreferences.DECOMPILE_ENGINE, engine, "StretchLines", realign);

            ClassFilePage page = page(preferences);

            assertThat(page.getText()).as(engine + " " + realign).contains("class Sample", "twice");
            assertThat(page.hyperlinks).as(engine + " " + realign).isNotNull();
            assertThat(saved(page, preferences)).contains("class Sample", "twice");
        }
    }

    @Test
    void removingTheCastsKeepsTheSource() {
        Map<String, String> preferences = preferences(GuiPreferences.REMOVE_UNNECESSARY_CASTS, "true");

        assertThat(page(preferences).getText()).contains("class Sample", "twice");
    }

    @Test
    void theLinesOfTheSourceCanBeReached() throws Exception {
        ClassFilePage page = page(preferences());
        int target = line("return total");

        assertThat(page.getMaximumLineNumber()).isGreaterThanOrEqualTo(target);
        assertThat(page.checkLineNumber(target)).isTrue();
        assertThat(page.checkLineNumber(page.getMaximumLineNumber() + 1)).isFalse();
        SwingUtilities.invokeAndWait(() -> {
            page.textArea.setSize(800, 600);
            page.goToLineNumber(target);
        });
        int[] caretLine = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                caretLine[0] = page.textArea.getLineOfOffset(page.textArea.getCaretPosition());
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(page.getText().split("\n")[caretLine[0]]).contains("return total");
    }

    @ParameterizedTest(name = "the line of \"{0}\" is in {1}")
    @CsvSource(delimiter = '|', value = {
            "NAMES.add          | static {...}",
            "this.count = count | Sample(int)",
            "total += value     | twice(int[], String)",
            "System.out.println | task()"})
    void aBreakpointIsDescribedByItsMethod(String code, String method) {
        assertThat(page(preferences()).getBreakpointMethodDescription(line(code))).isEqualTo(method);
    }

    @Test
    void aLineWithoutCodeHasNoMethod() {
        ClassFilePage page = page(preferences());

        assertThat(page.getBreakpointMethodDescription(1)).isNull();
        assertThat(page.getDebugTypeName()).isEqualTo("demo.Sample");
    }

    @Test
    void aBreakpointIsToggledAsTheDebuggerAnswers() {
        ClassFilePage page = page(preferences());
        int target = line("total += value");
        List<String> asked = new ArrayList<>();
        List<Boolean> answers = new ArrayList<>(List.of(true, false, false));
        page.setBreakpointToggleHandler((type, line) -> {
            asked.add(type + ":" + line);
            return answers.removeFirst();
        });

        page.toggleBreakpointLineNumber(target);
        assertThat(page.isBreakpointLineNumber(target)).isTrue();
        // the debugger says there is no breakpoint any longer
        page.toggleBreakpointLineNumber(target);
        assertThat(page.isBreakpointLineNumber(target)).isFalse();
        // the debugger refuses it: it is not shown
        page.toggleBreakpointLineNumber(target);
        assertThat(page.isBreakpointLineNumber(target)).isFalse();
        assertThat(asked).containsOnly("demo.Sample:" + target).hasSize(3);

        page.setBreakpointToggleHandler(null);
        page.toggleBreakpointLineNumber(target);
        assertThat(page.isBreakpointLineNumber(target)).isTrue();
        page.setBreakpointLine(target, false);
        assertThat(page.isBreakpointLineNumber(target)).isFalse();
    }

    @Test
    void theClassIsDecompiledAgainWhenThePreferencesOfTheDecompilerChange() throws Exception {
        Map<String, String> preferences = preferences();
        ClassFilePage page = page(preferences);
        String before = page.getText();

        Map<String, String> changed = preferences(GuiPreferences.DECOMPILE_ENGINE, com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_CFR);
        SwingUtilities.invokeAndWait(() -> page.preferencesChanged(changed));

        assertThat(page.getText()).isNotEqualTo(before).contains("class Sample");
        // only the preferences of the parser, and then nothing
        String decompiled = page.getText();
        changed.put(GuiPreferences.SHOW_COMPILER_WARNINGS, "true");
        SwingUtilities.invokeAndWait(() -> page.preferencesChanged(changed));
        SwingUtilities.invokeAndWait(() -> page.preferencesChanged(changed));
        assertThat(page.getText()).isEqualTo(decompiled);
    }

    @Test
    void theLineNumbersAndTheBreakpointsArePainted() throws Exception {
        ClassFilePage page = page(preferences());
        int target = line("total += value");
        BufferedImage without = paintLineNumbers(page);

        page.setBreakpointLine(target, true);
        BufferedImage with = paintLineNumbers(page);

        assertThat(colors(without)).isGreaterThan(1);
        assertThat(pixels(with)).isNotEqualTo(pixels(without));
    }

    private static BufferedImage paintLineNumbers(ClassFilePage page) throws Exception {
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            page.setSize(800, 600);
            layout(page);
            java.awt.Component numbers = page.scrollPane.getRowHeader().getView();
            image[0] = new BufferedImage(numbers.getWidth(), numbers.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image[0].createGraphics();
            try {
                graphics.setClip(0, 0, numbers.getWidth(), numbers.getHeight());
                numbers.paint(graphics);
            } finally {
                graphics.dispose();
            }
        });
        return image[0];
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static long colors(BufferedImage image) {
        return IntStream.of(pixels(image)).distinct().count();
    }

    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layout(nested);
            }
        }
    }

    @Test
    void thePageIsJavaAndIsNamedAfterItsSource() {
        ClassFilePage page = page(preferences());

        assertThat(page.getSyntaxStyle()).isEqualTo("text/java");
        assertThat(page.getFileName()).isEqualTo("demo/Sample.java");
    }

    @Test
    void aClassThatCannotBeReadShowsAndSavesAnError() {
        Map<String, String> preferences = preferences();
        ClassFilePage page = new ClassFilePage(CompiledEntries.api(preferences),
                CompiledEntries.archive(Map.of("demo/Broken.class", new byte[] {1, 2, 3}), "demo/Broken.class"));

        assertThat(page.getText()).contains("ERROR");
        assertThat(saved(page, preferences)).contains("ERROR");
        assertThat(page.getBreakpointMethodDescription(1)).isNull();
    }
}
