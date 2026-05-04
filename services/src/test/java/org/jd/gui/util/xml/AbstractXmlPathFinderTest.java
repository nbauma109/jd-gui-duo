/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.util.xml;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractXmlPathFinderTest {

    /** Concrete subclass that collects (path, text, position) triples. */
    private static class CollectingFinder extends AbstractXmlPathFinder {
        record Found(String path, String text, int position) {}
        final List<Found> found = new ArrayList<>();

        CollectingFinder(String... paths) {
            super(Arrays.asList(paths));
        }

        @Override
        public void handle(String path, String text, int position) {
            found.add(new Found(path, text, position));
        }
    }

    @Test
    void find_locatesSimpleTextNode() {
        String xml = "<root><child>hello</child></root>";
        CollectingFinder finder = new CollectingFinder("root/child");
        finder.find(xml);

        assertEquals(1, finder.found.size());
        assertEquals("root/child", finder.found.get(0).path());
        assertEquals("hello", finder.found.get(0).text());
    }

    @Test
    void find_locatesNestedPath() {
        String xml = "<a><b><c>deep</c></b></a>";
        CollectingFinder finder = new CollectingFinder("a/b/c");
        finder.find(xml);

        assertEquals(1, finder.found.size());
        assertEquals("a/b/c", finder.found.get(0).path());
        assertEquals("deep", finder.found.get(0).text());
    }

    @Test
    void find_doesNotMatchOnPartialPath() {
        String xml = "<a><b>value</b></a>";
        CollectingFinder finder = new CollectingFinder("b"); // path "b" is not a top-level element
        finder.find(xml);

        // The path "/b" never matches "/a/b", so nothing should be found
        assertTrue(finder.found.isEmpty());
    }

    @Test
    void find_matchesMultipleTargets() {
        String xml = "<root><name>Alice</name><name>Bob</name></root>";
        CollectingFinder finder = new CollectingFinder("root/name");
        finder.find(xml);

        assertEquals(2, finder.found.size());
        assertEquals("Alice", finder.found.get(0).text());
        assertEquals("Bob", finder.found.get(1).text());
    }

    @Test
    void find_withMultipleRegisteredPaths_triggersCorrectHandlers() {
        String xml = "<project><groupId>org.example</groupId><version>1.0</version></project>";
        CollectingFinder finder = new CollectingFinder("project/groupId", "project/version");
        finder.find(xml);

        assertEquals(2, finder.found.size());
        List<String> texts = finder.found.stream().map(CollectingFinder.Found::text).toList();
        assertTrue(texts.contains("org.example"));
        assertTrue(texts.contains("1.0"));
    }

    @Test
    void find_onEmptyXml_findsNothing() {
        CollectingFinder finder = new CollectingFinder("root/child");
        finder.find("<root/>");

        assertTrue(finder.found.isEmpty());
    }

    @Test
    void find_positionPointsAfterOpeningTag() {
        String xml = "<root><child>text</child></root>";
        CollectingFinder finder = new CollectingFinder("root/child");
        finder.find(xml);

        assertEquals(1, finder.found.size());
        int pos = finder.found.get(0).position();
        // Position should point to just after the '>' of <child>
        assertTrue(pos > 0);
        assertEquals('t', xml.charAt(pos)); // first char of "text"
    }

    @Test
    void find_ignoresNullOrEmptyPaths() {
        // Should not throw
        CollectingFinder finder = new CollectingFinder((String) null);
        finder.find("<a>val</a>");
        assertTrue(finder.found.isEmpty());
    }

    @Test
    void find_canBeCalledMultipleTimes_resetsState() {
        String xml1 = "<root><item>first</item></root>";
        String xml2 = "<root><item>second</item></root>";
        CollectingFinder finder = new CollectingFinder("root/item");

        finder.find(xml1);
        finder.find(xml2);

        assertEquals(2, finder.found.size());
        assertEquals("first", finder.found.get(0).text());
        assertEquals("second", finder.found.get(1).text());
    }
}
