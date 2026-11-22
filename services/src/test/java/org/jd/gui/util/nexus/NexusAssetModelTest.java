/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.nexus;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.jd.gui.util.nexus.dto.Asset;
import org.jd.gui.util.nexus.dto.Checksum;
import org.jd.gui.util.nexus.dto.Maven2;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The asset of a Nexus 3 repository, as its search answers describe it. */
class NexusAssetModelTest {

    private static final String ASSET = """
            {
              "downloadUrl": "https://repo.example.org/repository/maven-releases/org/demo/demo-lib/1.0/demo-lib-1.0.jar",
              "path": "org/demo/demo-lib/1.0/demo-lib-1.0.jar",
              "id": "bWF2ZW4",
              "repository": "maven-releases",
              "format": "maven2",
              "checksum": {"sha1": "1", "sha256": "256", "sha512": "512", "md5": "5"},
              "contentType": "application/java-archive",
              "lastModified": "2024-05-06T07:08:09.000+00:00",
              "lastDownloaded": "2024-06-07T08:09:10.000+00:00",
              "uploader": "admin",
              "uploaderIp": "10.0.0.1",
              "fileSize": 12345,
              "blobCreated": "2024-05-06T07:08:00.000+00:00",
              "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.0", "classifier": "sources", "extension": "jar"}
            }
            """;

    @Test
    void anAssetIsReadWithItsChecksumsAndItsMavenCoordinates() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            Asset asset = jsonb.fromJson(ASSET, Asset.class);

            assertThat(asset.getDownloadUrl()).endsWith("demo-lib-1.0.jar");
            assertThat(asset.getPath()).isEqualTo("org/demo/demo-lib/1.0/demo-lib-1.0.jar");
            assertThat(asset).extracting(Asset::getId, Asset::getRepository, Asset::getFormat, Asset::getContentType, Asset::getUploader, Asset::getUploaderIp)
                    .containsExactly("bWF2ZW4", "maven-releases", "maven2", "application/java-archive", "admin", "10.0.0.1");
            assertThat(asset).extracting(Asset::getLastModified, Asset::getLastDownloaded, Asset::getBlobCreated)
                    .containsExactly("2024-05-06T07:08:09.000+00:00", "2024-06-07T08:09:10.000+00:00", "2024-05-06T07:08:00.000+00:00");
            assertThat(asset.getFileSize()).isEqualTo(12345L);
            assertThat(asset.getChecksum().getSha1()).isEqualTo("1");
            assertThat(asset.getMaven2().getGroupId()).isEqualTo("org.demo");
            assertThat(asset.getMaven2().getClassifier()).isEqualTo("sources");
        }
    }

    @Test
    void anAssetCanBeBuiltWithItsSetters() {
        Asset asset = new Asset();
        Checksum checksum = new Checksum();
        Maven2 maven2 = new Maven2();
        asset.setDownloadUrl("u");
        asset.setPath("p");
        asset.setId("i");
        asset.setRepository("r");
        asset.setFormat("f");
        asset.setChecksum(checksum);
        asset.setContentType("c");
        asset.setLastModified("m");
        asset.setLastDownloaded("d");
        asset.setUploader("up");
        asset.setUploaderIp("ip");
        asset.setFileSize(7L);
        asset.setBlobCreated("b");
        asset.setMaven2(maven2);

        assertThat(asset).extracting(Asset::getDownloadUrl, Asset::getPath, Asset::getId, Asset::getRepository, Asset::getFormat, Asset::getContentType,
                Asset::getLastModified, Asset::getLastDownloaded, Asset::getUploader, Asset::getUploaderIp, Asset::getFileSize, Asset::getBlobCreated)
                .containsExactly("u", "p", "i", "r", "f", "c", "m", "d", "up", "ip", 7L, "b");
        assertThat(asset.getChecksum()).isSameAs(checksum);
        assertThat(asset.getMaven2()).isSameAs(maven2);
    }
}
