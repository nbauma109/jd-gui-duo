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
package org.jd.gui.util.nexus;

import org.jd.gui.util.nexus.model.NexusSearchResult;

/**
 * We expose a uniform search interface across Nexus version 2 and version 3.
 * Implementations must never use proxies for network calls.
 */
public interface NexusSearch {

    /**
     * We search by a freeform keyword.
     */
    NexusSearchResult searchByKeyword(String keyword, int pageNo) throws Exception;

    /**
     * We search by SHA-1 content hash.
     */
    NexusSearchResult searchBySha1(String sha1, int pageNo) throws Exception;

    /**
     * We search by group, artifact, and optional version.
     * A null version means we search across versions.
     */
    default NexusSearchResult searchByGav(String groupId, String artifactId, String version, int pageNo) throws Exception {
    	return searchByGav(groupId, artifactId, version, null, null, pageNo);
    }

    /**
     * We search by group, artifact, version, classifier
     * A null version means we search across versions.
     */
    NexusSearchResult searchByGav(String groupId, String artifactId, String version, String classifier, String packaging, int pageNo) throws Exception;

    /**
     * We search by class name.
     * If fullyQualified is true, we treat the input as a fully qualified class name.
     * Otherwise we treat it as a simple class name.
     */
    NexusSearchResult searchByClassName(String className, boolean fullyQualified, int pageNo) throws Exception;

    boolean supportsVersionDate();

    boolean supportsClassSearch();
}
