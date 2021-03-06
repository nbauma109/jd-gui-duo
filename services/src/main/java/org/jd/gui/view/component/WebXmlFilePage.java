/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.IndexesChangeListener;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.index.IndexesUtil;
import org.jd.gui.util.io.TextReader;
import org.jd.gui.util.xml.AbstractXmlPathFinder;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import jd.core.links.HyperlinkData;

public class WebXmlFilePage extends TypeReferencePage implements UriGettable, IndexesChangeListener {

    private static final long serialVersionUID = 1L;

    private final transient API api;
    private final transient Container.Entry entry;
    private transient Collection<Future<Indexes>> collectionOfFutureIndexes;

    public WebXmlFilePage(API api, Container.Entry entry) {
        this.api = api;
        this.entry = entry;
        try (InputStream inputStream = entry.getInputStream()) {
            // Load file contents
            String text = TextReader.getText(inputStream);
            // Create hyperlinks
            new PathFinder().find(text);
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
                if (hyperlinkData instanceof PathHyperlinkData) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
                    PathHyperlinkData d = (PathHyperlinkData) hyperlinkData;
                    String path = d.path;
                    Container.Entry nextEntry = searchEntry(this.entry.getContainer().getRoot(), path);
                    if (nextEntry != null) {
                        api.openURI(x, y, Collections.singletonList(nextEntry), null, path);
                    }
                } else {
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
                }
            } catch (URISyntaxException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }

    public static Container.Entry searchEntry(Container.Entry parent, String path) {
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return recursiveSearchEntry(parent, path);
    }

    public static Container.Entry recursiveSearchEntry(Container.Entry parent, String path) {
        Container.Entry entry = null;

        for (Container.Entry child : parent.getChildren().values()) {
            if (path.equals(child.getPath())) {
                entry = child;
                break;
            }
        }

        if (entry != null) {
            return entry;
        }
        for (Container.Entry child : parent.getChildren().values()) {
            if (path.startsWith(child.getPath() + '/')) {
                entry = child;
                break;
            }
        }

        return entry != null ? searchEntry(entry, path) : null;
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
            boolean enabled;

            if (data instanceof PathHyperlinkData) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
                PathHyperlinkData d = (PathHyperlinkData) data;
                enabled = searchEntry(this.entry.getContainer().getRoot(), d.path) != null;
            } else {
                String internalTypeName = data.getInternalTypeName();
                enabled = IndexesUtil.containsInternalTypeName(collectionOfFutureIndexes, internalTypeName);
            }

            if (data.isEnabled() != enabled) {
                data.setEnabled(enabled);
                refresh = true;
            }
        }

        if (refresh) {
            textArea.repaint();
        }
    }

    public static class PathHyperlinkData extends TypeHyperlinkData {
        private final String path;

        PathHyperlinkData(int startPosition, int endPosition, String path) {
            super(startPosition, endPosition, null);
            this.path = path;
        }
    }

    private static final List<String> typeHyperlinkPaths = Arrays.asList(
        "web-app/filter/filter-class",
        "web-app/listener/listener-class",
        "web-app/servlet/servlet-class");

    private static final List<String> pathHyperlinkPaths = Arrays.asList(
        "web-app/jsp-config/taglib/taglib-location",
        "web-app/welcome-file-list/welcome-file",
        "web-app/login-config/form-login-config/form-login-page",
        "web-app/login-config/form-login-config/form-error-page",
        "web-app/jsp-config/jsp-property-group/include-prelude",
        "web-app/jsp-config/jsp-property-group/include-coda");

    private static final List<String> hyperlinkPaths = new ArrayList<>(typeHyperlinkPaths.size() + pathHyperlinkPaths.size());

    static {
        hyperlinkPaths.addAll(typeHyperlinkPaths);
        hyperlinkPaths.addAll(pathHyperlinkPaths);
    }

    public class PathFinder extends AbstractXmlPathFinder {
        public PathFinder() {
            super(hyperlinkPaths);
        }

        @Override
        public void handle(String path, String text, int position) {
            String trim = text.trim();
            if (trim != null) {
                int startIndex = position + text.indexOf(trim);
                int endIndex = startIndex + trim.length();

                if (pathHyperlinkPaths.contains(path)) {
                    addHyperlink(new PathHyperlinkData(startIndex, endIndex, trim));
                } else {
                    String internalTypeName = trim.replace('.', '/');
                    addHyperlink(new TypeHyperlinkData(startIndex, endIndex, internalTypeName));
                }
            }
        }
    }
}
