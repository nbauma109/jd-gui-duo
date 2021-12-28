/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.model;

import java.util.Collection;

import javax.swing.Icon;

public interface Type {
    int FLAG_INTERFACE = 512;

    int getFlags();

    String getName();

    String getSuperName();

    String getOuterName();

    String getDisplayTypeName();

    String getDisplayInnerTypeName();

    String getDisplayPackageName();

    Icon getIcon();

    Collection<Type> getInnerTypes();

    Collection<Field> getFields();

    Collection<Method> getMethods();

    interface Field {
        int getFlags();

        String getName();

        String getDescriptor();

        String getDisplayName();

        Icon getIcon();
    }

    interface Method {
        int getFlags();

        String getName();

        String getDescriptor();

        String getDisplayName();

        Icon getIcon();
    }
}
