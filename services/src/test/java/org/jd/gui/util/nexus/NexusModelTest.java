/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.nexus;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.jd.gui.util.nexus.dto.Checksum;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NexusModelTest {

    private static NexusArtifact artifact(String group, String artifact, String version) {
        return new NexusArtifact(group, artifact, version, null, null, "jar", "central", null);
    }

    @Test
    void artifactsAreOrderedByGroupThenArtifactThenVersion() {
        List<NexusArtifact> artifacts = new ArrayList<>(List.of(
                artifact("org.b", "a", "1"), artifact("org.a", "z", "1"), artifact("org.a", "a", "2"), artifact("org.a", "a", "1")));

        Collections.sort(artifacts);

        assertThat(artifacts).extracting(a -> a.groupId() + ":" + a.artifactId() + ":" + a.version())
                .containsExactly("org.a:a:1", "org.a:a:2", "org.a:z:1", "org.b:a:1");
        assertThat(artifact("g", "a", "1").compareTo(artifact("g", "a", "1"))).isZero();
    }

    @Test
    void theChecksumsOfAnAssetAreRead() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            Checksum checksum = jsonb.fromJson("{\"sha1\": \"1\", \"sha256\": \"256\", \"sha512\": \"512\", \"md5\": \"5\"}", Checksum.class);

            assertThat(checksum).extracting(Checksum::getSha1, Checksum::getSha256, Checksum::getSha512, Checksum::getMd5).containsExactly("1", "256", "512", "5");
        }
    }

    @Test
    void theChecksumsCanBeSetAndRead() {
        Checksum checksum = new Checksum();
        checksum.setSha1("a");
        checksum.setSha256("b");
        checksum.setSha512("c");
        checksum.setMd5("d");

        assertThat(checksum).extracting(Checksum::getSha1, Checksum::getSha256, Checksum::getSha512, Checksum::getMd5).containsExactly("a", "b", "c", "d");
    }
}
