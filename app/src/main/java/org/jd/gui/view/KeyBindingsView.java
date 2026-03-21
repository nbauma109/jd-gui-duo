/*
 * Copyright (c) 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.service.preferencespanel.UIKeyBindingsPreferencesProvider;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.swing.SwingUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

public class KeyBindingsView implements org.jd.gui.spi.PreferencesPanel.PreferencesPanelChangeListener {
    private final Map<String, String> preferences;
    private final UIKeyBindingsPreferencesProvider keyBindingsPanel = new UIKeyBindingsPreferencesProvider();

    private JDialog dialog;
    private final JButton okButton = new JButton("   Ok   ");
    private Runnable okCallback;

    public KeyBindingsView(Configuration configuration, JFrame mainFrame) {
        this.preferences = configuration.getPreferences();

        SwingUtil.invokeLater(() -> {
            dialog = new JDialog(mainFrame, "Key Bindings", false);
            ImageUtil.addJDIconsToFrame(dialog);

            keyBindingsPanel.init(Color.decode(configuration.getPreferences().get(GuiPreferences.ERROR_BACKGROUND_COLOR)));
            keyBindingsPanel.addPreferencesChangeListener(this);

            JComponent content = (JComponent) dialog.getContentPane();
            content.setLayout(new BorderLayout());
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            keyBindingsPanel.setPreferredSize(new Dimension(720, 540));
            content.add(keyBindingsPanel, BorderLayout.CENTER);

            Box controls = Box.createVerticalBox();
            controls.add(Box.createVerticalStrut(15));

            Box buttons = Box.createHorizontalBox();
            buttons.add(Box.createHorizontalGlue());

            JButton defaultsButton = new JButton("Restore Defaults");
            defaultsButton.addActionListener(_ -> keyBindingsPanel.restoreDefaults());
            buttons.add(defaultsButton);
            buttons.add(Box.createHorizontalStrut(5));

            okButton.addActionListener(_ -> {
                keyBindingsPanel.savePreferences(preferences);
                dialog.setVisible(false);
                okCallback.run();
            });
            buttons.add(okButton);
            buttons.add(Box.createHorizontalStrut(5));

            JButton cancelButton = new JButton("Cancel");
            Action cancelAction = new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.setVisible(false);
                }
            };
            cancelButton.addActionListener(cancelAction);
            buttons.add(cancelButton);

            controls.add(buttons);
            content.add(controls, BorderLayout.SOUTH);

            JRootPane rootPane = dialog.getRootPane();
            rootPane.setDefaultButton(okButton);
            rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "KeyBindings.cancel");
            rootPane.getActionMap().put("KeyBindings.cancel", cancelAction);

            dialog.pack();
            dialog.setLocationRelativeTo(mainFrame);
        });
    }

    public void show(Runnable okCallback) {
        this.okCallback = okCallback;

        SwingUtil.invokeLater(() -> {
            keyBindingsPanel.loadPreferences(preferences);
            preferencesPanelChanged(keyBindingsPanel);
            dialog.setLocationRelativeTo(dialog.getParent());
            dialog.setVisible(true);
        });
    }

    @Override
    public void preferencesPanelChanged(org.jd.gui.spi.PreferencesPanel source) {
        SwingUtil.invokeLater(() -> okButton.setEnabled(source.arePreferencesValid()));
    }
}
