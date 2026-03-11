/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import static org.jd.gui.util.decompiler.GuiPreferences.ADVANCED_CLASS_LOOKUP;
import static org.jd.gui.util.decompiler.GuiPreferences.INCLUDE_RUNNING_VM_BOOT_CLASSPATH;
import static org.jd.gui.util.decompiler.GuiPreferences.JRE_SYSTEM_LIBRARY_PATH;
import static org.jd.gui.util.decompiler.GuiPreferences.REMOVE_UNNECESSARY_CASTS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_ERRORS;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_INFO;
import static org.jd.gui.util.decompiler.GuiPreferences.SHOW_COMPILER_WARNINGS;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.jd.gui.spi.EclipsePreferencesPanel;
import org.jd.gui.spi.PreferencesPanel;

public class EclipsePreferencesProvider extends JPanel implements EclipsePreferencesPanel {

    private static final long serialVersionUID = 1L;

    private static final String UNKNOWN_VERSION = "";
    private static final String DEFAULT_JAVA_VERSION = JavaCore.latestSupportedJavaVersion();
    private static final String MINIMUM_JAVA_VERSION = JavaCore.VERSION_1_8;
    private static final List<String> JAVA_VERSIONS = createJavaVersions();

    protected JCheckBox showCompilerErrorsCheckBox;
    protected JCheckBox showCompilerWarningsCheckBox;
    protected JCheckBox showCompilerInfoCheckBox;
    protected JCheckBox advancedClassLookupCheckBox;
    protected JCheckBox removeUnnecessaryCastsCheckBox;
    protected JCheckBox includeRunningVMBootClasspathCheckBox;
    protected JLabel jreSystemLibraryPathLabel;
    protected JTextField jreSystemLibraryPathTextField;
    protected JButton jreSystemLibraryPathButton;
    protected JLabel sourceLabel;
    protected JComboBox<String> sourceComboBox;
    protected JLabel complianceLabel;
    protected JComboBox<String> complianceComboBox;

    public EclipsePreferencesProvider() {
        super(new GridBagLayout());

        showCompilerErrorsCheckBox = new JCheckBox("Show compiler errors");
        showCompilerWarningsCheckBox = new JCheckBox("Show compiler warnings");
        showCompilerInfoCheckBox = new JCheckBox("Show compiler info");
        advancedClassLookupCheckBox = new JCheckBox("Advanced class lookup");
        removeUnnecessaryCastsCheckBox = new JCheckBox("Remove unnecessary casts");
        includeRunningVMBootClasspathCheckBox = new JCheckBox("Include running VM boot classpath");
        jreSystemLibraryPathLabel = new JLabel("JRE System Library");
        jreSystemLibraryPathTextField = new JTextField();
        jreSystemLibraryPathButton = createFolderButton();
        sourceLabel = new JLabel("Source");
        sourceComboBox = new JComboBox<String>(JAVA_VERSIONS.toArray(new String[JAVA_VERSIONS.size()]));
        complianceLabel = new JLabel("Compliance");
        complianceComboBox = new JComboBox<String>(JAVA_VERSIONS.toArray(new String[JAVA_VERSIONS.size()]));

        jreSystemLibraryPathTextField.setColumns(40);
        sourceComboBox.setPrototypeDisplayValue(DEFAULT_JAVA_VERSION);
        complianceComboBox.setPrototypeDisplayValue(DEFAULT_JAVA_VERSION);

        addCheckBoxColumns(0);
        addJreSystemLibraryRow(1);
        addSingleVersionRow(sourceLabel, sourceComboBox, 2);
        addSingleVersionRow(complianceLabel, complianceComboBox, 3);
        addVerticalGlue(4);

        includeRunningVMBootClasspathCheckBox.addActionListener(e -> updateJreSystemLibraryState());
        sourceComboBox.addActionListener(e -> limitSelectionToCurrentJre(sourceComboBox));
        complianceComboBox.addActionListener(e -> limitSelectionToCurrentJre(complianceComboBox));
        jreSystemLibraryPathButton.addActionListener(e -> chooseJreSystemLibraryPath());
        jreSystemLibraryPathTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateVersionSelections();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateVersionSelections();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateVersionSelections();
            }
        });

        includeRunningVMBootClasspathCheckBox.setSelected(true);
        sourceComboBox.setSelectedItem(DEFAULT_JAVA_VERSION);
        complianceComboBox.setSelectedItem(DEFAULT_JAVA_VERSION);

        updateJreSystemLibraryState();
        updateVersionSelections();
    }

    private static List<String> createJavaVersions() {
        List<String> supportedVersions = new ArrayList<String>();
        List<String> allVersions = JavaCore.getAllVersions();

        for (String version : allVersions) {
            if (JavaCore.compareJavaVersions(version, MINIMUM_JAVA_VERSION) >= 0) {
                supportedVersions.add(version);
            }
        }

        return supportedVersions;
    }

    private JButton createFolderButton() {
        JButton button = new JButton();
        Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");

        if (folderIcon != null) {
            button.setIcon(folderIcon);
        } else {
            button.setText("...");
        }

        return button;
    }

    private void addCheckBoxColumns(int gridy) {
        JPanel leftPanel = new JPanel(new GridBagLayout());
        JPanel rightPanel = new JPanel(new GridBagLayout());

        addCheckBoxToColumn(leftPanel, showCompilerErrorsCheckBox, 0);
        addCheckBoxToColumn(leftPanel, showCompilerWarningsCheckBox, 1);
        addCheckBoxToColumn(leftPanel, showCompilerInfoCheckBox, 2);

        addCheckBoxToColumn(rightPanel, advancedClassLookupCheckBox, 0);
        addCheckBoxToColumn(rightPanel, removeUnnecessaryCastsCheckBox, 1);
        addCheckBoxToColumn(rightPanel, includeRunningVMBootClasspathCheckBox, 2);

        GridBagConstraints leftConstraints = new GridBagConstraints();
        leftConstraints.gridx = 0;
        leftConstraints.gridy = gridy;
        leftConstraints.weightx = 0.5d;
        leftConstraints.fill = GridBagConstraints.HORIZONTAL;
        leftConstraints.anchor = GridBagConstraints.NORTHWEST;
        leftConstraints.insets = new Insets(2, 2, 2, 12);
        add(leftPanel, leftConstraints);

        GridBagConstraints rightConstraints = new GridBagConstraints();
        rightConstraints.gridx = 1;
        rightConstraints.gridy = gridy;
        rightConstraints.gridwidth = 2;
        rightConstraints.weightx = 0.5d;
        rightConstraints.fill = GridBagConstraints.HORIZONTAL;
        rightConstraints.anchor = GridBagConstraints.NORTHWEST;
        rightConstraints.insets = new Insets(2, 2, 2, 2);
        add(rightPanel, rightConstraints);
    }

    private void addCheckBoxToColumn(JPanel panel, JComponent component, int gridy) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = gridy;
        constraints.weightx = 1.0d;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(2, 2, 2, 2);
        panel.add(component, constraints);
    }

    private void addJreSystemLibraryRow(int gridy) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = gridy;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(8, 2, 2, 6);
        add(jreSystemLibraryPathLabel, labelConstraints);

        GridBagConstraints textFieldConstraints = new GridBagConstraints();
        textFieldConstraints.gridx = 1;
        textFieldConstraints.gridy = gridy;
        textFieldConstraints.weightx = 1.0d;
        textFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        textFieldConstraints.insets = new Insets(8, 2, 2, 6);
        add(jreSystemLibraryPathTextField, textFieldConstraints);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 2;
        buttonConstraints.gridy = gridy;
        buttonConstraints.anchor = GridBagConstraints.WEST;
        buttonConstraints.insets = new Insets(8, 0, 2, 2);
        add(jreSystemLibraryPathButton, buttonConstraints);
    }

    private void addSingleVersionRow(JLabel label, JComboBox<String> comboBox, int gridy) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = gridy;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(2, 2, 2, 6);
        add(label, labelConstraints);

        GridBagConstraints comboConstraints = new GridBagConstraints();
        comboConstraints.gridx = 1;
        comboConstraints.gridy = gridy;
        comboConstraints.anchor = GridBagConstraints.WEST;
        comboConstraints.fill = GridBagConstraints.NONE;
        comboConstraints.weightx = 0.0d;
        comboConstraints.insets = new Insets(2, 2, 2, 2);
        add(comboBox, comboConstraints);
    }

    private void addVerticalGlue(int gridy) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = gridy;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0d;
        constraints.weighty = 1.0d;
        constraints.fill = GridBagConstraints.BOTH;
        add(new JPanel(), constraints);
    }

    private void chooseJreSystemLibraryPath() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileHidingEnabled(false);

        String currentPath = jreSystemLibraryPathTextField.getText();
        if (StringUtils.isNotBlank(currentPath)) {
            fileChooser.setCurrentDirectory(new File(currentPath));
        }

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) {
            return;
        }

        String selectedPath = selectedFile.getAbsolutePath();
        if (!isValidJrePath(selectedPath)) {
            JOptionPane.showMessageDialog(
                this,
                "The selected directory is not a valid JRE or JDK home.",
                "Invalid JRE System Library",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        jreSystemLibraryPathTextField.setText(selectedPath);
        updateVersionSelections();
    }

    private void updateJreSystemLibraryState() {
        boolean enabled = !includeRunningVMBootClasspathCheckBox.isSelected();
        jreSystemLibraryPathLabel.setEnabled(enabled);
        jreSystemLibraryPathTextField.setEnabled(enabled);
        jreSystemLibraryPathButton.setEnabled(enabled);
        updateVersionSelections();
    }

    private void updateVersionSelections() {
        String maximumVersion = determineSelectedJreVersion();
        limitSelectionToMaximum(sourceComboBox, maximumVersion);
        limitSelectionToMaximum(complianceComboBox, maximumVersion);
    }

    private void limitSelectionToCurrentJre(JComboBox<String> comboBox) {
        limitSelectionToMaximum(comboBox, determineSelectedJreVersion());
    }

    private void limitSelectionToMaximum(JComboBox<String> comboBox, String maximumVersion) {
        if (UNKNOWN_VERSION.equals(maximumVersion)) {
            return;
        }

        String selectedVersion = (String) comboBox.getSelectedItem();
        if ((selectedVersion != null) && (JavaCore.compareJavaVersions(selectedVersion, maximumVersion) > 0)) {
            comboBox.setSelectedItem(maximumVersion);
        }
    }

    private String determineSelectedJreVersion() {
        String version;

        if (includeRunningVMBootClasspathCheckBox.isSelected()) {
            version = normalizeJavaVersion(System.getProperty("java.specification.version"));
        } else {
            version = detectJavaVersionFromPath(jreSystemLibraryPathTextField.getText());
        }

        return toSupportedJavaVersion(version);
    }

    private String detectJavaVersionFromPath(String path) {
        if (StringUtils.isBlank(path)) {
            return UNKNOWN_VERSION;
        }

        File selectedDirectory = new File(path.trim());
        File releaseFile = findReleaseFile(selectedDirectory);
        if (releaseFile == null) {
            return UNKNOWN_VERSION;
        }

        Properties properties = new Properties();
        FileInputStream inputStream = null;

        try {
            inputStream = new FileInputStream(releaseFile);
            properties.load(inputStream);
            return normalizeJavaVersion(properties.getProperty("JAVA_VERSION"));
        } catch (IOException ignored) {
            return UNKNOWN_VERSION;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
    }

    private File findReleaseFile(File selectedDirectory) {
        if (selectedDirectory == null || !selectedDirectory.isDirectory()) {
            return null;
        }

        File directReleaseFile = new File(selectedDirectory, "release");
        if (directReleaseFile.isFile()) {
            return directReleaseFile;
        }

        File parentDirectory = selectedDirectory.getParentFile();
        if (parentDirectory == null) {
            return null;
        }

        File parentReleaseFile = new File(parentDirectory, "release");
        if (parentReleaseFile.isFile()) {
            return parentReleaseFile;
        }

        return null;
    }

    private String normalizeJavaVersion(String version) {
        if (version == null) {
            return UNKNOWN_VERSION;
        }

        String sanitizedVersion = version.trim().replace("\"", "");
        if (sanitizedVersion.isEmpty()) {
            return UNKNOWN_VERSION;
        }

        if (sanitizedVersion.startsWith("1.")) {
            return JavaCore.VERSION_1_8;
        }

        int separatorIndex = sanitizedVersion.indexOf('.');
        if (separatorIndex > 0) {
            return sanitizedVersion.substring(0, separatorIndex);
        }

        int dashIndex = sanitizedVersion.indexOf('-');
        if (dashIndex > 0) {
            return sanitizedVersion.substring(0, dashIndex);
        }

        return sanitizedVersion;
    }

    private String toSupportedJavaVersion(String version) {
        if (UNKNOWN_VERSION.equals(version)) {
            return UNKNOWN_VERSION;
        }

        if (JavaCore.isSupportedJavaVersion(version) && JavaCore.compareJavaVersions(version, MINIMUM_JAVA_VERSION) >= 0) {
            return version;
        }

        return UNKNOWN_VERSION;
    }

    private boolean isValidJrePath(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }

        File selectedDirectory = new File(path.trim());
        return findReleaseFile(selectedDirectory) != null;
    }

    @Override
    public String getPreferencesGroupTitle() {
        return "Eclipse";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Compiler";
    }

    @Override
    public JComponent getPanel() {
        return this;
    }

    @Override
    public void init(Color errorBackgroundColor) {
        // nothing to do
    }

    @Override
    public boolean isActivated() {
        return true;
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        showCompilerErrorsCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_ERRORS)));
        showCompilerWarningsCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_WARNINGS)));
        showCompilerInfoCheckBox.setSelected("true".equals(preferences.get(SHOW_COMPILER_INFO)));
        advancedClassLookupCheckBox.setSelected("true".equals(preferences.get(ADVANCED_CLASS_LOOKUP)));
        removeUnnecessaryCastsCheckBox.setSelected("true".equals(preferences.get(REMOVE_UNNECESSARY_CASTS)));
        includeRunningVMBootClasspathCheckBox.setSelected(!"false".equals(preferences.get(INCLUDE_RUNNING_VM_BOOT_CLASSPATH)));
        jreSystemLibraryPathTextField.setText(preferences.get(JRE_SYSTEM_LIBRARY_PATH));
        setSelectedVersion(sourceComboBox, preferences.getOrDefault(JavaCore.COMPILER_SOURCE, JavaCore.latestSupportedJavaVersion()));
        setSelectedVersion(complianceComboBox, preferences.getOrDefault(JavaCore.COMPILER_COMPLIANCE, JavaCore.latestSupportedJavaVersion()));
        updateJreSystemLibraryState();
    }

    private void setSelectedVersion(JComboBox<String> comboBox, String value) {
        String version = toSupportedJavaVersion(normalizeJavaVersion(value));
        comboBox.setSelectedItem(UNKNOWN_VERSION.equals(version) ? DEFAULT_JAVA_VERSION : version);
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(SHOW_COMPILER_ERRORS, Boolean.toString(showCompilerErrorsCheckBox.isSelected()));
        preferences.put(SHOW_COMPILER_WARNINGS, Boolean.toString(showCompilerWarningsCheckBox.isSelected()));
        preferences.put(SHOW_COMPILER_INFO, Boolean.toString(showCompilerInfoCheckBox.isSelected()));
        preferences.put(ADVANCED_CLASS_LOOKUP, Boolean.toString(advancedClassLookupCheckBox.isSelected()));
        preferences.put(REMOVE_UNNECESSARY_CASTS, Boolean.toString(removeUnnecessaryCastsCheckBox.isSelected()));
        preferences.put(INCLUDE_RUNNING_VM_BOOT_CLASSPATH, Boolean.toString(includeRunningVMBootClasspathCheckBox.isSelected()));
        preferences.put(JRE_SYSTEM_LIBRARY_PATH, jreSystemLibraryPathTextField.getText());
        preferences.put(JavaCore.COMPILER_SOURCE, (String) sourceComboBox.getSelectedItem());
        preferences.put(JavaCore.COMPILER_COMPLIANCE, (String) complianceComboBox.getSelectedItem());
    }

    @Override
    public boolean arePreferencesValid() {
        if (!includeRunningVMBootClasspathCheckBox.isSelected()) {
            return isValidJrePath(jreSystemLibraryPathTextField.getText());
        }

        return true;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        // nothing to do
    }

    @Override
    public void restoreDefaults() {
        showCompilerErrorsCheckBox.setSelected(false);
        showCompilerWarningsCheckBox.setSelected(false);
        showCompilerInfoCheckBox.setSelected(false);
        advancedClassLookupCheckBox.setSelected(false);
        removeUnnecessaryCastsCheckBox.setSelected(false);
        includeRunningVMBootClasspathCheckBox.setSelected(true);
        jreSystemLibraryPathTextField.setText("");
        sourceComboBox.setSelectedItem(DEFAULT_JAVA_VERSION);
        complianceComboBox.setSelectedItem(DEFAULT_JAVA_VERSION);
        updateJreSystemLibraryState();
    }

    @Override
    public boolean useCompactDisplay() {
        return true;
    }
}