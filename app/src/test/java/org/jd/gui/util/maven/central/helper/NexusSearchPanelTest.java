/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.maven.central.helper;

import org.fife.ui.rtextarea.RTextScrollPane;
import org.jd.gui.api.API;
import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

/** The search of artifacts: what is asked of the server, the results in the table and the snippets for the build tools. */
class NexusSearchPanelTest {

    private static final NexusArtifact PLAIN = new NexusArtifact("org.demo", "tool", "1.2", LocalDate.of(2024, 3, 9), "", "jar", "releases",
            "https://repo.example.com/tool-1.2.jar");
    private static final NexusArtifact OTHER = new NexusArtifact("org.demo", "tool", "1.3", null, "linux", "war", "releases",
            "https://repo.example.com/tool-1.3-linux.war");

    private final List<String> asked = new ArrayList<>();
    private final List<URI> opened = new ArrayList<>();
    private List<NexusArtifact> firstPage = List.of(PLAIN);
    private NexusSearchPanel panel;

    @BeforeEach
    void createPanel() throws Exception {
        Map<String, String> preferences = new HashMap<>();
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getPreferences" -> preferences;
            case "isDarkMode" -> false;
            case "openURI" -> {
                opened.add((URI) args[0]);
                yield true;
            }
            case "compareFiles" -> {
                opened.add((URI) args[0]);
                opened.add((URI) args[1]);
                yield null;
            }
            default -> null;
        });
        SwingUtilities.invokeAndWait(() -> panel = new NexusSearchPanel(api));
        // a server that has two pages of results: the one given, then an empty one
        NexusSearch server = (NexusSearch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {NexusSearch.class}, (proxy, method, args) -> {
            StringBuilder call = new StringBuilder(method.getName());
            for (Object argument : args) {
                call.append(' ').append(argument);
            }
            asked.add(call.toString());
            int page = (Integer) args[args.length - 1];
            return new NexusSearchResult(page == 0 ? firstPage : List.of());
        });
        Field search = NexusSearchPanel.class.getDeclaredField("search");
        search.setAccessible(true);
        search.set(panel, server);
    }

    private static <T extends Component> List<T> find(Component component, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(find(child, type));
            }
        }
        return found;
    }

    private JTabbedPane tabs(String firstTitle) {
        return find(panel, JTabbedPane.class).stream().filter(t -> firstTitle.equals(t.getTitleAt(0))).findFirst().orElseThrow();
    }

    private JTable table() {
        return find(panel, JTable.class).get(0);
    }

    private JButton button(String text) {
        return find(panel, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    /** Searches in a mode, with the texts in its fields, and waits for the end of the search. */
    private void search(int mode, String... texts) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            button("Search").addPropertyChangeListener("enabled", change -> {
                if (Boolean.TRUE.equals(change.getNewValue())) {
                    done.countDown();
                }
            });
            JTabbedPane modes = tabs("Keyword");
            modes.setSelectedIndex(mode);
            List<JTextField> fields = find((Component) modes.getComponentAt(mode), JTextField.class);
            for (int i = 0; i < texts.length; i++) {
                fields.get(i).setText(texts[i]);
            }
            button("Search").doClick(0);
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        // the rest of the end of the search runs after the button is enabled
        SwingUtilities.invokeAndWait(() -> { });
    }

    private Map<String, String> snippets() throws Exception {
        Map<String, String> snippets = new LinkedHashMap<>();
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tools = tabs("Maven");
            for (int i = 0; i < tools.getTabCount(); i++) {
                snippets.put(tools.getTitleAt(i), ((RTextScrollPane) tools.getComponentAt(i)).getTextArea().getText());
            }
        });
        return snippets;
    }

    @ParameterizedTest(name = "mode {0} asks {3}")
    @CsvSource(delimiter = '|', value = {
            "0 | ' tool '    | ''     | searchByKeyword tool 0",
            "1 | ' abc123 '  | ''     | searchBySha1 abc123 0",
            "2 | org.demo    | tool   | searchByGav org.demo tool null 0",
            "3 | org.demo.A  | ''     | searchByClassName org.demo.A true 0",
            "3 | A           | ''     | searchByClassName A false 0"})
    void eachModeAsksItsSearchAndShowsTheResults(int mode, String first, String second, String call) throws Exception {
        if (mode == 2) {
            search(mode, first, second);
        } else {
            search(mode, first);
        }

        // the search of a hash has one page only, the others go on until an empty one
        assertThat(asked).startsWith(call).hasSize(mode == 1 ? 1 : 2);
        assertThat(table().getRowCount()).isEqualTo(1);
        assertThat(table().getSelectedRow()).isZero();
    }

    @Test
    void theTableShowsWhatIsKnownOfEachArtifact() throws Exception {
        firstPage = List.of(PLAIN, OTHER);

        search(2, "org.demo", "tool", "1.2");

        assertThat(asked.get(0)).isEqualTo("searchByGav org.demo tool 1.2 0");
        JTable table = table();
        List<String> columns = new ArrayList<>();
        List<Object> first = new ArrayList<>();
        for (int column = 0; column < table.getColumnCount(); column++) {
            columns.add(table.getColumnName(column));
            first.add(table.getValueAt(0, column));
            assertThat(table.isCellEditable(0, column)).isFalse();
        }
        assertThat(columns).containsExactly("Group", "Artifact", "Version", "Date", "Classifier", "Extension", "Repository");
        assertThat(first).containsExactly("org.demo", "tool", "1.2", "2024-03-09", "", "jar", "releases");
        assertThat(table.getValueAt(1, 3)).isEqualTo("");
    }

    @Test
    void theSnippetsOfAPlainArtifactNameItsCoordinates() throws Exception {
        search(0, "tool");

        Map<String, String> snippets = snippets();

        assertThat(snippets.get("Maven")).contains("<groupId>org.demo</groupId>", "<artifactId>tool</artifactId>", "<version>1.2</version>")
                .doesNotContain("<classifier>", "<type>");
        assertThat(snippets.get("Gradle")).contains("implementation \"org.demo:tool:1.2\"");
        assertThat(snippets.get("Grape")).contains("@Grab(group='org.demo', module='tool', version='1.2')");
        assertThat(snippets)
                .containsEntry("Ivy", "<dependency org=\"org.demo\" name=\"tool\" rev=\"1.2\" type=\"jar\" />\n")
                .containsEntry("SBT", "libraryDependencies += \"org.demo\" % \"tool\" % \"1.2\"\n")
                .containsEntry("Leiningen", "[\"org.demo/tool\" \"1.2\"]\n")
                .containsEntry("Buildr", "compile 'org.demo:tool:jar:1.2'\n")
                .containsEntry("bld", "dependency(\"org.demo\", \"tool\", \"1.2\");\n");
    }

    @Test
    void theSnippetsFollowTheSelectionAndNameTheClassifierAndTheType() throws Exception {
        firstPage = List.of(PLAIN, OTHER);
        search(0, "tool");

        SwingUtilities.invokeAndWait(() -> table().setRowSelectionInterval(1, 1));
        Map<String, String> snippets = snippets();

        assertThat(snippets.get("Maven")).contains("<classifier>linux</classifier>", "<type>war</type>");
        assertThat(snippets.get("Gradle")).contains("implementation \"org.demo:tool:1.3:linux@war\"");
        assertThat(snippets.get("Ivy")).contains("type=\"war\" classifier=\"linux\"");
        assertThat(snippets.get("SBT")).contains("classifier \"linux\"");
        assertThat(snippets.get("Leiningen")).contains(":classifier \"linux\" :extension \"war\"");
        assertThat(snippets.get("Grape")).contains("classifier='linux', type='war'");
        assertThat(snippets).containsEntry("Buildr", "compile 'org.demo:tool:war:linux:1.3'\n");
        assertThat(snippets.get("bld")).contains("classifier=\"linux\", type=\"war\"");
        SwingUtilities.invokeAndWait(() -> table().clearSelection());
        assertThat(snippets().values()).containsOnly("");
    }

    @ParameterizedTest
    @CsvSource(value = {"'', war, org.demo:tool:2@war", "sources, jar, org.demo:tool:2:sources", "sources, '', org.demo:tool:2:sources"})
    void theGradleSnippetNamesAnotherTypeThanJarAlsoWithoutAClassifier(String classifier, String extension, String expected) throws Exception {
        firstPage = List.of(new NexusArtifact("org.demo", "tool", "2", null, classifier, extension, "releases", ""));

        search(0, "tool");

        assertThat(snippets().get("Gradle")).contains("\"" + expected + "\"");
    }

    @Test
    void aDoubleClickOpensTheArtifactAndTwoOfThemCanBeCompared() throws Exception {
        firstPage = List.of(PLAIN, OTHER);
        search(0, "tool");

        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.setSize(700, 100);
            int y = table.getRowHeight() + 2;
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, 5, y, 2, false, MouseEvent.BUTTON1));
            // below the rows
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 90, 2, false, MouseEvent.BUTTON1));
        });
        assertThat(opened).containsExactly(URI.create(OTHER.artifactLink()));
        opened.clear();

        Field field = NexusSearchPanel.class.getDeclaredField("compareFilesItem");
        field.setAccessible(true);
        JMenuItem compare = (JMenuItem) field.get(panel);
        assertThat(compare.getParent()).isInstanceOf(JPopupMenu.class);
        SwingUtilities.invokeAndWait(() -> {
            table().setRowSelectionInterval(0, 0);
            compare.doClick(0); // one artifact only
            table().setRowSelectionInterval(0, 1);
            compare.doClick(0);
        });

        assertThat(opened).containsExactly(URI.create(PLAIN.artifactLink()), URI.create(OTHER.artifactLink()));
    }
}
