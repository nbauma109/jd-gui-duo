/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.spi.PreferencesPanel;
import org.jd.gui.util.ThemeUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JTree;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTED_WORD_HIGHLIGHT_ENABLED;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.DEFAULT_SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.FONT_SIZE_KEY;
import static org.jd.gui.util.decompiler.GuiPreferences.SEARCH_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTION_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_COLOR;
import static org.jd.gui.util.decompiler.GuiPreferences.SELECTED_WORD_HIGHLIGHT_ENABLED;
import static org.jd.gui.util.decompiler.GuiPreferences.TREE_NODE_FONT_SIZE_KEY;

public class ViewerPreferencesProvider extends JPanel implements PreferencesPanel, DocumentListener {

    private static final long serialVersionUID = 1L;
    protected static final int MIN_VALUE = 2;
    protected static final int MAX_VALUE = 40;

    protected transient PreferencesPanel.PreferencesPanelChangeListener listener;
    protected JTextField fontSizeTextField;
    protected JTextField treeNodeFontSizeTextField;
    protected JCheckBox selectedWordHighlightCheckBox;
    protected JButton selectedWordHighlightColorButton;
    protected JPanel selectedWordHighlightColorPreview;
    protected JButton searchHighlightColorButton;
    protected JPanel searchHighlightColorPreview;
    protected JButton selectionHighlightColorButton;
    protected JPanel selectionHighlightColorPreview;
    protected Color errorBackgroundColor = Color.RED;
    protected Color defaultBackgroundColor;
    protected Color selectedWordHighlightColor = Color.decode(DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR);
    protected Color searchHighlightColor = Color.decode(DEFAULT_SEARCH_HIGHLIGHT_COLOR);
    protected Color selectionHighlightColor = Color.decode(DEFAULT_SELECTION_HIGHLIGHT_COLOR);

    public ViewerPreferencesProvider() {
        super(new GridLayout(6, 2, 6, 4));

        add(new JLabel("Font Size Of Text Area (" + MIN_VALUE + ".." + MAX_VALUE + "): "));

        fontSizeTextField = new JTextField();
        fontSizeTextField.getDocument().addDocumentListener(this);
        add(fontSizeTextField);

        add(new JLabel("Font Size Of Tree Node (" + MIN_VALUE + ".." + MAX_VALUE + "): "));

        treeNodeFontSizeTextField = new JTextField();
        treeNodeFontSizeTextField.getDocument().addDocumentListener(this);
        add(treeNodeFontSizeTextField);

        add(new JLabel("Highlight Selected Word: "));

        selectedWordHighlightCheckBox = new JCheckBox();
        selectedWordHighlightCheckBox.addActionListener(_ -> firePreferencesChanged());
        add(selectedWordHighlightCheckBox);

        add(new JLabel("Highlight Color: "));

        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        selectedWordHighlightColorPreview = new JPanel();
        selectedWordHighlightColorPreview.setOpaque(true);
        selectedWordHighlightColorPreview.setPreferredSize(new Dimension(18, 18));
        selectedWordHighlightColorPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        colorPanel.add(selectedWordHighlightColorPreview);

        selectedWordHighlightColorButton = new JButton();
        selectedWordHighlightColorButton.addActionListener(_ -> chooseSelectedWordHighlightColor());
        colorPanel.add(selectedWordHighlightColorButton);
        add(colorPanel);

        add(new JLabel("Search Highlight Color: "));
        searchHighlightColorPreview = createColorPreview();
        searchHighlightColorButton = new JButton();
        searchHighlightColorButton.addActionListener(_ -> chooseSearchHighlightColor());
        add(createColorPanel(searchHighlightColorPreview, searchHighlightColorButton));

        add(new JLabel("Selection Highlight Color: "));
        selectionHighlightColorPreview = createColorPreview();
        selectionHighlightColorButton = new JButton();
        selectionHighlightColorButton.addActionListener(_ -> chooseSelectionHighlightColor());
        add(createColorPanel(selectionHighlightColorPreview, selectionHighlightColorButton));

        defaultBackgroundColor = fontSizeTextField.getBackground();
        updateColorPreviews();
    }

    // --- PreferencesPanel --- //
    @Override
    public String getPreferencesGroupTitle() { return "Viewer"; }
    @Override
    public String getPreferencesPanelTitle() { return "Appearance"; }

    @Override
    public void init(Color errorBackgroundColor) {
        this.errorBackgroundColor = errorBackgroundColor;
    }

    @Override
    public void loadPreferences(Map<String, String> preferences) {
        String fontSize = preferences.get(FONT_SIZE_KEY);
        String treeNodeFontSize = preferences.get(TREE_NODE_FONT_SIZE_KEY);

        if (fontSize == null) {
            // Search default value for the current platform
            RSyntaxTextArea textArea = ThemeUtil.applyTheme(preferences, new RSyntaxTextArea());

            fontSize = String.valueOf(textArea.getFont().getSize());
        }
        if (treeNodeFontSize == null) {
            treeNodeFontSize = String.valueOf(new JTree().getFont().getSize());
        }

        fontSizeTextField.setText(fontSize);
        fontSizeTextField.setCaretPosition(fontSizeTextField.getText().length());
        treeNodeFontSizeTextField.setText(treeNodeFontSize);
        treeNodeFontSizeTextField.setCaretPosition(treeNodeFontSizeTextField.getText().length());
        selectedWordHighlightCheckBox.setSelected(Boolean.parseBoolean(
            preferences.getOrDefault(SELECTED_WORD_HIGHLIGHT_ENABLED, DEFAULT_SELECTED_WORD_HIGHLIGHT_ENABLED)
        ));
        selectedWordHighlightColor = decodeColor(
            preferences.get(SELECTED_WORD_HIGHLIGHT_COLOR),
            Color.decode(DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR)
        );
        searchHighlightColor = decodeColor(
            preferences.get(SEARCH_HIGHLIGHT_COLOR),
            Color.decode(DEFAULT_SEARCH_HIGHLIGHT_COLOR)
        );
        selectionHighlightColor = decodeColor(
            preferences.get(SELECTION_HIGHLIGHT_COLOR),
            Color.decode(DEFAULT_SELECTION_HIGHLIGHT_COLOR)
        );
        updateColorPreviews();
    }

    @Override
    public void savePreferences(Map<String, String> preferences) {
        preferences.put(FONT_SIZE_KEY, fontSizeTextField.getText());
        preferences.put(TREE_NODE_FONT_SIZE_KEY, treeNodeFontSizeTextField.getText());
        preferences.put(SELECTED_WORD_HIGHLIGHT_ENABLED, Boolean.toString(selectedWordHighlightCheckBox.isSelected()));
        preferences.put(SELECTED_WORD_HIGHLIGHT_COLOR, encodeColor(selectedWordHighlightColor));
        preferences.put(SEARCH_HIGHLIGHT_COLOR, encodeColor(searchHighlightColor));
        preferences.put(SELECTION_HIGHLIGHT_COLOR, encodeColor(selectionHighlightColor));
    }

    @Override
    public boolean arePreferencesValid() {
        return isValidFontSize(fontSizeTextField.getText()) && isValidFontSize(treeNodeFontSizeTextField.getText());
    }

    protected boolean isValidFontSize(String fontSize) {
        try {
            if (fontSize != null && fontSize.matches("\\d+")) {
                int i = Integer.parseInt(fontSize);
                return i >= MIN_VALUE && i <= MAX_VALUE;
            }
        } catch (NumberFormatException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
        return false;
    }

    @Override
    public void addPreferencesChangeListener(PreferencesPanel.PreferencesPanelChangeListener listener) {
        this.listener = listener;
    }

    // --- DocumentListener --- //
    @Override
    public void insertUpdate(DocumentEvent e) { onTextChange(); }
    @Override
    public void removeUpdate(DocumentEvent e) { onTextChange(); }
    @Override
    public void changedUpdate(DocumentEvent e) { onTextChange(); }

    public void onTextChange() {
        fontSizeTextField.setBackground(isValidFontSize(fontSizeTextField.getText()) ? defaultBackgroundColor : errorBackgroundColor);
        treeNodeFontSizeTextField.setBackground(isValidFontSize(treeNodeFontSizeTextField.getText()) ? defaultBackgroundColor : errorBackgroundColor);

        firePreferencesChanged();
    }

    protected void firePreferencesChanged() {
        if (listener != null) {
            listener.preferencesPanelChanged(this);
        }
    }

    @Override
    public void restoreDefaults() {
        fontSizeTextField.setText("12");
        treeNodeFontSizeTextField.setText(String.valueOf(new JTree().getFont().getSize()));
        selectedWordHighlightCheckBox.setSelected(Boolean.parseBoolean(DEFAULT_SELECTED_WORD_HIGHLIGHT_ENABLED));
        selectedWordHighlightColor = Color.decode(DEFAULT_SELECTED_WORD_HIGHLIGHT_COLOR);
        searchHighlightColor = Color.decode(DEFAULT_SEARCH_HIGHLIGHT_COLOR);
        selectionHighlightColor = Color.decode(DEFAULT_SELECTION_HIGHLIGHT_COLOR);
        updateColorPreviews();
        firePreferencesChanged();
    }

    @Override
    public boolean useCompactDisplay() {
        return true;
    }

    protected void chooseSelectedWordHighlightColor() {
        Color color = JColorChooser.showDialog(this, "Selected Word Highlight Color", selectedWordHighlightColor);

        if (color != null) {
            selectedWordHighlightColor = color;
            updateColorPreviews();
            firePreferencesChanged();
        }
    }

    protected void chooseSearchHighlightColor() {
        Color color = JColorChooser.showDialog(this, "Search Highlight Color", searchHighlightColor);

        if (color != null) {
            searchHighlightColor = color;
            updateColorPreviews();
            firePreferencesChanged();
        }
    }

    protected void chooseSelectionHighlightColor() {
        Color color = JColorChooser.showDialog(this, "Selection Highlight Color", selectionHighlightColor);

        if (color != null) {
            selectionHighlightColor = color;
            updateColorPreviews();
            firePreferencesChanged();
        }
    }

    protected void updateColorPreviews() {
        selectedWordHighlightColorPreview.setBackground(selectedWordHighlightColor);
        selectedWordHighlightColorButton.setText(encodeColor(selectedWordHighlightColor));
        searchHighlightColorPreview.setBackground(searchHighlightColor);
        searchHighlightColorButton.setText(encodeColor(searchHighlightColor));
        selectionHighlightColorPreview.setBackground(selectionHighlightColor);
        selectionHighlightColorButton.setText(encodeColor(selectionHighlightColor));
    }

    protected JPanel createColorPreview() {
        JPanel preview = new JPanel();
        preview.setOpaque(true);
        preview.setPreferredSize(new Dimension(18, 18));
        preview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return preview;
    }

    protected JPanel createColorPanel(JPanel preview, JButton button) {
        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        colorPanel.add(preview);
        colorPanel.add(button);
        return colorPanel;
    }

    protected static String encodeColor(Color color) {
        return String.format("0x%06X", color.getRGB() & 0xFFFFFF);
    }

    protected static Color decodeColor(String value, Color fallbackColor) {
        if (value == null || value.isBlank()) {
            return fallbackColor;
        }

        try {
            return Color.decode(value);
        } catch (NumberFormatException e) {
            assert ExceptionUtil.printStackTrace(e);
            return fallbackColor;
        }
    }
}
