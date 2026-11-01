/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.model.Container;
import org.jd.gui.util.parser.jdt.core.AbstractJavaListener;
import org.jd.gui.view.SearchInConstantPoolsView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds which types, methods and fields of a Java source file hold a reference matching a search.
 * It applies the rules of the source indexer (type references, constructor calls, method calls,
 * field accesses and string literals) and names members like the source type factory does, so the
 * results can be matched with the members of its types.
 */
final class SourceReferenceScanner extends AbstractJavaListener {

    /** The method or the fields that the visited code belongs to; neither for type headers and initializers. */
    private record Member(String method, List<String> fields) {
        static final Member NONE = new Member(null, List.of());
    }

    private final Pattern pattern;
    private final int flags;
    private final Deque<String> typeNames = new ArrayDeque<>();
    private final Deque<Member> enclosingMembers = new ArrayDeque<>();
    private final Set<String> typesWithReference = new HashSet<>();
    private final Map<String, Set<String>> methodsByType = new HashMap<>();
    private final Map<String, Set<String>> fieldsByType = new HashMap<>();
    private Member member = Member.NONE;

    private SourceReferenceScanner(Container.Entry entry, Pattern pattern, int flags) {
        super(entry);
        this.pattern = pattern;
        this.flags = flags;
    }

    /**
     * @return the references found by internal type name; a type is present as soon as it holds a
     *         reference, even one that no member node can show (type header, initializer)
     */
    static Map<String, ReferenceScanner.Result> scan(ASTParser parser, Container.Entry entry, Pattern pattern, int flags) {
        SourceReferenceScanner scanner = new SourceReferenceScanner(entry, pattern, flags);
        parser.createAST(null).accept(scanner);

        Map<String, ReferenceScanner.Result> results = new HashMap<>();
        for (String typeName : scanner.typesWithReference) {
            results.put(typeName, new ReferenceScanner.Result(
                    scanner.methodsByType.getOrDefault(typeName, Set.of()), scanner.fieldsByType.getOrDefault(typeName, Set.of())));
        }
        return results;
    }

    // --- Scope tracking --- //

    @Override
    protected boolean enterTypeDeclaration(AbstractTypeDeclaration node, int flag) {
        String name = node.getName().getIdentifier();
        String internalTypeName = internalTypeName(name);

        typeNames.push(internalTypeName);
        nameToInternalTypeName.put(name, internalTypeName);
        enterMember(Member.NONE);
        return true;
    }

    /** Same naming as the source type factory: the package for a root type, the outer type and '$' for a nested one. */
    private String internalTypeName(String name) {
        if (!typeNames.isEmpty()) {
            return typeNames.peek() + '$' + name;
        }
        return packageName.isEmpty() ? name : packageName + '/' + name;
    }

    @Override
    protected void exitTypeDeclaration() {
        typeNames.pop();
        exitMember();
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        String name = node.isConstructor() ? StringConstants.INSTANCE_CONSTRUCTOR : node.getName().getIdentifier();
        @SuppressWarnings("unchecked")
        String paramDescriptors = createParamDescriptors(node.parameters());
        enterMember(new Member(name + paramDescriptors + createDescriptor(node.getReturnType2(), 0), List.of()));
        return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        exitMember();
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        List<String> keys = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<VariableDeclarationFragment> fragments = node.fragments();
        for (VariableDeclarationFragment fragment : fragments) {
            keys.add(fieldKey(node, fragment));
        }
        enterMember(new Member(null, keys));
        return true;
    }

    @Override
    public void endVisit(FieldDeclaration node) {
        exitMember();
    }

    /** The initializer of one declarator belongs to that field only, while the shared type belongs to all of them. */
    @Override
    public boolean visit(VariableDeclarationFragment node) {
        if (node.getParent() instanceof FieldDeclaration field) {
            enterMember(new Member(null, List.of(fieldKey(field, node))));
        }
        return true;
    }

    @Override
    public void endVisit(VariableDeclarationFragment node) {
        if (node.getParent() instanceof FieldDeclaration) {
            exitMember();
        }
    }

    private String fieldKey(FieldDeclaration field, VariableDeclarationFragment fragment) {
        return fragment.getName().getIdentifier() + createDescriptor(field.getType(), fragment.getExtraDimensions());
    }

    @Override
    public boolean visit(Initializer node) {
        enterMember(Member.NONE);
        return true;
    }

    @Override
    public void endVisit(Initializer node) {
        exitMember();
    }

    private void enterMember(Member entered) {
        enclosingMembers.push(member);
        member = entered;
    }

    private void exitMember() {
        member = enclosingMembers.pop();
    }

    // --- References --- //

    @Override
    public boolean visit(SimpleType node) {
        hitIf(matchesType(resolveInternalTypeName(node)));
        return true;
    }

    @Override
    public boolean visit(QualifiedType node) {
        hitIf(matchesType(resolveInternalTypeName(node)));
        return false;
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        hitIf(matchesType(resolveInternalTypeName(node)));
        return false;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        hitIf(has(SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) && pattern.matcher(simpleName(typeToString(node.getType()))).matches());
        return true;
    }

    @Override
    public boolean visit(FieldAccess node) {
        hitIf(has(SearchInConstantPoolsView.SEARCH_FIELD) && pattern.matcher(node.getName().getIdentifier()).matches());
        return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        hitIf(has(SearchInConstantPoolsView.SEARCH_METHOD) && pattern.matcher(node.getName().getIdentifier()).matches());
        return true;
    }

    @Override
    public boolean visit(StringLiteral node) {
        hitIf(has(SearchInConstantPoolsView.SEARCH_STRING) && pattern.matcher(node.getLiteralValue()).matches());
        return true;
    }

    private boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Unresolved types are marked with a leading '*' by the resolver, and are not indexed. */
    private boolean matchesType(String internalTypeName) {
        return has(SearchInConstantPoolsView.SEARCH_TYPE) && internalTypeName.charAt(0) != '*' && pattern.matcher(simpleName(internalTypeName)).matches();
    }

    private static String simpleName(String internalName) {
        return internalName.substring(Math.max(internalName.lastIndexOf('/'), internalName.lastIndexOf('$')) + 1);
    }

    private void hitIf(boolean matching) {
        String typeName = typeNames.peek();
        if (matching && typeName != null) {
            typesWithReference.add(typeName);
            if (member.method() != null) {
                methodsByType.computeIfAbsent(typeName, k -> new HashSet<>()).add(member.method());
            }
            if (!member.fields().isEmpty()) {
                fieldsByType.computeIfAbsent(typeName, k -> new HashSet<>()).addAll(member.fields());
            }
        }
    }
}
