/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.controller;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.net.UriUtil;
import org.jd.gui.view.OpenTypeHierarchyView;
import org.jd.gui.api.feature.IndexesChangeListener;

import java.awt.Point;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class OpenTypeHierarchyController implements IndexesChangeListener {
    private final API api;
    private final ScheduledExecutorService executor;

    private final OpenTypeHierarchyView openTypeHierarchyView;
    private final SelectLocationController selectLocationController;

    private Collection<Future<Indexes>> collectionOfFutureIndexes;
    private Consumer<URI> openCallback;

    public OpenTypeHierarchyController(API api, ScheduledExecutorService executor, JFrame mainFrame) {
        this.api = api;
        this.executor = executor;
        // Create UI
        openTypeHierarchyView = new OpenTypeHierarchyView(api, mainFrame, this::onTypeSelected);
        selectLocationController = new SelectLocationController(api, mainFrame);
    }

    public void show(Collection<Future<Indexes>> collectionOfFutureIndexes, Container.Entry entry, String typeName, Consumer<URI> openCallback) {
        // Init attributes
        this.collectionOfFutureIndexes = collectionOfFutureIndexes;
        this.openCallback = openCallback;
        executor.execute(() -> {
            // Waiting the end of indexation...
            openTypeHierarchyView.showWaitCursor();
            SwingUtilities.invokeLater(() -> {
                openTypeHierarchyView.hideWaitCursor();
                // Show
                openTypeHierarchyView.show(collectionOfFutureIndexes, entry, typeName);
            });
        });
    }

    protected void onTypeSelected(Point leftBottom, Collection<Container.Entry> entries, String typeName) {
        if (entries.size() == 1) {
            // Open the single entry uri
            openCallback.accept(UriUtil.createURI(api, collectionOfFutureIndexes, entries.iterator().next(), null, typeName));
        } else {
            // Multiple entries -> Open a "Select location" popup
            selectLocationController.show(
                new Point(leftBottom.x+16+2, leftBottom.y+2),
                entries,
                entry -> openCallback.accept(UriUtil.createURI(api, collectionOfFutureIndexes, entry, null, typeName)), // entry selected
                openTypeHierarchyView::focus);                                                                   // popup closeClosure
        }
    }

    // --- IndexesChangeListener --- //
    @Override
    public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
        if (openTypeHierarchyView.isVisible()) {
            // Update the list of containers
            this.collectionOfFutureIndexes = collectionOfFutureIndexes;
            // And refresh
            openTypeHierarchyView.updateTree(collectionOfFutureIndexes);
        }
    }
}
