/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.treenode;

import org.jd.gui.util.ImageUtil;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

public class ClassesDirectoryTreeNodeFactoryProvider extends DirectoryTreeNodeFactoryProvider {

    protected static final ImageIcon ICON = new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/packagefolder_obj.png"));

    @Override
    public String[] getSelectors() {
        // We keep the original archive selectors and expand them to cover:
        // - Java Archive (JAR), Web Application Archive (WAR), Java Module (JMOD)
        // - Multi-release layouts without needing to update this code every Java release
        // - Spring Boot executable layout (BOOT-INF/classes)
        final List<String> selectors = new ArrayList<>();

        // Multi-release (standard)
        selectors.add("jar:dir:META-INF/versions");
        addVersionedSelectors(selectors, "jar:dir:META-INF/versions", 5, 255);

        // Spring Boot executable JAR layout
        selectors.add("jar:dir:BOOT-INF/classes");
        selectors.add("jar:dir:BOOT-INF/classes/META-INF/versions");
        addVersionedSelectors(selectors, "jar:dir:BOOT-INF/classes/META-INF/versions", 5, 255);

        // Standard WAR layout, plus the same path when a WAR is treated as a plain JAR
        selectors.add("war:dir:WEB-INF/classes");
        selectors.add("jar:dir:WEB-INF/classes");

        // JMOD layout (and a multi-release-like variant under the classes root)
        selectors.add("jmod:dir:classes");
        selectors.add("jmod:dir:classes/META-INF/versions");
        addVersionedSelectors(selectors, "jmod:dir:classes/META-INF/versions", 5, 255);

        // Some packagers place classes under a "classes" directory even in JARs
        selectors.add("jar:dir:classes");

        return appendSelectors(selectors.toArray(new String[0]));
    }

    private static void addVersionedSelectors(final List<String> selectors,
                                             final String baseSelector,
                                             final int minVersion,
                                             final int maxVersion) {
        for (int version = minVersion; version <= maxVersion; version++) {
            selectors.add(baseSelector + "/" + version);
        }
    }

    @Override
    public ImageIcon getIcon() {
        return ICON;
    }

    @Override
    public ImageIcon getOpenIcon() {
        return null;
    }
}
