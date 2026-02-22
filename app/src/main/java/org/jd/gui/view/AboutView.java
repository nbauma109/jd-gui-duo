/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.benf.cfr.reader.util.CfrVersionInfo;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.swing.SwingUtil;

import com.strobel.Procyon;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import jadx.core.Jadx;

public class AboutView {

    private static final String VERSION = "version ";

    private JDialog aboutDialog;
    private JButton aboutOkButton;

    public AboutView(JFrame mainFrame, boolean darkMode) {
        // Build GUI
        SwingUtil.invokeLater(() -> {
            aboutDialog = new JDialog(mainFrame, "About Java Decompiler", false);
            aboutDialog.setResizable(false);
            ImageUtil.addJDIconsToFrame(aboutDialog);

            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.setLayout(new BorderLayout());
            aboutDialog.add(panel);

            Box vbox = Box.createVerticalBox();
            panel.add(vbox, BorderLayout.NORTH);
            JPanel subpanel = new JPanel();
            vbox.add(subpanel);
            if (darkMode) {
                subpanel.setBorder(BorderFactory.createLineBorder(Color.WHITE));
                subpanel.setBackground(Color.BLACK);
            } else {
                subpanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
                subpanel.setBackground(Color.WHITE);
            }
            subpanel.setLayout(new BorderLayout());
            JLabel logo = new JLabel(new ImageIcon(ImageUtil.getImage("/org/jd/gui/images/jd_icon_64.png")));
            logo.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            subpanel.add(logo, BorderLayout.WEST);
            Box subvbox = Box.createVerticalBox();
            subvbox.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 15));
            subpanel.add(subvbox, BorderLayout.EAST);
            Box hbox = Box.createHorizontalBox();
            subvbox.add(hbox);
            JLabel mainLabel = new JLabel("Java Decompiler");
            mainLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 14));
            hbox.add(mainLabel);
            hbox.add(Box.createHorizontalGlue());
            hbox = Box.createHorizontalBox();
            subvbox.add(hbox);
            JPanel subsubpanel = new JPanel();
            hbox.add(subsubpanel);
            subsubpanel.setLayout(new GridLayout(9, 2));
            subsubpanel.setOpaque(false);
            subsubpanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));

            Attributes allAttributes = new Attributes();
            try {
                Enumeration<URL> enumeration = AboutView.class.getClassLoader().getResources("META-INF/MANIFEST.MF");

                while (enumeration.hasMoreElements()) {
                    try (InputStream is = enumeration.nextElement().openStream()) {
                        Attributes attributes = new Manifest(is).getMainAttributes();
                        if (attributes != null) {
                            allAttributes.putAll(attributes);
                        }
                    }
                }
            } catch (IOException e) {
                assert ExceptionUtil.printStackTrace(e);
            }

            subsubpanel.add(new JLabel("JD-GUI-DUO"));
            subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("JD-GUI-Version")));
            if (allAttributes != null) {
                subsubpanel.add(new JLabel("Transformer API"));
                subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("transformer-api-version")));
                subsubpanel.add(new JLabel("JD-Core v0"));
                subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("jd-core-v0-version")));
                subsubpanel.add(new JLabel("JD-Core v1"));
                subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("jd-core-v1-version")));
                subsubpanel.add(new JLabel("FernFlower"));
                subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("fernflower-version")));
                subsubpanel.add(new JLabel("VineFlower"));
                subsubpanel.add(new JLabel(VERSION + allAttributes.getValue("vineflower-version")));
            }
            subsubpanel.add(new JLabel("Procyon"));
            subsubpanel.add(new JLabel(VERSION + Procyon.version()));
            subsubpanel.add(new JLabel("CFR"));
            subsubpanel.add(new JLabel(VERSION + CfrVersionInfo.VERSION_INFO));
            subsubpanel.add(new JLabel("JADX"));
            subsubpanel.add(new JLabel(VERSION + Jadx.getVersion()));
            hbox.add(Box.createHorizontalGlue());

            hbox = Box.createHorizontalBox();
            hbox.add(new JLabel("Copyright © 2008-2025 Emmanuel Dupuy and other contributors"));
            hbox.add(Box.createHorizontalGlue());
            subvbox.add(hbox);

            vbox.add(Box.createVerticalStrut(10));

            hbox = Box.createHorizontalBox();
            panel.add(hbox, BorderLayout.SOUTH);
            hbox.add(Box.createHorizontalGlue());
            aboutOkButton = new JButton("    Ok    ");
            Action aboutOkActionListener = new AbstractAction() {

                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    aboutDialog.setVisible(false);
                }
            };
            aboutOkButton.addActionListener(aboutOkActionListener);
            hbox.add(aboutOkButton);
            hbox.add(Box.createHorizontalGlue());

            // Last setup
            JRootPane rootPane = aboutDialog.getRootPane();
            rootPane.setDefaultButton(aboutOkButton);
            rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "AboutView.ok");
            rootPane.getActionMap().put("AboutView.ok", aboutOkActionListener);

            // Prepare to display
            aboutDialog.pack();
        });
    }

    public void show() {
        SwingUtil.invokeLater(() -> {
            // Show
            aboutDialog.setLocationRelativeTo(aboutDialog.getParent());
            aboutDialog.setVisible(true);
            aboutOkButton.requestFocus();
        });
    }
}
