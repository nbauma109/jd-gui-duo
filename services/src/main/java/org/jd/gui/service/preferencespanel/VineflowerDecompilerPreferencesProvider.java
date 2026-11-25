/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2024-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.vineflower.java.decompiler.api.DecompilerOption;
import org.vineflower.java.decompiler.main.Init;
import org.vineflower.java.decompiler.main.extern.IFernflowerLogger.Severity;
import org.vineflower.java.decompiler.main.extern.IFernflowerPreferences;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

public class VineflowerDecompilerPreferencesProvider extends AbstractDecompilerPreferencesProvider {

    private static final long serialVersionUID = 1L;

    private static final String TRUE = "1";
    private static final String FALSE = "0";

    private static final String[] LOG_LEVELS = Arrays.stream(Severity.values()).map(Severity::name).toArray(String[]::new);
    private static final Dimension FIELD_DIMENSION = new Dimension(250, 20);

    public VineflowerDecompilerPreferencesProvider() {
        super(4, TRUE, FALSE);

        // Initialize Vineflower (to load plugins)
        Init.init();

        for (DecompilerOption option : DecompilerOption.getAll()) {
            JComponent component = switch (option.type()) {
                case BOOLEAN -> new JCheckBox();
                case INTEGER -> new JSpinner();
                case STRING -> {
                    if ("log-level".equals(option.id())) {
                        yield new JComboBox<>(LOG_LEVELS);
                    }

                    JTextField text = new JTextField();
                    text.setMaximumSize(FIELD_DIMENSION);
                    text.setMinimumSize(FIELD_DIMENSION);
                    text.setPreferredSize(FIELD_DIMENSION);
                    yield text;
                }
            };

            register(option.id(), component);
            JLabel label = new JLabel(option.name());
            label.setToolTipText(option.description());
            label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

            add(label);
            add(component);
        }

        JComponent dumpLines = new JCheckBox();
        register(IFernflowerPreferences.DUMP_ORIGINAL_LINES, dumpLines);
        JLabel linesLabel = new JLabel("Dump Line Numbers");
        linesLabel.setToolTipText("Dump original line numbers in the output");
        linesLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        add(linesLabel);
        add(dumpLines);
    }

    /** An option without a default (and the dump of the line numbers, which is not an option of the decompiler) is off, or empty. */
    @Override
    protected String defaultValue(String key, JComponent component) {
        return DecompilerOption.getAll().stream().filter(option -> option.id().equals(key))
                .map(option -> Objects.toString(option.defaultValue(), "")).findFirst().orElse("");
    }
}
