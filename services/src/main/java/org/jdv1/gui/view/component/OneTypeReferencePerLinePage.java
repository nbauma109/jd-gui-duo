/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.view.component.TypeReferencePage;
import org.jdv1.gui.api.feature.IndexesChangeListener;
import org.jdv1.gui.util.index.IndexesUtil;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class OneTypeReferencePerLinePage extends TypeReferencePage implements UriGettable, IndexesChangeListener {

    private static final long serialVersionUID = 1L;

    private final transient API api;
    private final transient Container.Entry entry;
    private transient Collection<Future<Indexes>> collectionOfFutureIndexes = Collections.emptyList();

    public OneTypeReferencePerLinePage(API api, Container.Entry entry) {
        this.api = api;
        this.entry = entry;
        // Load content file & Create hyperlinks
        StringBuilder sb = new StringBuilder();
        int offset = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(entry.getInputStream(), StandardCharsets.UTF_8))) {
            String line;

            while ((line = br.readLine()) != null) {
                String trim = line.trim();

                if (!trim.isEmpty()) {
                    int startIndex = offset + line.indexOf(trim);
                    int endIndex = startIndex + trim.length();
                    String internalTypeName = trim.replace('.', '/');

                    addHyperlink(new TypeReferencePage.TypeHyperlinkData(startIndex, endIndex, internalTypeName));
                }

                offset += line.length() + 1;
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        // Display
        setText(sb.toString());
    }

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
            TypeHyperlinkData entryData = (TypeHyperlinkData)nextEntry.getValue();
            String internalTypeName = entryData.getInternalTypeName();
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
}
