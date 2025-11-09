/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.util.maven.central.helper.model.spellcheck.suggestions;

import java.util.List;

import jakarta.json.bind.annotation.JsonbProperty;

public class Suggestion {
    private int numFound;
    private int startOffset;
    private int endOffset;
    @JsonbProperty("suggestion")
    private List<String> suggestions;

    public int getNumFound() {
        return numFound;
    }

    public void setNumFound(final int numFound) {
        this.numFound = numFound;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(final int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(final int endOffset) {
        this.endOffset = endOffset;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(final List<String> suggestions) {
        this.suggestions = suggestions;
    }

}
