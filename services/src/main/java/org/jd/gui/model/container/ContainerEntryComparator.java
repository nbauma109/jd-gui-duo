/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.model.container;

import org.jd.gui.api.model.Container.EntryPath;

import java.util.Comparator;

/**
 * Directories before files, sorted by path
 */
public class ContainerEntryComparator implements java.io.Serializable, Comparator<EntryPath> {
    /**
     * Comparators should be Serializable: A non-serializable Comparator can prevent an otherwise-Serializable ordered collection from being serializable.
     */
    private static final long serialVersionUID = 1L;

    public static final ContainerEntryComparator COMPARATOR = new ContainerEntryComparator();

    @Override
    public int compare(EntryPath e1, EntryPath e2) {
        if (e1.isDirectory()) {
            if (!e2.isDirectory()) {
                return -1;
            }
        } else if (e2.isDirectory()) {
            return 1;
        }
        return e1.getPath().compareTo(e2.getPath());
    }
}
