/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.maven.central.helper.model;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.jd.gui.util.maven.central.helper.model.responseheader.params.Params;
import org.jd.gui.util.maven.central.helper.model.spellcheck.suggestions.Suggestion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The model of the answer of Maven Central's Solr search, as JSON-B reads it. */
class SolrResponseModelTest {

    private static final String ANSWER = """
            {
              "responseHeader": {
                "status": 0,
                "QTime": 7,
                "params": {
                  "q": "g:org.demo", "core": "gav", "defType": "edismax", "qf": "text^20", "spellcheck": true, "indent": "off", "fl": "id,g,a,v",
                  "start": 3, "spellcheck.count": 5, "sort": "score desc", "rows": 20, "wt": "json", "version": "2.2"
                }
              },
              "response": {"numFound": 42, "start": 3, "docs": []}
            }
            """;

    @Test
    void theHeaderOfAnAnswerGivesItsStatusItsTimeAndItsParameters() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            ResponseRoot root = jsonb.fromJson(ANSWER, ResponseRoot.class);

            assertThat(root.getResponseHeader().getStatus()).isZero();
            assertThat(root.getResponseHeader().getQueryTime()).isEqualTo(7);
            assertThat(root.getResponse().getNumFound()).isEqualTo(42);
            assertThat(root.getResponse().getStart()).isEqualTo(3);
            Params params = root.getResponseHeader().getParams();
            assertThat(params.getQuery()).isEqualTo("g:org.demo");
            assertThat(params.getCore()).isEqualTo("gav");
            assertThat(params.getDefType()).isEqualTo("edismax");
            assertThat(params.getQf()).isEqualTo("text^20");
            assertThat(params.isSpellcheck()).isTrue();
            assertThat(params.getIndent()).isEqualTo("off");
            assertThat(params.getFl()).isEqualTo("id,g,a,v");
            assertThat(params.getStart()).isEqualTo(3);
            assertThat(params.getSpellCheckCont()).isEqualTo(5);
            assertThat(params.getSort()).isEqualTo("score desc");
            assertThat(params.getRows()).isEqualTo(20);
            assertThat(params.getWt()).isEqualTo("json");
            assertThat(params.getVersion()).isEqualTo("2.2");
        }
    }

    @Test
    void theParametersCanBeSetAndRead() {
        Params params = new Params();
        params.setQuery("q");
        params.setCore("c");
        params.setDefType("d");
        params.setQf("f");
        params.setSpellcheck(true);
        params.setIndent("i");
        params.setFl("fl");
        params.setStart(1);
        params.setSpellCheckCont(2);
        params.setSort("s");
        params.setRows(3);
        params.setWt("w");
        params.setVersion("v");

        assertThat(params).extracting(Params::getQuery, Params::getCore, Params::getDefType, Params::getQf, Params::isSpellcheck, Params::getIndent,
                Params::getFl, Params::getStart, Params::getSpellCheckCont, Params::getSort, Params::getRows, Params::getWt, Params::getVersion)
                .containsExactly("q", "c", "d", "f", true, "i", "fl", 1, 2, "s", 3, "w", "v");
    }

    @Test
    void aSpellCheckSuggestionGivesItsOffsetsAndItsWords() throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            Suggestion suggestion = jsonb.fromJson("{\"numFound\": 2, \"startOffset\": 4, \"endOffset\": 9, \"suggestion\": [\"commons\", \"common\"]}", Suggestion.class);

            assertThat(suggestion.getNumFound()).isEqualTo(2);
            assertThat(suggestion.getStartOffset()).isEqualTo(4);
            assertThat(suggestion.getEndOffset()).isEqualTo(9);
            assertThat(suggestion.getSuggestions()).containsExactly("commons", "common");
        }
    }

    @Test
    void theParametersWrittenAsStringsAreRead() throws Exception {
        String solr = """
                {"responseHeader": {"status": 0, "QTime": 12, "params": {"q": "g:org.demo", "core": "gav", "indent": "off", "fl": "id,g,a,v",
                  "start": "20", "sort": "score desc", "rows": "40", "wt": "json", "version": "2.2"}},
                 "response": {"numFound": 0, "start": 0, "docs": []}}
                """;
        try (Jsonb jsonb = JsonbBuilder.create()) {
            Params params = jsonb.fromJson(solr, ResponseRoot.class).getResponseHeader().getParams();

            assertThat(params.getStart()).isEqualTo(20);
            assertThat(params.getRows()).isEqualTo(40);
        }
    }
}
