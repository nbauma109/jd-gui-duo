/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.swing.SwingUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import de.cismet.custom.visualdiff.PlatformService;

public class PreferencesView implements PreferencesPanel.PreferencesPanelChangeListener {
    private final Map<String, String> preferences;
    private final Collection<PreferencesPanel> panels;
    private final Map<PreferencesPanel, Boolean> valids = new HashMap<>();

    private JDialog preferencesDialog;
    private final JButton preferencesOkButton = new JButton();

    private Runnable okCallback;

    public PreferencesView(Configuration configuration, JFrame mainFrame, Collection<PreferencesPanel> panels) {
        this.preferences = configuration.getPreferences();
        this.panels = panels;
        // Build GUI
        SwingUtil.invokeLater(() -> {
            preferencesDialog = new JDialog(mainFrame, "Preferences", false);

            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.setLayout(new BorderLayout());
            preferencesDialog.add(panel);

            // Box for preferences panels
            Box preferencesPanels = Box.createVerticalBox();
            preferencesPanels.setBackground(panel.getBackground());
            preferencesPanels.setOpaque(true);
            Color errorBackgroundColor = Color.decode(configuration.getPreferences().get(GuiPreferences.ERROR_BACKGROUND_COLOR));

            // Group "PreferencesPanel" by group name
            Map<String, List<PreferencesPanel>> groups = new HashMap<>();
            List<String> sortedGroupNames = new ArrayList<>();

            for (PreferencesPanel pp : panels) {
                List<PreferencesPanel> pps = groups.get(pp.getPreferencesGroupTitle());

                pp.init(errorBackgroundColor);
                pp.addPreferencesChangeListener(this);

                if (pps == null) {
                    String groupNames = pp.getPreferencesGroupTitle();
                    pps=new ArrayList<>();
                    groups.put(groupNames, pps);
                    sortedGroupNames.add(groupNames);
                }

                pps.add(pp);
            }

            Collections.sort(sortedGroupNames);

            // Add preferences panels
            for (String groupName : sortedGroupNames) {
                Box vbox = Box.createVerticalBox();
                TitledBorder titledBorder = BorderFactory.createTitledBorder(groupName);
                titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD));
                vbox.setBorder(titledBorder);

                List<PreferencesPanel> sortedPreferencesPanels = groups.get(groupName);
                Collections.sort(sortedPreferencesPanels, Comparator.comparing(PreferencesPanel::getPreferencesPanelTitle));

                for (PreferencesPanel pp : sortedPreferencesPanels) {
                    if (!pp.useCompactDisplay()) {
                        // Add title
                        Box hbox = Box.createHorizontalBox();
                        JLabel title = new JLabel(pp.getPreferencesPanelTitle());
                        title.setFont(title.getFont().deriveFont(Font.PLAIN));
                        hbox.add(title);
                        hbox.add(Box.createHorizontalGlue());
                        hbox.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
                        vbox.add(hbox);
                    }
                    // Add panel
                    JComponent component = pp.getPanel();
                    component.setMaximumSize(new Dimension(component.getMaximumSize().width, component.getPreferredSize().height));
                    vbox.add(component);
                }

                preferencesPanels.add(vbox);
            }

            JScrollPane preferencesScrollPane = new JScrollPane(preferencesPanels);
            preferencesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            preferencesScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            panel.add(preferencesScrollPane, BorderLayout.CENTER);

            Box vbox = Box.createVerticalBox();
            panel.add(vbox, BorderLayout.SOUTH);

            vbox.add(Box.createVerticalStrut(15));

            // Buttons "Ok" and "Cancel"
            Box hbox = Box.createHorizontalBox();
            hbox.add(Box.createHorizontalGlue());
            preferencesOkButton.setText("   Ok   ");
            preferencesOkButton.addActionListener(e -> {
                for (PreferencesPanel pp : panels) {
                    pp.savePreferences(preferences);
                }
                preferencesDialog.setVisible(false);
                okCallback.run();
            });
            hbox.add(preferencesOkButton);
            hbox.add(Box.createHorizontalStrut(5));
            JButton preferencesCancelButton = new JButton("Cancel", ImageUtil.newImageIcon("/org/jd/gui/images/close_active.gif"));
            Action preferencesCancelActionListener = new AbstractAction() {

                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent actionEvent) { preferencesDialog.setVisible(false); }
            };
            preferencesCancelButton.addActionListener(preferencesCancelActionListener);
            hbox.add(preferencesCancelButton);
            vbox.add(hbox);

            // Last setup
            JRootPane rootPane = preferencesDialog.getRootPane();
            rootPane.setDefaultButton(preferencesOkButton);
            rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "PreferencesDescription.cancel");
            rootPane.getActionMap().put("PreferencesDescription.cancel", preferencesCancelActionListener);

            // Size of the screen
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            // Height of the task bar
            Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(preferencesDialog.getGraphicsConfiguration());
            // screen height in pixels without taskbar
            int taskBarHeight = scnMax.bottom + scnMax.top;
            int maxHeight = screenSize.height - taskBarHeight;
            int preferredHeight = preferencesPanels.getPreferredSize().height + 2;

            if (preferredHeight > maxHeight) {
                preferredHeight = maxHeight;
            }

            preferencesScrollPane.setPreferredSize(new Dimension(400, preferredHeight));
            if (PlatformService.getInstance().isMac()) {
                preferencesDialog.setMinimumSize(new Dimension(540, 200));
            } else {
                preferencesDialog.setMinimumSize(new Dimension(300, 200));
            }

            // Prepare to display
            preferencesDialog.pack();
            preferencesDialog.setLocationRelativeTo(mainFrame);
        });
    }

    public void show(Runnable okCallback) {
        this.okCallback = okCallback;

        SwingUtilities.invokeLater(() -> {
            // Init
            for (PreferencesPanel pp : panels) {
                pp.loadPreferences(preferences);
            }
            // Show
            preferencesDialog.setVisible(true);
        });
    }

    // --- PreferencesPanel.PreferencesChangeListener --- //
    @Override
    public void preferencesPanelChanged(PreferencesPanel source) {
        SwingUtil.invokeLater(() -> {
            boolean valid = source.arePreferencesValid();

            valids.put(source, Boolean.valueOf(valid));

            if (valid) {
                for (PreferencesPanel pp : panels) {
                    if (valids.get(pp) == Boolean.FALSE) {
                        preferencesOkButton.setEnabled(false);
                        return;
                    }
                }
                preferencesOkButton.setEnabled(true);
            } else {
                preferencesOkButton.setEnabled(false);
            }
        });
    }

}
