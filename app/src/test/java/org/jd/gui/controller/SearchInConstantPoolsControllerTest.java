/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchInConstantPoolsControllerTest {

    @Test
    void singleRegexMetacharacterIsALiteralPrefix() {
        for (String metacharacter : new String[] { "[", "(", "\\", "+", "{", "^", "$", "|", ")" }) {
            assertTrue(SearchInConstantPoolsController.createPattern(metacharacter).matcher(metacharacter + "suffix").matches(), metacharacter);
            assertFalse(SearchInConstantPoolsController.createPattern(metacharacter).matcher("suffix").matches(), metacharacter);
        }
    }

    @Test
    void singleCharacterMatchesNamesStartingWithIt() {
        assertTrue(SearchInConstantPoolsController.createPattern("a").matcher("abc").matches());
        assertFalse(SearchInConstantPoolsController.createPattern("a").matcher("bca").matches());
        assertTrue(SearchInConstantPoolsController.createPattern(".").matcher(".x").matches());
        assertFalse(SearchInConstantPoolsController.createPattern(".").matcher("x").matches());
    }

    @Test
    void singleWildcardsKeepTheirMeaning() {
        assertTrue(SearchInConstantPoolsController.createPattern("*").matcher("anything").matches());
        assertTrue(SearchInConstantPoolsController.createPattern("?").matcher("x").matches());
    }

    @Test
    void wildcardsInLongerPatternsKeepTheirMeaning() {
        assertTrue(SearchInConstantPoolsController.createPattern("g?t*").matcher("getName").matches());
        assertFalse(SearchInConstantPoolsController.createPattern("g?t*").matcher("setName").matches());
    }
}
