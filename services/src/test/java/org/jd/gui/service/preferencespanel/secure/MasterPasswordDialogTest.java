/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel.secure;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The dialog that asks for the master password. It is not shown, and only what raises no message is tried. */
class MasterPasswordDialogTest {

    @BeforeAll
    static void needsAScreen() {
        assumeFalse(GraphicsEnvironment.isHeadless());
    }

    private static <T extends Component> List<T> find(Container container, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container nested) {
                found.addAll(find(nested, type));
            }
        }
        return found;
    }

    private static void click(MasterPasswordDialog dialog, String text) {
        find(dialog.getContentPane(), JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow().doClick(0);
    }

    @ParameterizedTest(name = "{0}: {1} fields")
    @CsvSource({"SETUP, 2, Set Master Password", "UNLOCK, 1, Unlock Secrets", "CHANGE, 3, Change Master Password"})
    void eachModeHasItsTitleAndItsFields(MasterPasswordDialog.Mode mode, int fields, String title) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MasterPasswordDialog dialog = new MasterPasswordDialog(null, mode);

            assertThat(dialog.getTitle()).isEqualTo(title);
            assertThat(find(dialog.getContentPane(), JPasswordField.class)).hasSize(fields);
            assertThat(dialog.getPassword()).isNull();
            dialog.dispose();
        });
    }

    private static MasterPasswordDialog filled(MasterPasswordDialog.Mode mode, String... texts) {
        MasterPasswordDialog dialog = new MasterPasswordDialog(null, mode);
        List<JPasswordField> fields = find(dialog.getContentPane(), JPasswordField.class);
        for (int i = 0; i < texts.length; i++) {
            fields.get(i).setText(texts[i]);
        }
        return dialog;
    }

    @Test
    void thePasswordThatIsAcceptedIsGivenBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MasterPasswordDialog unlock = filled(MasterPasswordDialog.Mode.UNLOCK, "open");
            MasterPasswordDialog setup = filled(MasterPasswordDialog.Mode.SETUP, "long enough", "long enough");
            MasterPasswordDialog change = filled(MasterPasswordDialog.Mode.CHANGE, "old one", "long enough", "long enough");

            click(unlock, "OK");
            click(setup, "OK");
            click(change, "OK");

            assertThat(new String(unlock.getPassword())).isEqualTo("open");
            assertThat(new String(setup.getPassword())).isEqualTo("long enough");
            assertThat(setup.getCurrentPassword()).isNull();
            assertThat(new String(change.getPassword())).isEqualTo("long enough");
            assertThat(new String(change.getCurrentPassword())).isEqualTo("old one");
        });
    }

    @Test
    void cancellingOrClosingGivesNothingBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MasterPasswordDialog dialog = filled(MasterPasswordDialog.Mode.CHANGE, "old one", "long enough", "long enough");
            click(dialog, "OK");

            click(dialog, "Cancel");
            for (WindowListener listener : dialog.getWindowListeners()) {
                listener.windowClosing(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
                listener.windowClosed(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSED));
            }

            assertThat(dialog.getPassword()).isNull();
            assertThat(dialog.getCurrentPassword()).isNull();
        });
    }
}
