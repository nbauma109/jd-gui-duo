/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.service.treenode;

import java.util.regex.Pattern;

@org.kohsuke.MetaInfServices(org.jd.gui.spi.TreeNodeFactory.class)
public class SpiFileTreeNodeFactoryProvider extends TextFileTreeNodeFactoryProvider {
    @Override
    public String[] getSelectors() {
        return appendSelectors("*:file:*");
    }

    @Override
    public Pattern getPathPattern() {
        if (externalPathPattern == null) {
            return Pattern.compile("(.*\\/)?META-INF\\/services\\/.*");
        }
        return externalPathPattern;
    }
}
