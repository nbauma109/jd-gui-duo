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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Computes the URIs of the type, field and method tree nodes that must be displayed under
 * the matching class files in the "Search In Constant Pools" dialog.
 */
final class FragmentUriCollector {

    private static final String STATIC_INITIALIZER = "<clinit>";
    private static final int MAX_NESTING = 32;

    private final Function<Container.Entry, Collection<Type>> typeLoader;
    private final Function<Container.Entry, ReferenceScanner.Result> referenceScanner;
    private final Pattern typePattern;
    private final Pattern memberPattern;
    private final Pattern declaredTypePattern;
    private final boolean declarations;
    private final boolean references;
    private final int flags;
    private final Set<URI> fragmentUris = new HashSet<>();

    private FragmentUriCollector(Function<Container.Entry, Collection<Type>> typeLoader,
            Function<Container.Entry, ReferenceScanner.Result> referenceScanner, String pattern, int flags) {
        this.declarations = (flags & SearchInConstantPoolsView.SEARCH_DECLARATION) != 0;
        this.typeLoader = typeLoader;
        this.referenceScanner = referenceScanner;
        this.flags = flags;
        this.references = (flags & SearchInConstantPoolsView.SEARCH_REFERENCE) != 0;
        // typePattern is used to match constructor declarations by type simple name
        this.typePattern = patternIf(declarations && (flags & SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) != 0, pattern);
        // declaredTypePattern is used to match nested type declarations by type simple name
        this.declaredTypePattern = patternIf(declarations && (flags & SearchInConstantPoolsView.SEARCH_TYPE) != 0, pattern);
        // memberPattern is used to match method/field declaration names
        this.memberPattern = patternIf(declarations && (flags & (SearchInConstantPoolsView.SEARCH_METHOD | SearchInConstantPoolsView.SEARCH_FIELD)) != 0, pattern);
    }

    static Set<URI> collect(Collection<Container.Entry> matchingEntries, Function<Container.Entry, Collection<Type>> typeLoader, String pattern, int flags) {
        return collect(matchingEntries, typeLoader, entry -> ReferenceScanner.Result.EMPTY, pattern, flags);
    }

    static Set<URI> collect(Collection<Container.Entry> matchingEntries, Function<Container.Entry, Collection<Type>> typeLoader,
            Function<Container.Entry, ReferenceScanner.Result> referenceScanner, String pattern, int flags) {
        FragmentUriCollector collector = new FragmentUriCollector(typeLoader, referenceScanner, pattern, flags);
        for (Container.Entry entry : matchingEntries) {
            collector.collectEntry(entry);
        }
        return collector.fragmentUris;
    }

    private static Pattern patternIf(boolean needed, String pattern) {
        return needed ? SearchInConstantPoolsController.createPattern(pattern) : null;
    }

    private void collectEntry(Container.Entry entry) {
        // A source file can declare several root types, each with inner types
        Collection<Type> types = typeLoader.apply(entry);
        Container.Entry outerEntry = types == null ? null : getTopLevelOuterEntry(entry, firstType(types));
        if (outerEntry != null) {
            for (Type type : types) {
                // The type is nested when the metadata says so, or when it is filed under another class file
                collectTypeTree(outerEntry, type, outerEntry != entry || (type != null && type.getOuterName() != null));
                // Only the entry's own types can be scanned, inner types have their own class files
                if (references) {
                    collectReferenceFragments(outerEntry, entry, type);
                }
            }
        }
    }

    private static Type firstType(Collection<Type> types) {
        return types == null ? null : types.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    /**
     * Returns the URIs of the inner class files of the given top-level class files. The filtered
     * container must keep them resolvable (without displaying them): type lookup resolves inner types through the parent's children
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

    /**
     * Resolves the class file under which the entry's type is displayed. The type metadata tells
     * the outer type, since a '$' in a name alone does not make a class an inner class.
     */
    private Container.Entry getTopLevelOuterEntry(Container.Entry entry, Type type) {
        Container.Entry current = entry;
        Type currentType = type;
        for (int depth = 0; depth < MAX_NESTING && current != null && currentType != null && currentType.getOuterName() != null; depth++) {
            current = sibling(current, currentType.getOuterName());
            currentType = current == null ? null : firstType(typeLoader.apply(current));
        }
        return currentType == null || currentType.getOuterName() == null ? pathBasedOuterEntry(current) : current;
    }

    private static Container.Entry sibling(Container.Entry entry, String internalName) {
        String path = entry.getPath();
        String directory = path.substring(0, path.lastIndexOf('/') + 1);
        String simpleName = internalName.substring(internalName.lastIndexOf('/') + 1);
        return entry.getParent().getChildren().get(new FileEntryPath(directory + simpleName + StringConstants.CLASS_FILE_SUFFIX));
    }

    /**
     * Fallback for classes without outer type metadata (anonymous and local classes): the outer
     * class file is the one named before the first '$' of the file name, if it exists. Packages may
     * contain '$', and so may the name of a top-level class, which then stays its own entry.
     */
    static Container.Entry pathBasedOuterEntry(Container.Entry entry) {
        if (entry == null) {
            return null;
        }
        String path = entry.getPath();
        int idx = path.indexOf(StringConstants.INTERNAL_INNER_SEPARATOR, path.lastIndexOf('/') + 1);
        if (idx != -1 && path.endsWith(StringConstants.CLASS_FILE_SUFFIX) && entry.getParent() != null) {
            Container.Entry outer = entry.getParent().getChildren().get(new FileEntryPath(path.substring(0, idx) + StringConstants.CLASS_FILE_SUFFIX));
            if (outer != null) {
                return outer;
            }
        }
        return entry;
    }

    private void collectTypeTree(Container.Entry outerEntry, Type type, boolean nested) {
        if (type != null) {
            // Always show the type node for every match (declaration or reference), so the user
            // sees the class/interface/enum/annotation/record icon instead of just the file icon.
            addTypeFragmentWithAncestors(fragmentUris, outerEntry, type.getName(), nested);
            collectMemberFragments(fragmentUris, outerEntry, type);
            for (Type innerType : innerTypesOf(type)) {
                fragmentUris.addAll(collectNestedType(outerEntry, innerType));
            }
        }
    }

    /**
     * Collects the nodes of a type nested in a matching type. The type is only displayed when it
     * declares a match, or contains a nested type that does: the class file matched through its
     * outer type or another member, not through every type it happens to declare.
     */
    private Set<URI> collectNestedType(Container.Entry outerEntry, Type type) {
        Set<URI> found = new HashSet<>();
        if (type != null) {
            collectMemberFragments(found, outerEntry, type);
            for (Type innerType : innerTypesOf(type)) {
                found.addAll(collectNestedType(outerEntry, innerType));
            }
            if (!found.isEmpty() || declaredTypeMatches(type)) {
                addTypeFragmentWithAncestors(found, outerEntry, type.getName(), true);
            }
        }
        return found;
    }

    private static Collection<Type> innerTypesOf(Type type) {
        Collection<Type> innerTypes = type.getInnerTypes();
        return innerTypes == null ? List.of() : innerTypes;
    }

    private boolean declaredTypeMatches(Type type) {
        return declaredTypePattern != null && declaredTypePattern.matcher(lastIdentifier(type.getName())).matches();
    }

    /** For declarations only, we can pinpoint specific matching members. */
    private void collectMemberFragments(Set<URI> target, Container.Entry outerEntry, Type type) {
        if (declarations) {
            collectConstructorFragments(target, outerEntry, type);
            collectMethodFragments(target, outerEntry, type);
            collectFieldFragments(target, outerEntry, type);
        }
    }

    /** Constructor declarations are indexed by type simple name. */
    private void collectConstructorFragments(Set<URI> target, Container.Entry outerEntry, Type type) {
        if (typePattern != null && typePattern.matcher(lastIdentifier(type.getName())).matches()) {
            for (Type.Method method : type.getMethods()) {
                if (StringConstants.INSTANCE_CONSTRUCTOR.equals(method.getName())) {
                    addMemberFragment(target, outerEntry, type, method.getName(), method.getDescriptor());
                }
            }
        }
    }

    /** Method declarations are matched by method name. */
    private void collectMethodFragments(Set<URI> target, Container.Entry outerEntry, Type type) {
        if (memberPattern != null && (flags & SearchInConstantPoolsView.SEARCH_METHOD) != 0) {
            for (Type.Method method : type.getMethods()) {
                String methodName = method.getName();
                if (!StringConstants.INSTANCE_CONSTRUCTOR.equals(methodName) && !STATIC_INITIALIZER.equals(methodName) && memberPattern.matcher(methodName).matches()) {
                    addMemberFragment(target, outerEntry, type, methodName, method.getDescriptor());
                }
            }
        }
    }

    /** Field declarations are matched by field name. */
    private void collectFieldFragments(Set<URI> target, Container.Entry outerEntry, Type type) {
        if (memberPattern != null && (flags & SearchInConstantPoolsView.SEARCH_FIELD) != 0) {
            for (Type.Field field : type.getFields()) {
                if (memberPattern.matcher(field.getName()).matches()) {
                    addMemberFragment(target, outerEntry, type, field.getName(), field.getDescriptor());
                }
            }
        }
    }

    private static void addMemberFragment(Set<URI> target, Container.Entry outerEntry, Type type, String name, String descriptor) {
        target.add(fragmentUri(outerEntry, type.getName() + '-' + name + '-' + descriptor));
    }

    /**
     * Adds the members of the type that hold a matching reference. A reference in a member that
     * has no tree node (static initializer, synthetic method) is covered by the type node.
     */
    private void collectReferenceFragments(Container.Entry outerEntry, Container.Entry entry, Type type) {
        if (type == null) {
            return;
        }
        ReferenceScanner.Result result = referenceScanner.apply(entry);

        for (Type.Method method : type.getMethods()) {
            if (!STATIC_INITIALIZER.equals(method.getName()) && result.methods().contains(method.getName() + method.getDescriptor())) {
                addMemberFragment(fragmentUris, outerEntry, type, method.getName(), method.getDescriptor());
            }
        }
        for (Type.Field field : type.getFields()) {
            if (result.fields().contains(field.getName() + field.getDescriptor())) {
                addMemberFragment(fragmentUris, outerEntry, type, field.getName(), field.getDescriptor());
            }
        }
    }

    /**
     * Adds the fragment URI for the given type name plus, for a nested type, all ancestor type
     * URIs (stripping '$'-delimited suffixes) so that the full path in the tree is visible.
     */
    private static void addTypeFragmentWithAncestors(Set<URI> target, Container.Entry outerEntry, String typeName, boolean nested) {
        target.add(fragmentUri(outerEntry, typeName));
        if (!nested) {
            return;
        }
        String name = typeName;
        // Only the simple name can hold inner type separators, packages may contain '$'
        int simpleNameStart = typeName.lastIndexOf('/') + 1;
        int lastDollar;
        while ((lastDollar = name.lastIndexOf('$')) >= simpleNameStart && lastDollar > 0) {
            name = name.substring(0, lastDollar);
            target.add(fragmentUri(outerEntry, name));
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
