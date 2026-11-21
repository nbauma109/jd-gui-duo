/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.security.SecureSession;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** What the tests of the preference panels share: finding their fields and buttons, and the master password of the session. */
final class PanelSupport {

    private PanelSupport() {
    }

    static <T extends Component> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T extends Component> void collect(Component c, Class<T> type, List<T> found) {
        if (type.isInstance(c)) {
            found.add(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    /** The text fields of a panel in their order, the password fields apart. */
    static List<JTextField> textFields(Container panel) {
        return find(panel, JTextField.class).stream().filter(f -> !(f instanceof JPasswordField)).toList();
    }

    static List<JPasswordField> passwordFields(Container panel) {
        return find(panel, JPasswordField.class);
    }

    static JButton button(Container panel, String text) {
        return find(panel, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    static void click(JButton button) {
        for (java.awt.event.ActionListener listener : button.getActionListeners()) {
            listener.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, button.getActionCommand()));
        }
    }

    /** Puts a master password in the session, as if the user had typed it in the dialog (which cannot be shown here). */
    static void unlock(String master) throws ReflectiveOperationException {
        Field vaultField = SecureSession.class.getDeclaredField("vault");
        vaultField.setAccessible(true);
        Object vault = vaultField.get(SecureSession.get());
        Method set = vault.getClass().getDeclaredMethod("set", char[].class);
        set.setAccessible(true);
        set.invoke(vault, (Object) master.toCharArray());
    }

    static void lock() {
        SecureSession.get().clear();
    }
}
