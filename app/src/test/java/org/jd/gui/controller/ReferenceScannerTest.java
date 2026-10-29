/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_CONSTRUCTOR;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_FIELD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_METHOD;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_STRING;
import static org.jd.gui.view.SearchInConstantPoolsView.SEARCH_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceScannerTest {

    private static ReferenceScanner.Result scan(String pattern, int flags) throws IOException {
        try (InputStream in = ReferenceScannerFixture.class.getResourceAsStream("ReferenceScannerFixture.class")) {
            return ReferenceScanner.scan(in, SearchInConstantPoolsController.createPattern(pattern), flags);
        }
    }

    @Test
    void methodReferenceIsFoundInTheCallingMethodOnly() throws IOException {
        assertEquals(Set.of("callsMethod()V"), scan("valueOf", SEARCH_METHOD).methods());
    }

    @Test
    void fieldReferenceIsFoundInTheAccessingMethods() throws IOException {
        assertEquals(Set.of("readsField()V", "usesString()V"), scan("name", SEARCH_FIELD).methods());
    }

    @Test
    void stringConstantIsFoundInItsMethod() throws IOException {
        assertEquals(Set.of("usesString()V"), scan("needle*", SEARCH_STRING).methods());
    }

    @Test
    void constructorReferenceMatchesTheTypeSimpleName() throws IOException {
        assertEquals(Set.of("buildsType()V"), scan("StringBuilder", SEARCH_CONSTRUCTOR).methods());
    }

    @Test
    void typeNameIsNotAMethodMatch() throws IOException {
        assertTrue(scan("StringBuilder", SEARCH_METHOD).methods().isEmpty());
    }

    @Test
    void typeReferenceCoversInstructionsThrowsClausesAndFieldDescriptors() throws IOException {
        assertTrue(scan("StringBuilder", SEARCH_TYPE).methods().contains("buildsType()V"));
        assertTrue(scan("IOException", SEARCH_TYPE).methods().contains("throwsType()V"));
        assertEquals(Set.of("itemsLjava/util/List;"), scan("List", SEARCH_TYPE).fields());
    }

    @Test
    void methodWithoutMatchingReferenceIsNotReported() throws IOException {
        assertFalse(scan("*", SEARCH_METHOD | SEARCH_FIELD | SEARCH_STRING).methods().contains("untouched()V"));
    }
}
