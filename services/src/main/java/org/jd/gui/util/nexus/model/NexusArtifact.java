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
package org.jd.gui.util.nexus.model;

import java.time.LocalDate;

/**
 * We represent one artifact coordinate and the contributing repository.
 */
public record NexusArtifact(String groupId, String artifactId, String version, LocalDate versionDate, String classifier, String extension, String repository, String artifactLink)
implements Comparable<NexusArtifact> {

	@Override
	public int compareTo(NexusArtifact o) {
		int cmp = groupId.compareTo(o.groupId);
		if (cmp == 0) {
			cmp = artifactId.compareTo(o.artifactId);
			if (cmp == 0) {
				cmp = version.compareTo(o.version);
			}
		}
		return cmp;
	}
}
