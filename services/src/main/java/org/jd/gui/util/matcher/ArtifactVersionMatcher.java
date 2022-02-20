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
package org.jd.gui.util.matcher;

public class ArtifactVersionMatcher {

    private String artifactId;
    private String version = "";

    public void parse(String baseFileName) {
        int idx = baseFileName.lastIndexOf('-');
        if (idx != -1) {
            artifactId = baseFileName.substring(0, idx);
            version = baseFileName.substring(idx + 1) + (version.length() > 0 ? "-" : "") + version;
            if (version.matches("[A-Za-z]+") || artifactId.matches(".*-[\\d.]+")) {
                parse(artifactId);
            }
        } else {
            artifactId = baseFileName + "-" + version;
            version = "";
        }
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }
}
