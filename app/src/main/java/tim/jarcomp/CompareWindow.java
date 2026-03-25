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

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.api.model.ArchiveFormat;
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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
    private static final String ERROR_TITLE = "Error";
    private static final ArchiveFormat[] COMPARE_ARCHIVE_FORMATS = {
        ArchiveFormat.JAR,
        ArchiveFormat.ZIP,
        ArchiveFormat.WAR,
        ArchiveFormat.EAR,
        ArchiveFormat.TAR_GZ,
        ArchiveFormat.TAR_XZ,
        ArchiveFormat.TAR_BZ2,
        ArchiveFormat.SEVEN_ZIP
    };

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

            // Create a loader using ArchiveReader to support all archive formats
            try (ArchiveReader reader = new ArchiveReader(file)) {
                ArchiveReaderLoader archiveLoader = new ArchiveReaderLoader(reader);
                String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
                Loader apiLoader = LoaderUtils.createLoader(preferences, archiveLoader, file.toURI());
                String entryInternalName = ClassUtil.getInternalName(entryPath);
                DecompilationResult decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
                return decompilationResult.getDecompiledOutput();
            }
        }

        // For non-class files, use ArchiveReader to support all formats
        try (ArchiveReader reader = new ArchiveReader(file)) {
            byte[] content = reader.getEntryContent(entryPath);
            if (content == null) {
                return "";
            }
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Loader implementation that uses ArchiveReader to load class files from any supported archive format
     */
    private static class ArchiveReaderLoader implements org.jd.core.v1.api.loader.Loader {
        private final ArchiveReader reader;

        public ArchiveReaderLoader(ArchiveReader reader) {
            this.reader = reader;
        }

        @Override
        public boolean canLoad(String internalName) {
            try {
                String path = internalName;
                if (!path.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                    path = internalName + StringConstants.CLASS_FILE_SUFFIX;
                }
                return reader.getEntryContent(path).length > 0;
            } catch (IOException _) {
                return false;
            }
        }

        @Override
        public byte[] load(String internalName) throws IOException {
            String path = internalName;
            if (!path.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
                path = internalName + StringConstants.CLASS_FILE_SUFFIX;
            }
            return reader.getEntryContent(path);
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
     * The text fields are wide and accept drag and drop of supported archive files.
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
                    "Archive files",
                    ArchiveFormat.extensionsOf(COMPARE_ARCHIVE_FORMATS)
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
        button1.addActionListener(e -> chooseArchiveFile(parentFrame, field1));
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
        button2.addActionListener(e -> chooseArchiveFile(parentFrame, field2));
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
            File file1 = validateSelectedArchive(parentFrame, field1.getText(), "First");
            File file2 = validateSelectedArchive(parentFrame, field2.getText(), "Second");

            if ((file1 == null) || (file2 == null)) {
                return null;
            }
            if (file1.equals(file2)) {
                JOptionPane.showMessageDialog(parentFrame, "The two files are the same. Please select different files.", ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return new File[]{file1, file2};
        }

        return null;
    }

    private void chooseArchiveFile(JFrame parentFrame, JTextField field) {
        if (fileChooser.showOpenDialog(parentFrame) == JFileChooser.APPROVE_OPTION) {
            File chosen = fileChooser.getSelectedFile();
            if (!isArchiveFile(chosen)) {
                JOptionPane.showMessageDialog(parentFrame, "Please choose a valid archive file.", "Invalid File Type", JOptionPane.ERROR_MESSAGE);
                return;
            }
            field.setText(chosen.getAbsolutePath());
        }
    }

    private File validateSelectedArchive(JFrame parentFrame, String path, String label) {
        File file = path.isEmpty() ? null : new File(path);

        if (file == null || !file.exists() || !file.canRead() || !isArchiveFile(file)) {
            JOptionPane.showMessageDialog(parentFrame, label + " file is invalid. Please select a readable archive file.", ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        return file;
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
                        JOptionPane.showMessageDialog(parentFrame, "Please drop a supported archive file.", "Invalid File Type", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    field.setText(f.getAbsolutePath());
                    return true;
                } catch (Exception _) {
                    JOptionPane.showMessageDialog(parentFrame, "Could not import the dropped file.", ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
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
     * @return true if the file matches one of the supported archive formats
     */
    private boolean isArchiveFile(File f) {
        return f != null && ArchiveFormat.matchesAny(f.getName(), COMPARE_ARCHIVE_FORMATS);
    }

    private Color computeAlternateColor() {
        return api.isDarkMode() ? DARK_GRAY : YELLOW;
    }
}
