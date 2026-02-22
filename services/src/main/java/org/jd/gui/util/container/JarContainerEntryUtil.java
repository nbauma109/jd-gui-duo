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
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jadx.core.utils.StringUtils;

public final class JarContainerEntryUtil {

    private JarContainerEntryUtil() {
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

    private static void populateInnerTypePaths(final Set<String> innerTypePaths, Container.Entry entry) {
        try (InputStream is = entry.getInputStream()) {
            ClassReader classReader = new ClassReader(is);
            String p = entry.getPath();
            final String prefixPath = p.substring(0, p.length() - classReader.getClassName().length() - 6);

            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9) {
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

    public static String inferGroupFromFile(JarFile jarFile) {
        String group = inferGroupFromFile(jarFile, StringConstants.CLASS_FILE_SUFFIX);
        if (group == null) {
            group = inferGroupFromFile(jarFile, ".properties");
        }
        if (group == null) {
            group = "com.mycompany";
        }
        return group;
    }

    private static String inferGroupFromFile(JarFile jarFile, String extension) {
        Set<String> possibleGroups = new HashSet<>();
        int minDepth = Integer.MAX_VALUE;
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry nextEntry = entries.nextElement();
            String entryName = nextEntry.getName();
            int idx = entryName.lastIndexOf('/');
            if (idx != -1 && (extension == null || entryName.endsWith(extension))) {
                String packageName = entryName.substring(0, idx);
                int currentDepth = StringUtils.countMatches(packageName, "/");
                if (currentDepth < minDepth) {
                    possibleGroups.clear();
                    minDepth = currentDepth;
                    possibleGroups.add(packageName.replace('/', '.'));
                } else if (currentDepth == minDepth) {
                    possibleGroups.add(packageName.replace('/', '.'));
                    idx = packageName.lastIndexOf('/');
                    if (idx != -1 && possibleGroups.size() > 1) {
                        if (possibleGroups.size() != 2) {
                            throw new IllegalStateException("2 groups expected");
                        }
                        String[] pairOfPossibleGroups = possibleGroups.toArray(String[]::new);
                        for (int i = 0; i < pairOfPossibleGroups.length; i++) {
                            for (String prefix : Arrays.asList("org", "net", "com")) {
                                String otherPossibleGroup = pairOfPossibleGroups[(i + 1) % 2];
                                if (pairOfPossibleGroups[i].startsWith(prefix) && !otherPossibleGroup.startsWith(prefix)) {
                                    possibleGroups.remove(otherPossibleGroup);
                                }
                            }
                        }
                        if (possibleGroups.size() == 2) {
                            possibleGroups.clear();
                            possibleGroups.add(packageName.substring(0, idx).replace('/', '.'));
                            minDepth--;
                        }
                    }
                }
            }
        }
        if (possibleGroups.isEmpty()) {
            return null;
        }
        return possibleGroups.iterator().next();
    }
}
