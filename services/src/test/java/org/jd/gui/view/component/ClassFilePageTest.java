package org.jd.gui.view.component;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import jd.core.links.DeclarationData;
import jd.core.links.HyperlinkData;
import jd.core.links.HyperlinkReferenceData;
import jd.core.links.ReferenceData;
import jd.core.links.StringData;
import junit.framework.TestCase;

public class ClassFilePageTest extends TestCase {

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

    public void testMatchFragmentAndAddDocumentRange() {
        HashMap<String, DeclarationData> declarations = initDeclarations();
        ArrayList<DocumentRange> ranges = new ArrayList<>();

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("Test-attributeBoolean-Z", declarations, ranges);
        Assert.assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-attributeBoolean-Z", declarations, ranges);
        Assert.assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("*/Test-attributeBoolean-Z", declarations, ranges);
        Assert.assertEquals(2, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("Test-createBuffer-(I)[C", declarations, ranges);
        Assert.assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-createBuffer-(I)[C", declarations, ranges);
        Assert.assertEquals(1, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("*/Test-getString-(*)?", declarations, ranges);
        Assert.assertEquals(2, ranges.size());

        ranges.clear();
        ClassFilePage.matchFragmentAndAddDocumentRange("test/Test-add-(?J)?", declarations, ranges);
        Assert.assertEquals(1, ranges.size());
    }

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
        Assert.assertEquals(1, ranges.size());

        parameters.put("highlightScope", "");
        ranges.clear();
        ClassFilePage.matchQueryAndAddDocumentRange(parameters, declarations, hyperlinks, strings, ranges);
        Assert.assertEquals(1, ranges.size());

        parameters.put("highlightScope", "Test");
        ranges.clear();
        ClassFilePage.matchQueryAndAddDocumentRange(parameters, declarations, hyperlinks, strings, ranges);
        Assert.assertEquals(1, ranges.size());
    }

    public void testMatchScope() {
        Assert.assertTrue(ClassFilePage.matchScope(null, "java/lang/String"));
        Assert.assertTrue(ClassFilePage.matchScope("", "java/lang/String"));

        Assert.assertTrue(ClassFilePage.matchScope("java/lang/String", "java/lang/String"));
        Assert.assertTrue(ClassFilePage.matchScope("*/lang/String", "java/lang/String"));
        Assert.assertTrue(ClassFilePage.matchScope("*/String", "java/lang/String"));

        Assert.assertTrue(ClassFilePage.matchScope(null, "Test"));
        Assert.assertTrue(ClassFilePage.matchScope("", "Test"));

        Assert.assertTrue(ClassFilePage.matchScope("Test", "Test"));
        Assert.assertTrue(ClassFilePage.matchScope("*/Test", "Test"));
    }
}