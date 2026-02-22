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
package org.jd.gui.service.preferencespanel.secure;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

/**
 * MasterPasswordDialog
 *
 * This dialog handles three scenarios:
 * 1) Initial setup: user defines a new master password and confirms it.
 * 2) Unlock: user enters the existing master password to decrypt stored secrets.
 * 3) Change: user enters current master password, then a new one and confirmation.
 *
 * We do not persist the master password itself; we only hold the character array in memory for the caller.
 * The caller is responsible for zeroing the returned character arrays after use.
 */
public final class MasterPasswordDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	public enum Mode { SETUP, UNLOCK, CHANGE }

    private final Mode mode;

    private JPasswordField currentField;
    private JPasswordField passField;
    private JPasswordField confirmField;

    // We keep results in private fields with explicit zeroing on close.
    private char[] resultPassword;
    private char[] resultCurrentPassword;

    public MasterPasswordDialog(Window owner, Mode mode) {
        super(owner, buildTitle(mode), ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        buildUi();
        pack();
        setLocationRelativeTo(owner);

        // We clear sensitive fields deterministically when the window is closed or disposed.
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { zeroSecrets(); }
            @Override public void windowClosing(WindowEvent e) { zeroSecrets(); }
        });
    }

    private static String buildTitle(Mode mode) {
        return switch (mode) {
            case SETUP -> "Set Master Password";
            case UNLOCK -> "Unlock Secrets";
            case CHANGE -> "Change Master Password";
            default -> "Master Password";
        };
    }

    private void buildUi() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;

        if (mode == Mode.CHANGE) {
            panel.add(new JLabel("Current master password:"), gc);
            gc.gridx = 1;
            currentField = new JPasswordField(24);
            panel.add(currentField, gc);
            gc.gridx = 0;
            gc.gridy++;
        }

        if (mode != Mode.UNLOCK) {
            panel.add(new JLabel("New master password:"), gc);
            gc.gridx = 1;
            passField = new JPasswordField(24);
            panel.add(passField, gc);
            gc.gridx = 0;
            gc.gridy++;

            panel.add(new JLabel("Confirm new master password:"), gc);
            gc.gridx = 1;
            confirmField = new JPasswordField(24);
            panel.add(confirmField, gc);
        } else {
            panel.add(new JLabel("Master password:"), gc);
            gc.gridx = 1;
            passField = new JPasswordField(24);
            panel.add(passField, gc);
        }
        gc.gridx = 0;
        gc.gridy++;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(this::onOk);
        cancel.addActionListener(e -> onCancel());
        buttons.add(ok);
        buttons.add(cancel);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    private void onOk(ActionEvent e) {
        if (mode == Mode.UNLOCK) {
            char[] pwd = passField.getPassword();
            if (pwd.length == 0) {
                showError("Please enter the master password.");
                return;
            }
            resultPassword = pwd;
            setVisible(false);
            return;
        }

        char[] newPassword = passField.getPassword();
        char[] confirm = confirmField.getPassword();

        if (newPassword.length < 8) {
            showError("Please choose a password with at least 8 characters.");
            zero(newPassword);
            zero(confirm);
            return;
        }
        if (!Arrays.equals(newPassword, confirm)) {
            showError("Passwords do not match.");
            zero(newPassword);
            zero(confirm);
            return;
        }

        if (mode == Mode.CHANGE) {
            char[] current = currentField.getPassword();
            if (current.length == 0) {
                showError("Please enter the current master password.");
                zero(newPassword);
                zero(confirm);
                return;
            }
            resultCurrentPassword = current;
        }

        resultPassword = newPassword;
        setVisible(false);
    }

    private void onCancel() {
        zeroSecrets();
        resultPassword = null;
        resultCurrentPassword = null;
        setVisible(false);
    }

    private void zeroSecrets() {
        if (currentField != null) {
            zero(currentField.getPassword());
        }
        if (passField != null) {
            zero(passField.getPassword());
        }
        if (confirmField != null) {
            zero(confirmField.getPassword());
        }
    }

    private static void zero(char[] a) {
        if (a != null) {
            Arrays.fill(a, '\0');
        }
    }

    private static void showError(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public char[] getPassword() { return resultPassword; }

    public char[] getCurrentPassword() { return resultCurrentPassword; }
}
