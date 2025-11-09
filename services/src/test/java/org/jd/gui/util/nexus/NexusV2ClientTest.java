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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for NexusV2Client against the public Nexus 2 instance at
 * https://nexus.xwiki.org/nexus.
 *
 * These tests verify:
 *   - factory detection of version 2
 *   - keyword search
 *   - SHA-1 search
 *   - GAV search, including classifier variants and API based artifact links
 *   - class name search (simple and fully qualified)
 *   - the fact that version dates are not populated
 *
 * Tests are network dependent and tagged as "integration".
 */
@Tag("integration")
class NexusV2ClientTest {

    private static final String NEXUS2_BASE = "https://nexus.xwiki.org/nexus";

    private static final String COMMONS_LANG3_GROUP = "org.apache.commons";
    private static final String COMMONS_LANG3_ARTIFACT = "commons-lang3";
    private static final String COMMONS_LANG3_VERSION = "3.19.0";
    private static final String COMMONS_LANG3_SHA1 = "d6524b169a6574cd253760c472d419b47bfd37e6";

    private static final String FULL_CLASS_NAME = "org.apache.commons.lang3.StringUtils";
    private static final String SIMPLE_CLASS_NAME = "StringUtils";

    private NexusV2Client newClient() {
        NexusConfig config = new NexusConfig(NEXUS2_BASE, null, null);
        return new NexusV2Client(config);
    }

    @Test
    void factoryDetectsNexusV2() throws Exception {
        NexusConfig config = new NexusConfig(NEXUS2_BASE, null, null);
        NexusSearch search = NexusSearchFactory.create(config);

        assertNotNull(search);
        assertTrue(search instanceof NexusV2Client, "Factory should create a NexusV2Client for a Nexus 2 server");
        assertFalse(search.supportsVersionDate(), "Nexus 2 implementation must not claim version date support");
        assertTrue(search.supportsClassSearch(), "Nexus 2 implementation must support class search");
    }

    @Test
    void keywordSearch_returnsArtifactsWithApiDownloadLinks() throws Exception {
        NexusV2Client client = newClient();

        NexusSearchResult result = client.searchByKeyword("xwiki", 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "Keyword search should return at least one artifact");

        NexusArtifact first = artifacts.get(0);
        assertNotNull(first.groupId());
        assertNotNull(first.artifactId());
        assertNotNull(first.version());
        assertNull(first.versionDate(), "Nexus 2 client must not populate versionDate");

        assertNotNull(first.repository(), "Repository must be populated from repositoryId");
        assertNotNull(first.artifactLink(), "artifactLink must be populated");
        assertTrue(first.artifactLink().startsWith(NEXUS2_BASE + "/service/local/artifact/maven/content"),
                "artifactLink must use the Nexus 2 maven content API endpoint");
    }

    @Test
    void sha1Search_returnsExpectedCommonsLang3Artifact() throws Exception {
        NexusV2Client client = newClient();

        NexusSearchResult result = client.searchBySha1(COMMONS_LANG3_SHA1, 0);

        assertNotNull(result);
        List<NexusArtifact> artifacts = result.artifacts();
        assertNotNull(artifacts);
        assertFalse(artifacts.isEmpty(), "SHA-1 search should return at least one artifact");

        boolean containsCommonsLang3 = containsArtifact(artifacts,
                COMMONS_LANG3_GROUP, COMMONS_LANG3_ARTIFACT);
        assertTrue(containsCommonsLang3,
                "Results should contain " + COMMONS_LANG3_GROUP + ":" + COMMONS_LANG3_ARTIFACT);

        NexusArtifact first = artifacts.get(0);
        assertNull(first.versionDate(), "Nexus 2 client must not populate versionDate");
        assertNotNull(first.artifactLink());
        assertTrue(first.artifactLink().startsWith(NEXUS2_BASE + "/service/local/artifact/maven/content"),
                "artifactLink must use the Nexus 2 maven content API endpoint");
    }

    @Test
    void gavSearch_returnsMainAndClassifierArtifactsWithApiLinks() throws Exception {
        NexusV2Client client = newClient();

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
                "We expect at least one main artifact with null or empty classifier for the exact GAV");

        boolean hasClassifierVariants = lang3Artifacts.stream()
                .anyMatch(a -> a.classifier() != null && !a.classifier().isBlank());
        assertTrue(hasClassifierVariants,
                "We expect at least one classifier variant (for example sources or javadoc) for the exact GAV");

        boolean allHaveLinks = lang3Artifacts.stream()
                .allMatch(a -> a.artifactLink() != null
                        && !a.artifactLink().isBlank()
                        && a.artifactLink().startsWith(NEXUS2_BASE + "/service/local/artifact/maven/content"));
        assertTrue(allHaveLinks, "All commons-lang3 artifacts must have API-based artifactLink values");
    }

    @Test
    void gavSearchWithClassifier() throws Exception {
    	NexusV2Client client = newClient();
    	
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
    			&& a.artifactLink().startsWith(NEXUS2_BASE + "/service/local/artifact/maven/content"));
    	assertTrue(allHaveLinks, "All commons-lang3 artifacts must have API-based artifactLink values");
    }
    
    @Test
    void classNameSearch_simpleAndFullyQualifiedReturnResults() throws Exception {
        NexusV2Client client = newClient();

        NexusSearchResult simple = client.searchByClassName(SIMPLE_CLASS_NAME, false, 0);
        assertNotNull(simple);
        assertNotNull(simple.artifacts());
        assertFalse(simple.artifacts().isEmpty(),
                "Simple class name search should return at least one artifact");

        NexusSearchResult fqcn = client.searchByClassName(FULL_CLASS_NAME, true, 0);
        assertNotNull(fqcn);
        assertNotNull(fqcn.artifacts());
        assertFalse(fqcn.artifacts().isEmpty(),
                "Fully qualified class name search should return at least one artifact");
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
