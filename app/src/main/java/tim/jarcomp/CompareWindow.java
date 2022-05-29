package tim.jarcomp;

import org.apache.commons.io.IOUtils;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.core.v1.util.StringConstants;
import org.jd.gui.api.API;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.loader.LoaderUtils;
import org.jd.gui.util.loader.ZipLoader;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.HighlighterFactory;
import org.netbeans.modules.editor.java.JavaKit;
import org.oxbow.swingbits.list.CheckListRenderer;
import org.oxbow.swingbits.table.filter.TableRowFilterSupport;

import com.heliosdecompiler.transformerapi.StandardTransformers;
import com.heliosdecompiler.transformerapi.TransformationException;
import com.heliosdecompiler.transformerapi.common.Loader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
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
                    c.setBackground(isChange ? YELLOW : getBackground());
                }
                return c;
            }
        };
        table.setColumnControlVisible(true);
        table.setHighlighters(HighlighterFactory.createSimpleStriping());
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

    protected String getContent(File file, String entryPath) throws IOException, TransformationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (entryPath.endsWith(StringConstants.CLASS_FILE_SUFFIX)) {
            Map<String, String> preferences = api.getPreferences();
            preferences.put(Preferences.WRITE_LINE_NUMBERS, "false");
            preferences.put(Preferences.REALIGN_LINE_NUMBERS, "false");
            ZipLoader zipLoader = new ZipLoader(file);
            String decompileEngine = preferences.getOrDefault(DECOMPILE_ENGINE, ENGINE_JD_CORE_V1);
            Loader apiLoader = LoaderUtils.createLoader(preferences, zipLoader, file.toURI());
            String entryInternalName = ClassUtil.getInternalName(entryPath);
            DecompilationResult decompilationResult = StandardTransformers.decompile(apiLoader, entryInternalName, preferences, decompileEngine);
            return decompilationResult.getDecompiledOutput();
        }
        try (ZipFile zipFile = new ZipFile(file); InputStream in = zipFile.getInputStream(zipFile.getEntry(entryPath))) {
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

        File file1 = inFile1;
        File file2 = inFile2;
        if (file1 == null || !file1.exists() || !file1.canRead()) {
            file1 = selectFile("Select first file", null);
        }
        // Bail if cancel pressed
        if (file1 == null) {
            return;
        }
        // Select second file if necessary
        if (file2 == null || !file2.exists() || !file2.canRead()) {
            file2 = selectFile("Select second file", file1);
        }
        // Bail if cancel pressed
        if (file2 == null) {
            return;
        }
        files[0] = file1;
        files[1] = file2;

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
        // System.out.println(_files[0].getName() + " has " + results.getNumFiles(0) + "
        // files, "
        // + _files[1].getName() + " has " + results.getNumFiles(1));
        if (results.getEntriesDifferent()) {
            statusLabel2.setText((archivesDifferent ? "and" : "but") + " the files have different contents");
        } else {
            if (results.isEntriesCRCChecked()) {
                statusLabel2.setText((archivesDifferent ? "but" : "and") + " the files have exactly the same contents");
            } else {
                statusLabel2.setText((archivesDifferent ? "but" : "and") + " the files appear to have the same contents");
            }
        }
        refreshButton.setEnabled(true);
        // Possibilities:
        // Jars have same size, same CRC checksum, same contents
        // Jars have same size but different CRC checksum, different contents
        // Jars have different size, different CRC checksum, but same contents
        // Individual files have same size but different CRC checksum
        // Jars have absolutely nothing in common
    }

    /**
     * Select a file for the comparison
     * 
     * @param inTitle     title of dialog
     * @param inFirstFile File to compare selected file with (or null)
     * @return selected File, or null if cancelled
     */
    private File selectFile(String inTitle, File inFirstFile) {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new GenericFileFilter("Jar files and Zip files", new String[] { "jar", "zip" }));
        }
        fileChooser.setDialogTitle(inTitle);
        File file = null;
        boolean rechoose = true;
        while (rechoose) {
            file = null;
            rechoose = false;
            int result = fileChooser.showOpenDialog(mainWindow);
            if (result == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();
                rechoose = (!file.exists() || !file.canRead());
            }
            // Check it's not the same as the first file, if any
            if (inFirstFile != null && file != null && file.equals(inFirstFile)) {
                JOptionPane.showMessageDialog(mainWindow,
                        "The second file is the same as the first file!\n" + "Please select another file to compare with '" + inFirstFile.getName() + "'", "Two files equal",
                        JOptionPane.ERROR_MESSAGE);
                rechoose = true;
            }
        }
        return file;
    }
}
