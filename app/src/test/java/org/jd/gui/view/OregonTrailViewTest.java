/*
 * © 2021-2025 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OregonTrailViewTest {

    @Test
    void toHtmlKeepsEmphasisTagsAndEscapesEverythingElse() {
        assertEquals("<b>a &lt;c> &amp; d</b>", OregonTrailView.toHtml("<b>a <c> & d</b>"));
    }
}
