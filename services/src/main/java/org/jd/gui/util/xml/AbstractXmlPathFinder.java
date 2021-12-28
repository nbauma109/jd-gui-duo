/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
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

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public abstract class AbstractXmlPathFinder {
    private Map<String, Set<String>> tagNameToPaths = new HashMap<>();
    private StringBuilder sb = new StringBuilder(200);

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

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text));

            String tagName = "";
            int offset = 0;

            while (reader.hasNext()) {
                reader.next();

                switch (reader.getEventType())
                {
                case XMLStreamConstants.START_ELEMENT:
                    tagName = reader.getLocalName();
                    sb.append('/').append(tagName);
                    offset = reader.getLocation().getCharacterOffset();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    sb.setLength(sb.length() - reader.getLocalName().length() - 1);
                    break;
                case XMLStreamConstants.CHARACTERS:
                    Set<String> setOfPaths = tagNameToPaths.get(tagName);

                    if (setOfPaths != null) {
                        String path = sb.toString();

                        if (setOfPaths.contains(path)) {
                            // Search start offset
                            while (offset > 0) {
                                if (text.charAt(offset) == '>') {
                                    break;
                                }
                                offset--;
                            }

                            handle(path.substring(1), reader.getText(), offset+1);
                        }
                    }
                    break;
                }
            }
        } catch (XMLStreamException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    public abstract void handle(String path, String text, int position);
}
