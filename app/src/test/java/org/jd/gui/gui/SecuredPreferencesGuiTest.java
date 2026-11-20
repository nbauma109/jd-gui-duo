/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.JTextComponentFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/** The secured preferences: the Nexus repository and the proxy, and the master password that protects the credentials. */
class SecuredPreferencesGuiTest extends GuiTestBase {

    private static final String TITLE = "Secured Preferences";

    @AfterEach
    void clearTheSettings() {
        GuiActionRunner.execute(() -> {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w != window.target() && w instanceof Dialog) {
                    w.dispose();
                }
            }
        });
        clickMenu("Help", "Secured Preferences...");
        DialogFixture dialog = dialog(TITLE);
        clear(dialog, 0);
        clear(dialog, 1);
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing(TITLE));
    }

    @Test
    void repositoryUrlIsValidatedAndKept() {
        DialogFixture dialog = open();

        text(dialog, 0).setText("not a url");
        text(dialog, 0).focus();
        button(dialog, "Ok").click();
        // refused: the dialog stays open
        assertThat(isDialogShowing(TITLE)).isTrue();

        text(dialog, 0).setText("http://127.0.0.1:9");
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing(TITLE));

        DialogFixture reopened = open();
        assertThat(text(reopened, 0).text()).isEqualTo("http://127.0.0.1:9");
        button(reopened, "Cancel").click();
    }

    @Test
    void aUserNameWithoutPasswordIsRefused() {
        DialogFixture dialog = open();
        text(dialog, 0).setText("http://127.0.0.1:9");
        text(dialog, 1).setText("alice");

        button(dialog, "Ok").click();

        assertThat(isDialogShowing(TITLE)).isTrue();
        button(dialog, "Cancel").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing(TITLE));
    }

    @Test
    void proxySettingsAreKept() {
        DialogFixture dialog = open();
        text(dialog, 2).setText("proxy.example.com");
        text(dialog, 3).setText("3128");
        button(dialog, "Ok").click();
        waitUntil("the dialog is closed", () -> !isDialogShowing(TITLE));

        DialogFixture reopened = open();
        assertThat(text(reopened, 2).text()).isEqualTo("proxy.example.com");
        assertThat(text(reopened, 3).text()).isEqualTo("3128");
        button(reopened, "Cancel").click();
    }

    @Test
    void credentialsAreProtectedByAMasterPassword() {
        DialogFixture dialog = open();
        text(dialog, 0).setText("http://127.0.0.1:9");
        text(dialog, 1).setText("alice");
        password(dialog, 0).setText("s3cret");
        button(dialog, "Ok").click();

        DialogFixture master = dialog("Set Master Password");
        passwordOf(master, 0).setText("short");
        passwordOf(master, 1).setText("short");
        button(master, "OK").click();
        dismissError(); // fewer than 8 characters

        passwordOf(master, 0).setText("masterpass1");
        passwordOf(master, 1).setText("masterpass2");
        button(master, "OK").click();
        dismissError(); // the confirmation differs
        assertThat(isDialogShowing("Set Master Password")).isTrue();

        passwordOf(master, 0).setText("masterpass1");
        passwordOf(master, 1).setText("masterpass1");
        button(master, "OK").click();

        waitUntil("the preferences are saved", () -> !isDialogShowing(TITLE) && !isDialogShowing("Set Master Password"));
        DialogFixture reopened = open();
        assertThat(text(reopened, 1).text()).isEqualTo("alice");
        assertThat(text(reopened, 0).text()).isEqualTo("http://127.0.0.1:9");
    }

    @Test
    void cancellingTheMasterPasswordDoesNotKeepTheCredentials() {
        DialogFixture dialog = open();
        text(dialog, 1).setText("bob");
        password(dialog, 0).setText("pw");
        button(dialog, "Ok").click();

        button(dialog("Set Master Password"), "Cancel").click();

        waitUntil("the master password dialog is closed", () -> !isDialogShowing("Set Master Password"));
        assertThat(isDialogShowing("Set Master Password")).isFalse();
    }

    // -------------------------------------------------------------------

    private static void dismissError() {
        button(dialog("Error"), "OK").click();
        waitUntil("the error is dismissed", () -> !isDialogShowing("Error"));
    }

    private static DialogFixture open() {
        clickMenu("Help", "Secured Preferences...");
        return dialog(TITLE);
    }

    private static void clear(DialogFixture dialog, int index) {
        List<JButton> buttons = findIn(dialog.target(), JButton.class, b -> b.isShowing() && "Clear".equals(b.getText()));
        new org.assertj.swing.fixture.JButtonFixture(robot, buttons.get(index)).click();
    }

    private static JTextComponentFixture text(DialogFixture dialog, int index) {
        return new JTextComponentFixture(robot, findIn(dialog.target(), JTextField.class, f -> f.isShowing() && !(f instanceof JPasswordField)).get(index));
    }

    private static JTextComponentFixture password(DialogFixture dialog, int index) {
        return passwordOf(dialog, index);
    }

    private static JTextComponentFixture passwordOf(DialogFixture dialog, int index) {
        return new JTextComponentFixture(robot, findIn(dialog.target(), JPasswordField.class, Component::isShowing).get(index));
    }
}
