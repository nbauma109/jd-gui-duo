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
package org.jd.gui.util.nexus;

import org.jd.gui.util.central.SolrCentralSearchClient;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NexusSearchFactoryTest {

    @Test
    void create_withNullConfig_returnsSolrCentralSearchClient() {
        NexusSearch search = NexusSearchFactory.create(null);

        assertNotNull(search);
        assertInstanceOf(SolrCentralSearchClient.class, search);
    }

    @Test
    void create_withAbsentConfig_returnsSolrCentralSearchClient() {
        // NexusConfig with null baseUrl means isPresent() == false
        NexusConfig absentConfig = new NexusConfig(null, null, null);
        NexusSearch search = NexusSearchFactory.create(absentConfig);

        assertNotNull(search);
        assertInstanceOf(SolrCentralSearchClient.class, search);
    }

    @Test
    void create_withBlankBaseUrl_returnsSolrCentralSearchClient() {
        NexusConfig blankConfig = new NexusConfig("   ", null, null);
        NexusSearch search = NexusSearchFactory.create(blankConfig);

        assertNotNull(search);
        assertInstanceOf(SolrCentralSearchClient.class, search);
    }

    @Test
    void create_withNullConfigAndNullProxy_returnsSolrCentralSearchClient() {
        NexusSearch search = NexusSearchFactory.create(null, null);

        assertNotNull(search);
        assertInstanceOf(SolrCentralSearchClient.class, search);
    }

    @Test
    void solrCentralSearchClient_supportsVersionDateAndClassSearch() {
        NexusSearch search = NexusSearchFactory.create(null);

        assertInstanceOf(SolrCentralSearchClient.class, search);
        // SolrCentralSearchClient supports both features
        org.junit.jupiter.api.Assertions.assertTrue(search.supportsVersionDate());
        org.junit.jupiter.api.Assertions.assertTrue(search.supportsClassSearch());
    }
}
