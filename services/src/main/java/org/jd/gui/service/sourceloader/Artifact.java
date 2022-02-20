/*******************************************************************************
 * Copyright (C) 2022 GPLv3
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
package org.jd.gui.service.sourceloader;

public record Artifact(String groupId, String artifactId, String version, String fileName, boolean found, boolean sourceAvailable)
implements Comparable<Artifact> {

    @Override
    public int compareTo(Artifact o) {
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
