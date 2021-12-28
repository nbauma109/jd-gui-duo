/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.io.TextReader;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.view.component.TypeReferencePage;
import org.jdv1.gui.api.feature.IndexesChangeListener;
import org.jdv1.gui.util.index.IndexesUtil;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlFilePage extends TypeReferencePage implements UriGettable, IndexesChangeListener {

    private static final long serialVersionUID = 1L;

    private transient API api;
    private transient Container.Entry entry;
    private transient Collection<Future<Indexes>> collectionOfFutureIndexes;

    public XmlFilePage(API api, Container.Entry entry) {
        this.api = api;
        this.entry = entry;
        try (InputStream inputStream = entry.getInputStream()) {
            // Load content file
            String text = TextReader.getText(inputStream);
            // Create hyperlinks
            Pattern pattern = Pattern.compile("(?s)<\\s*bean[^<]+class\\s*=\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(textArea.getText());

            while (matcher.find()) {
                // Spring type reference found
                String value = matcher.group(1);
                String trim = value.trim();

                if (trim != null) {
                    int startIndex = matcher.start(1) - 1;
                    int endIndex = startIndex + value.length() + 2;
                    String internalTypeName = trim.replace('.', '/');
                    addHyperlink(new TypeHyperlinkData(startIndex, endIndex, internalTypeName));
                }
            }
            // Display
            setText(text);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    @Override
    public String getSyntaxStyle() { return SyntaxConstants.SYNTAX_STYLE_XML; }

    @Override
    protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) { return ((TypeHyperlinkData)hyperlinkData).isEnabled(); }

    @Override
    protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
        TypeHyperlinkData data = (TypeHyperlinkData)hyperlinkData;

        if (data.isEnabled()) {
            try {
                // Save current position in history
                Point location = textArea.getLocationOnScreen();
                int offset = textArea.viewToModel2D(new Point(x-location.x, y-location.y));
                URI uri = entry.getUri();
                api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));

                // Open link
                String internalTypeName = data.getInternalTypeName();
                List<Container.Entry> entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, internalTypeName);
                String rootUri = entry.getContainer().getRoot().getUri().toString();
                List<Container.Entry> sameContainerEntries = new ArrayList<>();

                for (Container.Entry nextEntry : entries) {
                    if (nextEntry.getUri().toString().startsWith(rootUri)) {
                        sameContainerEntries.add(nextEntry);
                    }
                }

                if (!sameContainerEntries.isEmpty()) {
                    api.openURI(x, y, sameContainerEntries, null, data.getInternalTypeName());
                } else if (!entries.isEmpty()) {
                    api.openURI(x, y, entries, null, data.getInternalTypeName());
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

        for (Map.Entry<Integer, HyperlinkData> nextEntry : hyperlinks.entrySet()) {
            TypeHyperlinkData data = (TypeHyperlinkData)nextEntry.getValue();
            String internalTypeName = data.getInternalTypeName();
            boolean enabled = IndexesUtil.containsInternalTypeName(collectionOfFutureIndexes, internalTypeName);

            if (data.isEnabled() != enabled) {
                data.setEnabled(enabled);
                refresh = true;
            }
        }

        if (refresh) {
            textArea.repaint();
        }
    }
}
