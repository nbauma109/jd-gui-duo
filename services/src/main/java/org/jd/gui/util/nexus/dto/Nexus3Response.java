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
package org.jd.gui.util.nexus.dto;

import java.util.List;

/**
 * We map the Nexus Repository Manager version 3 search response structure
 * (component list with nested assets) exactly as found in the provided JSON.
 * We keep private fields and expose standard getters and setters so JSON-B
 * (Yasson) can bind values by name without extra annotations.
 *
 */
public final class Nexus3Response {

    private List<Component> items;
    private String continuationToken;

    public List<Component> getItems() {
        return items;
    }

    public void setItems(List<Component> items) {
        this.items = items;
    }

    public String getContinuationToken() {
        return continuationToken;
    }

    public void setContinuationToken(String continuationToken) {
        this.continuationToken = continuationToken;
    }
}
