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
package tim.jarcomp;

import org.apache.commons.io.IOUtils;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.core.v1.util.ZipLoader;
import org.jd.gui.api.API;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.loader.LoaderUtils;
import org.jdesktop.swingx.JXTable;
import org.netbeans.modules.editor.java.JavaKit;
import org.oxbow.swingbits.list.CheckListRenderer;
import org.oxbow.swingbits.table.filter.TableRowFilterSupport;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;

import static com.heliosdecompiler.transformerapi.StandardTransformers.Decompilers.ENGINE_JD_CORE_V1;
import static org.jd.gui.util.decompiler.GuiPreferences.DECOMPILE_ENGINE;

import de.cismet.custom.visualdiff.DiffPanel;
import jd.core.ClassUtil;
import jd.core.DecompilationResult;
import jd.core.preferences.Preferences;

/**
 * Class to manage the main compare window
 */
public class CompareWindow {

    private static final Color YELLOW = new Color(255, 255, 200);
    private static final Color DARK_GRAY = new Color(0x2A2A2A);

    /** Main window object */
    private JFrame mainWindow;
    /** Two files to compare */
    private File[] files = new File[2];
    /** Displays for jar file details */
    private JarDetailsDisplay[] detailsDisplays;
    /** Label for compare status */
    private JLabel statusLabel;
    /** Second label for contents status */
    private JLabel statusLabel2;
    /** Table model */
    private EntryTableModel tableModel;
    /** File chooser */
    private JFileChooser fileChooser;
    /** Refresh button to repeat comparison */
    private JButton refreshButton;

    private API api;

    /**
     * Constructor
     */
    public CompareWindow(API api) {
        this.api = api;
        mainWindow = new JFrame("Jar Comparer");
        ImageUtil.addJDIconsToFrame(mainWindow);
        mainWindow.getContentPane().add(makeComponents());
        mainWindow.pack();
        mainWindow.setVisible(true);
    }

    /**
     * Make the GUI components for the main dialog
     *
     * @return JPanel containing GUI components
     */
    private JPanel makeComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());

        // Top panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton compareButton = new JButton("Compare ...");
        compareButton.addActionListener(e -> startCompare());
        buttonPanel.add(compareButton);
        refreshButton = new JButton("Refresh");
        refreshButton.setEnabled(false);
        refreshButton.addActionListener(e -> startCompare(files[0], files[1]));
        buttonPanel.add(refreshButton);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(buttonPanel);

        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new GridLayout(1, 2, 5, 5));
        detailsDisplays = new JarDetailsDisplay[2];
        detailsDisplays[0] = new JarDetailsDisplay();
        detailsPanel.add(detailsDisplays[0], BorderLayout.WEST);
        detailsDisplays[1] = new JarDetailsDisplay();
        detailsPanel.add(detailsDisplays[1], BorderLayout.EAST);
        topPanel.add(detailsPanel);
        detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusLabel = new JLabel("");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(new EmptyBorder(5, 10, 1, 1));
        topPanel.add(statusLabel);
        statusLabel2 = new JLabel("");
        statusLabel2.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel2.setBorder(new EmptyBorder(1, 10, 5, 1));
        topPanel.add(statusLabel2);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // main table panel
        tableModel = new EntryTableModel();
        JXTable table = new JXTable(tableModel) {
            private static final long serialVersionUID = 1L;

            /** Modify the renderer according to the row status */
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    int modelRow = convertRowIndexToModel(row);
                    boolean isChange = ((EntryTableModel) getModel()).areDifferent(modelRow);
                    c.setBackground(isChange ? computeAlternateColor() : getBackground());
                }
                return c;
            }
        };
        table.setColumnControlVisible(true);
        table.setEditable(false);
        TableRowFilterSupport.forTable(table).actions(true).searchable(true).checkListRenderer(new CheckListRenderer()).apply();
        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        // Table sorting by clicking on column headings
        table.setAutoCreateRowSorter(true);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int modelCol = table.convertColumnIndexToModel(0);
                    int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
                    boolean isChange = tableModel.isModification(modelRow);
                    if (isChange) {
                        String fileName = (String) tableModel.getValueAt(modelRow, modelCol);
                        try {
                            JFrame diffFrame = new JFrame("Comparison view for class " + fileName);
                            ImageUtil.addJDIconsToFrame(diffFrame);
                            DiffPanel diffPanel = new DiffPanel(diffFrame);
                            String contentLeft = getContent(files[0], fileName);
                            String contentRight = getContent(files[1], fileName);
                            diffPanel.setLeftAndRight(contentLeft, JavaKit.JAVA_MIME_TYPE, files[0].getName(), contentRight, JavaKit.JAVA_MIME_TYPE, files[1].getName());
                            diffFrame.getContentPane().add(diffPanel);
                            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                            diffFrame.setLocation((int) Math.round(screenSize.getWidth() / 6.0), (int) Math.round(screenSize.getHeight() / 6.0));
                            diffFrame.setSize((int) Math.round(screenSize.getWidth() / 1.5), (int) Math.round(screenSize.getHeight() / 1.5));
                            diffFrame.setVisible(true);
                        } catch (Exception ex) {
                            assert ExceptionUtil.printStackTrace(ex);
                        }
                    }
                }
            }
        });

        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        return mainPanel;
    }

    protected String getContent(File file, String entryPath)
            throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (entryPath.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            Map<String, String> preferences = api.getPreferences();
            preferences.put(Preferences.WRITE_LINE_NUMBERS, "false");
            preferences.put(Preferences.REALIGN_LINE_NUMBERS, "false");
            try (FileInputStream in = new FileInputStream(file)) {
                ZipLoader zipLoader = new ZipLoader(in);
                String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
                Loader apiLoader = LoaderUtils.createLoader(preferences, zipLoader, file.toURI());
                String entryInternalName = ClassUtil.getInternalName(entryPath);
                DecompilationResult decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
                return decompilationResult.getDecompiledOutput();
            }
        }
        try (ZipFile zipFile = new ZipFile(file);
             InputStream in = zipFile.getInputStream(zipFile.getEntry(entryPath))) {
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        }
    }

    /**
     * Start the comparison process by prompting for two files
     */
    public void startCompare() {
        startCompare(null, null);
    }

    /**
     * Start the comparison using the two specified files
     *
     * @param inFile1 first file
     * @param inFile2 second file
     */
    public void startCompare(File inFile1, File inFile2) {
        // Clear table model
        tableModel.reset();

        if (inFile1 == null || inFile2 == null) {
	        // Open the unified selection dialog
	        File[] selected = selectTwoFiles(mainWindow, inFile1, inFile2);
	        if (selected == null) {
	            return;
	        }
	        files[0] = selected[0];
	        files[1] = selected[1];
        } else {
	        files[0] = inFile1;
	        files[1] = inFile2;
        }

        // Clear displays
        detailsDisplays[0].clear();
        detailsDisplays[1].clear();
        statusLabel.setText("comparing...");

        // Start separate thread to compare files
        new Thread(this::doCompare).start();
    }

    /**
     * Compare method, to be done in separate thread
     */
    private void doCompare() {
        CompareResults results = Comparer.compare(files[0], files[1]);
        tableModel.setEntryList(results.getEntryList());
        final boolean archivesDifferent = (results.getStatus() == EntryDetails.EntryStatus.CHANGED_SIZE);
        if (archivesDifferent) {
            statusLabel.setText("Archives have different size (" + results.getSize(0) + ", " + results.getSize(1) + ")");
        } else {
            statusLabel.setText("Archives have the same size (" + results.getSize(0) + ")");
        }
        detailsDisplays[0].setContents(files[0], results, 0);
        detailsDisplays[1].setContents(files[1], results, 1);
        if (results.getEntriesDifferent()) {
            statusLabel2.setText((archivesDifferent ? "and" : "but") + " the files have different contents");
        } else if (results.isEntriesCRCChecked()) {
            statusLabel2.setText((archivesDifferent ? "but" : "and") + " the files have exactly the same contents");
        } else {
            statusLabel2.setText((archivesDifferent ? "but" : "and") + " the files appear to have the same contents");
        }
        refreshButton.setEnabled(true);
    }

    /**
     * Show a dialog with two file selectors and return the selected files.
     * The text fields are wide and accept drag and drop of .jar, .zip, .war, or .ear files.
     *
     * @param parentFrame parent frame for the dialog
     * @param default1    optional default file for the first selector
     * @param default2    optional default file for the second selector
     * @return an array of two selected files, or null if cancelled or invalid
     */
    private File[] selectTwoFiles(JFrame parentFrame, File default1, File default2) {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1, 10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Shared file chooser
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new GenericFileFilter(
                    "Jar, Zip, War, and Ear archives",
                    new String[]{"jar", "zip", "war", "ear"}
            ));
            // Start in user home directory
            File home = new File(System.getProperty("user.home"));
            if (home.isDirectory()) {
                fileChooser.setCurrentDirectory(home);
            }
        }

        // First selector
        JPanel filePanel1 = new JPanel(new BorderLayout(5, 5));
        filePanel1.setBorder(new TitledBorder("Select first file"));
        JTextField field1 = new JTextField(60);
        field1.setEditable(false);
        if (default1 != null) {
            field1.setText(default1.getAbsolutePath());
        }
        configureFileDrop(field1, parentFrame);

        JButton button1 = new JButton(new ImageIcon(getClass().getResource("/org/jd/gui/images/open.png")));
        button1.addActionListener(e -> {
            if (fileChooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                File chosen = fileChooser.getSelectedFile();
                if (!isArchiveFile(chosen)) {
                    JOptionPane.showMessageDialog(parentFrame, "Please choose a .jar, .zip, .war, or .ear file.", "Invalid File Type", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                field1.setText(chosen.getAbsolutePath());
            }
        });
        filePanel1.add(field1, BorderLayout.CENTER);
        filePanel1.add(button1, BorderLayout.EAST);

        // Second selector
        JPanel filePanel2 = new JPanel(new BorderLayout(5, 5));
        filePanel2.setBorder(new TitledBorder("Select second file"));
        JTextField field2 = new JTextField(60);
        field2.setEditable(false);
        if (default2 != null) {
            field2.setText(default2.getAbsolutePath());
        }
        configureFileDrop(field2, parentFrame);

        JButton button2 = new JButton(new ImageIcon(getClass().getResource("/org/jd/gui/images/open.png")));
        button2.addActionListener(e -> {
            if (fileChooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
                File chosen = fileChooser.getSelectedFile();
                if (!isArchiveFile(chosen)) {
                    JOptionPane.showMessageDialog(parentFrame, "Please choose a .jar, .zip, .war, or .ear file.", "Invalid File Type", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                field2.setText(chosen.getAbsolutePath());
            }
        });
        filePanel2.add(field2, BorderLayout.CENTER);
        filePanel2.add(button2, BorderLayout.EAST);

        // Add both panels
        panel.add(filePanel1);
        panel.add(filePanel2);

        // Show dialog
        int result = JOptionPane.showConfirmDialog(
                parentFrame,
                panel,
                "Select files to compare - Drag and drop",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            File file1 = field1.getText().isEmpty() ? null : new File(field1.getText());
            File file2 = field2.getText().isEmpty() ? null : new File(field2.getText());

            if (file1 == null || !file1.exists() || !file1.canRead() || !isArchiveFile(file1)) {
                JOptionPane.showMessageDialog(parentFrame, "First file is invalid. Please select a readable .jar, .zip, .war, or .ear file.", "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            if (file2 == null || !file2.exists() || !file2.canRead() || !isArchiveFile(file2)) {
                JOptionPane.showMessageDialog(parentFrame, "Second file is invalid. Please select a readable .jar, .zip, .war, or .ear file.", "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            if (file1.equals(file2)) {
                JOptionPane.showMessageDialog(parentFrame, "The two files are the same. Please select different files.", "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return new File[]{file1, file2};
        }

        return null;
    }

    /**
     * Configure drag and drop support for a text field to accept a single archive file.
     *
     * @param field       the text field to configure
     * @param parentFrame the parent frame used for error dialogs
     */
    private void configureFileDrop(JTextField field, JFrame parentFrame) {
        field.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }
                support.setDropAction(COPY);
                return true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (dropped == null || dropped.isEmpty()) {
                        return false;
                    }
                    File f = dropped.get(0);
                    if (!f.isFile()) {
                        JOptionPane.showMessageDialog(parentFrame, "Please drop a file, not a directory.", "Invalid Drop", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    if (!isArchiveFile(f)) {
                        JOptionPane.showMessageDialog(parentFrame, "Please drop a .jar, .zip, .war, or .ear file.", "Invalid File Type", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    field.setText(f.getAbsolutePath());
                    return true;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parentFrame, "Could not import the dropped file.", "Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
        field.setFocusable(true);
    }

    /**
     * Check if a file has an allowed archive extension.
     *
     * @param f file to check
     * @return true if extension is jar, zip, war, or ear
     */
    private boolean isArchiveFile(File f) {
        if (f == null) {
            return false;
        }
        String name = f.getName().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".war") || name.endsWith(".ear");
    }

    private Color computeAlternateColor() {
        return api.isDarkMode() ? DARK_GRAY : YELLOW;
    }
}
