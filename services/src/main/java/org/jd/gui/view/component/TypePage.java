/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2022-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.fife.ui.rtextarea.DocumentRange;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.FocusedTypeGettable;
import org.jd.gui.api.feature.IndexesChangeListener;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.api.model.Type;
import org.jd.gui.util.index.IndexesUtil;
import org.jd.gui.util.matcher.DescriptorMatcher;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.gui.util.parser.jdt.RSyntaxASTParser;

import java.awt.Point;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jd.core.links.DeclarationData;
import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;
import jd.core.links.StringData;

public abstract class TypePage extends CustomLineNumbersPage
        implements UriGettable, IndexesChangeListener, FocusedTypeGettable {

    private static final long serialVersionUID = 1L;

    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("/\\*\s*(\\d+)\s*\\*/");

    protected final transient API api;
    protected final transient Container.Entry entry;
    protected transient Collection<Future<Indexes>> collectionOfFutureIndexes = Collections.emptyList();

    protected final transient ReferenceListener listener;
    protected final transient RSyntaxASTParser astParser;

    protected TypePage(API api, Container.Entry entry) {
    	super(api);

    	// Init attributes
        this.api = api;
        this.entry = entry;
        this.listener = new ReferenceListener(entry);
        this.listener.setHyperlinks(hyperlinks);
        this.astParser = new RSyntaxASTParser(entry, api);
        this.textArea.addParser(astParser);
    }

    @Override
    protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) {
        return ((HyperlinkReferenceData) hyperlinkData).getReference().isEnabled();
    }

    @Override
    protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
        HyperlinkReferenceData hyperlinkReferenceData = (HyperlinkReferenceData) hyperlinkData;

        if (hyperlinkReferenceData.getReference().isEnabled()) {
            try {
                // Save current position in history
                Point location = textArea.getLocationOnScreen();
                int offset = textArea.viewToModel2D(new Point(x - location.x, y - location.y));
                URI uri = entry.getUri();
                api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));

                // Open link
                ReferenceData reference = hyperlinkReferenceData.getReference();
                String typeName = reference.getTypeName();
                List<Container.Entry> entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, typeName);
                String fragment = typeName;

                if (reference.getName() != null) {
                    fragment += '-' + reference.getName();
                }
                if (reference.getDescriptor() != null) {
                    fragment += '-' + reference.getDescriptor();
                }

                if (entries.contains(entry)) {
                    api.openURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), fragment));
                } else {
                    String rootUri = entry.getContainer().getRoot().getUri().toString();
                    List<Container.Entry> sameContainerEntries = new ArrayList<>();

                    for (Container.Entry nextEntry : entries) {
                        if (nextEntry.getUri().toString().startsWith(rootUri)) {
                            sameContainerEntries.add(nextEntry);
                        }
                    }

                    if (!sameContainerEntries.isEmpty()) {
                        api.openURI(x, y, sameContainerEntries, null, fragment);
                    } else if (!entries.isEmpty()) {
                        api.openURI(x, y, entries, null, fragment);
                    }
                }
            } catch (URISyntaxException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }

    // --- UriGettable --- //
    @Override
    public URI getUri() {
        return entry.getUri();
    }

    // --- UriOpenable --- //
    /**
     * @param uri for URI format, @see jd.gui.api.feature.UriOpenable
     */
    @Override
    public boolean openUri(URI uri) {
        List<DocumentRange> ranges = new ArrayList<>();
        String fragment = uri.getFragment();
        String query = uri.getQuery();

        clearActiveHighlights();

        if (fragment != null) {
            matchFragmentAndAddDocumentRange(fragment, listener.getDeclarations(), ranges);
        }
        // The selected fragment decides where to scroll, not the earliest search highlight of the file
        List<DocumentRange> fragmentRanges = new ArrayList<>(ranges);

        if (query != null) {
            Map<String, String> parameters = parseQuery(query);

            if (parameters.containsKey("lineNumber")) {
                String lineNumber = parameters.get("lineNumber");

                try {
                    goToLineNumber(Integer.parseInt(lineNumber));
                    return true;
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                    return false;
                }
            }
            if (parameters.containsKey("position")) {
                String position = parameters.get("position");

                try {
                    int pos = Integer.parseInt(position);
                    if (textArea.getDocument().getLength() > pos) {
                        ranges.add(new DocumentRange(pos, pos));
                    }
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                    return false;
                }
            } else {
                matchQueryAndAddDocumentRange(parameters, listener.getDeclarations(), hyperlinks, listener.getStrings(),
                        ranges);
            }
        }

        if (!ranges.isEmpty()) {
            setSelectionHighlightColor();
            textArea.markAll(ranges);
            ranges.sort(Comparator.naturalOrder());
            setCaretPositionAndCenter(fragmentRanges.isEmpty() ? ranges.get(0) : Collections.min(fragmentRanges));
        }

        return true;
    }

    public static void matchFragmentAndAddDocumentRange(String fragment, Map<String, DeclarationData> declarations,
            List<DocumentRange> ranges) {
        if (DescriptorMatcher.hasHints(fragment)) {
            // The call has no binding: the types of its arguments are hints. Look for a method that has them, else for the ones that have as many parameters
            int found = ranges.size();
            matchExactFragmentAndAddDocumentRange(DescriptorMatcher.confirmHints(fragment), declarations, ranges);
            if (ranges.size() == found) {
                matchExactFragmentAndAddDocumentRange(DescriptorMatcher.dropHints(fragment), declarations, ranges);
            }
        } else {
            matchExactFragmentAndAddDocumentRange(fragment, declarations, ranges);
        }
    }

    private static void matchExactFragmentAndAddDocumentRange(String fragment, Map<String, DeclarationData> declarations,
            List<DocumentRange> ranges) {
        if (fragment.indexOf('?') == -1 && fragment.indexOf('*') == -1) {
            addDeclaration(fragment, declarations, ranges);
            return;
        }
        // Unknown type and/or descriptor ==> Select all and scroll to the first one
        int lastDash = fragment.lastIndexOf('-');
        if (lastDash == -1) {
            addTypeDeclarations(fragment, declarations, ranges);
        } else {
            String prefix = fragment.substring(0, lastDash + 1);
            String suffix = fragment.substring(lastDash + 1);
            BiPredicate<String, String> matchDescriptors = suffix.charAt(0) == '(' ? DescriptorMatcher::matchMethodDescriptors : DescriptorMatcher::matchFieldDescriptors;
            Predicate<String> sameMember;

            if (fragment.charAt(0) == '*') {
                // Unknown type
                String slashAndTypeNameAndName = prefix.substring(1);
                String typeNameAndName = prefix.substring(2);
                sameMember = key -> key.contains(slashAndTypeNameAndName) || key.startsWith(typeNameAndName);
            } else {
                // Known type
                sameMember = key -> key.startsWith(prefix);
            }
            addMembers(declarations, sameMember, suffix, matchDescriptors, ranges);
        }
    }

    /** Types: the name of the type, preceded by any package (the fragment starts with the wildcard for the package). */
    private static void addTypeDeclarations(String fragment, Map<String, DeclarationData> declarations, List<DocumentRange> ranges) {
        String slashAndTypeName = fragment.substring(1);
        String typeName = fragment.substring(2);

        for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
            if (entry.getKey().endsWith(slashAndTypeName) || entry.getKey().equals(typeName)) {
                ranges.add(new DocumentRange(entry.getValue().getStartPosition(), entry.getValue().getEndPosition()));
            }
        }
    }

    /** The members of the same type and name (as the keys say) whose descriptor matches. */
    private static void addMembers(Map<String, DeclarationData> declarations, Predicate<String> sameMember, String suffix,
            BiPredicate<String, String> matchDescriptors, List<DocumentRange> ranges) {
        for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
            String key = entry.getKey();
            if (sameMember.test(key) && matchDescriptors.test(suffix, key.substring(key.lastIndexOf('-') + 1))) {
                ranges.add(new DocumentRange(entry.getValue().getStartPosition(), entry.getValue().getEndPosition()));
            }
        }
    }

    /** Known type and descriptor ==> Search and high light item. */
    private static void addDeclaration(String fragment, Map<String, DeclarationData> declarations, List<DocumentRange> ranges) {
        DeclarationData data = declarations.get(fragment);
        if (data != null) {
            ranges.add(new DocumentRange(data.getStartPosition(), data.getEndPosition()));
        } else if (fragment.endsWith("-<clinit>-()V")) {
            // 'static' bloc not found ==> Select type declaration
            String typeName = fragment.substring(0, fragment.indexOf('-'));
            data = declarations.get(typeName);
            ranges.add(new DocumentRange(data.getStartPosition(), data.getEndPosition()));
        }
    }

    public static void matchQueryAndAddDocumentRange(Map<String, String> parameters,
            Map<String, DeclarationData> declarations, Map<Integer, HyperlinkData> hyperlinks, List<StringData> strings,
            List<DocumentRange> ranges) {

        String highlightFlags = parameters.get("highlightFlags");
        String highlightPattern = parameters.get("highlightPattern");

        if (highlightFlags != null && highlightPattern != null) {
            String highlightScope = parameters.get("highlightScope");
            String regexp = createRegExp(highlightPattern);
            Pattern pattern = Pattern.compile(regexp + ".*");

            if (highlightFlags.indexOf('s') != -1) {
                // Highlight strings
                Pattern patternForString = Pattern.compile(regexp);

                for (StringData data : strings) {
                    if (matchScope(highlightScope, data.owner())) {
                        Matcher matcher = patternForString.matcher(data.text());
                        int offset = data.startPosition();

                        while (matcher.find()) {
                            ranges.add(new DocumentRange(offset + matcher.start(), offset + matcher.end()));
                        }
                    }
                }
            }

            boolean t = highlightFlags.indexOf('t') != -1; // Highlight types
            boolean f = highlightFlags.indexOf('f') != -1; // Highlight fields
            boolean m = highlightFlags.indexOf('m') != -1; // Highlight methods
            boolean c = highlightFlags.indexOf('c') != -1; // Highlight constructors

            if (highlightFlags.indexOf('d') != -1) {
                // Highlight declarations
                for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
                    DeclarationData declaration = entry.getValue();

                    if (matchScope(highlightScope, declaration.getTypeName())) {
                        if (t && declaration.isAType() || c && declaration.isAConstructor()) {
                            matchAndAddDocumentRange(pattern, getMostInnerTypeName(declaration.getTypeName()),
                                    declaration.getStartPosition(), declaration.getEndPosition(), ranges);
                        }
                        if (f && declaration.isAField() || m && declaration.isAMethod()) {
                            matchAndAddDocumentRange(pattern, declaration.getName(), declaration.getStartPosition(),
                                    declaration.getEndPosition(), ranges);
                        }
                    }
                }
            }

            if (highlightFlags.indexOf('r') != -1) {
                // Highlight references
                for (Map.Entry<Integer, HyperlinkData> entry : hyperlinks.entrySet()) {
                    HyperlinkData hyperlink = entry.getValue();
                    ReferenceData reference = ((HyperlinkReferenceData) hyperlink).getReference();

                    if (matchScope(highlightScope, reference.getOwner())) {
                        if (t && reference.isAType() || c && reference.isAConstructor()) {
                            matchAndAddDocumentRange(pattern, getMostInnerTypeName(reference.getTypeName()),
                                    hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
                        }
                        if (f && reference.isAField() || m && reference.isAMethod()) {
                            matchAndAddDocumentRange(pattern, reference.getName(), hyperlink.getStartPosition(),
                                    hyperlink.getEndPosition(), ranges);
                        }
                    }
                }
            }
        }
    }

    public static boolean matchScope(String scope, String type) {
        if (scope == null || scope.isEmpty()) {
            return true;
        }
        if (scope.charAt(0) == '*') {
            return type.endsWith(scope.substring(1)) || type.equals(scope.substring(2));
        }
        return type.equals(scope);
    }

    public static void matchAndAddDocumentRange(Pattern pattern, String text, int start, int end,
            List<DocumentRange> ranges) {
        if (pattern.matcher(text).matches()) {
            ranges.add(new DocumentRange(start, end));
        }
    }

    public static String getMostInnerTypeName(String typeName) {
        int lastPackageSeparatorIndex = typeName.lastIndexOf('/') + 1;
        int lastTypeNameSeparatorIndex = typeName.lastIndexOf('$') + 1;
        int lastIndex = Math.max(lastPackageSeparatorIndex, lastTypeNameSeparatorIndex);
        return typeName.substring(lastIndex);
    }

    // --- FocusedTypeGettable --- //
    @Override
    public String getFocusedTypeName() {
        Map.Entry<Integer, DeclarationData> nextEntry = listener.getTypeDeclarations()
                .floorEntry(textArea.getCaretPosition());

        if (nextEntry != null) {
            DeclarationData data = nextEntry.getValue();
            if (data != null) {
                return data.getTypeName();
            }
        }

        return null;
    }

    @Override
    public Container.Entry getEntry() {
        return entry;
    }

    // --- IndexesChangeListener --- //
    @Override
    public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
        // Update the list of containers
        this.collectionOfFutureIndexes = collectionOfFutureIndexes;
        // Refresh links
        boolean refresh = false;

        for (ReferenceData reference : listener.getReferences()) {
            String typeName = reference.getTypeName();
            boolean enabled;

            if (reference.getName() == null) {
                enabled = false;

                try {
                    for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
                        if (futureIndexes.isDone()) {
                            @SuppressWarnings("rawtypes")
                            Map<String, Collection> index = futureIndexes.get().getIndex("typeDeclarations");
                            if (index != null && index.get(typeName) != null) {
                                enabled = true;
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    assert ExceptionUtil.printStackTrace(e);
                    // Restore interrupted state...
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else {
                try {
                    // Recursive search
                    typeName = searchTypeHavingMember(typeName, reference.getName(), reference.getDescriptor(), entry);
                    if (typeName != null) {
                        // Replace type with the real type having the referenced member
                        reference.setTypeName(typeName);
                        enabled = true;
                    } else {
                        enabled = false;
                    }
                } catch (StackOverflowError | OutOfMemoryError e) {
                    // Catch StackOverflowError or OutOfMemoryError
                    assert ExceptionUtil.printStackTrace(e);
                    enabled = false;
                }
            }

            if (reference.isEnabled() != enabled) {
                reference.setEnabled(enabled);
                refresh = true;
            }
        }

        if (refresh) {
            textArea.repaint();
        }
    }

    @SuppressWarnings({ "rawtypes" })
    protected String searchTypeHavingMember(String typeName, String name, String descriptor, Container.Entry entry) {
        List<Container.Entry> entries = new ArrayList<>();

        try {
            for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
                if (futureIndexes.isDone()) {
                    Map<String, Collection> index = futureIndexes.get().getIndex("typeDeclarations");
                    if (index != null) {
                        @SuppressWarnings("unchecked")
                        Collection<Container.Entry> collection = index.get(typeName);
                        if (collection != null) {
                            entries.addAll(collection);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            assert ExceptionUtil.printStackTrace(e);
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            assert ExceptionUtil.printStackTrace(e);
        }

        String rootUri = entry.getContainer().getRoot().getUri().toString();
        List<Container.Entry> sameContainerEntries = new ArrayList<>();

        for (Container.Entry e : entries) {
            if (e.getUri().toString().startsWith(rootUri)) {
                sameContainerEntries.add(e);
            }
        }

        if (!sameContainerEntries.isEmpty()) {
            return searchTypeHavingMember(typeName, name, descriptor, sameContainerEntries);
        }
        return searchTypeHavingMember(typeName, name, descriptor, entries);
    }

    protected String searchTypeHavingMember(String typeName, String name, String descriptor,
            List<Container.Entry> entries) {
        for (Container.Entry nextEntry : entries) {
            Type type = api.getTypeFactory(nextEntry).make(api, nextEntry, typeName);

            if (type != null) {
                if (descriptor.indexOf('(') == -1) {
                    // Search a field
                    for (Type.Field field : type.getFields()) {
                        if (field.getName().equals(name)
                                && DescriptorMatcher.matchFieldDescriptors(field.getDescriptor(), descriptor)) {
                            // Field found
                            return typeName;
                        }
                    }
                } else {
                    // Search a method: when the types of the arguments are only hints (a call without binding), a method having
                    // them is looked for first, then one having as many parameters
                    boolean hints = DescriptorMatcher.hasHints(descriptor);
                    String required = hints ? DescriptorMatcher.confirmHints(descriptor) : descriptor;
                    if (hasMethod(type, name, required) || (hints && hasMethod(type, name, DescriptorMatcher.dropHints(descriptor)))) {
                        // Method found
                        return typeName;
                    }
                }

                // Not found -> Search in super type
                String typeOwnerName = searchTypeHavingMember(type.getSuperName(), name, descriptor, nextEntry);
                if (typeOwnerName != null) {
                    return typeOwnerName;
                }
            }
        }

        return null;
    }

    private static boolean hasMethod(Type type, String name, String descriptor) {
        for (Type.Method method : type.getMethods()) {
            if (method.getName().equals(name) && DescriptorMatcher.matchMethodDescriptors(method.getDescriptor(), descriptor)) {
                return true;
            }
        }
        return false;
    }

    public void parseAndSetText(String text) {
        listener.clearData();
        // Parse
        char[] source = text.toCharArray();
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        String unitName = entry.getPath();
        // 1st pass for declarations
        ASTParser declarationParser = ASTParserFactory.getInstance().newASTParser(api, source, unitName, jarURI);
        declarationParser.createAST(null).accept(listener.getDeclarationListener());
        listener.init();
        // 2nd pass for references
        ASTParser astParserWithBindings = ASTParserFactory.getInstanceWithBindings().newASTParser(api, source, unitName, jarURI);
        ASTNode astNode = astParserWithBindings.createAST(null);
        astNode.accept(listener);
        // Display
        setText(text);
        initLineNumbers();
        setMisalignedLineNumbers(astNode, text);
    }

    private void setMisalignedLineNumbers(ASTNode astNode, String text) {
        if (astNode instanceof CompilationUnit cu) {
            @SuppressWarnings("unchecked")
            List<Comment> commentList = cu.getCommentList();
            for (Comment comment : commentList) {
                int startPosition = comment.getStartPosition();
                int endPosition = startPosition + comment.getLength();
                int startLineNumber = cu.getLineNumber(startPosition);
                int endLineNumber = cu.getLineNumber(endPosition);
                int columnNumber = cu.getColumnNumber(startPosition);
                if (comment.isBlockComment() && columnNumber == 0 && startLineNumber == endLineNumber) {
                    String commentText = text.substring(startPosition, endPosition);
                    Matcher commentMatcher = LINE_COMMENT_PATTERN.matcher(commentText);
                    if (commentMatcher.matches()) {
                        String lineNumberText = commentMatcher.group(1);
                        int originalLineNumber = Integer.parseInt(lineNumberText);
                        int textAreaLineNumber = startLineNumber;
                        if (originalLineNumber != textAreaLineNumber) {
                            setLineNumber(textAreaLineNumber, originalLineNumber);
                        }
                    }
                }
            }
        }
    }
}
