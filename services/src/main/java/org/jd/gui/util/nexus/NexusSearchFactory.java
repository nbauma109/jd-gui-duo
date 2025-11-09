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

import org.jd.gui.api.API;
import org.jd.gui.util.central.SolrCentralSearchClient;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.jd.gui.util.maven.central.helper.NexusConfigHelper;
import org.jd.gui.util.maven.central.helper.ProxyConfig;
import org.jd.gui.util.maven.central.helper.ProxyConfigHelper;

import java.awt.Component;

/**
 * We detect the Nexus server family and return the appropriate implementation.
 * We perform minimal probes with short timeouts. We never use a proxy.
 */
public final class NexusSearchFactory {

    private NexusSearchFactory() { }

    public static NexusSearch create(API api, Component component) {
        ProxyConfig proxyConfig = ProxyConfigHelper.fromPreferences(api.getPreferences(), component);
        NexusConfig nexusConfig = NexusConfigHelper.fromPreferences(api.getPreferences(), component);
    	return create(nexusConfig, proxyConfig);
    }

    public static NexusSearch create(NexusConfig config, ProxyConfig proxyConfig) {
        if (config == null || !config.isPresent()) {
            return new SolrCentralSearchClient(proxyConfig);
        }

        // We probe version 3 first, then version 2
        if (NexusV3Client.probe(config)) {
            return new NexusV3Client(config);
        }
        if (NexusV2Client.probe(config)) {
            return new NexusV2Client(config);
        }

        // If probes fail, we still try version 3 by default to give a best-effort client
        return new NexusV3Client(config);
    }

    public static NexusSearch create(NexusConfig config) {
    	return create(config, null);
    }
}
