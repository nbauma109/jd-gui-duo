package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jd.core.links.DeclarationData;
import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;
import jd.core.links.StringData;

public class ClassFilePageTest {

    public HashMap<String, DeclarationData> initDeclarations() {
        DeclarationData data = new DeclarationData(0, 1, "Test", "test", "I");
        HashMap<String, DeclarationData> declarations = new HashMap<>();

        // Init type declarations
        declarations.put("Test", data);
        declarations.put("test/Test", data);

        // Init field declarations
        declarations.put("Test-attributeInt-I", data);
        declarations.put("Test-attributeBoolean-Z", data);
        declarations.put("Test-attributeArrayBoolean-[[Z", data);
        declarations.put("Test-attributeString-Ljava/lang/String;", data);

        declarations.put("test/Test-attributeInt-I", data);
        declarations.put("test/Test-attributeBoolean-Z", data);
        declarations.put("test/Test-attributeArrayBoolean-[[Z", data);
        declarations.put("test/Test-attributeString-Ljava/lang/String;", data);

        // Init method declarations
        declarations.put("Test-getInt-()I", data);
        declarations.put("Test-getString-()Ljava/lang/String;", data);
        declarations.put("Test-add-(JJ)J", data);
        declarations.put("Test-createBuffer-(I)[C", data);

        declarations.put("test/Test-getInt-()I", data);
        declarations.put("test/Test-getString-()Ljava/lang/String;", data);
        declarations.put("test/Test-add-(JJ)J", data);
        declarations.put("test/Test-createBuffer-(I)[C", data);

        return declarations;
    }

    public TreeMap<Integer, HyperlinkData> initHyperlinks() {
        TreeMap<Integer, HyperlinkData> hyperlinks = new TreeMap<>();

        hyperlinks.put(0, new HyperlinkReferenceData(0, 1, new ReferenceData("java/lang/Integer", "MAX_VALUE", "I", "Test")));
        hyperlinks.put(1, new HyperlinkReferenceData(0, 1, new ReferenceData("java/lang/Integer", "toString", "()Ljava/lang/String;", "Test")));

        return hyperlinks;
    }

    public ArrayList<StringData> initStrings() {
        ArrayList<StringData> strings = new ArrayList<>();

        strings.add(new StringData(0, "abc", "Test"));

        return strings;
    }

    @Test
    public void testMatchFragmentAndAddDocumentRange() {
        HashMap<String, DeclarationData> declarations = initDeclarations();
        ArrayList<DocumentRange> ranges = new ArrayList<>();

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("Test-attributeBoolean-Z", declarations, ranges);
        assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-attributeBoolean-Z", declarations, ranges);
        assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("*/Test-attributeBoolean-Z", declarations, ranges);
        assertEquals(2, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("Test-createBuffer-(I)[C", declarations, ranges);
        assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-createBuffer-(I)[C", declarations, ranges);
        assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("*/Test-getString-(*)?", declarations, ranges);
        assertEquals(2, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-add-(?J)?", declarations, ranges);
        assertEquals(1, ranges.size());
    }

    @Test
    public void testMatchQueryAndAddDocumentRange() {
        HashMap<String, String> parameters = new HashMap<>();
        HashMap<String, DeclarationData> declarations = initDeclarations();
        TreeMap<Integer, HyperlinkData> hyperlinks = initHyperlinks();
        ArrayList<StringData> strings = initStrings();
        ArrayList<DocumentRange> ranges = new ArrayList<>();

        parameters.put("highlightPattern", "ab");
        parameters.put("highlightFlags", "s");

        parameters.put("highlightScope", null);
        ranges.clear();
        ClassFilePage.matchQueryAndAddDocumentRange(parameters, declarations, hyperlinks, strings, ranges);
        assertEquals(1, ranges.size());

        parameters.put("highlightScope", "");
        ranges.clear();
        ClassFilePage.matchQueryAndAddDocumentRange(parameters, declarations, hyperlinks, strings, ranges);
        assertEquals(1, ranges.size());

        parameters.put("highlightScope", "Test");
        ranges.clear();
        ClassFilePage.matchQueryAndAddDocumentRange(parameters, declarations, hyperlinks, strings, ranges);
        assertEquals(1, ranges.size());
    }

    @Test
    public void testMatchScope() {
        assertTrue(ClassFilePage.matchScope(null, "java/lang/String"));
        assertTrue(ClassFilePage.matchScope("", "java/lang/String"));

        assertTrue(ClassFilePage.matchScope("java/lang/String", "java/lang/String"));
        assertTrue(ClassFilePage.matchScope("*/lang/String", "java/lang/String"));
        assertTrue(ClassFilePage.matchScope("*/String", "java/lang/String"));

        assertTrue(ClassFilePage.matchScope(null, "Test"));
        assertTrue(ClassFilePage.matchScope("", "Test"));

        assertTrue(ClassFilePage.matchScope("Test", "Test"));
        assertTrue(ClassFilePage.matchScope("*/Test", "Test"));
    }
}