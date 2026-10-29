/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.model.container.entry.path.FileEntryPath;
import org.jd.gui.view.SearchInConstantPoolsView;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Computes the URIs of the type, field and method tree nodes that must be displayed under
 * the matching class files in the "Search In Constant Pools" dialog.
 */
final class FragmentUriCollector {

    private FragmentUriCollector() {
    }

    static Set<URI> collect(Collection<Container.Entry> matchingEntries, Function<Container.Entry, Type> typeLoader, String pattern, int flags) {
        return collect(matchingEntries, typeLoader, entry -> ReferenceScanner.Result.EMPTY, pattern, flags);
    }

    static Set<URI> collect(Collection<Container.Entry> matchingEntries, Function<Container.Entry, Type> typeLoader,
            Function<Container.Entry, ReferenceScanner.Result> referenceScanner, String pattern, int flags) {
        Set<URI> fragmentUris = new HashSet<>();
        boolean declarations = (flags & SearchInConstantPoolsView.SEARCH_DECLARATION) != 0;
        boolean references = (flags & SearchInConstantPoolsView.SEARCH_REFERENCE) != 0;

        // typePattern is used to match constructor declarations by type simple name
        Pattern typePattern = null;
        if (declarations && (flags & SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) != 0) {
            typePattern = SearchInConstantPoolsController.createPattern(pattern);
        }
        // memberPattern is used to match method/field declaration names
        Pattern memberPattern = null;
        if (declarations && (flags & (SearchInConstantPoolsView.SEARCH_METHOD | SearchInConstantPoolsView.SEARCH_FIELD)) != 0) {
            memberPattern = SearchInConstantPoolsController.createPattern(pattern);
        }

        for (Container.Entry entry : matchingEntries) {
            Type type = typeLoader.apply(entry);
            if (type == null) {
                continue;
            }
            Container.Entry outerEntry = getTopLevelOuterEntry(entry);
            if (outerEntry == null) {
                continue;
            }
            collectTypeFragments(outerEntry, type, typePattern, memberPattern, flags, fragmentUris);
            if (references) {
                collectReferenceFragments(outerEntry, entry, type, referenceScanner, fragmentUris);
            }
        }

        return fragmentUris;
    }

    /**
     * Returns the URIs of the inner class files of the given top-level class files. The filtered
     * container must expose them: type lookup resolves inner types through the parent's children
     * and caches the result per URI, so an outer type first built from a filtered view without its
     * inner class files would permanently lose them (the package nodes hide these files themselves).
     */
    static Set<URI> innerEntryUris(Collection<Container.Entry> outerEntries) {
        Set<URI> uris = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        Set<Container.Entry> parents = new HashSet<>();

        for (Container.Entry outer : outerEntries) {
            String path = outer.getPath();
            if (path.endsWith(StringConstants.CLASS_FILE_SUFFIX) && outer.getParent() != null) {
                prefixes.add(path.substring(0, path.length() - StringConstants.CLASS_FILE_SUFFIX.length()) + StringConstants.INTERNAL_INNER_SEPARATOR);
                parents.add(outer.getParent());
            }
        }
        for (Container.Entry parent : parents) {
            for (Container.Entry child : parent.getChildren().values()) {
                String path = child.getPath();
                if (path.endsWith(StringConstants.CLASS_FILE_SUFFIX) && prefixes.stream().anyMatch(path::startsWith)) {
                    uris.add(child.getUri());
                }
            }
        }
        return uris;
    }

    private static Container.Entry getTopLevelOuterEntry(Container.Entry entry) {
        String path = entry.getPath();
        int idx = path.indexOf(StringConstants.INTERNAL_INNER_SEPARATOR);
        if (idx != -1 && path.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            FileEntryPath outerPath = new FileEntryPath(path.substring(0, idx) + StringConstants.CLASS_FILE_SUFFIX);
            return entry.getParent().getChildren().get(outerPath); // may be null if outer not found
        }
        return entry; // Already a top-level class
    }

    private static void collectTypeFragments(Container.Entry outerEntry, Type type, Pattern typePattern, Pattern memberPattern, int flags, Set<URI> fragmentUris) {
        String typeName = type.getName();
        String simpleTypeName = lastIdentifier(typeName);

        // Always show the type node for every match (declaration or reference), so the user
        // sees the class/interface/enum/annotation/record icon instead of just the file icon.
        addTypeFragmentWithAncestors(outerEntry, typeName, fragmentUris);

        // For declarations only, we can additionally pinpoint specific matching members.
        if ((flags & SearchInConstantPoolsView.SEARCH_DECLARATION) == 0) {
            return;
        }

        // Constructor declarations are indexed by type simple name
        if (typePattern != null && typePattern.matcher(simpleTypeName).matches()) {
            for (Type.Method method : type.getMethods()) {
                if (StringConstants.INSTANCE_CONSTRUCTOR.equals(method.getName())) {
                    fragmentUris.add(fragmentUri(outerEntry, typeName + '-' + method.getName() + '-' + method.getDescriptor()));
                }
            }
        }

        if (memberPattern == null) {
            return;
        }

        // Method declarations matched by method name
        if ((flags & SearchInConstantPoolsView.SEARCH_METHOD) != 0) {
            for (Type.Method method : type.getMethods()) {
                String methodName = method.getName();
                if (!StringConstants.INSTANCE_CONSTRUCTOR.equals(methodName) && !"<clinit>".equals(methodName) && memberPattern.matcher(methodName).matches()) {
                    fragmentUris.add(fragmentUri(outerEntry, typeName + '-' + methodName + '-' + method.getDescriptor()));
                }
            }
        }

        // Field declarations matched by field name
        if ((flags & SearchInConstantPoolsView.SEARCH_FIELD) != 0) {
            for (Type.Field field : type.getFields()) {
                if (memberPattern.matcher(field.getName()).matches()) {
                    fragmentUris.add(fragmentUri(outerEntry, typeName + '-' + field.getName() + '-' + field.getDescriptor()));
                }
            }
        }
    }

    /**
     * Adds the members of the type that hold a matching reference. A reference in a member that
     * has no tree node (static initializer, synthetic method) is covered by the type node.
     */
    private static void collectReferenceFragments(Container.Entry outerEntry, Container.Entry entry, Type type,
            Function<Container.Entry, ReferenceScanner.Result> referenceScanner, Set<URI> fragmentUris) {
        ReferenceScanner.Result result = referenceScanner.apply(entry);
        String typeName = type.getName();

        for (Type.Method method : type.getMethods()) {
            if (!"<clinit>".equals(method.getName()) && result.methods().contains(method.getName() + method.getDescriptor())) {
                fragmentUris.add(fragmentUri(outerEntry, typeName + '-' + method.getName() + '-' + method.getDescriptor()));
            }
        }
        for (Type.Field field : type.getFields()) {
            if (result.fields().contains(field.getName() + field.getDescriptor())) {
                fragmentUris.add(fragmentUri(outerEntry, typeName + '-' + field.getName() + '-' + field.getDescriptor()));
            }
        }
    }

    /**
     * Adds the fragment URI for the given type name plus all ancestor type URIs
     * (stripping '$'-delimited suffixes) so that the full path in the tree is visible.
     */
    private static void addTypeFragmentWithAncestors(Container.Entry outerEntry, String typeName, Set<URI> fragmentUris) {
        fragmentUris.add(fragmentUri(outerEntry, typeName));
        String name = typeName;
        int lastDollar;
        while ((lastDollar = name.lastIndexOf('$')) != -1) {
            name = name.substring(0, lastDollar);
            fragmentUris.add(fragmentUri(outerEntry, name));
        }
    }

    /** Returns the simple identifier after the last '/' or '$'. */
    private static String lastIdentifier(String internalName) {
        int lastSlash = internalName.lastIndexOf('/') + 1;
        int lastDollar = internalName.lastIndexOf('$') + 1;
        return internalName.substring(Math.max(lastSlash, lastDollar));
    }

    /** Builds a fragment URI: same scheme/host/path as the entry's URI, with a new fragment. */
    private static URI fragmentUri(Container.Entry entry, String fragment) {
        URI base = entry.getUri();
        try {
            return new URI(base.getScheme(), base.getHost(), base.getPath(), fragment);
        } catch (URISyntaxException e) {
            assert ExceptionUtil.printStackTrace(e);
            return base;
        }
    }
}
