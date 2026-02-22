/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Type;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.function.TriConsumer;
import org.jd.gui.util.swing.SwingUtil;
import org.jd.gui.view.bean.OpenTypeListCellBean;
import org.jd.gui.view.renderer.OpenTypeListCellRenderer;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

public class OpenTypeView {
    protected static final int MAX_LINE_COUNT = 80;

    private final API api;

    private JDialog openTypeDialog;
    private JTextField openTypeEnterTextField;
    private JLabel openTypeMatchLabel;
    @SuppressWarnings("all")
    private JList openTypeList;

    @SuppressWarnings("unchecked")
    public OpenTypeView(API api, JFrame mainFrame, Consumer<String> changedPatternCallback, TriConsumer<Point, Collection<Container.Entry>, String> selectedTypeCallback) {
        this.api = api;
        // Build GUI
        SwingUtil.invokeLater(() -> {
            openTypeDialog = new JDialog(mainFrame, "Open Type", false);

            JPanel panel = new JPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.setLayout(new BorderLayout());
            openTypeDialog.add(panel);

            // Box for "Select a type to open"
            Box vbox = Box.createVerticalBox();
            panel.add(vbox, BorderLayout.NORTH);

            Box hbox = Box.createHorizontalBox();
            hbox.add(new JLabel("Select a type to open (* = any string, ? = any character, TZ = TimeZone):"));
            hbox.add(Box.createHorizontalGlue());
            vbox.add(hbox);

            vbox.add(Box.createVerticalStrut(10));

            // Text field
            openTypeEnterTextField = new JTextField(30);
            vbox.add(openTypeEnterTextField);
            openTypeEnterTextField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    if ("=(){}[]".indexOf(e.getKeyChar()) != -1 || Character.isDigit(e.getKeyChar()) && openTypeEnterTextField.getText().isEmpty()) {
                        // Characters =(){}[] are ignored and 1st character and digit cannot be typed on an empty search field
                        e.consume();
                    }
                }
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DOWN && openTypeList.getModel().getSize() > 0) {
                        openTypeList.setSelectedIndex(0);
                        openTypeList.requestFocus();
                        e.consume();
                    }
                }
            });
            openTypeEnterTextField.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) { openTypeList.clearSelection(); }
                @Override
                public void focusLost(FocusEvent e) {}
            });
            openTypeEnterTextField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { call(e); }
                @Override
                public void removeUpdate(DocumentEvent e) { call(e); }
                @Override
                public void changedUpdate(DocumentEvent e) { call(e); }
                protected void call(DocumentEvent e) {
                    try {
                        changedPatternCallback.accept(e.getDocument().getText(0, e.getDocument().getLength()));
                    } catch (BadLocationException ex) {
                        assert ExceptionUtil.printStackTrace(ex);
                    }
                }
            });

            vbox.add(Box.createVerticalStrut(10));

            hbox = Box.createHorizontalBox();
            openTypeMatchLabel = new JLabel("Matching types:");
            hbox.add(openTypeMatchLabel);
            hbox.add(Box.createHorizontalGlue());
            vbox.add(hbox);

            vbox.add(Box.createVerticalStrut(10));

            // List of types
            openTypeList = new JList<>();
            JScrollPane scrollPane = new JScrollPane(openTypeList);
            openTypeList.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_UP && openTypeList.getSelectedIndex()  == 0) {
                        openTypeEnterTextField.requestFocus();
                        e.consume();
                    }
                }
            });
            openTypeList.setModel(new DefaultListModel<>());
            openTypeList.setCellRenderer(new OpenTypeListCellRenderer());
            openTypeList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        onTypeSelected(selectedTypeCallback);
                    }
                }
            });
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setPreferredSize(new Dimension(400, 150));
            panel.add(scrollPane, BorderLayout.CENTER);

            // Buttons "Open" and "Cancel"
            vbox = Box.createVerticalBox();
            panel.add(vbox, BorderLayout.SOUTH);
            vbox.add(Box.createVerticalStrut(25));
            hbox = Box.createHorizontalBox();
            vbox.add(hbox);
            hbox.add(Box.createHorizontalGlue());
            JButton openTypeOpenButton = new JButton("Open", ImageUtil.newImageIcon("/org/jd/gui/images/open_type.png"));
            hbox.add(openTypeOpenButton);
            openTypeOpenButton.setEnabled(false);
            openTypeOpenButton.addActionListener(e -> onTypeSelected(selectedTypeCallback));
            hbox.add(Box.createHorizontalStrut(5));
            JButton openTypeCancelButton = new JButton("Cancel", ImageUtil.newImageIcon("/org/jd/gui/images/close_active.gif"));
            hbox.add(openTypeCancelButton);
            Action openTypeCancelActionListener = new AbstractAction() {

                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent actionEvent) { openTypeDialog.setVisible(false); }
            };
            openTypeCancelButton.addActionListener(openTypeCancelActionListener);

            // Last setup
            JRootPane rootPane = openTypeDialog.getRootPane();
            rootPane.setDefaultButton(openTypeOpenButton);
            rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "OpenTypeView.cancel");
            rootPane.getActionMap().put("OpenTypeView.cancel", openTypeCancelActionListener);

            openTypeList.addListSelectionListener(e -> openTypeOpenButton.setEnabled(openTypeList.getSelectedValue() != null));

            openTypeDialog.setMinimumSize(openTypeDialog.getSize());

            // Prepare to display
            openTypeDialog.pack();
            openTypeDialog.setLocationRelativeTo(mainFrame);
        });
    }

    public void show() {
        SwingUtil.invokeLater(() -> {
            // Init
            openTypeEnterTextField.selectAll();
            // Show
            openTypeDialog.setVisible(true);
            openTypeEnterTextField.requestFocus();
        });
    }

    public boolean isVisible() { return openTypeDialog.isVisible(); }

    public String getPattern() { return openTypeEnterTextField.getText(); }

    public void showWaitCursor() {
        SwingUtil.invokeLater(() -> openTypeDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)));
    }

    public void hideWaitCursor() {
        SwingUtil.invokeLater(() -> openTypeDialog.setCursor(Cursor.getDefaultCursor()));
    }

    @SuppressWarnings("unchecked")
    public void updateList(Map<String, Collection<Container.Entry>> map) {
        SwingUtil.invokeLater(() -> {
            @SuppressWarnings("all")
            DefaultListModel model = (DefaultListModel)openTypeList.getModel();
            List<String> typeNames = new ArrayList<>(map.keySet());
            int index = 0;

            typeNames.sort(Comparator.comparing(OpenTypeView::getSimpleTypeName));

            model.removeAllElements();

            for (String typeName : typeNames) {
                if (index < MAX_LINE_COUNT) {
                    Collection<Container.Entry> entries = map.get(typeName);
                    Container.Entry firstEntry = entries.iterator().next();
                    Type type = api.getTypeFactory(firstEntry).make(api, firstEntry, typeName);

                    if (type != null) {
                        model.addElement(new OpenTypeListCellBean(type.getDisplayTypeName(), type.getDisplayPackageName(), type.getIcon(), entries, typeName));
                    } else {
                        model.addElement(new OpenTypeListCellBean(typeName, entries, typeName));
                    }
                } else if (index == MAX_LINE_COUNT) {
                    model.addElement(null);
                }
            }

            int count = typeNames.size();

            switch (count) {
                case 0:
                    openTypeMatchLabel.setText("Matching types:");
                    break;
                case 1:
                    openTypeMatchLabel.setText("1 matching type:");
                    break;
                default:
                    openTypeMatchLabel.setText(count + " matching types:");
            }
        });
    }

    public void focus() {
        SwingUtil.invokeLater(() -> openTypeList.requestFocus());
    }

    protected void onTypeSelected(TriConsumer<Point, Collection<Container.Entry>, String> selectedTypeCallback) {
        SwingUtil.invokeLater(() -> {
            int index = openTypeList.getSelectedIndex();

            if (index != -1) {
                OpenTypeListCellBean selectedCellBean = (OpenTypeListCellBean)openTypeList.getModel().getElementAt(index);
                Point listLocation = openTypeList.getLocationOnScreen();
                Rectangle cellBound = openTypeList.getCellBounds(index, index);
                Point leftBottom = new Point(listLocation.x + cellBound.x, listLocation.y + cellBound.y + cellBound.height);
                selectedTypeCallback.accept(leftBottom, selectedCellBean.getEntries(), selectedCellBean.getTypeName());
            }
        });
    }

    private static String getSimpleTypeName(String typeName) {
        return typeName.substring(typeName.lastIndexOf('/') + 1);
    }
}
