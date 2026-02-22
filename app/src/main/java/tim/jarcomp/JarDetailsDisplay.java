package tim.jarcomp;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;

/**
 * Display unit for the details of a single jar file
 */
public class JarDetailsDisplay extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Array of four labels for details */
    private JLabel[] labels;
    /** Number of labels */
    private static final int NUM_LABELS = 4;

    /**
     * Constructor
     */
    public JarDetailsDisplay() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 0.25;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED), BorderFactory.createEmptyBorder(3, 3, 3, 3)));
        labels = new JLabel[NUM_LABELS];
        String[] headings = { "Name", "Path", "Size", "Files" };
        for (int i = 0; i < NUM_LABELS; i++) {
            JLabel preLabel = new JLabel(headings[i] + " :");
            c.gridy = i;
            add(preLabel, c);
        }
        c.gridx = 1;
        c.weightx = 0.75;
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < NUM_LABELS; i++) {
            labels[i] = new JLabel("");
            c.gridy = i;
            add(labels[i], c);
        }
    }

    /**
     * Clear the contents
     */
    public void clear() {
        for (int i = 0; i < NUM_LABELS; i++) {
            labels[i].setText("");
        }
    }

    /**
     * Set the contents of the display using the given compare results
     *
     * @param inFile    file object
     * @param inResults results of comparison
     * @param inIndex   0 or 1
     */
    public void setContents(File inFile, CompareResults inResults, int inIndex) {
        labels[0].setText(inFile.getName());
        labels[1].setText(inFile.getParent());
        labels[1].setToolTipText(inFile.getParent());
        labels[2].setText("" + inResults.getSize(inIndex));
        labels[3].setText("" + inResults.getNumFiles(inIndex));
    }
}
