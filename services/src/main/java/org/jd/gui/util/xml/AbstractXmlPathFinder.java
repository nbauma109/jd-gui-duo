/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.xml;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public abstract class AbstractXmlPathFinder {
    private final Map<String, Set<String>> tagNameToPaths = new HashMap<>();
    private final StringBuilder sb = new StringBuilder(200);

    protected AbstractXmlPathFinder(Collection<String> paths) {
        for (String path : paths) {
            if (path != null && !path.isEmpty()) {
                // Normalize path
                path = '/' + path;
                int lastIndex = path.lastIndexOf('/');
                String lastTagName = path.substring(lastIndex+1);

                // Add tag names to map
                tagNameToPaths.computeIfAbsent(lastTagName, k ->  new HashSet<>()).add(path);
            }
        }
    }

    public void find(String text) {
        sb.setLength(0);
        // the parser miscounts the columns after a lone CR: it reads LF instead, which keeps every position
        String document = text.replaceAll("\r(?!\n)", "\n");
        int[] lineStarts = lineStartOffsets(document);

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(document));

            String tagName = "";
            int offset = 0;

            while (reader.hasNext()) {
                reader.next();

                switch (reader.getEventType())
                {
                case XMLStreamConstants.START_ELEMENT:
                    tagName = reader.getLocalName();
                    sb.append('/').append(tagName);
                    // from the line and the column: the character offset that the parser of the JDK gives is wrong after some line breaks
                    offset = offsetOf(reader.getLocation(), lineStarts, document.length());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    sb.setLength(sb.length() - reader.getLocalName().length() - 1);
                    break;
                case XMLStreamConstants.CHARACTERS:
                    Set<String> setOfPaths = tagNameToPaths.get(tagName);

                    if (setOfPaths != null) {
                        String path = sb.toString();

                        if (setOfPaths.contains(path)) {
                            String value = reader.getText();
                            if (!value.isBlank()) {
                                handle(path.substring(1), value, positionOf(document, offset, value));
                            }
                        }
                    }
                    break;
                }
            }
        } catch (XMLStreamException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    /** The offset of each line of the text; lines end with LF (or CR LF). */
    private static int[] lineStartOffsets(String text) {
        IntStream.Builder starts = IntStream.builder().add(0);
        for (int i = text.indexOf('\n'); i != -1; i = text.indexOf('\n', i + 1)) {
            starts.add(i + 1);
        }
        return starts.build().toArray();
    }

    /** The offset just after the tag that the location (a line and a column, from 1) is at. */
    private static int offsetOf(Location location, int[] lineStarts, int length) {
        int line = Math.min(Math.max(location.getLineNumber(), 1), lineStarts.length);
        return Math.min(Math.max(lineStarts[line - 1] + location.getColumnNumber() - 1, 0), length);
    }

    /**
     * The position to give with the value of an element that starts at the offset, so that the position plus the index of the trimmed value
     * in the value is where the trimmed value is in the text. The text may differ from the value before it: comments, and line breaks that
     * the parser normalizes.
     */
    private static int positionOf(String text, int offset, String value) {
        int start = offset;
        while (start < text.length()) {
            if (Character.isWhitespace(text.charAt(start))) {
                start++;
            } else if (text.startsWith("<!--", start) && text.indexOf("-->", start + 4) != -1) {
                start = text.indexOf("-->", start + 4) + 3;
            } else {
                break;
            }
        }
        return start - value.indexOf(value.trim());
    }

    /**
     * Called for the value, which is not blank, of every element at one of the paths: the trimmed value starts at
     * {@code position + text.indexOf(text.trim())} in the document.
     */
    public abstract void handle(String path, String text, int position);
}
