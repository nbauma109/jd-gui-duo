/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** The stream that writes the line separator of the platform for every line feed. */
class NewlineOutputStreamTest {

    private static final String NL = System.lineSeparator();

    private static String written(IoConsumer consumer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NewlineOutputStream out = new NewlineOutputStream(bytes)) {
            consumer.accept(out);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private interface IoConsumer {
        void accept(NewlineOutputStream out) throws IOException;
    }

    @Test
    void aSingleLineFeedIsTheLineSeparatorAndTheOtherBytesAreKept() throws IOException {
        assertThat(written(out -> {
            out.write('a');
            out.write('\n');
            out.write('b');
        })).isEqualTo("a" + NL + "b");
    }

    @Test
    void everyLineFeedOfABufferIsTheLineSeparator() throws IOException {
        assertThat(written(out -> out.write("one\ntwo\n\nthree".getBytes(StandardCharsets.UTF_8)))).isEqualTo("one" + NL + "two" + NL + NL + "three");
        assertThat(written(out -> out.write("no line feed".getBytes(StandardCharsets.UTF_8)))).isEqualTo("no line feed");
        assertThat(written(out -> out.write("\n".getBytes(StandardCharsets.UTF_8)))).isEqualTo(NL);
        assertThat(written(out -> out.write(new byte[0]))).isEmpty();
    }

    @Test
    void aPartOfABufferIsConvertedFromItsOffsetToItsLength() throws IOException {
        byte[] bytes = "xxab\ncd\nef".getBytes(StandardCharsets.UTF_8);

        // the 7 bytes from the offset 2: "ab\ncd\ne"
        assertThat(written(out -> out.write(bytes, 2, 7))).isEqualTo("ab" + NL + "cd" + NL + "e");
        // no line feed in the part, which is not at the start of the buffer
        assertThat(written(out -> out.write(bytes, 2, 2))).isEqualTo("ab");
        // the part ends with a line feed
        assertThat(written(out -> out.write(bytes, 2, 3))).isEqualTo("ab" + NL);
        assertThat(written(out -> out.write(bytes, 5, 0))).isEmpty();
    }

    @Test
    void successiveWritesAreConvertedIndependently() throws IOException {
        assertThat(written(out -> {
            out.write("a\n".getBytes(StandardCharsets.UTF_8), 0, 2);
            out.write("b\nc".getBytes(StandardCharsets.UTF_8));
        })).isEqualTo("a" + NL + "b" + NL + "c");
    }
}
