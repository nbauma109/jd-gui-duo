/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Marker;
import org.jd.core.v1.printer.StringBuilderPrinter;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.index.IndexesUtil;

import java.awt.Point;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.swing.text.Segment;

import static jd.core.preferences.Preferences.ESCAPE_UNICODE_CHARACTERS;
import static org.jd.core.v1.api.printer.Printer.MODULE;
import static org.jd.core.v1.api.printer.Printer.PACKAGE;
import static org.jd.core.v1.api.printer.Printer.TYPE;

import jd.core.ClassUtil;
import jd.core.links.DeclarationData;
import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;

public class ModuleInfoFilePage extends ClassFilePage {

    private static final long serialVersionUID = 1L;

    public static final String SYNTAX_STYLE_JAVA_MODULE = "text/java-module";

    static {
        // Add a new token maker for Java 9+ module
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory)TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_JAVA_MODULE, ModuleInfoTokenMaker.class.getName());
    }

    public ModuleInfoFilePage(API api, Container.Entry entry) {
        super(api, entry);
    }

    @Override
    public void decompile(Map<String, String> preferences) {
        try {
            // Clear ...
            clearHyperlinks();
            clearLineNumbers();
            listener.getTypeDeclarations().clear();

            // Init preferences
            boolean unicodeEscape = Boolean.parseBoolean(preferences.getOrDefault(ESCAPE_UNICODE_CHARACTERS, Boolean.FALSE.toString()));

            // Init loader
            ContainerLoader loader = new ContainerLoader(entry);

            // Init printer
            ModuleInfoFilePrinter printer = new ModuleInfoFilePrinter();
            printer.setUnicodeEscape(unicodeEscape);

            // Format internal name
            String entryInternalName = ClassUtil.getInternalName(entry.getPath());

            // Decompile class file
            DECOMPILER.decompile(loader, printer, entryInternalName);
        } catch (Exception t) {
            assert ExceptionUtil.printStackTrace(t);
            setText("// INTERNAL ERROR //");
        }
    }

    @Override
    public String getSyntaxStyle() { return SYNTAX_STYLE_JAVA_MODULE; }

    @Override
    protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
        HyperlinkReferenceData hyperlinkReferenceData = (HyperlinkReferenceData)hyperlinkData;

        if (hyperlinkReferenceData.getReference().isEnabled()) {
            try {
                // Save current position in history
                Point location = textArea.getLocationOnScreen();
                int offset = textArea.viewToModel2D(new Point(x - location.x, y - location.y));
                URI uri = entry.getUri();
                api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));

                // Open link
                ModuleInfoReferenceData moduleInfoReferenceData = (ModuleInfoReferenceData)hyperlinkReferenceData.getReference();
                List<Container.Entry> entries;
                String fragment;

                switch (moduleInfoReferenceData.type) {
                    case TYPE:
                        fragment = moduleInfoReferenceData.getTypeName();
                        entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, fragment);
                        break;
                    case PACKAGE:
                        entries = IndexesUtil.find(collectionOfFutureIndexes, "packageDeclarations", moduleInfoReferenceData.getTypeName());
                        fragment = null;
                        break;
                    default: // MODULE
                        entries = IndexesUtil.find(collectionOfFutureIndexes, "javaModuleDeclarations", moduleInfoReferenceData.getName());
                        fragment = moduleInfoReferenceData.getTypeName();
                        break;
                }

                if (entries.contains(entry)) {
                    api.openURI(uri);
                } else {
                    String rootUri = entry.getContainer().getRoot().getUri().toString();
                    List<Container.Entry> sameContainerEntries = new ArrayList<>();

                    for (Container.Entry entry : entries) {
                        if (entry.getUri().toString().startsWith(rootUri)) {
                            sameContainerEntries.add(entry);
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

    // --- UriOpenable --- //
    @Override
    public boolean openUri(URI uri) {
        List<DocumentRange> ranges = new ArrayList<>();
        String fragment = uri.getFragment();
        String query = uri.getQuery();

        Marker.clearMarkAllHighlights(textArea);

        if (fragment != null && listener.getDeclarations().size() == 1) {
            DeclarationData declaration = listener.getDeclarations().entrySet().iterator().next().getValue();

            if (fragment.equals(declaration.getTypeName())) {
                ranges.add(new DocumentRange(declaration.getStartPosition(), declaration.getEndPosition()));
            }
        }

        if (query != null) {
            Map<String, String> parameters = parseQuery(query);

            String highlightFlags = parameters.get("highlightFlags");
            String highlightPattern = parameters.get("highlightPattern");

            if (highlightFlags != null && highlightPattern != null) {
                String regexp = createRegExp(highlightPattern);
                Pattern pattern = Pattern.compile(regexp + ".*");

                boolean t = highlightFlags.indexOf('t') != -1; // Highlight types
                boolean m = highlightFlags.indexOf('M') != -1; // Highlight modules

                if (highlightFlags.indexOf('d') != -1) {
                    // Highlight declarations
                    for (Map.Entry<String, DeclarationData> entry : listener.getDeclarations().entrySet()) {
                        DeclarationData declaration = entry.getValue();

                        if (m) {
                            matchAndAddDocumentRange(pattern, declaration.getName(), declaration.getStartPosition(), declaration.getEndPosition(), ranges);
                        }
                    }
                }

                if (highlightFlags.indexOf('r') != -1) {
                    // Highlight references
                    for (Map.Entry<Integer, HyperlinkData> entry : hyperlinks.entrySet()) {
                        HyperlinkData hyperlink = entry.getValue();
                        ReferenceData reference = ((HyperlinkReferenceData)hyperlink).getReference();
                        ModuleInfoReferenceData moduleInfoReferenceData = (ModuleInfoReferenceData)reference;

                        if (t && moduleInfoReferenceData.type == TYPE) {
                            matchAndAddDocumentRange(pattern, getMostInnerTypeName(moduleInfoReferenceData.getTypeName()), hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
                        }
                        if (m && moduleInfoReferenceData.type == MODULE) {
                            matchAndAddDocumentRange(pattern, moduleInfoReferenceData.getName(), hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
                        }
                    }
                }
            }
        }

        if (!ranges.isEmpty()) {
            textArea.setMarkAllHighlightColor(SELECT_HIGHLIGHT_COLOR);
            Marker.markAll(textArea, ranges);
            Collections.sort(ranges);
            setCaretPositionAndCenter(ranges.get(0));
        }

        return true;
    }

    // --- IndexesChangeListener --- //
    @Override
    public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
        // Update the list of containers
        this.collectionOfFutureIndexes = collectionOfFutureIndexes;
        // Refresh links
        boolean refresh = false;

        for (ReferenceData reference : listener.getReferences()) {
            ModuleInfoReferenceData moduleInfoReferenceData = (ModuleInfoReferenceData)reference;
            boolean enabled = false;

            try {
                for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
                    if (futureIndexes.isDone()) {
                        @SuppressWarnings("rawtypes")
                        Map<String, Collection> index;
                        String key = switch (moduleInfoReferenceData.type) {
                        case TYPE -> {
                            index = futureIndexes.get().getIndex("typeDeclarations");
                            yield reference.getTypeName();
                        }
                        case PACKAGE -> {
                            index = futureIndexes.get().getIndex("packageDeclarations");
                            yield reference.getTypeName();
                        }
                        default -> {
                            index = futureIndexes.get().getIndex("javaModuleDeclarations");
                            yield reference.getName();
                        }
                        };

                        if (index != null && index.get(key) != null) {
                            enabled = true;
                            break;
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

            if (reference.isEnabled() != enabled) {
                reference.setEnabled(enabled);
                refresh = true;
            }
        }

        if (refresh) {
            textArea.repaint();
        }
    }

    protected static class ModuleInfoReferenceData extends ReferenceData {
        private final int type;

        public ModuleInfoReferenceData(int type, String typeName, String name, String descriptor, String owner) {
            super(typeName, name, descriptor, owner);
            this.type = type;
        }
    }

    public class ModuleInfoFilePrinter extends StringBuilderPrinter {
        private final Map<String, ReferenceData> referencesCache = new HashMap<>();

        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {}

        @Override
        public void end() {
            setText(stringBuffer.toString());
            initLineNumbers();
        }

        @Override
        public void printDeclaration(int type, String internalTypeName, String name, String descriptor) {
            listener.addDeclaration(internalTypeName, new DeclarationData(stringBuffer.length(), name.length(), internalTypeName, name, descriptor));
            super.printDeclaration(type, internalTypeName, name, descriptor);
        }

        @Override
        public void printReference(int type, String internalTypeName, String name, String descriptor, String ownerInternalName) {
            String key = type == MODULE ? name : internalTypeName;
            ReferenceData reference = referencesCache.computeIfAbsent(key, k -> {
                ReferenceData moduleInfoReferenceData = new ModuleInfoReferenceData(type, internalTypeName, name, descriptor, ownerInternalName);
                listener.getReferences().add(moduleInfoReferenceData);
                return moduleInfoReferenceData;
            });

            addHyperlink(new HyperlinkReferenceData(stringBuffer.length(), name.length(), reference));
            super.printReference(type, internalTypeName, name, descriptor, ownerInternalName);
        }
    }

    // https://github.com/bobbylight/RSyntaxTextArea/wiki/Adding-Syntax-Highlighting-for-a-new-Language
    public static class ModuleInfoTokenMaker extends AbstractTokenMaker {
        @Override
        public TokenMap getWordsToHighlight() {
            TokenMap tokenMap = new TokenMap();

            tokenMap.put("exports", TokenTypes.RESERVED_WORD);
            tokenMap.put("module", TokenTypes.RESERVED_WORD);
            tokenMap.put("open", TokenTypes.RESERVED_WORD);
            tokenMap.put("opens", TokenTypes.RESERVED_WORD);
            tokenMap.put("provides", TokenTypes.RESERVED_WORD);
            tokenMap.put("requires", TokenTypes.RESERVED_WORD);
            tokenMap.put("to", TokenTypes.RESERVED_WORD);
            tokenMap.put("transitive", TokenTypes.RESERVED_WORD);
            tokenMap.put("uses", TokenTypes.RESERVED_WORD);
            tokenMap.put("with", TokenTypes.RESERVED_WORD);

            return tokenMap;
        }

        @Override
        public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
            // This assumes all keywords, etc. were parsed as "identifiers."
            if (tokenType==TokenTypes.IDENTIFIER) {
                int value = wordsToHighlight.get(segment, start, end);
                if (value != -1) {
                    tokenType = value;
                }
            }
            super.addToken(segment, start, end, tokenType, startOffset);
        }

        @Override
        public Token getTokenList(Segment text, int startTokenType, int startOffset) {
            resetTokenList();

            char[] array = text.array;
            int offset = text.offset;
            int end = offset + text.count;

            int newStartOffset = startOffset - offset;

            int currentTokenStart = offset;
            int currentTokenType  = startTokenType;

            for (int i=offset; i<end; i++) {
                char c = array[i];

                switch (currentTokenType) {
                    case TokenTypes.NULL:
                        currentTokenStart = i;   // Starting a new token here.
                        if (RSyntaxUtilities.isLetter(c) || c == '_') {
                            currentTokenType = TokenTypes.IDENTIFIER;
                        } else {
                            currentTokenType = TokenTypes.WHITESPACE;
                        }
                        break;
                    default: // Should never happen
                    case TokenTypes.WHITESPACE:
                        if (RSyntaxUtilities.isLetter(c) || c == '_') {
                            addToken(text, currentTokenStart, i-1, TokenTypes.WHITESPACE, newStartOffset+currentTokenStart);
                            currentTokenStart = i;
                            currentTokenType = TokenTypes.IDENTIFIER;
                        }
                        break;
                    case TokenTypes.IDENTIFIER:
                        if (!RSyntaxUtilities.isLetterOrDigit(c) && c != '_' && c != '.') {
                            addToken(text, currentTokenStart, i-1, TokenTypes.IDENTIFIER, newStartOffset+currentTokenStart);
                            currentTokenStart = i;
                            currentTokenType = TokenTypes.WHITESPACE;
                        }
                        break;
                }
            }

            if ((currentTokenType != TokenTypes.NULL)) {
                addToken(text, currentTokenStart,end-1, currentTokenType, newStartOffset+currentTokenStart);
            }
            addNullToken();

            return firstToken;
        }
    }
}
