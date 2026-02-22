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
package org.jd.gui.util.maven.central.helper;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jd.gui.api.API;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.ThemeUtil;
import org.jd.gui.util.nexus.NexusSearch;
import org.jd.gui.util.nexus.NexusSearchFactory;
import org.jd.gui.util.nexus.model.NexusArtifact;
import org.jd.gui.util.nexus.model.NexusSearchResult;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.HighlighterFactory;
import org.oxbow.swingbits.list.CheckListRenderer;
import org.oxbow.swingbits.table.filter.TableRowFilterSupport;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

/**
 * We provide a search panel for NexusSearch implementations.
 *
 * We expose four modes of search:
 *  - Keyword search
 *  - SHA-1 search
 *  - Group, artifact, version search
 *  - Class name search (simple or fully qualified, inferred from the presence of a dot)
 *
 * We execute searches in a background SwingWorker and report progress
 * through a single JProgressBar at the bottom. Results are displayed
 * in a table backed by a custom table model and are appended as pages
 * arrive. Snippets for several build tools are shown in RSyntaxTextArea
 * tabs for the selected row. A double click on a row opens the artifact
 * link in the main JD-GUI window via API.openURI. A right click on the
 * table shows a context menu with a Compare Files action when exactly
 * two rows are selected, which calls API.compareFiles.
 */
public final class NexusSearchPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_PAGES = 50;

    private final transient API api;
    private final transient NexusSearch search;

    private final JTabbedPane modeTabs;

    // Keyword tab
    private final JTextField keywordField;

    // SHA-1 tab
    private final JTextField sha1Field;

    // GAV tab
    private final JTextField groupField;
    private final JTextField artifactField;
    private final JTextField versionField;

    // Class tab
    private final JTextField classNameField;

    // Controls
    private JButton searchButton;
    private JButton cancelButton;
    private final JProgressBar progressBar;

    private final JXTable resultTable;
    private final ResultTableModel tableModel;

    private final JTabbedPane snippetTabs;
    private final RSyntaxTextArea mavenArea;
    private final RSyntaxTextArea gradleArea;
    private final RSyntaxTextArea ivyArea;
    private final RSyntaxTextArea sbtArea;
    private final RSyntaxTextArea leinArea;
    private final RSyntaxTextArea grapeArea;
    private final RSyntaxTextArea buildrArea;
    private final RSyntaxTextArea bldArea;

    private final JPopupMenu tablePopupMenu;
    private final JMenuItem compareFilesItem;

    private transient SearchWorker currentWorker;

    public NexusSearchPanel(API api) {
        super(new BorderLayout());
        this.api = api;

        modeTabs = new JTabbedPane();

        keywordField = new JTextField(30);
        sha1Field = new JTextField(30);
        groupField = new JTextField(20);
        artifactField = new JTextField(20);
        versionField = new JTextField(12);
        classNameField = new JTextField(30);

        modeTabs.addTab("Keyword", createKeywordPanel());
        modeTabs.addTab("SHA-1", createSha1Panel());
        modeTabs.addTab("Coordinates", createGavPanel());
        modeTabs.addTab("Class", createClassPanel());

        search = NexusSearchFactory.create(api, this);
        if (!search.supportsClassSearch()) {
            modeTabs.setEnabledAt(3, false);
            modeTabs.setToolTipTextAt(3, "Class search is not supported by this backend");
        }

        JPanel north = new JPanel(new BorderLayout());
        north.add(modeTabs, BorderLayout.CENTER);
        north.add(createControlStrip(), BorderLayout.SOUTH);

        tableModel = new ResultTableModel();
        resultTable = new JXTable(tableModel);
        resultTable.setFillsViewportHeight(true);
        resultTable.setColumnControlVisible(true);
        if (api.isDarkMode()) {
            resultTable.setHighlighters(HighlighterFactory.createAlternateStriping(new Color(0x1E1E1E), new Color(0x2A2A2A)));
        } else {
            resultTable.setHighlighters(HighlighterFactory.createSimpleStriping());
        }
        TableRowFilterSupport.forTable(resultTable)
                .actions(true)
                .searchable(true)
                .checkListRenderer(new CheckListRenderer())
                .apply();

        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int viewRow = resultTable.getSelectedRow();
            if (viewRow >= 0) {
                int modelRow = resultTable.convertRowIndexToModel(viewRow);
                NexusArtifact artifact = tableModel.getArtifactAt(modelRow);
                updateSnippets(artifact);
            } else {
                updateSnippets(null);
            }
        });

        tablePopupMenu = new JPopupMenu();
        compareFilesItem = new JMenuItem("Compare Files", new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/copy.png")));
        compareFilesItem.addActionListener(e -> compareSelectedArtifacts());
        tablePopupMenu.add(compareFilesItem);

        resultTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isConsumed()) {
                    e.consume();
                    int viewRow = resultTable.rowAtPoint(e.getPoint());
                    if (viewRow < 0) {
                        return;
                    }
                    int modelRow = resultTable.convertRowIndexToModel(viewRow);
                    NexusArtifact artifact = tableModel.getArtifactAt(modelRow);
                    if (artifact == null) {
                        return;
                    }
                    String link = artifact.artifactLink();
                    if (link == null || link.isBlank()) {
                        JOptionPane.showMessageDialog(
                                NexusSearchPanel.this,
                                "No download link is available for this artifact.",
                                "No link",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        return;
                    }
                    try {
                        api.openURI(URI.create(link));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                                NexusSearchPanel.this,
                                "Cannot open link:\n" + ex.getMessage(),
                                "Open error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = resultTable.rowAtPoint(e.getPoint());
                if ((row >= 0) && !resultTable.getSelectionModel().isSelectedIndex(row)) {
                    resultTable.getSelectionModel().setSelectionInterval(row, row);
                }
                int[] selected = resultTable.getSelectedRows();
                if (selected.length == 2) {
                    tablePopupMenu.show(resultTable, e.getX(), e.getY());
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(resultTable);

        snippetTabs = new JTabbedPane();
        mavenArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_XML);
        gradleArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_GROOVY);
        ivyArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_XML);
        sbtArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_SCALA);
        leinArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_CLOJURE);
        grapeArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_GROOVY);
        buildrArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_RUBY);
        bldArea = createReadOnlyEditor(api, SyntaxConstants.SYNTAX_STYLE_JAVA);

        snippetTabs.addTab("Maven", new RTextScrollPane(mavenArea));
        snippetTabs.addTab("Gradle", new RTextScrollPane(gradleArea));
        snippetTabs.addTab("Ivy", new RTextScrollPane(ivyArea));
        snippetTabs.addTab("SBT", new RTextScrollPane(sbtArea));
        snippetTabs.addTab("Leiningen", new RTextScrollPane(leinArea));
        snippetTabs.addTab("Grape", new RTextScrollPane(grapeArea));
        snippetTabs.addTab("Buildr", new RTextScrollPane(buildrArea));
        snippetTabs.addTab("bld", new RTextScrollPane(bldArea));

        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT,
                tableScrollPane,
                snippetTabs
        );
        splitPane.setResizeWeight(0.7);

        add(north, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(progressBar, BorderLayout.SOUTH);
    }

    private RSyntaxTextArea createReadOnlyEditor(API api, String syntaxStyle) {
        RSyntaxTextArea area = new RSyntaxTextArea();
        area.setEditable(false);
        area.setCodeFoldingEnabled(false);
        ThemeUtil.applyTheme(api, area);
        area.setSyntaxEditingStyle(syntaxStyle);
        return area;
    }

    private JPanel createKeywordPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("Keyword:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(keywordField, gbc);

        return panel;
    }

    private JPanel createSha1Panel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;

        panel.add(new JLabel("SHA-1:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(sha1Field, gbc);

        return panel;
    }

    private JPanel createGavPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Group:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(groupField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Artifact:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(artifactField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Version:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(versionField, gbc);

        return panel;
    }

    private JPanel createClassPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Class name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(classNameField, gbc);

        return panel;
    }

    private JPanel createControlStrip() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        searchButton = new JButton("Search", ImageUtil.newImageIcon("/org/jd/gui/images/search_src.png"));
        cancelButton = new JButton("Cancel", ImageUtil.newImageIcon("/org/jd/gui/images/close_active.gif"));
        cancelButton.setEnabled(false);

        searchButton.addActionListener(e -> startSearch());
        cancelButton.addActionListener(e -> cancelSearch());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(searchButton, gbc);

        gbc.gridx = 1;
        panel.add(cancelButton, gbc);

        return panel;
    }

    private void startSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            return;
        }

        int mode = modeTabs.getSelectedIndex();

        String keyword = null;
        String sha1 = null;
        String groupId = null;
        String artifactId = null;
        String version = null;
        String className = null;

        switch (mode) {
            case 0 -> keyword = keywordField.getText().trim();
            case 1 -> sha1 = sha1Field.getText().trim();
            case 2 -> {
                groupId = groupField.getText().trim();
                artifactId = artifactField.getText().trim();
                version = versionField.getText().trim();
                if (version.isEmpty()) {
                    version = null;
                }
            }
            case 3 -> className = classNameField.getText().trim();
            default -> {
                return;
            }
        }

        SearchRequest request = new SearchRequest(mode, keyword, sha1, groupId, artifactId, version, className);

        tableModel.setArtifacts(List.of());
        updateSnippets(null);

        currentWorker = new SearchWorker(this, search, request);
        currentWorker.addPropertyChangeListener(currentWorker);
        currentWorker.execute();

        searchButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setString("Starting...");
    }

    private void cancelSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    private void onSearchCompleted(Throwable error) {
        searchButton.setEnabled(true);
        cancelButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("");

        if (error != null) {
            JOptionPane.showMessageDialog(this,
                    error.getClass().getSimpleName() + ": " + error.getMessage(),
                    "Search error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (tableModel.getRowCount() > 0 && resultTable.getSelectedRow() < 0) {
            resultTable.getSelectionModel().setSelectionInterval(0, 0);
            NexusArtifact first = tableModel.getArtifactAt(0);
            updateSnippets(first);
        }
    }

    private record SearchRequest(
            int mode,
            String keyword,
            String sha1,
            String groupId,
            String artifactId,
            String version,
            String className) {
    }

    private static final class SearchWorker extends SwingWorker<Void, List<NexusArtifact>> implements PropertyChangeListener {

        private final NexusSearchPanel panel;
        private final NexusSearch search;
        private final SearchRequest request;

        private volatile Throwable error;

        SearchWorker(NexusSearchPanel panel, NexusSearch search, SearchRequest request) {
            this.panel = panel;
            this.search = search;
            this.request = request;
        }

        @Override
        protected Void doInBackground() {
            try {
                setProgress(5);

                if (request.mode() == 1) {
                    if (isCancelled()) {
                        return null;
                    }
                    setProgress(25);
                    NexusSearchResult page = search.searchBySha1(request.sha1(), 0);
                    if (!isCancelled() && page != null && page.artifacts() != null && !page.artifacts().isEmpty()) {
                        publish(page.artifacts());
                    }
                    setProgress(100);
                    return null;
                }

                for (int page = 0; page < MAX_PAGES && !isCancelled(); page++) {
                    int baseProgress = 10;
                    int pageRange = 80;
                    int pageProgress = baseProgress + (pageRange * page) / MAX_PAGES;
                    setProgress(pageProgress);

                    if (isCancelled()) {
                        break;
                    }

                    NexusSearchResult pageResult;
                    switch (request.mode()) {
                        case 0 -> pageResult = search.searchByKeyword(request.keyword(), page);
                        case 2 -> pageResult = search.searchByGav(request.groupId(), request.artifactId(), request.version(), page);
                        case 3 -> {
                            boolean fullyQualified = request.className() != null && request.className().contains(".");
                            pageResult = search.searchByClassName(request.className(), fullyQualified, page);
                        }
                        default -> pageResult = null;
                    }

                    if (pageResult == null || pageResult.artifacts() == null) {
                        break;
                    }

                    List<NexusArtifact> pageArtifacts = pageResult.artifacts();
                    if (pageArtifacts.isEmpty()) {
                        break;
                    }

                    publish(pageArtifacts);
                }

                setProgress(100);
            } catch (Throwable t) {
                this.error = t;
            }
            return null;
        }

        @Override
        protected void process(List<List<NexusArtifact>> chunks) {
            for (List<NexusArtifact> chunk : chunks) {
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                boolean hadRowsBefore = panel.tableModel.getRowCount() > 0;
                panel.tableModel.addArtifacts(chunk);

                if (!hadRowsBefore && panel.tableModel.getRowCount() > 0) {
                    panel.resultTable.getSelectionModel().setSelectionInterval(0, 0);
                    NexusArtifact first = panel.tableModel.getArtifactAt(0);
                    panel.updateSnippets(first);
                }
            }
        }

        @Override
        protected void done() {
            panel.onSearchCompleted(error);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("progress".equals(evt.getPropertyName())) {
                int value = (Integer) evt.getNewValue();
                panel.progressBar.setValue(value);
                if (value > 0 && value < 100) {
                    panel.progressBar.setString(value + " %");
                } else {
                    panel.progressBar.setString("");
                }
            }
        }
    }

    private static final class ResultTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMN_NAMES = {
                "Group",
                "Artifact",
                "Version",
                "Date",
                "Classifier",
                "Extension",
                "Repository"
        };

        private final List<NexusArtifact> artifacts = new ArrayList<>();

        public void setArtifacts(List<NexusArtifact> newArtifacts) {
            artifacts.clear();
            if (newArtifacts != null) {
                artifacts.addAll(newArtifacts);
            }
            fireTableDataChanged();
        }

        public void addArtifacts(List<NexusArtifact> newArtifacts) {
            if (newArtifacts == null || newArtifacts.isEmpty()) {
                return;
            }
            int first = artifacts.size();
            artifacts.addAll(newArtifacts);
            fireTableRowsInserted(first, artifacts.size() - 1);
        }

        public NexusArtifact getArtifactAt(int rowIndex) {
            if (rowIndex < 0 || rowIndex >= artifacts.size()) {
                return null;
            }
            return artifacts.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return artifacts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            NexusArtifact a = artifacts.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> a.groupId();
                case 1 -> a.artifactId();
                case 2 -> a.version();
                case 3 -> {
                    LocalDate date = a.versionDate();
                    yield date != null ? date.toString() : "";
                }
                case 4 -> a.classifier();
                case 5 -> a.extension();
                case 6 -> a.repository();
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    private void updateSnippets(NexusArtifact artifact) {
        if (artifact == null) {
            mavenArea.setText("");
            gradleArea.setText("");
            ivyArea.setText("");
            sbtArea.setText("");
            leinArea.setText("");
            grapeArea.setText("");
            buildrArea.setText("");
            bldArea.setText("");
            return;
        }

        String g = artifact.groupId();
        String a = artifact.artifactId();
        String v = artifact.version();
        String c = artifact.classifier();
        String p = artifact.extension();

        mavenArea.setText(buildMavenSnippet(g, a, v, c, p));
        gradleArea.setText(buildGradleSnippet(g, a, v, c, p));
        ivyArea.setText(buildIvySnippet(g, a, v, c, p));
        sbtArea.setText(buildSbtSnippet(g, a, v, c, p));
        leinArea.setText(buildLeinSnippet(g, a, v, c, p));
        grapeArea.setText(buildGrapeSnippet(g, a, v, c, p));
        buildrArea.setText(buildBuildrSnippet(g, a, v, c, p));
        bldArea.setText(buildBldSnippet(g, a, v, c, p));
    }

    private void compareSelectedArtifacts() {
        int[] selected = resultTable.getSelectedRows();
        if (selected == null || selected.length != 2) {
            return;
        }
        int modelRow1 = resultTable.convertRowIndexToModel(selected[0]);
        int modelRow2 = resultTable.convertRowIndexToModel(selected[1]);

        NexusArtifact a1 = tableModel.getArtifactAt(modelRow1);
        NexusArtifact a2 = tableModel.getArtifactAt(modelRow2);
        if (a1 == null || a2 == null) {
            return;
        }

        String link1 = a1.artifactLink();
        String link2 = a2.artifactLink();
        if (link1 == null || link1.isBlank() || link2 == null || link2.isBlank()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Both selected artifacts must have a download link in order to compare files.",
                    "Cannot compare",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            api.compareFiles(URI.create(link1), URI.create(link2));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Cannot compare files:\n" + ex.getMessage(),
                    "Compare error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static String buildMavenSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency>\n");
        sb.append("  <groupId>").append(g).append("</groupId>\n");
        sb.append("  <artifactId>").append(a).append("</artifactId>\n");
        sb.append("  <version>").append(v).append("</version>\n");
        if (StringUtils.isNotBlank(c)) {
            sb.append("  <classifier>").append(c).append("</classifier>\n");
        }
        if (StringUtils.isNotBlank(p) && !"jar".equalsIgnoreCase(p)) {
            sb.append("  <type>").append(p).append("</type>\n");
        }
        sb.append("</dependency>\n");
        return sb.toString();
    }

    private static String buildGradleSnippet(String g, String a, String v, String c, String p) {
        String coords;
        if (StringUtils.isNotBlank(c)) {
            if (StringUtils.isNotBlank(p) && !"jar".equalsIgnoreCase(p)) {
                coords = g + ":" + a + ":" + v + ":" + c + "@" + p;
            } else {
                coords = g + ":" + a + ":" + v + ":" + c;
            }
        } else {
            coords = g + ":" + a + ":" + v;
        }
        return "dependencies {\n    implementation \"" + coords + "\"\n}\n";
    }

    private static String buildIvySnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency org=\"").append(g)
                .append("\" name=\"").append(a)
                .append("\" rev=\"").append(v).append("\"");
        if (StringUtils.isNotBlank(p)) {
            sb.append(" type=\"").append(p).append("\"");
        }
        if (StringUtils.isNotBlank(c)) {
            sb.append(" classifier=\"").append(c).append("\"");
        }
        sb.append(" />\n");
        return sb.toString();
    }

    private static String buildSbtSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("libraryDependencies += \"").append(g).append("\" % \"").append(a).append("\" % \"").append(v).append("\"");
        if (StringUtils.isNotBlank(c)) {
            sb.append(" classifier \"").append(c).append("\"");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String buildLeinSnippet(String g, String a, String v, String c, String p) {
        String ga = g + "/" + a;
        StringBuilder sb = new StringBuilder();
        sb.append("[\"").append(ga).append("\" \"").append(v).append("\"");
        if (StringUtils.isNotBlank(c)) {
            sb.append(" :classifier \"").append(c).append("\"");
        }
        if (StringUtils.isNotBlank(p) && !"jar".equalsIgnoreCase(p)) {
            sb.append(" :extension \"").append(p).append("\"");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String buildGrapeSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("@Grapes(\n");
        sb.append("    @Grab(group='").append(g).append("', module='").append(a).append("', version='").append(v).append("'");
        if (StringUtils.isNotBlank(c)) {
            sb.append(", classifier='").append(c).append("'");
        }
        if (StringUtils.isNotBlank(p) && !"jar".equalsIgnoreCase(p)) {
            sb.append(", type='").append(p).append("'");
        }
        sb.append(")\n");
        sb.append(")\n");
        return sb.toString();
    }

    private static String buildBuildrSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        String packaging = (p == null || p.isBlank()) ? "jar" : p;
        sb.append("compile '").append(g).append(":").append(a).append(":").append(packaging).append(":").append(v);
        if (StringUtils.isNotBlank(c)) {
            sb.append(":").append(c);
        }
        sb.append("'\n");
        return sb.toString();
    }

    private static String buildBldSnippet(String g, String a, String v, String c, String p) {
        StringBuilder sb = new StringBuilder();
        sb.append("dependency(\"").append(g).append("\", \"").append(a).append("\", \"").append(v).append("\"");
        if (StringUtils.isNotBlank(c)) {
            sb.append(", classifier=\"").append(c).append("\"");
        }
        if (StringUtils.isNotBlank(p) && !"jar".equalsIgnoreCase(p)) {
            sb.append(", type=\"").append(p).append("\"");
        }
        sb.append(");\n");
        return sb.toString();
    }
}