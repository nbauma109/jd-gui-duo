/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.nexus;

import org.jd.gui.util.central.SolrCentralSearchClient;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Nexus 2 and Nexus 3 clients and their factory, against a local server (no network). */
class NexusClientsLocalTest {

    private static final String V2_SEARCH = "/service/local/lucene/search";
    private static final String V3_SEARCH = "/service/rest/v1/search";

    private static final String V2_XML = """
            <searchNGResponse>
              <data>
                <artifact>
                  <groupId>org.demo</groupId>
                  <artifactId>demo-lib</artifactId>
                  <version>1.2.3</version>
                  <artifactHits>
                    <artifactHit>
                      <repositoryId>releases</repositoryId>
                      <artifactLinks>
                        <artifactLink><extension>jar</extension></artifactLink>
                        <artifactLink><extension>jar</extension><classifier>sources</classifier></artifactLink>
                        <artifactLink><extension>pom</extension></artifactLink>
                      </artifactLinks>
                    </artifactHit>
                  </artifactHits>
                </artifact>
              </data>
            </searchNGResponse>
            """;

    private static final String V3_COMPONENTS = """
            {"items": [{
              "id": "c1", "repository": "maven-releases", "format": "maven2", "group": "org.demo", "name": "demo-lib", "version": "1.2.3",
              "assets": [
                {"repository": "maven-releases", "lastModified": "2024-05-06T07:08:09.000+00:00",
                 "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "extension": "jar"}},
                {"repository": "maven-releases", "lastModified": "not a date",
                 "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "classifier": "sources", "extension": "jar"}},
                {"repository": "maven-releases", "path": "no-maven2-part"}
              ]
            }, {"id": "c2", "assets": null}], "continuationToken": null}
            """;

    private static final String V3_ASSETS = """
            {"items": [
              {"repository": "maven-releases", "lastModified": "2023-01-02T03:04:05Z",
               "maven2": {"groupId": "org.demo", "artifactId": "demo-lib", "version": "1.2.3", "extension": "jar"}}
            ]}
            """;

    private StubServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new StubServer();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private NexusConfig config() {
        return new NexusConfig(server.baseUrl() + "/", null, null);
    }

    // ------------------------------------------------------------------ Nexus 2

    @Test
    void nexus2KeywordSearchListsOneArtifactPerLink() throws Exception {
        server.answer(V2_SEARCH, 200, V2_XML);

        NexusSearchResult result = new NexusV2Client(config()).searchByKeyword("demo lib", 1);

        assertThat(result.artifacts()).extracting(NexusArtifact::extension, NexusArtifact::classifier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("jar", null), org.assertj.core.groups.Tuple.tuple("jar", "sources"),
                        org.assertj.core.groups.Tuple.tuple("pom", null));
        assertThat(result.artifacts().get(1).artifactLink()).isEqualTo(
                server.baseUrl() + "/service/local/artifact/maven/content?r=releases&g=org.demo&a=demo-lib&v=1.2.3&p=jar&c=sources");
        assertThat(result.artifacts().get(0)).extracting(NexusArtifact::groupId, NexusArtifact::artifactId, NexusArtifact::version, NexusArtifact::repository, NexusArtifact::versionDate)
                .containsExactly("org.demo", "demo-lib", "1.2.3", "releases", null);
        assertThat(server.requests()).containsExactly(V2_SEARCH + "?q=demo+lib&from=20&count=20");
    }

    @Test
    void nexus2SearchesBySha1AndClassName() throws Exception {
        server.answer(V2_SEARCH, 200, V2_XML);
        NexusV2Client client = new NexusV2Client(config());

        assertThat(client.searchBySha1("abc123", 0).artifacts()).hasSize(3);
        assertThat(client.searchByClassName("StringUtils", true, 2).artifacts()).hasSize(3);
        assertThat(client.searchByClassName("StringUtils", false, 0).artifacts()).hasSize(3);

        assertThat(server.requests()).containsExactly(
                V2_SEARCH + "?sha1=abc123&from=0&count=20", V2_SEARCH + "?cn=StringUtils&from=40&count=20", V2_SEARCH + "?cn=StringUtils&from=0&count=20");
        assertThat(client.supportsClassSearch()).isTrue();
        assertThat(client.supportsVersionDate()).isFalse();
    }

    @Test
    void nexus2GavSearchFiltersOnClassifierAndPackaging() throws Exception {
        server.answer(V2_SEARCH, 200, V2_XML);
        NexusV2Client client = new NexusV2Client(config());

        assertThat(client.searchByGav("org.demo", "demo-lib", null, null, null, 0).artifacts()).hasSize(3);
        assertThat(client.searchByGav("org.demo", "demo-lib", "1.2.3", "sources", "jar", 0).artifacts())
                .extracting(NexusArtifact::classifier).containsExactly("sources");
        assertThat(client.searchByGav("org.demo", "demo-lib", " ", null, "pom", 0).artifacts())
                .extracting(NexusArtifact::extension).containsOnly("pom");

        assertThat(server.requests()).containsExactly(
                V2_SEARCH + "?g=org.demo&a=demo-lib&from=0&count=20",
                V2_SEARCH + "?g=org.demo&a=demo-lib&v=1.2.3&c=sources&p=jar&from=0&count=20",
                V2_SEARCH + "?g=org.demo&a=demo-lib&p=pom&from=0&count=20");
    }

    @Test
    void nexus2ToleratesEmptyAndMalformedBodies() throws Exception {
        NexusV2Client client = new NexusV2Client(config());
        server.answer(V2_SEARCH, 200, "<searchNGResponse></searchNGResponse>");
        assertThat(client.searchByKeyword("none", 0).artifacts()).isEmpty();

        server.answer(V2_SEARCH, 200, "this is <not xml");
        assertThat(client.searchByKeyword("broken", 0).artifacts()).isEmpty();

        server.answer(V2_SEARCH, 200, "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>");
        assertThat(client.searchByKeyword("xxe", 0).artifacts()).isEmpty();
    }

    @Test
    void nexus2ReportsHttpErrorsAndSendsBasicAuthentication() {
        server.answer(V2_SEARCH, 500, "boom");
        NexusV2Client client = new NexusV2Client(new NexusConfig(server.baseUrl(), "alice", "s3cret".toCharArray()));

        assertThatThrownBy(() -> client.searchByKeyword("x", 0)).isInstanceOf(IOException.class).hasMessageContaining("HTTP 500").hasMessageContaining("boom");
        assertThat(server.lastAuthorization()).isEqualTo("Basic " + java.util.Base64.getEncoder().encodeToString("alice:s3cret".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
    }

    // ------------------------------------------------------------------ Nexus 3

    @Test
    void nexus3KeywordSearchReadsTheAssetsOfTheComponents() throws Exception {
        server.answer(V3_SEARCH, 200, V3_COMPONENTS);

        NexusSearchResult result = new NexusV3Client(config()).searchByKeyword("demo", 0);

        assertThat(result.artifacts()).extracting(NexusArtifact::classifier, NexusArtifact::versionDate)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(null, LocalDate.of(2024, 5, 6)), org.assertj.core.groups.Tuple.tuple("sources", null));
        assertThat(result.artifacts().get(0).artifactLink()).isEqualTo(
                server.baseUrl() + "/service/rest/v1/search/assets/download?repository=maven-releases&group=org.demo&name=demo-lib&version=1.2.3&extension=jar");
        assertThat(result.artifacts().get(1).artifactLink()).endsWith("&extension=jar&classifier=sources");
        assertThat(server.requests()).containsExactly(V3_SEARCH + "?q=demo");
    }

    @Test
    void nexus3SearchesAssetsBySha1() throws Exception {
        server.answer(V3_SEARCH + "/assets", 200, V3_ASSETS);
        NexusV3Client client = new NexusV3Client(config());

        NexusSearchResult result = client.searchBySha1("deadbeef", 0);

        assertThat(result.artifacts()).extracting(NexusArtifact::versionDate).containsExactly(LocalDate.of(2023, 1, 2));
        assertThat(server.requests()).containsExactly(V3_SEARCH + "/assets?sha1=deadbeef");
        assertThat(client.supportsVersionDate()).isTrue();
        assertThat(client.supportsClassSearch()).isFalse();
    }

    @Test
    void nexus3GavSearchBuildsItsQueryAndFilters() throws Exception {
        server.answer(V3_SEARCH, 200, V3_COMPONENTS);
        NexusV3Client client = new NexusV3Client(config());

        assertThat(client.searchByGav("org.demo", "demo-lib", "1.2.3", null, null, 0).artifacts()).hasSize(2);
        assertThat(client.searchByGav(null, "demo-lib", null, "sources", "jar", 0).artifacts()).extracting(NexusArtifact::classifier).containsExactly("sources");
        assertThat(client.searchByGav("org.demo", null, "1.2.3", null, "pom", 0).artifacts()).isEmpty();

        assertThat(server.requests()).containsExactly(
                V3_SEARCH + "?group=org.demo&name=demo-lib&version=1.2.3", V3_SEARCH + "?name=demo-lib", V3_SEARCH + "?group=org.demo&version=1.2.3");
    }

    @Test
    void nexus3ToleratesBadBodiesAndRejectsClassSearch() throws Exception {
        NexusV3Client client = new NexusV3Client(config());
        server.answer(V3_SEARCH, 200, "");
        assertThat(client.searchByKeyword("empty", 0).artifacts()).isEmpty();
        server.answer(V3_SEARCH, 200, "{not json");
        assertThat(client.searchByKeyword("broken", 0).artifacts()).isEmpty();
        server.answer(V3_SEARCH, 200, "{\"items\": null}");
        assertThat(client.searchByKeyword("null items", 0).artifacts()).isEmpty();
        server.answer(V3_SEARCH + "/assets", 200, "{\"items\": null}");
        assertThat(client.searchBySha1("x", 0).artifacts()).isEmpty();

        assertThatThrownBy(() -> client.searchByClassName("X", true, 0)).isInstanceOf(UnsupportedOperationException.class);
        server.answer(V3_SEARCH, 401, "denied");
        assertThatThrownBy(() -> client.searchByKeyword("x", 0)).isInstanceOf(IOException.class).hasMessageContaining("HTTP 401");
    }

    // ------------------------------------------------------------------ factory

    @Test
    void factoryPicksTheClientOfTheServerFamily() {
        server.answer("/service/rest/v1/status", 200, "");
        assertThat(NexusSearchFactory.create(config())).isInstanceOf(NexusV3Client.class);
    }

    @Test
    void factoryFallsBackToNexus2ThenToNexus3() {
        server.answer("/service/local/status", 200, "<status><version>2.14</version></status>");
        assertThat(NexusSearchFactory.create(config())).isInstanceOf(NexusV2Client.class);

        server.answer("/service/local/status", 200, "<other/>");
        assertThat(NexusSearchFactory.create(config(), null)).isInstanceOf(NexusV3Client.class);
    }

    @Test
    void factoryUsesMavenCentralWithoutANexusConfiguration() {
        assertThat(NexusSearchFactory.create(new NexusConfig("  ", null, null), null)).isInstanceOf(SolrCentralSearchClient.class);
        assertThat(NexusSearchFactory.create((NexusConfig) null, null)).isInstanceOf(SolrCentralSearchClient.class);
    }
}
