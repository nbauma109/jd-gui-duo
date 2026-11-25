/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component;

import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

/** The list of the breakpoints: what it shows, and what its rows and buttons ask for. */
class BreakpointListPanelTest {

    private static final BreakpointListPanel.Row LINE = new BreakpointListPanel.Row("demo.A:3", "A [line: 3]", "installed", true);
    private static final BreakpointListPanel.Row OFF = new BreakpointListPanel.Row("demo.A:7", "A [line: 7]", "pending", false);
    private static final BreakpointListPanel.Row THROWN = new BreakpointListPanel.Row("exception:java.io.IOException", "IOException", "installed", true);
    private static final BreakpointListPanel.Row IGNORED = new BreakpointListPanel.Row("exception:java.lang.Error", "Error", "pending", false);

    private static ImageIcon icon(int size) {
        return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }

    private final ImageIcon[] icons = {icon(1), icon(2), icon(3), icon(4), icon(5), icon(6), icon(7), icon(8), icon(9)};
    private final List<String> asked = new ArrayList<>();

    private BreakpointListPanel panel(boolean listening) {
        BreakpointListPanel panel = new BreakpointListPanel(new BreakpointListPanel.Icons(icons[0], icons[1], icons[2], icons[3], icons[4], icons[5],
                icons[6], icons[7], icons[8]));
        if (listening) {
            panel.setRemoveCallback(key -> asked.add("remove " + key));
            panel.setRemoveAllCallback(() -> asked.add("remove all"));
            panel.setEnabledCallback((key, enabled) -> asked.add("enable " + key + " " + enabled));
            panel.setNavigateCallback(key -> asked.add("go to " + key));
            panel.setSkipAllCallback(skip -> asked.add("skip all " + skip));
            panel.setAddExceptionCallback(() -> asked.add("add exception"));
        }
        panel.setBreakpoints(List.of(LINE, OFF, THROWN, IGNORED));
        return panel;
    }

    private static <T extends Component> List<T> find(Component component, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(find(child, type));
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static JList<BreakpointListPanel.Row> list(BreakpointListPanel panel) {
        return find(panel, JList.class).get(0);
    }

    private static AbstractButton button(BreakpointListPanel panel, String toolTip) {
        return find(panel, AbstractButton.class).stream().filter(b -> toolTip.equals(b.getToolTipText())).findFirst().orElseThrow();
    }

    /** Gives the key to the listeners: a key event that is dispatched goes to the component that has the focus, and none has it here. */
    private static void press(JComponent component, int key) {
        KeyEvent event = new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : component.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static void click(JList<?> list, int row, int x, int clicks) {
        int y = list.getCellBounds(row, row).y + 5;
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, clicks, false, MouseEvent.BUTTON1));
    }

    @Test
    void eachRowShowsTheIconOfItsKindAndOfItsState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JList<BreakpointListPanel.Row> list = list(panel(false));

            for (int row = 0; row < 4; row++) {
                BreakpointListPanel.Row value = list.getModel().getElementAt(row);
                JPanel shown = (JPanel) list.getCellRenderer().getListCellRendererComponent(list, value, row, row == 1, false);
                JLabel label = find(shown, JLabel.class).get(0);
                assertThat(label.getText()).isEqualTo(value.text()).isEqualTo(value.toString());
                assertThat(label.getIcon()).isSameAs(icons[row]);
                assertThat(find(shown, JCheckBox.class).get(0).isSelected()).isEqualTo(value.enabled());
                assertThat(shown.getToolTipText()).isEqualTo(value.key() + " [" + value.state() + "]");
            }
        });
    }

    @Test
    void theButtonsFollowTheSelectionAndAskForTheirAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BreakpointListPanel panel = panel(true);
            AbstractButton remove = button(panel, "Remove Selected Breakpoints");
            AbstractButton goTo = button(panel, "Go to File for Breakpoint");
            assertThat(remove.isEnabled()).isFalse();
            assertThat(goTo.isEnabled()).isFalse();
            assertThat(button(panel, "Remove All Breakpoints").isEnabled()).isTrue();

            list(panel).setSelectedIndices(new int[] {0, 2});
            remove.doClick(0);
            goTo.doClick(0);
            button(panel, "Remove All Breakpoints").doClick(0);
            button(panel, "Add Java Exception Breakpoint...").doClick(0);
            AbstractButton skip = button(panel, "Skip All Breakpoints");
            skip.doClick(0);
            assertThat(skip.isContentAreaFilled()).isTrue();
            skip.doClick(0);
            assertThat(skip.isContentAreaFilled()).isFalse();

            assertThat(asked).containsExactly("remove demo.A:3", "remove exception:java.io.IOException", "go to demo.A:3", "remove all",
                    "add exception", "skip all true", "skip all false");
        });
    }

    @Test
    void theKeysAndTheClicksActOnTheRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BreakpointListPanel panel = panel(true);
            JList<BreakpointListPanel.Row> list = list(panel);
            list.setSize(300, 200);

            list.setSelectedIndex(1);
            press(list, KeyEvent.VK_DELETE);
            press(list, KeyEvent.VK_ENTER);
            press(list, KeyEvent.VK_SPACE);
            press(list, KeyEvent.VK_A);
            click(list, 0, 3, 1); // the check box
            click(list, 2, 100, 1); // the text, once
            list.setSelectedIndex(2);
            click(list, 2, 100, 2);
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 100, 190, 2, false, MouseEvent.BUTTON1)); // below the rows

            assertThat(asked).containsExactly("remove demo.A:7", "go to demo.A:7", "enable demo.A:7 true", "enable demo.A:3 false",
                    "go to exception:java.io.IOException");
        });
    }

    @Test
    void withoutListenersNothingHappens() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BreakpointListPanel panel = panel(false);
            JList<BreakpointListPanel.Row> list = list(panel);
            list.setSize(300, 200);
            list.setSelectedIndex(0);

            press(list, KeyEvent.VK_DELETE);
            press(list, KeyEvent.VK_ENTER);
            press(list, KeyEvent.VK_SPACE);
            click(list, 0, 3, 1);
            find(panel, AbstractButton.class).forEach(button -> button.doClick(0));

            assertThat(asked).isEmpty();
        });
    }

    @Test
    void theBreakpointsThatStayKeepTheirSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BreakpointListPanel panel = panel(false);
            list(panel).setSelectedIndices(new int[] {1, 2});

            panel.setBreakpoints(List.of(THROWN, LINE));

            assertThat(list(panel).getSelectedValuesList()).containsExactly(THROWN);
            panel.setBreakpoints(List.of());
            assertThat(button(panel, "Remove All Breakpoints").isEnabled()).isFalse();
        });
    }
}
