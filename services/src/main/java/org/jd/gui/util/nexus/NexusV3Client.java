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

import org.apache.commons.lang3.StringUtils;
import org.jd.gui.util.maven.central.helper.NexusConfig;
import org.jd.gui.util.nexus.dto.Asset;
import org.jd.gui.util.nexus.dto.Component;
import org.jd.gui.util.nexus.dto.Maven2;
import org.jd.gui.util.nexus.dto.Nexus3AssetSearchResponse;
import org.jd.gui.util.nexus.dto.Nexus3Response;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Nexus Repository 3 search implementation.
 *
 * We use the version 1 Search API:
 *
 *   - GET /service/rest/v1/search              (component search)
 *   - GET /service/rest/v1/search/assets       (asset search, for SHA-1)
 *
 * For each asset we create one NexusArtifact with:
 *   - groupId, artifactId, version, classifier, extension from the "maven2" object
 *   - repository from the asset repository name
 *   - versionDate from asset.lastModified (if parseable)
 *   - artifactLink built from the search-assets download API:
 *
 *       /service/rest/v1/search/assets/download
 *          ?repository=<repo>
 *          &group=<groupId>
 *          &name=<artifactId>
 *          &version=<version>
 *          &extension=<extension>
 *          &classifier=<classifier>
 *
 * We do not use proxies in this client.
 *
 * Pagination:
 *   The Nexus 3 Search API uses continuation tokens, not page numbers. For the
 *   sake of a simple, stateless client we currently ignore pageNo and always
 *   fetch the first page of results.
 */
final class NexusV3Client extends AbstractNexusClient implements NexusSearch {

    NexusV3Client(NexusConfig config) {
        super(config);
    }

    // Probe helper used by the factory
    static boolean probe(NexusConfig cfg) {
        try {
            String url = trimTrailingSlash(cfg.baseUrl) + "/service/rest/v1/status";
            String body = new NexusV3Client(cfg).get(url, 4000, 6000);
            return body != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public NexusSearchResult searchByKeyword(String keyword, int pageNo) throws Exception {
        String path = "/service/rest/v1/search?q=" + enc(keyword);
        String body = get(buildUrl(config.baseUrl, path), 8000, 15000);
        return parseComponents(body);
    }

    @Override
    public NexusSearchResult searchBySha1(String sha1, int pageNo) throws Exception {
        String path = "/service/rest/v1/search/assets?sha1=" + enc(sha1);
        String body = get(buildUrl(config.baseUrl, path), 8000, 15000);
        return parseAssets(body);
    }

    @Override
    public NexusSearchResult searchByGav(String groupId, String artifactId, String version, String classifier, String packaging, int pageNo) throws Exception {
        StringBuilder path = new StringBuilder("/service/rest/v1/search?");

        boolean first = true;
        if (groupId != null && !groupId.trim().isEmpty()) {
            path.append("group=").append(enc(groupId.trim()));
            first = false;
        }
        if (artifactId != null && !artifactId.trim().isEmpty()) {
            if (!first) {
                path.append('&');
            }
            path.append("name=").append(enc(artifactId.trim()));
            first = false;
        }
        if (version != null && !version.trim().isEmpty()) {
            if (!first) {
                path.append('&');
            }
            path.append("version=").append(enc(version.trim()));
        }

        String body = get(buildUrl(config.baseUrl, path.toString()), 8000, 15000);
        NexusSearchResult nexusSearchResult = parseComponents(body);
        if (classifier != null) {
        	nexusSearchResult.artifacts().removeIf(a -> !classifier.equals(a.classifier()));
        }
        if (packaging != null) {
        	nexusSearchResult.artifacts().removeIf(a -> !packaging.equals(a.extension()));
        }
		return nexusSearchResult;
    }

    @Override
    public NexusSearchResult searchByClassName(String className, boolean fullyQualified, int pageNo) throws Exception {
        throw new UnsupportedOperationException();
    }

    private NexusSearchResult parseComponents(String json) {
        List<NexusArtifact> list = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return new NexusSearchResult(list);
        }

        try (Jsonb jsonb = JsonbBuilder.create()) {
            Nexus3Response resp = jsonb.fromJson(json, Nexus3Response.class);
            if (resp == null || resp.getItems() == null) {
                return new NexusSearchResult(list);
            }
            for (Component component : resp.getItems()) {
                if (component.getAssets() == null) {
                    continue;
                }
                for (Asset asset : component.getAssets()) {
                    NexusArtifact artifact = createArtifactFromAsset(asset);
                    if (artifact != null) {
                        list.add(artifact);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new NexusSearchResult(list);
    }

    private NexusSearchResult parseAssets(String json) {
        List<NexusArtifact> list = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return new NexusSearchResult(list);
        }

        try (Jsonb jsonb = JsonbBuilder.create()) {
            Nexus3AssetSearchResponse resp = jsonb.fromJson(json, Nexus3AssetSearchResponse.class);
            if (resp == null || resp.getItems() == null) {
                return new NexusSearchResult(list);
            }
            for (Asset asset : resp.getItems()) {
                NexusArtifact artifact = createArtifactFromAsset(asset);
                if (artifact != null) {
                    list.add(artifact);
                }
            }
        } catch (Exception ignored) {
        }

        return new NexusSearchResult(list);
    }

    private NexusArtifact createArtifactFromAsset(Asset asset) {
        if (asset == null) {
            return null;
        }
        Maven2 m2 = asset.getMaven2();
        if (m2 == null) {
            return null;
        }

        String groupId = m2.getGroupId();
        String artifactId = m2.getArtifactId();
        String version = m2.getVersion();
        String classifier = m2.getClassifier();
        String extension = m2.getExtension();
        String repository = asset.getRepository();

        LocalDate versionDate = null;
        String lastModified = asset.getLastModified();
        if (StringUtils.isNotBlank(lastModified)) {
            try {
                versionDate = OffsetDateTime.parse(lastModified.trim()).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // leave null
            }
        }

        String artifactLink = buildDownloadUrl(
                config.baseUrl,
                repository,
                groupId,
                artifactId,
                version,
                extension,
                classifier
        );

        return new NexusArtifact(
                groupId,
                artifactId,
                version,
                versionDate,
                classifier,
                extension,
                repository,
                artifactLink
        );
    }

    /**
     * We build an API based download URL using the search-assets download endpoint:
     *
     *   /service/rest/v1/search/assets/download
     *      ?repository=<repo>
     *      &group=<groupId>
     *      &name=<artifactId>
     *      &version=<version>
     *      &extension=<extension>
     *      &classifier=<classifier>
     */
    private static String buildDownloadUrl(String baseUrl,
                                           String repository,
                                           String groupId,
                                           String artifactId,
                                           String version,
                                           String extension,
                                           String classifier) {
        StringBuilder sb = new StringBuilder();
        sb.append(trimTrailingSlash(baseUrl))
                .append("/service/rest/v1/search/assets/download");

        sb.append("?repository=").append(urlEncode(repository));
        sb.append("&group=").append(urlEncode(groupId));
        sb.append("&name=").append(urlEncode(artifactId));
        sb.append("&version=").append(urlEncode(version));

        String ext = (extension == null || extension.isBlank()) ? "jar" : extension.trim();
        sb.append("&extension=").append(urlEncode(ext));

        // classifier may be empty string; the API accepts an empty classifier
        if (classifier != null) {
            sb.append("&classifier=").append(urlEncode(classifier.trim()));
        }

        return sb.toString();
    }

    private static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public boolean supportsVersionDate() {
        return true; // lastModified is available on assets
    }

    @Override
    public boolean supportsClassSearch() {
        // Nexus 3 does not expose dedicated class-name search; we approximate via "q="
        // but signal to callers that class search is not officially supported.
        return false;
    }
}