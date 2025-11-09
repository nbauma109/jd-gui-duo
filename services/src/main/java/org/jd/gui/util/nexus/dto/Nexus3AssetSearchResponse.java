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
 * We model the Nexus Repository Manager version 3 response returned by
 * the SHA-1 asset search endpoint:
 *
 *   /service/rest/v1/search/assets?sha1=...
 *
 * This reduced response lists assets directly under "items" and does not
 * include component-level fields such as "group", "name", or "version"
 * at the top level. Each item contains an embedded "maven2" block with
 * coordinates and extension information.
 *
 * We keep fields private with standard getters and setters so Jakarta JSON Binding
 * (JSON-B / Yasson) can bind values by name without additional annotations.
 */
public final class Nexus3AssetSearchResponse {

    private List<Asset> items;
    private String continuationToken;

    public List<Asset> getItems() {
        return items;
    }

    public void setItems(List<Asset> items) {
        this.items = items;
    }

    public String getContinuationToken() {
        return continuationToken;
    }

    public void setContinuationToken(String continuationToken) {
        this.continuationToken = continuationToken;
    }
}
