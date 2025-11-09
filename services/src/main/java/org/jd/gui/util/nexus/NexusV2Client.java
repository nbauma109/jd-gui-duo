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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * We implement Nexus version 2 search with a small DOM parser on the Lucene search XML.
 *
 * For each <artifact> entry we:
 *   - read groupId, artifactId, version,
 *   - iterate its <artifactHit> children to obtain repositoryId and <artifactLink> entries,
 *   - for each <artifactLink> we create one NexusArtifact with classifier and extension.
 *
 * The download link uses the official Nexus 2 API endpoint:
 *
 *   /service/local/artifact/maven/content?r=repoId&g=...&a=...&v=...&p=...&c=...
 *
 * instead of a direct repository path.
 */
final class NexusV2Client extends AbstractNexusClient implements NexusSearch {

    private static final String COUNT = "&count=";
	private static final String FROM = "&from=";
	private static final int PAGE_SIZE = 20;

    NexusV2Client(NexusConfig config) {
        super(config);
    }

    // Probe helper used by the factory
    static boolean probe(NexusConfig cfg) {
        try {
            String url = trimTrailingSlash(cfg.baseUrl) + "/service/local/status";
            String body = new NexusV2Client(cfg).get(url, 4000, 6000);
            return body != null && body.contains("<status>");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public NexusSearchResult searchByKeyword(String keyword, int pageNo) throws Exception {
        int from = Math.max(0, pageNo) * PAGE_SIZE;
        String path = "/service/local/lucene/search?q=" + enc(keyword)
                + FROM + from
                + COUNT + PAGE_SIZE;
        String body = get(buildUrl(config.baseUrl, path), 8000, 15000);
        return parse(body);
    }

    @Override
    public NexusSearchResult searchBySha1(String sha1, int pageNo) throws Exception {
        int from = Math.max(0, pageNo) * PAGE_SIZE;
        String path = "/service/local/lucene/search?sha1=" + enc(sha1)
                + FROM + from
                + COUNT + PAGE_SIZE;
        String body = get(buildUrl(config.baseUrl, path), 8000, 15000);
        return parse(body);
    }

    @Override
    public NexusSearchResult searchByGav(String groupId, String artifactId, String version, String classifier, String packaging, int pageNo) throws Exception {
        int from = Math.max(0, pageNo) * PAGE_SIZE;
        StringBuilder path = new StringBuilder("/service/local/lucene/search?g=")
                .append(enc(groupId)).append("&a=").append(enc(artifactId));
        if (version != null && !version.trim().isEmpty()) {
            path.append("&v=").append(enc(version));
        }
        if (classifier != null && !classifier.trim().isEmpty()) {
        	path.append("&c=").append(enc(classifier));
        }
        if (packaging != null && !packaging.trim().isEmpty()) {
        	path.append("&p=").append(enc(packaging));
        }
        path.append(FROM).append(from).append(COUNT).append(PAGE_SIZE);
        String body = get(buildUrl(config.baseUrl, path.toString()), 8000, 15000);
        NexusSearchResult nexusSearchResult = parse(body);
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
        int from = Math.max(0, pageNo) * PAGE_SIZE;
        String param = "cn";
        String path = "/service/local/lucene/search?" + param + "=" + enc(className)
                + FROM + from
                + COUNT + PAGE_SIZE;
        String body = get(buildUrl(config.baseUrl, path), 8000, 15000);
        return parse(body);
    }

    /**
     * We parse the Nexus 2 Lucene XML response into a list of NexusArtifact instances.
     * The structure is:
     *
     *   <artifact>
     *     <groupId>...</groupId>
     *     <artifactId>...</artifactId>
     *     <version>...</version>
     *     <artifactHits>
     *       <artifactHit>
     *         <repositoryId>...</repositoryId>
     *         <artifactLinks>
     *           <artifactLink>
     *             <classifier>sources</classifier>
     *             <extension>jar</extension>
     *           </artifactLink>
     *           ...
     *         </artifactLinks>
     *       </artifactHit>
     *     </artifactHits>
     *   </artifact>
     *
     * For each (artifactHit, artifactLink) pair we create one NexusArtifact.
     */
    private NexusSearchResult parse(String xml) {
        List<NexusArtifact> list = new ArrayList<>();
        if (xml == null || xml.isEmpty()) {
            return new NexusSearchResult(list);
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList artifacts = doc.getElementsByTagName("artifact");
            for (int i = 0; i < artifacts.getLength(); i++) {
                Element artifactElement = (Element) artifacts.item(i);
                String groupId = text(artifactElement, "groupId");
                String artifactId = text(artifactElement, "artifactId");
                String version = text(artifactElement, "version");

                NodeList hits = artifactElement.getElementsByTagName("artifactHit");
                for (int h = 0; h < hits.getLength(); h++) {
                    Element hitElement = (Element) hits.item(h);
                    String repositoryId = text(hitElement, "repositoryId");

                    NodeList links = hitElement.getElementsByTagName("artifactLink");
                    for (int l = 0; l < links.getLength(); l++) {
                        Element linkElement = (Element) links.item(l);
                        String classifier = text(linkElement, "classifier");
                        String extension = text(linkElement, "extension");

                        String artifactLink = buildDownloadUrl(
                                config.baseUrl,
                                groupId,
                                artifactId,
                                version,
                                extension,
                                classifier,
                                repositoryId);

                        list.add(new NexusArtifact(
                                groupId,
                                artifactId,
                                version,
                                null,              // versionDate is not resolved for Nexus 2
                                classifier,
                                extension,
                                repositoryId,
                                artifactLink
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new NexusSearchResult(list);
    }

    private static String text(Element e, String tag) {
        NodeList nodes = e.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value != null ? value.trim() : null;
    }

    /**
     * We build a Nexus 2 download URL using the official artifact content API.
     *
     * /service/local/artifact/maven/content
     *   ?r=repoId
     *   &g=groupId
     *   &a=artifactId
     *   &v=version
     *   &p=extension
     *   &c=classifier (optional)
     */
    private static String buildDownloadUrl(String baseUrl,
                                           String groupId,
                                           String artifactId,
                                           String version,
                                           String extension,
                                           String classifier,
                                           String repositoryId) {
        StringBuilder sb = new StringBuilder();
        sb.append(trimTrailingSlash(baseUrl))
                .append("/service/local/artifact/maven/content");
        sb.append("?r=").append(urlEncode(repositoryId));
        sb.append("&g=").append(urlEncode(groupId));
        sb.append("&a=").append(urlEncode(artifactId));
        sb.append("&v=").append(urlEncode(version));

        String ext = (extension == null || extension.isBlank()) ? "jar" : extension.trim();
        sb.append("&p=").append(urlEncode(ext));

        if (classifier != null && !classifier.trim().isEmpty()) {
            sb.append("&c=").append(urlEncode(classifier.trim()));
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
        return false; // we do not resolve uploaded timestamps per artifact
    }

    @Override
    public boolean supportsClassSearch() {
        return true; // cn and fc parameters are supported by Nexus 2 Lucene search
    }
}
