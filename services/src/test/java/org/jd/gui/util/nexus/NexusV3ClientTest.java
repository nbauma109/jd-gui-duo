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

import org.jd.gui.util.maven.central.helper.NexusConfig;
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
 * Integration tests for NexusV3Client against the public Nexus Repository three instance at
 * https://repository.cloudera.com.
 *
 * These tests verify:
 *   - factory detection of version three
 *   - keyword search
 *   - SHA-1 search
 *   - group, artifact, version search with classifier variants and API based download links
 *
 * Tests are network dependent and tagged as "integration".
 */
@Tag("integration")
class NexusV3ClientTest {

    private static final String NEXUS3_BASE = "https://repository.cloudera.com";

    private static final String COMMONS_LANG3_GROUP = "org.apache.commons";
    private static final String COMMONS_LANG3_ARTIFACT = "commons-lang3";
    private static final String COMMONS_LANG3_VERSION = "3.19.0";
    private static final String COMMONS_LANG3_SHA1 = "d6524b169a6574cd253760c472d419b47bfd37e6";

    private static final String KEYWORD = "spark";

    private NexusV3Client newClient() {
        NexusConfig config = new NexusConfig(NEXUS3_BASE, null, null);
        return new NexusV3Client(config);
    }

    @Test
    void factoryDetectsNexusV3() throws Exception {
        NexusConfig config = new NexusConfig(NEXUS3_BASE, null, null);
        NexusSearch search = NexusSearchFactory.create(config);

        assertNotNull(search);
        assertTrue(search instanceof NexusV3Client,
                "Factory should create a NexusV3Client for a Nexus Repository three server");
        assertTrue(search.supportsVersionDate(), "Nexus three implementation must support version date");
        assertFalse(search.supportsClassSearch(),
                "Nexus three implementation must indicate that dedicated class search is not supported");
    }

    @Test
    void keywordSearch_returnsArtifactsWithVersionDateAndApiDownloadLinks() throws Exception {
        NexusV3Client client = newClient();

        NexusSearchResult result = client.searchByKeyword(KEYWORD, 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "Keyword search should return at least one artifact");

        NexusArtifact first = artifacts.get(0);
        assertNotNull(first.groupId());
        assertNotNull(first.artifactId());
        assertNotNull(first.version());
        assertNotNull(first.repository());

        boolean hasVersionDate = artifacts.stream()
                .map(NexusArtifact::versionDate)
                .anyMatch(d -> d != null && d.isBefore(LocalDate.now().plusDays(1)));
        assertTrue(hasVersionDate, "At least one artifact should have a non null version date");

        boolean allHaveLinks = artifacts.stream()
                .allMatch(a -> a.artifactLink() != null && !a.artifactLink().isBlank());
        assertTrue(allHaveLinks, "All artifacts should have a non empty artifactLink");

        assertTrue(first.artifactLink().startsWith(NEXUS3_BASE + "/service/rest/v1/search/assets/download"),
                "artifactLink must use the Nexus three search-assets download API endpoint");
    }

    @Test
    void sha1Search_returnsExpectedCommonsLang3Artifact() throws Exception {
        NexusV3Client client = newClient();

        NexusSearchResult result = client.searchBySha1(COMMONS_LANG3_SHA1, 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "SHA-1 search should return at least one artifact");

        boolean containsCommonsLang3 = containsArtifact(
                artifacts,
                COMMONS_LANG3_GROUP,
                COMMONS_LANG3_ARTIFACT);
        assertTrue(containsCommonsLang3,
                "Results should contain " + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT);

        NexusArtifact first = artifacts.get(0);
        assertNotNull(first.artifactLink());
        assertTrue(first.artifactLink().startsWith(NEXUS3_BASE + "/service/rest/v1/search/assets/download"),
                "artifactLink must use the Nexus three search-assets download API endpoint");
    }

    @Test
    void gavSearch_returnsMainAndClassifierArtifactsWithApiLinks() throws Exception {
        NexusV3Client client = newClient();

        NexusSearchResult result = client.searchByGav(
                COMMONS_LANG3_GROUP,
                COMMONS_LANG3_ARTIFACT,
                COMMONS_LANG3_VERSION,
                0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "GAV search should return at least one artifact");

        List<NexusArtifact> lang3Artifacts = artifacts.stream()
                .filter(a -> COMMONS_LANG3_GROUP.equals(a.groupId())
                        && COMMONS_LANG3_ARTIFACT.equals(a.artifactId())
                        && COMMONS_LANG3_VERSION.equals(a.version()))
                .toList();

        assertFalse(lang3Artifacts.isEmpty(),
                "Results should contain the exact GAV "
                        + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT + ":" + COMMONS_LANG3_VERSION);

        boolean hasMainArtifact = lang3Artifacts.stream()
                .anyMatch(a -> a.classifier() == null || a.classifier().isBlank());
        assertTrue(hasMainArtifact,
                "There should be at least one main artifact with null or empty classifier for the exact GAV");

        boolean hasClassifierVariants = lang3Artifacts.stream()
                .anyMatch(a -> a.classifier() != null && !a.classifier().isBlank());
        assertTrue(hasClassifierVariants,
                "There should be at least one classifier variant (for example sources or javadoc) for the exact GAV");

        boolean allHaveLinks = lang3Artifacts.stream()
                .allMatch(a -> a.artifactLink() != null
                        && !a.artifactLink().isBlank()
                        && a.artifactLink().startsWith(NEXUS3_BASE + "/service/rest/v1/search/assets/download"));
        assertTrue(allHaveLinks, "All commons-lang3 artifacts must have API based artifactLink values");
    }

    @Test
    void gavSearchWithClassifier() throws Exception {
    	NexusV3Client client = newClient();
    	
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
    			&& !a.artifactLink().isBlank()
    			&& a.artifactLink().startsWith(NEXUS3_BASE + "/service/rest/v1/search/assets/download"));
    	assertTrue(allHaveLinks, "All commons-lang3 artifacts must have API based artifactLink values");
    }
    
    private static boolean containsArtifact(List<NexusArtifact> artifacts,
                                            String expectedGroup,
                                            String expectedArtifact) {
        for (NexusArtifact a : artifacts) {
            if (expectedGroup.equals(a.groupId())
                    && expectedArtifact.equals(a.artifactId())) {
                return true;
            }
        }
        return false;
    }
}