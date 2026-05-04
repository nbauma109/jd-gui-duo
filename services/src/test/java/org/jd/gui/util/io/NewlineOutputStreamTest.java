/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewlineOutputStreamTest {

    private static String write(byte[] data) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (NewlineOutputStream out = new NewlineOutputStream(sink)) {
            out.write(data);
        }
        return sink.toString(StandardCharsets.UTF_8);
    }

    private static String writeSingle(String text) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (NewlineOutputStream out = new NewlineOutputStream(sink)) {
            for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
                out.write(b & 0xFF);
            }
        }
        return sink.toString(StandardCharsets.UTF_8);
    }

    @Test
    void write_byteArray_replacesNewlinesWithSystemLineSeparator() throws IOException {
        String input = "line1\nline2\nline3";
        String expected = "line1" + System.lineSeparator() + "line2" + System.lineSeparator() + "line3";

        assertEquals(expected, write(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void write_singleByte_replacesNewlineWithSystemLineSeparator() throws IOException {
        String expected = "a" + System.lineSeparator() + "b";
        assertEquals(expected, writeSingle("a\nb"));
    }

    @Test
    void write_noNewlines_passesDataThrough() throws IOException {
        String input = "hello world";
        assertEquals(input, write(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void write_emptyData_producesEmptyOutput() throws IOException {
        assertEquals("", write(new byte[0]));
    }

    @Test
    void write_onlyNewline_producesOnlyLineSeparator() throws IOException {
        assertEquals(System.lineSeparator(), write(new byte[]{'\n'}));
    }

    @Test
    void write_multipleConsecutiveNewlines_replacesEach() throws IOException {
        String expected = System.lineSeparator() + System.lineSeparator() + System.lineSeparator();
        assertEquals(expected, write("\n\n\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void write_withOffsetAndLength_replacesNewlinesInRange() throws IOException {
        byte[] data = "XYline1\nline2\nZW".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (NewlineOutputStream out = new NewlineOutputStream(sink)) {
            // write only "line1\nline2\n" (offset=2, len=12)
            out.write(data, 2, 12);
        }
        String result = sink.toString(StandardCharsets.UTF_8);
        String expected = "line1" + System.lineSeparator() + "line2" + System.lineSeparator();
        assertEquals(expected, result);
    }

    @Test
    void write_byteArrayOverload_delegatesToOffsetLength() throws IOException {
        String input = "a\nb";
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (NewlineOutputStream out = new NewlineOutputStream(sink)) {
            out.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String expected = "a" + System.lineSeparator() + "b";
        assertEquals(expected, sink.toString(StandardCharsets.UTF_8));
    }
}
