/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rtextarea.Marker;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;

/**
 * Page containing type references (Hyperlinks to pages of type)
 */
public abstract class TypeReferencePage extends HyperlinkPage {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// --- UriOpenable --- //
    @Override
	public boolean openUri(URI uri) {
        ArrayList<DocumentRange> ranges = new ArrayList<>();
        String query = uri.getQuery();

        Marker.clearMarkAllHighlights(textArea);

        if (query != null) {
            Map<String, String> parameters = parseQuery(query);

            if (parameters.containsKey("lineNumber")) {
                String lineNumber = parameters.get("lineNumber");

                try {
                    goToLineNumber(Integer.parseInt(lineNumber));
                    return true;
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else if (parameters.containsKey("position")) {
                String position = parameters.get("position");

                try {
                    int pos = Integer.parseInt(position);
                    if (textArea.getDocument().getLength() > pos) {
                        ranges.add(new DocumentRange(pos, pos));
                    }
                } catch (NumberFormatException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            } else {
                String highlightFlags = parameters.get("highlightFlags");
                String highlightPattern = parameters.get("highlightPattern");

                if ((highlightFlags != null) && (highlightPattern != null)) {
                    String regexp = createRegExp(highlightPattern);

                    if (highlightFlags.indexOf('s') != -1) {
                        // Highlight strings
                        Pattern pattern = Pattern.compile(regexp);
                        Matcher matcher = pattern.matcher(textArea.getText());

                        while (matcher.find()) {
                            ranges.add(new DocumentRange(matcher.start(), matcher.end()));
                        }
                    }

                    if ((highlightFlags.indexOf('t') != -1) && (highlightFlags.indexOf('r') != -1)) {
                        // Highlight type references
                        Pattern pattern = Pattern.compile(regexp + ".*");

                        for (Map.Entry<Integer, HyperlinkData> entry : hyperlinks.entrySet()) {
                            TypeHyperlinkData hyperlink = (TypeHyperlinkData)entry.getValue();
                            String name = getMostInnerTypeName(hyperlink.internalTypeName);

                            if (pattern.matcher(name).matches()) {
                                ranges.add(new DocumentRange(hyperlink.getStartPosition(), hyperlink.getEndPosition()));
                            }
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

        return false;
    }

    public String getMostInnerTypeName(String typeName) {
        int lastPackageSeparatorIndex = typeName.lastIndexOf('/') + 1;
        int lastTypeNameSeparatorIndex = typeName.lastIndexOf('$') + 1;
        int lastIndex = Math.max(lastPackageSeparatorIndex, lastTypeNameSeparatorIndex);
        return typeName.substring(lastIndex);
    }

    public static class TypeHyperlinkData extends HyperlinkData {
        public boolean enabled;
        public String internalTypeName;

        public TypeHyperlinkData(int startPosition, int endPosition, String internalTypeName) {
            super(startPosition, endPosition);
            this.enabled = false;
            this.internalTypeName = internalTypeName;
        }
    }
}
