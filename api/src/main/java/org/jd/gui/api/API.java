/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;

import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.spi.ContainerFactory;
import org.jd.gui.spi.FileLoader;
import org.jd.gui.spi.Indexer;
import org.jd.gui.spi.PanelFactory;
import org.jd.gui.spi.SourceSaver;
import org.jd.gui.spi.TreeNodeFactory;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.spi.UriLoader;

public interface API {
    boolean openURI(URI uri);

    boolean openURI(int x, int y, Collection<Container.Entry> entries, String query, String fragment);

    void addURI(URI uri);

    <T extends JComponent & UriGettable> void addPanel(String title, Icon icon, String tip, T component);

    Collection<Action> getContextualActions(Container.Entry entry, String fragment);

    UriLoader getUriLoader(URI uri);

    FileLoader getFileLoader(File file);

    ContainerFactory getContainerFactory(Path rootPath);

    PanelFactory getMainPanelFactory(Container container);

    TreeNodeFactory getTreeNodeFactory(Container.Entry entry);

    TypeFactory getTypeFactory(Container.Entry entry);

    Indexer getIndexer(Container.Entry entry);

    SourceSaver getSourceSaver(Container.Entry entry);

    Map<String, String> getPreferences();

	Collection<Indexes> getCollectionOfIndexes();

	Collection<Future<Indexes>> getCollectionOfFutureIndexes();

    interface LoadSourceListener {
        void sourceLoaded(String source);
    }

    String getSource(Container.Entry entry);

    void loadSource(Container.Entry entry, LoadSourceListener listener);

    File loadSourceFile(Container.Entry entry);

}
