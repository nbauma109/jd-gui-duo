/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.io.TextReader;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.view.component.HyperlinkPage;
import org.jd.gui.api.feature.IndexesChangeListener;
import org.jd.gui.util.index.IndexesUtil;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class ManifestFilePage extends HyperlinkPage implements UriGettable, IndexesChangeListener {

    private static final long serialVersionUID = 1L;

    private final transient API api;
    private final transient Container.Entry entry;
    private transient Collection<Future<Indexes>> collectionOfFutureIndexes = Collections.emptyList();

    public ManifestFilePage(API api, Container.Entry entry) {
        this.api = api;
        this.entry = entry;
        try (InputStream inputStream = entry.getInputStream()) {
            // Load content file
            String text = TextReader.getText(inputStream);
            // Parse hyperlinks. Docs:
            // - http://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html
            // - http://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html
            int startLineIndex = text.indexOf("Main-Class:");
            if (startLineIndex != -1) {
                // Example: Main-Class: jd.gui.App
                int startIndex = skipSeparators(text, startLineIndex + "Main-Class:".length());
                int endIndex = searchEndIndexOfValue(text, startLineIndex, startIndex);
                String typeName = text.substring(startIndex, endIndex);
                String internalTypeName = typeName.replace('.', '/');
                addHyperlink(new ManifestHyperlinkData(startIndex, endIndex, internalTypeName + "-main-([Ljava/lang/String;)V"));
            }

            startLineIndex = text.indexOf("Premain-Class:");
            if (startLineIndex != -1) {
                // Example: Premain-Class: packge.JavaAgent
                int startIndex = skipSeparators(text, startLineIndex + "Premain-Class:".length());
                int endIndex = searchEndIndexOfValue(text, startLineIndex, startIndex);
                String typeName = text.substring(startIndex, endIndex);
                String internalTypeName = typeName.replace('.', '/');
                // Undefined parameters : 2 candidate methods
                // http://docs.oracle.com/javase/6/docs/api/java/lang/instrument/package-summary.html
                addHyperlink(new ManifestHyperlinkData(startIndex, endIndex, internalTypeName + "-premain-(*)?"));
            }
            // Display
            setText(text);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    public int skipSeparators(String text, int index) {
        int length = text.length();

        while (index < length) {
            if (" \t\n\r".indexOf(text.charAt(index)) == -1) {
                return index;
            }
            index++;
        }

        return index;
    }

    public int searchEndIndexOfValue(String text, int startLineIndex, int startIndex) {
        int length = text.length();
        int index;
        for (index = startIndex; index < length; index++) {
            // MANIFEST.MF Specification: max line length = 72
            // http://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html
            char c = text.charAt(index);
            if (c == '\r') {
                // CR followed by LF ?
                if (index-startLineIndex >= 70 && index+1 < length && text.charAt(index+1) == ' ') {
                    // skip whitespace
                } else if (index-startLineIndex >= 70 && index+2 < length && text.charAt(index+1) == '\n' && text.charAt(index+2) == ' ') {
                    // Multiline value
                    index++;
                } else {
                    // (End of file) or (single line value) => return end index
                    return index;
                }
                // Multiline value
                startLineIndex = index+1;
            }
            if (c == '\n') {
                if (index-startLineIndex < 70 || index+1 >= length || text.charAt(index+1) != ' ') {
                    // (End of file) or (single line value) => return end index
                    return index;
                }
                // Multiline value
                startLineIndex = index+1;
            }
        }

        return index;
    }

    @Override
    protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) { return ((ManifestHyperlinkData)hyperlinkData).isEnabled(); }

    @Override
    protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
        ManifestHyperlinkData data = (ManifestHyperlinkData)hyperlinkData;

        if (data.isEnabled()) {
            try {
                // Save current position in history
                Point location = textArea.getLocationOnScreen();
                int offset = textArea.viewToModel2D(new Point(x-location.x, y-location.y));
                URI uri = entry.getUri();
                api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));
                // Open link
                String text = getText();
                String textLink = getValue(text, hyperlinkData.getStartPosition(), hyperlinkData.getEndPosition());
                String internalTypeName = textLink.replace('.', '/');
                List<Container.Entry> entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, internalTypeName);
                String rootUri = entry.getContainer().getRoot().getUri().toString();
                List<Container.Entry> sameContainerEntries = new ArrayList<>();

                for (Container.Entry nextEntry : entries) {
                    if (nextEntry.getUri().toString().startsWith(rootUri)) {
                        sameContainerEntries.add(nextEntry);
                    }
                }

                if (!sameContainerEntries.isEmpty()) {
                    api.openURI(x, y, sameContainerEntries, null, data.fragment);
                } else if (!entries.isEmpty()) {
                    api.openURI(x, y, entries, null, data.fragment);
                }
            } catch (URISyntaxException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }

    // --- UriGettable --- //
    @Override
    public URI getUri() { return entry.getUri(); }

    // --- ContentSavable --- //
    @Override
    public String getFileName() {
        String path = entry.getPath();
        int index = path.lastIndexOf('/');
        return path.substring(index+1);
    }

    // --- IndexesChangeListener --- //
    @Override
    public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
        // Update the list of containers
        this.collectionOfFutureIndexes = collectionOfFutureIndexes;
        // Refresh links
        boolean refresh = false;
        String text = getText();

        for (Map.Entry<Integer, HyperlinkData> nextEntry : hyperlinks.entrySet()) {
            ManifestHyperlinkData entryData = (ManifestHyperlinkData)nextEntry.getValue();
            String textLink = getValue(text, entryData.getStartPosition(), entryData.getEndPosition());
            String internalTypeName = textLink.replace('.', '/');
            boolean enabled = IndexesUtil.containsInternalTypeName(collectionOfFutureIndexes, internalTypeName);

            if (entryData.isEnabled() != enabled) {
                entryData.setEnabled(enabled);
                refresh = true;
            }
        }

        if (refresh) {
            textArea.repaint();
        }
    }

    public static String getValue(String text, int startPosition, int endPosition) {
        return text
            // Extract text of link
            .substring(startPosition, endPosition)
            // Convert multiline value
            .replace("\r\n ", "")
            .replace("\r ", "")
            .replace("\n ", "");
    }

    public static class ManifestHyperlinkData extends HyperlinkData {
        private final String fragment;

        ManifestHyperlinkData(int startPosition, int endPosition, String fragment) {
            super(startPosition, endPosition);
            this.fragment = fragment;
        }
    }
}
