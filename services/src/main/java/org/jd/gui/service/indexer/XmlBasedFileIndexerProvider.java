/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.indexer;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class XmlBasedFileIndexerProvider extends AbstractIndexerProvider {
    private XMLInputFactory factory;

    public XmlBasedFileIndexerProvider() {
        factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*.xsl", "*:file:*.xslt", "*:file:*.xsd");
    }

    @Override
    public void index(API api, Container.Entry entry, Indexes indexes, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction) {
        Set<String> stringSet = new HashSet<>();
        XMLStreamReader reader = null;

        try (InputStream inputStream = entry.getInputStream()) {

            reader = factory.createXMLStreamReader(inputStream);

            stringSet.add(reader.getVersion());
            stringSet.add(reader.getEncoding());
            stringSet.add(reader.getCharacterEncodingScheme());

            while (reader.hasNext()) {
                switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    stringSet.add(reader.getLocalName());
                    for (int i = reader.getAttributeCount() - 1; i >= 0; i--) {
                        stringSet.add(reader.getAttributeLocalName(i));
                        stringSet.add(reader.getAttributeValue(i));
                    }
                    for (int i = reader.getNamespaceCount() - 1; i >= 0; i--) {
                        stringSet.add(reader.getNamespacePrefix(i));
                        stringSet.add(reader.getNamespaceURI(i));
                    }
                    break;
                case XMLStreamConstants.PROCESSING_INSTRUCTION:
                    stringSet.add(reader.getPITarget());
                    stringSet.add(reader.getPIData());
                    break;
                case XMLStreamConstants.START_DOCUMENT:
                    stringSet.add(reader.getVersion());
                    stringSet.add(reader.getEncoding());
                    stringSet.add(reader.getCharacterEncodingScheme());
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE:
                    stringSet.add(reader.getLocalName());
                    stringSet.add(reader.getText());
                    break;
                case XMLStreamConstants.ATTRIBUTE:
                    stringSet.add(reader.getPrefix());
                    stringSet.add(reader.getNamespaceURI());
                    stringSet.add(reader.getLocalName());
                    stringSet.add(reader.getText());
                    break;
                case XMLStreamConstants.COMMENT, XMLStreamConstants.DTD, XMLStreamConstants.CDATA, XMLStreamConstants.CHARACTERS:
                    stringSet.add(reader.getText().trim());
                    break;
                case XMLStreamConstants.NAMESPACE:
                    for (int i = reader.getNamespaceCount() - 1; i >= 0; i--) {
                        stringSet.add(reader.getNamespacePrefix(i));
                        stringSet.add(reader.getNamespaceURI(i));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }

        indexStrings(entry, indexes, stringSet);
    }

    @SuppressWarnings("unchecked")
    private static void indexStrings(Container.Entry entry, Indexes indexes, Set<String> stringSet) {
        @SuppressWarnings("rawtypes")
        Map<String, Collection> stringIndex = indexes.getIndex("strings");

        for (String string : stringSet) {
            if (string != null && !string.isEmpty()) {
                stringIndex.get(string).add(entry);
            }
        }
    }
}
