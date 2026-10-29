/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.component;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

/**
 * Breakpoints view laid out like Eclipse's: an icon toolbar on the right, then one selectable row per
 * breakpoint made of an enable checkbox, the breakpoint icon and {@code Type [line: N] - method(Args)}.
 */
public class BreakpointListPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** One breakpoint; {@code key} is {@code typeName:lineNumber}. */
    public record Row(String key, String text, String state, boolean enabled) {
        @Override
        public String toString() {
            return text;
        }
    }

    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(model);
    private final JButton removeButton;
    private final JButton removeAllButton;
    private final JButton goToFileButton;
    private final JToggleButton skipAllButton;
    private final JButton addExceptionButton;
    private final int checkBoxWidth = new JCheckBox().getPreferredSize().width + 4;

    private transient Consumer<String> removeCallback;
    private transient Runnable removeAllCallback;
    private transient BiConsumer<String, Boolean> enabledCallback;
    private transient Consumer<String> navigateCallback;
    private transient Consumer<Boolean> skipAllCallback;
    private transient Runnable addExceptionCallback;

    public BreakpointListPanel(Icon enabledIcon, Icon disabledIcon, Icon exceptionIcon, Icon disabledExceptionIcon,
            Icon removeIcon, Icon removeAllIcon, Icon goToFileIcon, Icon skipAllIcon, Icon addExceptionIcon) {
        super(new BorderLayout());

        removeButton = newButton(removeIcon, "Remove Selected Breakpoints");
        removeAllButton = newButton(removeAllIcon, "Remove All Breakpoints");
        goToFileButton = newButton(goToFileIcon, "Go to File for Breakpoint");
        skipAllButton = new JToggleButton(skipAllIcon);
        style(skipAllButton, "Skip All Breakpoints");
        // a toggle must show that it is pressed
        skipAllButton.addItemListener(e -> {
            boolean pressed = skipAllButton.isSelected();
            skipAllButton.setContentAreaFilled(pressed);
            skipAllButton.setBorderPainted(pressed);
        });
        addExceptionButton = newButton(addExceptionIcon, "Add Java Exception Breakpoint...");
        addExceptionButton.addActionListener(e -> {
            if (addExceptionCallback != null) {
                addExceptionCallback.run();
            }
        });

        removeButton.addActionListener(e -> removeSelected());
        removeAllButton.addActionListener(e -> {
            if (removeAllCallback != null) {
                removeAllCallback.run();
            }
        });
        goToFileButton.addActionListener(e -> navigateToSelected());
        skipAllButton.addActionListener(e -> {
            if (skipAllCallback != null) {
                skipAllCallback.accept(skipAllButton.isSelected());
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 2));
        toolbar.add(removeButton);
        toolbar.add(removeAllButton);
        toolbar.add(goToFileButton);
        toolbar.add(skipAllButton);
        toolbar.add(addExceptionButton);
        add(toolbar, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new Renderer(enabledIcon, disabledIcon, exceptionIcon, disabledExceptionIcon));
        list.setFixedCellHeight(21);
        list.addListSelectionListener(e -> updateButtons());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) {
                    return;
                }
                if (e.getX() < checkBoxWidth) {
                    Row row = model.get(index);
                    if (enabledCallback != null) {
                        enabledCallback.accept(row.key(), !row.enabled());
                    }
                } else if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateToSelected();
                }
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        updateButtons();
    }

    private static JButton newButton(Icon icon, String toolTip) {
        JButton button = new JButton(icon);
        style(button, toolTip);
        return button;
    }

    private static void style(AbstractButton button, String toolTip) {
        button.setToolTipText(toolTip);
        button.setFocusable(false);
        button.setMargin(new Insets(1, 2, 1, 2));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
    }

    public void setRemoveCallback(Consumer<String> callback) {
        this.removeCallback = callback;
    }

    public void setRemoveAllCallback(Runnable callback) {
        this.removeAllCallback = callback;
    }

    public void setEnabledCallback(BiConsumer<String, Boolean> callback) {
        this.enabledCallback = callback;
    }

    public void setNavigateCallback(Consumer<String> callback) {
        this.navigateCallback = callback;
    }

    public void setAddExceptionCallback(Runnable callback) {
        this.addExceptionCallback = callback;
    }

    public void setSkipAllCallback(Consumer<Boolean> callback) {
        this.skipAllCallback = callback;
    }

    /** Replaces the rows and keeps the selection of the breakpoints that are still there. */
    public void setBreakpoints(List<Row> rows) {
        List<String> selected = new ArrayList<>();
        for (Row row : list.getSelectedValuesList()) {
            selected.add(row.key());
        }
        model.clear();
        model.addAll(rows);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            if (selected.contains(model.get(i).key())) {
                indices.add(i);
            }
        }
        list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
        updateButtons();
    }

    private void removeSelected() {
        if (removeCallback != null) {
            for (Row row : list.getSelectedValuesList()) {
                removeCallback.accept(row.key());
            }
        }
    }

    private void navigateToSelected() {
        Row row = list.getSelectedValue();
        if (row != null && navigateCallback != null) {
            navigateCallback.accept(row.key());
        }
    }

    private void updateButtons() {
        boolean selection = !list.isSelectionEmpty();
        removeButton.setEnabled(selection);
        goToFileButton.setEnabled(selection);
        removeAllButton.setEnabled(!model.isEmpty());
    }

    private final class Renderer implements ListCellRenderer<Row> {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JCheckBox checkBox = new JCheckBox();
        private final JLabel label = new JLabel();
        private final Icon enabledIcon;
        private final Icon disabledIcon;
        private final Icon exceptionIcon;
        private final Icon disabledExceptionIcon;

        Renderer(Icon enabledIcon, Icon disabledIcon, Icon exceptionIcon, Icon disabledExceptionIcon) {
            this.enabledIcon = enabledIcon;
            this.disabledIcon = disabledIcon;
            this.exceptionIcon = exceptionIcon;
            this.disabledExceptionIcon = disabledExceptionIcon;
            checkBox.setOpaque(false);
            checkBox.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            label.setIconTextGap(5);
            panel.add(checkBox, BorderLayout.WEST);
            panel.add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Row> jList, Row row, int index, boolean selected, boolean focus) {
            panel.setBackground(selected ? UIManager.getColor("List.selectionBackground") : jList.getBackground());
            label.setForeground(selected ? UIManager.getColor("List.selectionForeground") : jList.getForeground());
            label.setFont(jList.getFont());
            label.setText(row.text());
            boolean exception = row.key().startsWith("exception:");
            label.setIcon(exception ? (row.enabled() ? exceptionIcon : disabledExceptionIcon) : (row.enabled() ? enabledIcon : disabledIcon));
            checkBox.setSelected(row.enabled());
            panel.setToolTipText(row.key() + " [" + row.state() + "]");
            return panel;
        }
    }
}
