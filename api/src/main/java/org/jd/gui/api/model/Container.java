/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.model;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public interface Container {
    String getType();

    Entry getRoot();

    default URI getRootUri() {
        return getRoot().getUri();
    }

    /**
     * File or directory
     */
    interface Entry extends EntryPath {
        Container getContainer();

        Entry getParent();

        URI getUri();

        long length();

        long compressedLength();

        InputStream getInputStream();

        Map<EntryPath, Entry> getChildren();

    }

    interface EntryPath {

        boolean isDirectory();

        String getPath();
    }
}
