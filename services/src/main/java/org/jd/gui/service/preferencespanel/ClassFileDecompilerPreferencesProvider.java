/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.ImageUtil;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_CFR;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_FERNFLOWER;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_VINEFLOWER;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JADX;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V0;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_PROCYON;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

public class ClassFileDecompilerPreferencesProvider extends JPanel implements PreferencesPanel {

    private static final long serialVersionUID = 1L;

    protected JComboBox<String> decompileEngine;
    protected JButton configureDecompiler;

    private static final String[] DECOMPILERS = { ENGINE_JD_CORE_V1, ENGINE_JD_CORE_V0, ENGINE_CFR, ENGINE_PROCYON, ENGINE_FERNFLOWER, ENGINE_VINEFLOWER, ENGINE_JADX };

    private transient Map<String, PreferencesPanel> decompilerPreferencesProviders = new HashMap<>();

    public ClassFileDecompilerPreferencesProvider() {
        super(new GridLayout(0, 2));

        decompileEngine = new JComboBox<>(DECOMPILERS);
        configureDecompiler = new JButton("Configure", new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/preferences.png")));
        configureDecompiler.addActionListener(e -> configureDecompiler());

        add(decompileEngine);
        add(configureDecompiler);

        JDCoreDecompilerPreferencesProvider jdCoreDecompilerPreferencesProvider = new JDCoreDecompilerPreferencesProvider(decompileEngine);
        decompilerPreferencesProviders.put(ENGINE_JD_CORE_V0, jdCoreDecompilerPreferencesProvider);
        decompilerPreferencesProviders.put(ENGINE_JD_CORE_V1, jdCoreDecompilerPreferencesProvider);
        decompilerPreferencesProviders.put(ENGINE_CFR, new CFRDecompilerPreferencesProvider());
        decompilerPreferencesProviders.put(ENGINE_PROCYON, new ProcyonDecompilerPreferencesProvider());
        decompilerPreferencesProviders.put(ENGINE_FERNFLOWER, new FernflowerDecompilerPreferencesProvider());
        decompilerPreferencesProviders.put(ENGINE_VINEFLOWER, new VineflowerDecompilerPreferencesProvider());
        decompilerPreferencesProviders.put(ENGINE_JADX, new JadxDecompilerPreferencesProvider());

        decompileEngine.addActionListener(e -> jdCoreDecompilerPreferencesProvider.toggleOldOptions());
   }

    public void configureDecompiler() {
        String selectedDecompiler = decompileEngine.getSelectedItem().toString();
        JDialog optionDialog = new JDialog((JDialog) SwingUtilities.getWindowAncestor(this), selectedDecompiler + " Settings", true);
        optionDialog.setLayout(new BorderLayout());
        JPanel dialogPanel = (JPanel) optionDialog.getContentPane();
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel preferencesPanels = (JPanel) decompilerPreferencesProviders.get(selectedDecompiler);
        JScrollPane preferencesScrollPane = new JScrollPane(preferencesPanels);
        preferencesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        preferencesScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        optionDialog.getContentPane().add(preferencesScrollPane, BorderLayout.CENTER);
        Box vbox = Box.createVerticalBox();
        optionDialog.getContentPane().add(vbox, BorderLayout.SOUTH);
        vbox.add(Box.createVerticalStrut(15));
        Box hbox = Box.createHorizontalBox();
        hbox.add(Box.createHorizontalGlue());
        JButton defaultsButton = new JButton("Restore Defaults");
        defaultsButton.addActionListener(e -> ((PreferencesPanel) preferencesPanels).restoreDefaults());
        hbox.add(defaultsButton);
        hbox.add(Box.createHorizontalStrut(5));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> optionDialog.dispose());
        hbox.add(okButton);
        hbox.add(Box.createHorizontalGlue());
        vbox.add(hbox);
        optionDialog.setLocationRelativeTo(this);
        optionDialog.pack();
        optionDialog.setVisible(true);
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() {
        return "Decompiler";
    }

    @Override
    public String getPreferencesPanelTitle() {
        return "Class file";
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        decompileEngine.setSelectedItem(preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1));
        for (PreferencesPanel preferencesPanel : decompilerPreferencesProviders.values()) {
            preferencesPanel.loadPreferences(preferences);
        }
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(DECOMPILE_ENGINE, decompileEngine.getSelectedItem().toString());
        for (PreferencesPanel preferencesPanel : decompilerPreferencesProviders.values()) {
            preferencesPanel.savePreferences(preferences);
        }
    }

    @Override
    public boolean arePreferencesValid() {
        return true;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        // nothing to do
    }

    @Override
    public void restoreDefaults() {
        decompileEngine.setSelectedItem(ENGINE_JD_CORE_V1);
    }

    @Override
    public boolean useCompactDisplay() {
        return true;
    }
}
