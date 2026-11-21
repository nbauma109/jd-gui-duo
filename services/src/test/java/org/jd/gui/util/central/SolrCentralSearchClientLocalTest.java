/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.central;

import com.sun.net.httpserver.HttpServer;

import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/** The Maven Central (Solr) client against a local server that answers like search.maven.org. */
class SolrCentralSearchClientLocalTest {

    private static final String DOCS = """
            {"responseHeader": {"status": 0}, "response": {"numFound": 3, "start": 0, "docs": [
              {"id": "org.demo:demo-lib:1.0", "g": "org.demo", "a": "demo-lib", "v": "1.0", "p": "jar", "repositoryId": "central", "timestamp": 1700000000000,
               "ec": ["-sources.jar", ".jar", ".pom", "-javadoc.jar", "-tests.jar", "-", "-.jar", "", null, "-lonely"]},
              {"id": "org.demo:other", "g": "org.demo", "a": "other", "latestVersion": "2.5", "p": "  ", "repositoryId": " ", "timestamp": 0},
              {"id": "org.demo:bundle:3", "g": "org.demo", "a": "bundle", "v": "3", "p": "bundle", "timestamp": 1700000000000, "ec": [".jar"]}
            ]}}
            """;

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile String body = DOCS;
    private SolrCentralSearchClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/solrsearch/select", exchange -> {
            synchronized (requests) {
                requests.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        client = new SolrCentralSearchClient(null, base + "/solrsearch/select", base + "/remotecontent?filepath=");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String lastQuery() {
        synchronized (requests) {
            return requests.get(requests.size() - 1);
        }
    }

    @Test
    void keywordSearchListsTheMainArtifactAndOneArtifactPerClassifierToken() throws Exception {
        NexusSearchResult result = client.searchByKeyword("  demo ", 0);

        assertThat(lastQuery()).isEqualTo("q=demo&rows=200&start=0&wt=json");
        assertThat(result.artifacts()).extracting(NexusArtifact::artifactId, NexusArtifact::classifier, NexusArtifact::extension)
                .containsExactly(
                        tuple("demo-lib", null, "jar"), tuple("demo-lib", "sources", "jar"), tuple("demo-lib", "javadoc", "jar"), tuple("demo-lib", "tests", "jar"),
                        tuple("other", null, "jar"), tuple("bundle", null, "bundle"));
        NexusArtifact main = result.artifacts().get(0);
        assertThat(main).extracting(NexusArtifact::groupId, NexusArtifact::version, NexusArtifact::repository, NexusArtifact::versionDate)
                .containsExactly("org.demo", "1.0", "central", LocalDate.of(2023, 11, 14));
        assertThat(main.artifactLink()).isEqualTo("http://localhost:" + server.getAddress().getPort()
                + "/remotecontent?filepath=org%2Fdemo%2Fdemo-lib%2F1.0%2Fdemo-lib-1.0.jar");
        assertThat(result.artifacts().get(1).artifactLink()).endsWith("filepath=org%2Fdemo%2Fdemo-lib%2F1.0%2Fdemo-lib-1.0-sources.jar");
    }

    @Test
    void missingFieldsFallBackToDefaults() throws Exception {
        NexusSearchResult result = client.searchByKeyword("other", 0);

        NexusArtifact other = result.artifacts().get(4);
        assertThat(other).extracting(NexusArtifact::version, NexusArtifact::repository, NexusArtifact::extension)
                .containsExactly("2.5", "central", "jar"); // the latest version, the default repository and extension
        assertThat(other.versionDate()).isEqualTo(LocalDate.of(1970, 1, 1));
    }

    @Test
    void searchesBuildTheirQueriesAndPageThroughTheResults() throws Exception {
        client.searchBySha1(" da39a3ee ", 0);
        assertThat(lastQuery()).isEqualTo("q=1:da39a3ee&rows=200&start=0&wt=json");

        client.searchByClassName(" StringUtils ", false, 2);
        assertThat(lastQuery()).isEqualTo("q=c:StringUtils&rows=200&start=400&wt=json");

        client.searchByClassName("org.demo.Type", true, -3);
        assertThat(lastQuery()).isEqualTo("q=fc:org.demo.Type&rows=200&start=0&wt=json"); // a negative page is the first one
    }

    @Test
    void coordinatesSearchUsesTheGavCoreAndFiltersTheResults() throws Exception {
        NexusSearchResult all = client.searchByGav("org.demo", "demo-lib", "1.0", null, null, 0);
        assertThat(lastQuery()).isEqualTo("q=g:org.demo AND a:demo-lib AND v:1.0&rows=200&start=0&wt=json&core=gav");
        assertThat(all.artifacts()).hasSize(6);

        NexusSearchResult sources = client.searchByGav("org.demo", "demo-lib", "1.0", "sources", "jar", 0);
        assertThat(lastQuery()).isEqualTo("q=g:org.demo AND a:demo-lib AND v:1.0 AND l:sources AND p:jar&rows=200&start=0&wt=json&core=gav");
        assertThat(sources.artifacts()).extracting(NexusArtifact::classifier).containsExactly("sources");

        NexusSearchResult bundles = client.searchByGav("org.demo", " ", " ", null, "bundle", 0);
        assertThat(lastQuery()).isEqualTo("q=g:org.demo AND p:bundle&rows=200&start=0&wt=json&core=gav");
        assertThat(bundles.artifacts()).extracting(NexusArtifact::artifactId).containsExactly("bundle");

        client.searchByGav(null, "demo-lib", null, null, null, 0);
        assertThat(lastQuery()).isEqualTo("q=a:demo-lib&rows=200&start=0&wt=json&core=gav");
    }

    @Test
    void emptyOrMalformedAnswersGiveNoArtifact() throws Exception {
        body = "{\"responseHeader\": {\"status\": 0}}";
        assertThat(client.searchByKeyword("none", 0).artifacts()).isEmpty();

        body = "{\"response\": {\"numFound\": 0, \"start\": 0, \"docs\": null}}";
        assertThat(client.searchByKeyword("none", 0).artifacts()).isEmpty();

        body = "null";
        assertThat(client.searchByKeyword("null", 0).artifacts()).isEmpty();
    }

    @Test
    void anHttpErrorIsReportedWithItsBody() {
        status.set(500);
        body = "boom";

        assertThatThrownBy(() -> client.searchByKeyword("x", 0)).isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 500").hasMessageContaining("boom");
    }

    @Test
    void invalidArgumentsAreRefused() {
        assertThatThrownBy(() -> client.searchByKeyword(null, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.searchBySha1(null, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.searchByClassName(null, true, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> client.searchByGav(null, null, "1.0", null, null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theClientAnnouncesWhatItSupports() {
        assertThat(client.supportsVersionDate()).isTrue();
        assertThat(client.supportsClassSearch()).isTrue();
        assertThat(new SolrCentralSearchClient(null)).isNotNull(); // the public constructor keeps the real endpoints
    }
}
