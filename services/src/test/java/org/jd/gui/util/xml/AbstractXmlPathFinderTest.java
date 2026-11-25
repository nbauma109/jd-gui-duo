/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The values of the elements at some paths of a document, and where they are in it. */
class AbstractXmlPathFinderTest {

    private static final String DOCUMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
              <item>first</item>
              <group>
            \t\t<item>
                  second
                </item>
              </group>
              <group><item><!-- why --> third</item><item></item><item>   </item></group>
              <other>not asked for</other>
              <item attribute="a > b">fourth</item> tail
            </root>
            """;

    /** What is found: the path, and the text of the document where the finder says the value is. */
    private static List<String> find(String document) {
        List<String> found = new ArrayList<>();
        new AbstractXmlPathFinder(List.of("root/item", "root/group/item")) {
            @Override
            public void handle(String path, String text, int position) {
                String value = text.trim();
                int start = position + text.indexOf(value);
                found.add(path + "=" + document.substring(start, start + value.length()));
            }
        }.find(document);
        return found;
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    void theValuesAreWhereTheyAreInTheDocumentWhateverTheLineEnds(String lineEnd) {
        List<String> found = find(DOCUMENT.replace("\n", lineEnd));

        assertThat(found).containsExactly("root/item=first", "root/group/item=second", "root/group/item=third", "root/item=fourth");
    }

    @Test
    void aDocumentOnOneLineIsReadToo() {
        assertThat(find("<root><item>a.B</item><group><item>c.D</item></group></root>"))
                .containsExactly("root/item=a.B", "root/group/item=c.D");
    }

    @Test
    void aDocumentThatIsNotWellFormedGivesWhatComesBeforeTheError() {
        assertThat(find("<root><item>a.B</item><item></root>")).containsExactly("root/item=a.B");
    }
}
