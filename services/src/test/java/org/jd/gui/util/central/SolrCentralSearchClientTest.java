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
package org.jd.gui.util.central;

import org.jd.gui.util.maven.central.helper.ProxyConfig;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for SolrCentralSearchClient against https://search.maven.org.
 *
 * These tests exercise:
 *  - keyword search
 *  - SHA-1 search
 *  - GAV search
 *  - class name and fully qualified class name search
 *
 * They assume public Maven Central is reachable and may be tagged to run on demand.
 */
@Tag("integration")
class SolrCentralSearchClientTest {

    private static final String COMMONS_LANG3_GROUP = "org.apache.commons";
    private static final String COMMONS_LANG3_ARTIFACT = "commons-lang3";
    private static final String COMMONS_LANG3_VERSION = "3.19.0";
    private static final String COMMONS_LANG3_SHA1 = "d6524b169a6574cd253760c472d419b47bfd37e6";

    private static final String FQCN = "org.apache.commons.lang3.StringUtils";
    private static final String SIMPLE_CLASS = "StringUtils";

    private SolrCentralSearchClient newClient() {
        ProxyConfig proxy = null; // No proxy used in these integration tests
        return new SolrCentralSearchClient(proxy);
    }

    @Test
    void keywordSearch_returnsArtifactsWithVersionDateAndArtifactLink() throws Exception {
        SolrCentralSearchClient client = newClient();

        NexusSearchResult result = client.searchByKeyword("guice", 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "Keyword search should return at least one artifact");

        NexusArtifact first = artifacts.get(0);
        assertNotNull(first.groupId());
        assertNotNull(first.artifactId());
        assertNotNull(first.version());

        boolean hasDate = artifacts.stream()
                .map(NexusArtifact::versionDate)
                .anyMatch(d -> d != null && d.isBefore(LocalDate.now().plusDays(1)));
        assertTrue(hasDate, "At least one artifact should have a non null version date");

        boolean allHaveLinks = artifacts.stream()
                .allMatch(a -> a.artifactLink() != null && !a.artifactLink().isBlank());
        assertTrue(allHaveLinks, "All artifacts should have a non empty artifactLink");

        assertTrue(first.artifactLink().startsWith("https://search.maven.org/remotecontent?filepath="),
                "artifactLink should use the remotecontent API endpoint");
    }

    @Test
    void sha1Search_returnsExpectedCommonsLang3Artifact() throws Exception {
        SolrCentralSearchClient client = newClient();

        NexusSearchResult result = client.searchBySha1(COMMONS_LANG3_SHA1, 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "SHA-1 search should return at least one artifact");

        boolean containsCommonsLang3 = artifacts.stream().anyMatch(a ->
                COMMONS_LANG3_GROUP.equals(a.groupId())
                        && COMMONS_LANG3_ARTIFACT.equals(a.artifactId()));
        assertTrue(containsCommonsLang3,
                "Results should contain " + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT);

        NexusArtifact first = artifacts.get(0);
        assertNotNull(first.repository(), "Repository should be populated (for example central)");
        assertNotNull(first.artifactLink(), "artifactLink should be populated");
    }

    @Test
    void gavSearch_returnsArtifactsForRequestedCoordinates() throws Exception {
        SolrCentralSearchClient client = newClient();

        NexusSearchResult result = client.searchByGav(
                COMMONS_LANG3_GROUP,
                COMMONS_LANG3_ARTIFACT,
                COMMONS_LANG3_VERSION,
                0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "GAV search should return at least one artifact");

        boolean containsExact = artifacts.stream().anyMatch(a ->
                COMMONS_LANG3_GROUP.equals(a.groupId())
                        && COMMONS_LANG3_ARTIFACT.equals(a.artifactId())
                        && COMMONS_LANG3_VERSION.equals(a.version()));
        assertTrue(containsExact,
                "Results should contain the exact GAV "
                        + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT + ":" + COMMONS_LANG3_VERSION);
    }

    @Test
    void gavSearch_returnsArtifactsForRequestedCoordinatesWithClassifier() throws Exception {
    	SolrCentralSearchClient client = newClient();
    	
    	NexusSearchResult result = client.searchByGav(
    			COMMONS_LANG3_GROUP,
    			COMMONS_LANG3_ARTIFACT,
    			COMMONS_LANG3_VERSION,
    			"sources",
    			"jar",
    			0);
    	
    	assertNotNull(result);
    	List<NexusArtifact> artifacts = result.artifacts();
    	assertNotNull(artifacts);
    	assertFalse(artifacts.isEmpty(), "GAV search should return at least one artifact");
    	
    	List<NexusArtifact> lang3Artifacts = artifacts.stream()
    			.filter(a -> COMMONS_LANG3_GROUP.equals(a.groupId())
    					&& COMMONS_LANG3_ARTIFACT.equals(a.artifactId())
    					&& COMMONS_LANG3_VERSION.equals(a.version())
    					&& "jar".equals(a.extension())
    					&& "sources".equals(a.classifier()))
    			.toList();
    	
    	assertFalse(lang3Artifacts.isEmpty(),
    			"Results should contain sources jar for GAV "
    					+ COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT + ":" + COMMONS_LANG3_VERSION);
    	
    	assertTrue(artifacts.stream()
    			.allMatch(a -> COMMONS_LANG3_GROUP.equals(a.groupId())
    					&& COMMONS_LANG3_ARTIFACT.equals(a.artifactId())
    					&& COMMONS_LANG3_VERSION.equals(a.version())
    					&& "jar".equals(a.extension())
    					&& "sources".equals(a.classifier())));
    	
    	boolean allHaveLinks = lang3Artifacts.stream()
    			.allMatch(a -> a.artifactLink() != null
    			&& !a.artifactLink().isBlank());
    	assertTrue(allHaveLinks, "All commons-lang3 artifacts must have API based artifactLink values");
    }
    
    @Test
    void gavSearch_returnsArtifactsForRequestedCoordinatesNoVersion() throws Exception {
    	SolrCentralSearchClient client = newClient();
    	
    	NexusSearchResult result = client.searchByGav(
    			"commons-io",
    			"commons-io",
    			null,
    			0);
    	
    	assertNotNull(result);
    	List<NexusArtifact> artifacts = result.artifacts();
    	assertNotNull(artifacts);
    	assertFalse(artifacts.isEmpty(), "GAV search should return at least one artifact");
    	
    	boolean containsExact = artifacts.stream().anyMatch(a ->
    	"commons-io".equals(a.groupId())
    	&& "commons-io".equals(a.artifactId())
    	&& "2.20.0".equals(a.version()));
    	assertTrue(containsExact,
    			"Results should contain the exact GAV "
    					+ "commons-io" + ":" + "commons-io" + ":" + "2.20.0");
    }
    
    @Test
    void classNameSearch_simpleAndFullyQualifiedReturnResultsAndWeCanFindCommonsLang3AcrossPages() throws Exception {
        SolrCentralSearchClient client = newClient();

        NexusSearchResult simple = client.searchByClassName(SIMPLE_CLASS, false, 0);
        assertNotNull(simple);
        assertNotNull(simple.artifacts());
        assertFalse(simple.artifacts().isEmpty(),
                "Simple class name search should return at least one artifact on the first page");

        boolean foundCommonsLang3 = false;
        int maxPagesToScan = 10; // Safety bound to avoid unbounded scans

        for (int page = 0; page < maxPagesToScan && !foundCommonsLang3; page++) {
            NexusSearchResult pageResult = client.searchByClassName(FQCN, true, page);
            assertNotNull(pageResult);
            assertNotNull(pageResult.artifacts());

            if (!pageResult.artifacts().isEmpty()) {
                boolean pageHasCommonsLang3 = pageResult.artifacts().stream().anyMatch(a ->
                        COMMONS_LANG3_GROUP.equals(a.groupId())
                                && COMMONS_LANG3_ARTIFACT.equals(a.artifactId()));
                if (pageHasCommonsLang3) {
                    foundCommonsLang3 = true;
                }
            } else {
                // If we hit an empty page earlier than maxPagesToScan, Solr probably ran out of results
                break;
            }
        }

        assertTrue(foundCommonsLang3,
                "FQCN search should eventually contain "
                        + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT
                        + " within the first " + maxPagesToScan + " pages");
    }
}
