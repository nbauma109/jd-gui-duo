/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.view.component;

import static org.jd.core.v1.api.printer.Printer.MODULE;
import static org.jd.core.v1.api.printer.Printer.PACKAGE;
import static org.jd.core.v1.api.printer.Printer.TYPE;

import java.awt.Point;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.swing.text.Segment;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rtextarea.Marker;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.util.parser.jdt.core.HyperlinkReferenceData;
import org.jd.gui.util.parser.jdt.core.ReferenceData;
import org.jdv1.gui.util.decompiler.StringBuilderPrinter;
import org.jdv1.gui.util.index.IndexesUtil;

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
            boolean unicodeEscape = getPreferenceValue(preferences, ESCAPE_UNICODE_CHARACTERS, false);

            // Init loader
            ContainerLoader loader = new ContainerLoader(entry);

            // Init printer
            ModuleInfoFilePrinter printer = new ModuleInfoFilePrinter();
            printer.setUnicodeEscape(unicodeEscape);

            // Format internal name
            String entryPath = entry.getPath();
            assert entryPath.endsWith(".class");
            String entryInternalName = entryPath.substring(0, entryPath.length() - 6); // 6 = ".class".length()

            // Decompile class file
            DECOMPILER.decompile(loader, printer, entryInternalName);
        } catch (Throwable t) {
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
                int offset = textArea.viewToModel(new Point(x - location.x, y - location.y));
                URI uri = entry.getUri();
                api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));

                // Open link
                ModuleInfoReferenceData moduleInfoReferenceData = (ModuleInfoReferenceData)hyperlinkReferenceData.getReference();
                List<Container.Entry> entries;
                String fragment;

                switch (moduleInfoReferenceData.type) {
                    case TYPE:
                        entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, fragment = moduleInfoReferenceData.getTypeName());
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
                    ArrayList<Container.Entry> sameContainerEntries = new ArrayList<>();

                    for (Container.Entry entry : entries) {
                        if (entry.getUri().toString().startsWith(rootUri)) {
                            sameContainerEntries.add(entry);
                        }
                    }

                    if (sameContainerEntries.size() > 0) {
                        api.openURI(x, y, sameContainerEntries, null, fragment);
                    } else if (entries.size() > 0) {
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
        ArrayList<DocumentRange> ranges = new ArrayList<>();
        String fragment = uri.getFragment();
        String query = uri.getQuery();

        Marker.clearMarkAllHighlights(textArea);

        if ((fragment != null) && (listener.getDeclarations().size() == 1)) {
            DeclarationData declaration = listener.getDeclarations().entrySet().iterator().next().getValue();

            if (fragment.equals(declaration.getTypeName())) {
                ranges.add(new DocumentRange(declaration.getStartPosition(), declaration.getEndPosition()));
            }
        }

        if (query != null) {
            Map<String, String> parameters = parseQuery(query);

            String highlightFlags = parameters.get("highlightFlags");
            String highlightPattern = parameters.get("highlightPattern");

            if ((highlightFlags != null) && (highlightPattern != null)) {
                String regexp = createRegExp(highlightPattern);
                Pattern pattern = Pattern.compile(regexp + ".*");

                boolean t = (highlightFlags.indexOf('t') != -1); // Highlight types
                boolean M = (highlightFlags.indexOf('M') != -1); // Highlight modules

                if (highlightFlags.indexOf('d') != -1) {
                    // Highlight declarations
                    for (Map.Entry<String, DeclarationData> entry : listener.getDeclarations().entrySet()) {
                        DeclarationData declaration = entry.getValue();

                        if (M) {
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

                        if (t && (moduleInfoReferenceData.type == TYPE)) {
                            matchAndAddDocumentRange(pattern, getMostInnerTypeName(moduleInfoReferenceData.getTypeName()), hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
                        }
                        if (M && (moduleInfoReferenceData.type == MODULE)) {
                            matchAndAddDocumentRange(pattern, moduleInfoReferenceData.getName(), hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
                        }
                    }
                }
            }
        }

        if ((ranges != null) && !ranges.isEmpty()) {
            textArea.setMarkAllHighlightColor(SELECT_HIGHLIGHT_COLOR);
            Marker.markAll(textArea, ranges);
            ranges.sort(null);
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
                        Map<String, Collection> index;
                        String key;

                        switch (moduleInfoReferenceData.type) {
                            case TYPE:
                                index = futureIndexes.get().getIndex("typeDeclarations");
                                key = reference.getTypeName();
                                break;
                            case PACKAGE:
                                index = futureIndexes.get().getIndex("packageDeclarations");
                                key = reference.getTypeName();
                                break;
                            default: // MODULE
                                index = futureIndexes.get().getIndex("javaModuleDeclarations");
                                key = reference.getName();
                                break;
                        }

                        if ((index != null) && (index.get(key) != null)) {
                            enabled = true;
                            break;
                        }
                    }
                }
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
        public int type;

        public ModuleInfoReferenceData(int type, String typeName, String name, String descriptor, String owner) {
            super(typeName, name, descriptor, owner);
            this.type = type;
        }
    }

    public class ModuleInfoFilePrinter extends StringBuilderPrinter {
        protected HashMap<String, ReferenceData> referencesCache = new HashMap<>();

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
            String key = (type == MODULE) ? name : internalTypeName;
            ReferenceData reference = referencesCache.get(key);

            if (reference == null) {
                reference = new ModuleInfoReferenceData(type, internalTypeName, name, descriptor, ownerInternalName);
                referencesCache.put(key, reference);
                listener.getReferences().add(reference);
            }

            addHyperlink(new HyperlinkReferenceData(stringBuffer.length(), name.length(), reference));
            super.printReference(type, internalTypeName, name, descriptor, ownerInternalName);
        }
    }

    // https://github.com/bobbylight/RSyntaxTextArea/wiki/Adding-Syntax-Highlighting-for-a-new-Language
    public static class ModuleInfoTokenMaker extends AbstractTokenMaker {
        @Override
        public TokenMap getWordsToHighlight() {
            TokenMap tokenMap = new TokenMap();

            tokenMap.put("exports", Token.RESERVED_WORD);
            tokenMap.put("module", Token.RESERVED_WORD);
            tokenMap.put("open", Token.RESERVED_WORD);
            tokenMap.put("opens", Token.RESERVED_WORD);
            tokenMap.put("provides", Token.RESERVED_WORD);
            tokenMap.put("requires", Token.RESERVED_WORD);
            tokenMap.put("to", Token.RESERVED_WORD);
            tokenMap.put("transitive", Token.RESERVED_WORD);
            tokenMap.put("uses", Token.RESERVED_WORD);
            tokenMap.put("with", Token.RESERVED_WORD);

            return tokenMap;
        }

        @Override
        public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
            // This assumes all keywords, etc. were parsed as "identifiers."
            if (tokenType==Token.IDENTIFIER) {
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
                    case Token.NULL:
                        currentTokenStart = i;   // Starting a new token here.
                        if (RSyntaxUtilities.isLetter(c) || (c == '_')) {
                            currentTokenType = Token.IDENTIFIER;
                        } else {
                            currentTokenType = Token.WHITESPACE;
                        }
                        break;
                    default: // Should never happen
                    case Token.WHITESPACE:
                        if (RSyntaxUtilities.isLetter(c) || (c == '_')) {
                            addToken(text, currentTokenStart, i-1, Token.WHITESPACE, newStartOffset+currentTokenStart);
                            currentTokenStart = i;
                            currentTokenType = Token.IDENTIFIER;
                        }
                        break;
                    case Token.IDENTIFIER:
                        if (!RSyntaxUtilities.isLetterOrDigit(c) && (c != '_') && (c != '.')) {
                            addToken(text, currentTokenStart, i-1, Token.IDENTIFIER, newStartOffset+currentTokenStart);
                            currentTokenStart = i;
                            currentTokenType = Token.WHITESPACE;
                        }
                        break;
                }
            }

            if (currentTokenType == Token.NULL) {
                addNullToken();
            }else {
                addToken(text, currentTokenStart,end-1, currentTokenType, newStartOffset+currentTokenStart);
                addNullToken();
            }

            return firstToken;
        }
    }
}
