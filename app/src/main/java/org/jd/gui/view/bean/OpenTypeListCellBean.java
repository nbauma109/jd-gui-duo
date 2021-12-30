/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.bean;

import org.jd.gui.api.model.Container;

import java.util.Collection;

import javax.swing.Icon;

public class OpenTypeListCellBean {
    private final String label;
    private final String packag;
    private final Icon icon;
    private final Collection<Container.Entry> entries;
    private final String typeName;

    public OpenTypeListCellBean(String label, Collection<Container.Entry> entries, String typeName) {
        this(label, null, null, entries, typeName);
    }

    public OpenTypeListCellBean(String label, String packag, Icon icon, Collection<Container.Entry> entries, String typeName) {
        this.label = label;
        this.packag = packag;
        this.icon = icon;
        this.entries = entries;
        this.typeName = typeName;
    }

    public Collection<Container.Entry> getEntries() {
        return entries;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getLabel() {
        return label;
    }

    public String getPackag() {
        return packag;
    }

    public Icon getIcon() {
        return icon;
    }
}
