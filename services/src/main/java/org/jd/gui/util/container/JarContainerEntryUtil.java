/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.container;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.model.container.ContainerEntryComparator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.*;

public class JarContainerEntryUtil {

    private JarContainerEntryUtil() {
        super();
    }

    public static Collection<Container.Entry> removeInnerTypeEntries(Map<Container.EntryPath, Container.Entry> entries) {
        Set<String> potentialOuterTypePaths = new HashSet<>();
        Map<Container.EntryPath, Container.Entry> filteredSubEntries;

        for (Container.Entry e : entries.values()) {
            if (!e.isDirectory()) {
                String p = e.getPath();

                if (p.toLowerCase().endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                    int lastSeparatorIndex = p.lastIndexOf('/');
                    int dollarIndex = p.indexOf('$', lastSeparatorIndex+1);

                    if (dollarIndex != -1) {
                        potentialOuterTypePaths.add(p.substring(0, dollarIndex) + StringConstants.CLASS_FILE_SUFFIX);
                    }
                }
            }
        }

        if (potentialOuterTypePaths.isEmpty()) {
            filteredSubEntries = entries;
        } else {
            Set<String> innerTypePaths = new HashSet<>();

            for (Container.Entry e : entries.values()) {
                if (!e.isDirectory() && potentialOuterTypePaths.contains(e.getPath())) {
                    populateInnerTypePaths(innerTypePaths, e);
                }
            }

            filteredSubEntries = new TreeMap<>(ContainerEntryComparator.COMPARATOR);

            for (Map.Entry<Container.EntryPath, Container.Entry> entry : entries.entrySet()) {
                Container.Entry e = entry.getValue();
                if (!e.isDirectory()) {
                    String p = e.getPath();

                    if (p.toLowerCase().endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                        int indexDollar = p.lastIndexOf('$');

                        if (indexDollar != -1) {
                            int indexSeparator = p.lastIndexOf('/');

                            if (indexDollar > indexSeparator) {
                                if (innerTypePaths.contains(p)) {
                                    // Inner class found -> Skip
                                    continue;
                                }
                                populateInnerTypePaths(innerTypePaths, e);

                                if (innerTypePaths.contains(p)) {
                                    // Inner class found -> Skip
                                    continue;
                                }
                            }
                        }
                    }
                }
                // Valid path
                filteredSubEntries.put(entry.getKey(), e);
            }
        }

        return filteredSubEntries.values();
    }

    protected static void populateInnerTypePaths(final Set<String> innerTypePaths, Container.Entry entry) {
        try (InputStream is = entry.getInputStream()) {
            ClassReader classReader = new ClassReader(is);
            String p = entry.getPath();
            final String prefixPath = p.substring(0, p.length() - classReader.getClassName().length() - 6);

            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM7) {
                @Override
                public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
                    innerTypePaths.add(prefixPath + name + StringConstants.CLASS_FILE_SUFFIX);
                }
            };

            classReader.accept(classVisitor, ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
