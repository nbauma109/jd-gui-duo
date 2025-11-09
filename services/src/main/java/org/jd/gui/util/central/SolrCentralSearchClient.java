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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jd.gui.util.maven.central.helper.ProxyConfig;
import org.jd.gui.util.maven.central.helper.model.ResponseRoot;
import org.jd.gui.util.maven.central.helper.model.response.Response;
import org.jd.gui.util.maven.central.helper.model.response.docs.Docs;
import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Solr based search for Maven Central (search.maven.org).
 *
 * We rely on the JSON /solrsearch/select endpoint and map its Docs into NexusArtifact.
 * For each Docs entry we:
 *   - create one main artifact (classifier null, extension derived from packaging or ec),
 *   - then create one additional artifact per classifier token in ec:
 *       token ".ext"             -> main extension, already covered by main artifact
 *       token "-classifier.ext"  -> artifact with classifier and extension
 *
 * The artifactLink uses the remotecontent endpoint, which is the stable download API.
 */
public final class SolrCentralSearchClient implements NexusSearch, ProxyCapable {

	private static final String SELECT_ENDPOINT = "https://search.maven.org/solrsearch/select";
    private static final String REMOTE_CONTENT_ENDPOINT = "https://search.maven.org/remotecontent?filepath=";
    private static final int ROWS = 200;
    private static final String AND = " AND ";

    private final ProxyConfig proxyConfig;

    public SolrCentralSearchClient(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public NexusSearchResult searchByKeyword(String keyword, int pageNo) throws Exception {
        Objects.requireNonNull(keyword, "keyword must not be null");
        String qExpr = keyword.trim();
        return executeQuery(qExpr, pageNo);
    }

    @Override
    public NexusSearchResult searchBySha1(String sha1, int pageNo) throws Exception {
        Objects.requireNonNull(sha1, "sha1 must not be null");
        String qExpr = "1:" + sha1.trim();
        return executeQuery(qExpr, pageNo);
    }

    @Override
    public NexusSearchResult searchByGav(String groupId, String artifactId, String version, String classifier, String packaging, int pageNo) throws Exception {
        if (groupId == null && artifactId == null) {
            throw new IllegalArgumentException("Either groupId or artifactId must be provided");
        }
        StringBuilder expr = new StringBuilder();
        boolean hasPrevious = false;
        if (StringUtils.isNotBlank(groupId)) {
            expr.append("g:").append(groupId.trim());
            hasPrevious = true;
        }
        if (StringUtils.isNotBlank(artifactId)) {
            if (hasPrevious) {
                expr.append(AND);
            }
            expr.append("a:").append(artifactId.trim());
            hasPrevious = true;
        }
        if (StringUtils.isNotBlank(version)) {
            if (hasPrevious) {
                expr.append(AND);
            }
            expr.append("v:").append(version.trim());
        }
        if (StringUtils.isNotBlank(classifier)) {
        	if (hasPrevious) {
        		expr.append(AND);
        	}
        	expr.append("l:").append(classifier.trim());
        }
        if (StringUtils.isNotBlank(packaging)) {
        	if (hasPrevious) {
        		expr.append(AND);
        	}
        	expr.append("p:").append(packaging.trim());
        }
        NexusSearchResult nexusSearchResult = executeQuery(expr.toString(), pageNo, true);
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
        Objects.requireNonNull(className, "className must not be null");
        String prefix = fullyQualified ? "fc:" : "c:";
        String qExpr = prefix + className.trim();
        return executeQuery(qExpr, pageNo);
    }

    private NexusSearchResult executeQuery(String qExpr, int pageNo) throws Exception {
		return executeQuery(qExpr, pageNo, false);
	}

	private NexusSearchResult executeQuery(String queryExpression, int pageNo, boolean gav) throws Exception {
        int safePage = Math.max(0, pageNo);
        int start = safePage * ROWS;

        String qParam = java.net.URLEncoder.encode(queryExpression, StandardCharsets.UTF_8);
        StringBuilder urlBuilder = new StringBuilder();
		urlBuilder.append(SELECT_ENDPOINT);
		urlBuilder.append("?q=");
		urlBuilder.append(qParam);
		urlBuilder.append("&rows=");
		urlBuilder.append(ROWS);
		urlBuilder.append("&start=");
		urlBuilder.append(start);
		urlBuilder.append("&wt=json");
		if (gav) {
			urlBuilder.append("&core=gav");
		}
		String url = urlBuilder.toString();

        HttpURLConnection connection = open(url, "GET", proxyConfig);
        int code = connection.getResponseCode();

        try (InputStream in = code < 300 ? connection.getInputStream() : connection.getErrorStream();
             Jsonb jsonb = JsonbBuilder.create()) {

            String json = IOUtils.toString(in, StandardCharsets.UTF_8);
            if (code >= 300) {
                throw new IllegalStateException("HTTP " + code + " from " + url + ": " + json);
            }

            ResponseRoot root = jsonb.fromJson(json, ResponseRoot.class);
            return mapResponse(root);
        }
    }

    private NexusSearchResult mapResponse(ResponseRoot root) {
        List<NexusArtifact> artifacts = new ArrayList<>();
        if (root == null) {
            return new NexusSearchResult(artifacts);
        }
        Response response = root.getResponse();
        if (response == null || response.getDocs() == null) {
            return new NexusSearchResult(artifacts);
        }

        for (Docs doc : response.getDocs()) {
            String groupId = doc.getG();
            String artifactId = doc.getA();
            String version = doc.getV() != null && !doc.getV().isBlank()
                    ? doc.getV()
                    : doc.getLatestVersion();

            LocalDate versionDate = doc.getDate();

            String baseExtension = deriveMainExtension(doc);
            String repository = doc.getRepositoryId();
            if (repository == null || repository.isBlank()) {
                repository = "central";
            }

            // Main artifact: classifier null
            String mainLink = buildRemoteContentUrl(groupId, artifactId, version, null, baseExtension);
            artifacts.add(new NexusArtifact(
                    groupId,
                    artifactId,
                    version,
                    versionDate,
                    null,
                    baseExtension,
                    repository,
                    mainLink
            ));

            // Additional artifacts: one per classifier token in ec
            if (doc.getEc() != null) {
                for (String token : doc.getEc()) {
                    if (token == null) {
                        continue;
                    }
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }

                    // Tokens of the form ".jar" are already represented by the main artifact
                    if (trimmed.startsWith(".") && trimmed.length() > 1) {
                        continue;
                    }

                    // Tokens of the form "-sources.jar", "-javadoc.jar", etc.
                    if (trimmed.startsWith("-")) {
                        int dot = trimmed.lastIndexOf('.');
                        if (dot <= 1 || dot == trimmed.length() - 1) {
                            continue;
                        }
                        String classifier = trimmed.substring(1, dot);
                        String ext = trimmed.substring(dot + 1);
                        String link = buildRemoteContentUrl(groupId, artifactId, version, classifier, ext);

                        artifacts.add(new NexusArtifact(
                                groupId,
                                artifactId,
                                version,
                                versionDate,
                                classifier,
                                ext,
                                repository,
                                link
                        ));
                    }
                }
            }
        }

        return new NexusSearchResult(artifacts);
    }
    /**
     * We derive the main extension for the primary artifact.
     *
     * For Solr results, packaging (p) is already the correct main type
     * (for example "jar", "pom"). If it is absent or blank, we simply
     * default to "jar" as a sensible fallback for Maven Central.
     */
    private static String deriveMainExtension(Docs doc) {
        String p = doc.getP();
        if (p != null) {
            p = p.trim();
            if (!p.isEmpty()) {
                return p;
            }
        }
        return "jar";
    }

    /**
     * We build a download link using the documented remotecontent service, which is a stable
     * endpoint for downloads rather than a raw repository path.
     */
    private static String buildRemoteContentUrl(String groupId,
                                                String artifactId,
                                                String version,
                                                String classifier,
                                                String extension) {
        StringBuilder path = new StringBuilder();
        path.append(groupId.replace('.', '/'))
                .append('/')
                .append(artifactId)
                .append('/')
                .append(version)
                .append('/')
                .append(artifactId)
                .append('-')
                .append(version);
        if (StringUtils.isNotBlank(classifier)) {
            path.append('-').append(classifier.trim());
        }
        String ext = (extension == null || extension.isBlank()) ? "jar" : extension.trim();
        path.append('.').append(ext);
        return REMOTE_CONTENT_ENDPOINT
                + java.net.URLEncoder.encode(path.toString(), StandardCharsets.UTF_8);
    }

    @Override
    public boolean supportsVersionDate() {
        return true;
    }

    @Override
    public boolean supportsClassSearch() {
        return true;
    }
}
