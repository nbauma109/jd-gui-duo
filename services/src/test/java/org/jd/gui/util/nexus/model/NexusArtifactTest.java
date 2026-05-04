/*******************************************************************************
 *
 * © 2025 Nicolas Baumann (@nbauma109)
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

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusArtifactTest {

    private static NexusArtifact artifact(String groupId, String artifactId, String version) {
        return new NexusArtifact(groupId, artifactId, version, null, null, "jar", "central", "http://example.com");
    }

    @Test
    void compareTo_sameCoordinates_returnsZero() {
        NexusArtifact a = artifact("org.example", "lib", "1.0.0");
        NexusArtifact b = artifact("org.example", "lib", "1.0.0");

        assertEquals(0, a.compareTo(b));
    }

    @Test
    void compareTo_differentGroupId_sortsByGroup() {
        NexusArtifact a = artifact("org.aaa", "lib", "1.0.0");
        NexusArtifact b = artifact("org.zzz", "lib", "1.0.0");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareTo_sameGroupDifferentArtifact_sortsByArtifact() {
        NexusArtifact a = artifact("org.example", "aaa", "1.0.0");
        NexusArtifact b = artifact("org.example", "zzz", "1.0.0");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareTo_sameGroupAndArtifactDifferentVersion_sortsByVersion() {
        NexusArtifact a = artifact("org.example", "lib", "1.0.0");
        NexusArtifact b = artifact("org.example", "lib", "2.0.0");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void sort_producesCorrectOrder() {
        NexusArtifact a = artifact("org.example", "lib", "1.0.0");
        NexusArtifact b = artifact("org.apache", "commons", "3.0");
        NexusArtifact c = artifact("org.apache", "commons", "1.0");
        NexusArtifact d = artifact("org.apache", "ant", "1.10");

        List<NexusArtifact> list = new ArrayList<>(List.of(a, b, c, d));
        Collections.sort(list);

        assertEquals(d, list.get(0)); // org.apache / ant
        assertEquals(c, list.get(1)); // org.apache / commons / 1.0
        assertEquals(b, list.get(2)); // org.apache / commons / 3.0
        assertEquals(a, list.get(3)); // org.example / lib
    }

    @Test
    void nexusSearchResult_storesArtifacts() {
        List<NexusArtifact> artifacts = List.of(
                artifact("g", "a", "1.0"),
                artifact("g", "b", "2.0")
        );
        NexusSearchResult result = new NexusSearchResult(new ArrayList<>(artifacts));

        assertEquals(2, result.artifacts().size());
    }

    @Test
    void nexusArtifact_allFieldsAccessible() {
        LocalDate date = LocalDate.of(2024, 6, 1);
        NexusArtifact artifact = new NexusArtifact(
                "com.example", "mylib", "1.2.3", date,
                "sources", "jar", "central", "http://example.com/mylib-1.2.3-sources.jar");

        assertEquals("com.example", artifact.groupId());
        assertEquals("mylib", artifact.artifactId());
        assertEquals("1.2.3", artifact.version());
        assertEquals(date, artifact.versionDate());
        assertEquals("sources", artifact.classifier());
        assertEquals("jar", artifact.extension());
        assertEquals("central", artifact.repository());
        assertEquals("http://example.com/mylib-1.2.3-sources.jar", artifact.artifactLink());
    }
}
