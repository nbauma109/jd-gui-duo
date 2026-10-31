/*
 * © 2008-2026 Emmanuel Dupuy
 * © 2021-2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.Constants;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.ContentCopyable;
import org.jd.gui.api.feature.ContentSavable;
import org.jd.gui.api.feature.ContentSearchable;
import org.jd.gui.api.feature.ContentSelectable;
import org.jd.gui.api.feature.FocusedTypeGettable;
import org.jd.gui.api.feature.LineNumberNavigable;
import org.jd.gui.api.feature.PageChangeListener;
import org.jd.gui.api.feature.PageClosable;
import org.jd.gui.api.feature.PreferencesChangeListener;
import org.jd.gui.api.feature.SourcesSavable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.model.history.History;
import org.jd.gui.util.CustomMultiResolutionImage;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.KeyBindings;
import org.jd.gui.util.KeyBindings.Binding;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.util.swing.SwingUtil;
import org.jd.gui.view.component.DynamicPage;
import org.jd.gui.view.component.BreakpointListPanel;
import org.jd.gui.view.component.DebugValueTree;
import org.jd.gui.view.component.IconButton;
import org.jd.gui.view.component.LinelessTree;
import org.jd.gui.view.component.ShadowPopupPanel;
import org.jd.gui.view.component.panel.MainTabbedPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URI;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeSelectionModel;

import static org.jd.gui.util.ImageUtil.newImageIcon;
import static org.jd.gui.util.swing.SwingUtil.invokeLater;
import static org.jd.gui.util.swing.SwingUtil.newAction;

import de.cismet.custom.visualdiff.PlatformService;

import org.jd.gui.model.debug.DebugVariableNode;

@SuppressWarnings("unchecked")
public class MainView<T extends JComponent & UriGettable> implements UriOpenable, PreferencesChangeListener {

    private static final String JAVA_DECOMPILER = "Java Decompiler";
    private static final String EXIT_ACTION_KEY = "MainView.exit";
    private final History history;
    private final Consumer<File> openFilesCallback;
    private static final String BASE_DESCRIPTION_KEY = "baseShortDescription";
    private final EnumMap<Binding, Action> keyBindingActions = new EnumMap<>(Binding.class);
    private JFrame mainFrame;
    private final JMenu recentFiles = new JMenu("Recent Files");
    private Action closeAction;
    private Action exitAction;
    private Action openTypeAction;
    private Action quickOutlineAction;
    private Action backwardAction;
    private Action forwardAction;
    private Action disconnectAction;
    private Action resumeAction;
    private Action stepIntoAction;
    private Action stepOverAction;
    private Action stepReturnAction;
    private Action toggleHighlightAction;
    @SuppressWarnings("all")
    private MainTabbedPanel mainTabbedPanel;
    private Box findPanel;
    private JComboBox<String> findComboBox;
    private SearchUIOptions searchUIOptions;
    private Color findBackgroundColor;
    private Color findErrorBackgroundColor;
    private JTextField findEditor;
    private AbstractButton findNextButton;
    private AbstractButton findPreviousButton;
    private JPanel debugVariablesPanel;
    private JTabbedPane debugTabbedPane;
    private JTree debugVariablesTree;
    private DefaultTreeModel debugVariablesTreeModel;
    private BreakpointListPanel breakpointListPanel;
    private Consumer<String> breakpointNavigateCallback;
    private Consumer<Boolean> skipAllBreakpointsCallback;
    private Runnable addExceptionBreakpointCallback;
    private Consumer<String> removeBreakpointCallback;
    private Runnable removeAllBreakpointsCallback;
    private Runnable toggleBreakpointCallback;
    private Runnable dropToFrameCallback;
    private Runnable runToLineCallback;
    private Runnable inspectCallback;
    private Action inspectAction;
    private java.util.function.Function<DebugVariableNode.Kind, Icon> debugVariableIcons;
    private Action runToLineAction;
    private Action dropToFrameAction;
    private java.util.function.BiConsumer<String, Boolean> breakpointEnabledCallback;
    private JTree debugExpressionsTree;
    private DefaultTreeModel debugExpressionsTreeModel;
    private Consumer<String> addExpressionCallback;
    private Consumer<String> removeExpressionCallback;
    private Runnable removeAllExpressionsCallback;
    private Runnable findNextShortcutAction;
    private Runnable findPreviousShortcutAction;
    private KeyStroke installedExitKeyStroke;

    static {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Icon icon = UIManager.getIcon(key);
            if (icon instanceof ImageIcon imageIcon) {
                UIManager.put(key, new ImageIcon(new CustomMultiResolutionImage(imageIcon.getImage())));
            }
        }
    }

    public MainView(
            Configuration configuration, API api, History history,
            ActionListener openActionListener,
            ActionListener compareActionListener,
            ActionListener compareJDActionListener,
            ActionListener closeActionListener,
            ActionListener saveActionListener,
            ActionListener saveAllSourcesActionListener,
            ActionListener exitActionListener,
            ActionListener copyActionListener,
            ActionListener pasteActionListener,
            ActionListener selectAllActionListener,
            ActionListener findActionListener,
            ActionListener findPreviousActionListener,
            ActionListener findNextActionListener,
            ActionListener findWithOptionsActionListener,
            Runnable findCriteriaChangedCallback,
            ActionListener openTypeActionListener,
            ActionListener openTypeHierarchyActionListener,
            ActionListener quickOutlineActionListener,
            ActionListener goToActionListener,
            ActionListener backwardActionListener,
            ActionListener forwardActionListener,
            ActionListener remoteDebugActionListener,
            ActionListener disconnectActionListener,
            ActionListener resumeActionListener,
            ActionListener stepIntoActionListener,
            ActionListener stepOverActionListener,
            ActionListener stepReturnActionListener,
            ActionListener searchActionListener,
            ActionListener jdWebSiteActionListener,
            ActionListener jdGuiIssuesActionListener,
            ActionListener jdCoreIssuesActionListener,
            ActionListener keyBindingsActionListener,
            ActionListener preferencesActionListener,
            ActionListener securedPreferencesActionListener,
            ActionListener eclipsePreferencesActionListener,
            ActionListener mavenCentralHelperActionListener,
            Consumer<Boolean> toggleHighlightChangedCallback,
            ActionListener aboutActionListener,
            Runnable panelClosedCallback,
            Consumer<T> currentPageChangedCallback,
            Consumer<File> openFilesCallback) {
        this.history = history;
        this.openFilesCallback = openFilesCallback;
        // Build GUI
        releaseDebugFunctionKeys();
        SwingUtil.invokeLater(() -> {
            mainFrame = new JFrame(JAVA_DECOMPILER);
            ImageUtil.addJDIconsToFrame(mainFrame);
            mainFrame.setMinimumSize(new Dimension(Constants.MINIMAL_WIDTH, Constants.MINIMAL_HEIGHT));
            mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            // Find panel //
            Action findNextAction = newAction(SearchUIOptions.getFindNextLabel(), newImageIcon("/org/jd/gui/images/next_nav.png"), true, findNextActionListener);
            findPanel = Box.createHorizontalBox();
            findPanel.setVisible(false);
            findPanel.add(new JLabel("Find: "));
            findComboBox = new JComboBox<>();
            findComboBox.setEditable(true);
            findEditor = getFindEditor();
            findEditor.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        findPanel.setVisible(false);
                    }
                }
            });
            SearchUIOptions.configureIncrementalSearchField(findEditor, configuration.getPreferences());
            SearchUIOptions.installCriteriaListener(findEditor, findCriteriaChangedCallback);
            findEditor.setOpaque(true);
            this.findBackgroundColor = findEditor.getBackground();
            findComboBox.setBackground(this.findBackgroundColor);
            this.findErrorBackgroundColor = Color.decode(configuration.getPreferences().get(GuiPreferences.ERROR_BACKGROUND_COLOR));

            findPanel.add(findComboBox);
            findPanel.add(Box.createHorizontalStrut(5));
            JToolBar toolBar = new JToolBar();
            toolBar.setFloatable(false);
            toolBar.setRollover(true);

            findNextButton = new IconButton(SearchUIOptions.getFindNextLabel(), newAction(newImageIcon("/org/jd/gui/images/next_nav.png"), true, findNextActionListener));
            SearchUIOptions.configureFindButton(findNextButton, configuration.getPreferences());
            toolBar.add(findNextButton);

            toolBar.add(Box.createHorizontalStrut(5));

            findPreviousButton = new IconButton(SearchUIOptions.getFindPreviousLabel(), newAction(newImageIcon("/org/jd/gui/images/prev_nav.png"), true, findPreviousActionListener));
            SearchUIOptions.configureFindButton(findPreviousButton, configuration.getPreferences());
            toolBar.add(findPreviousButton);

            findPanel.add(toolBar);

            searchUIOptions = new SearchUIOptions(configuration.getPreferences(), findWithOptionsActionListener);
            searchUIOptions.attachTo(toolBar);
            searchUIOptions.bindOptionKeyStrokes(findEditor, configuration.getPreferences());

            findNextShortcutAction = () -> {
                String str = getFindText();
                if (str.length() > 1) {
                    int index = ((DefaultComboBoxModel<String>) findComboBox.getModel()).getIndexOf(str);
                    if (index != -1) {
                        findComboBox.removeItemAt(index);
                    }
                    findComboBox.insertItemAt(str, 0);
                    findComboBox.setSelectedIndex(0);
                    findNextAction.actionPerformed(null);
                }
            };
            findPreviousShortcutAction = () -> findPreviousActionListener.actionPerformed(null);
            SearchUIOptions.bindSearchNavigationKeyStrokes(findEditor, configuration.getPreferences(), findNextShortcutAction, findPreviousShortcutAction);

            findPanel.add(Box.createHorizontalGlue());

            IconButton findCloseButton = new IconButton(newAction(null, null, true, e -> findPanel.setVisible(false)));
            findCloseButton.setContentAreaFilled(false);
            findCloseButton.setIcon(newImageIcon("/org/jd/gui/images/close.gif"));
            findCloseButton.setRolloverIcon(newImageIcon("/org/jd/gui/images/close_active.gif"));
            findPanel.add(findCloseButton);

            if (PlatformService.getInstance().isMac()) {
                findPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
                Border border = BorderFactory.createEmptyBorder();
                findNextButton.setBorder(border);
                findPreviousButton.setBorder(border);
                findCloseButton.setBorder(border);
            } else {
                findPanel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 2));
            }

            // Actions //
            boolean browser = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
            Action openAction = newAction("Open File...", newImageIcon("/org/jd/gui/images/open.png"), true, "Open a file", openActionListener);
            Action compareAction = newAction("Compare Files...", newImageIcon("/org/jd/gui/images/copy.png"), true, compareActionListener);
            Action compareJDAction = newAction("Compare JD-Core V0 vs V1...", false, compareJDActionListener);
            closeAction = newAction("Close", false, closeActionListener);
            Action saveAction = newAction("Save", newImageIcon("/org/jd/gui/images/save.png"), false, saveActionListener);
            Action saveAllSourcesAction = newAction("Save All Sources", newImageIcon("/org/jd/gui/images/save_all.png"), false, saveAllSourcesActionListener);
            exitAction = newAction("Exit", true, "Quit this program", exitActionListener);
            Action copyAction = newAction("Copy", newImageIcon("/org/jd/gui/images/copy.png"), false, copyActionListener);
            Action pasteAction = newAction("Paste Log", newImageIcon("/org/jd/gui/images/paste.png"), true, pasteActionListener);
            Action selectAllAction = newAction("Select all", false, selectAllActionListener);
            Action findAction = newAction("Find...", false, findActionListener);
            openTypeAction = newAction("Open Type...", newImageIcon("/org/jd/gui/images/open_type.png"), false, openTypeActionListener);
            Action openTypeHierarchyAction = newAction("Open Type Hierarchy...", false, openTypeHierarchyActionListener);
            quickOutlineAction = newAction("Quick Outline...", false, quickOutlineActionListener);
            Action goToAction = newAction("Go to Line...", false, goToActionListener);
            backwardAction = newAction("Back", newImageIcon("/org/jd/gui/images/backward_nav.png"), false, backwardActionListener);
            forwardAction = newAction("Forward", newImageIcon("/org/jd/gui/images/forward_nav.png"), false, forwardActionListener);
            Action remoteDebugAction = newAction("Remote Debug...", newImageIcon("/org/jd/gui/images/bug_exc.png"), true, "Attach to a remote JVM debugger", remoteDebugActionListener);
            disconnectAction = newAction("Disconnect", newImageIcon("/org/jd/gui/images/disconnect_co.png"), false, "Disconnect from remote JVM", disconnectActionListener);
            resumeAction = newAction("Resume", newImageIcon("/org/jd/gui/images/resume_co.png"), false, "Resume execution", resumeActionListener);
            stepIntoAction = newAction("Step Into", newImageIcon("/org/jd/gui/images/stepinto_co.png"), false, "Step into", stepIntoActionListener);
            stepOverAction = newAction("Step Over", newImageIcon("/org/jd/gui/images/stepover_co.png"), false, "Step over", stepOverActionListener);
            stepReturnAction = newAction("Step Return", newImageIcon("/org/jd/gui/images/stepreturn_co.png"), false, "Step return", stepReturnActionListener);
            inspectAction = newAction("Inspect", newImageIcon("/org/jd/gui/images/insp_sbook.png"), false, "Inspect the selected expression",
                    e -> { if (inspectCallback != null) { inspectCallback.run(); } });
            runToLineAction = newAction("Run to Line", newImageIcon("/org/jd/gui/images/runtoline_co.png"), false, "Run to the caret line",
                    e -> { if (runToLineCallback != null) { runToLineCallback.run(); } });
            dropToFrameAction = newAction("Drop to Frame", newImageIcon("/org/jd/gui/images/drop_to_frame.png"), false, "Drop to the selected stack frame",
                    e -> { if (dropToFrameCallback != null) { dropToFrameCallback.run(); } });
            Action toggleBreakpointAction = newAction("Toggle Breakpoint", newImageIcon("/org/jd/gui/images/brkp_obj.png"), true, "Toggle breakpoint on the caret line",
                    e -> { if (toggleBreakpointCallback != null) { toggleBreakpointCallback.run(); } });
            Action searchAction = newAction("Search...", newImageIcon("/org/jd/gui/images/search_src.png"), false, searchActionListener);
            Action jdWebSiteAction = newAction("JD Web site", newImageIcon("/org/jd/gui/images/github.png"), browser, "Open JD Web site", jdWebSiteActionListener);
            Action jdGuiIssuesActionAction = newAction("JD-GUI issues", browser, "Open JD-GUI issues page", jdGuiIssuesActionListener);
            Action jdCoreIssuesActionAction = newAction("JD-Core issues", browser, "Open JD-Core issues page", jdCoreIssuesActionListener);
            Action keyBindingsAction = newAction("Key Bindings...", newImageIcon("/org/jd/gui/images/key_bindings.png"), true, "Open the key bindings window", keyBindingsActionListener);
            Action preferencesAction = newAction("Preferences...", newImageIcon("/org/jd/gui/images/preferences.png"), true, "Open the preferences panel",
                    preferencesActionListener);
            Action securedPreferencesAction = newAction("Secured Preferences...", newImageIcon("/org/jd/gui/images/secured_preferences.png"), true, "Open the secured preferences panel",
                    securedPreferencesActionListener);
            Action eclipsePreferencesAction = newAction("Eclipse Preferences...", newImageIcon("/org/jd/gui/images/eclipse.png"), true, "Open the Eclipse preferences panel",
                    eclipsePreferencesActionListener);
            Action mavenCentralHelperAction = newAction("Search maven central...", newImageIcon("/org/jd/gui/images/search_src.png"), true, "Search maven central",
            		mavenCentralHelperActionListener);
            toggleHighlightAction = new AbstractAction("Toggle Highlight", newImageIcon("/org/jd/gui/images/mark_occurrences.png")) {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    boolean selected = e.getSource() instanceof AbstractButton button
                        ? button.isSelected()
                        : !Boolean.TRUE.equals(getValue(Action.SELECTED_KEY));
                    putValue(Action.SELECTED_KEY, selected);
                    toggleHighlightChangedCallback.accept(selected);
                }
            };
            toggleHighlightAction.putValue(Action.SHORT_DESCRIPTION, "Toggle selected word highlight");
            toggleHighlightAction.putValue(Action.SELECTED_KEY, isSelectedWordHighlightEnabled(configuration.getPreferences()));
            Action aboutAction = newAction("About...", true, "About JD-GUI", aboutActionListener);

            registerKeyBinding(Binding.OPEN_FILE, openAction);
            registerKeyBinding(Binding.CLOSE, closeAction);
            registerKeyBinding(Binding.SAVE, saveAction);
            registerKeyBinding(Binding.SAVE_ALL_SOURCES, saveAllSourcesAction);
            registerKeyBinding(Binding.EXIT, exitAction);
            registerKeyBinding(Binding.COPY, copyAction);
            registerKeyBinding(Binding.PASTE_LOG, pasteAction);
            registerKeyBinding(Binding.SELECT_ALL, selectAllAction);
            registerKeyBinding(Binding.FIND, findAction);
            registerKeyBinding(Binding.OPEN_TYPE, openTypeAction);
            registerKeyBinding(Binding.OPEN_TYPE_HIERARCHY, openTypeHierarchyAction);
            registerKeyBinding(Binding.QUICK_OUTLINE, quickOutlineAction);
            registerKeyBinding(Binding.GO_TO_LINE, goToAction);
            registerKeyBinding(Binding.BACK, backwardAction);
            registerKeyBinding(Binding.FORWARD, forwardAction);
            registerKeyBinding(Binding.REMOTE_DEBUG, remoteDebugAction);
            registerKeyBinding(Binding.DISCONNECT, disconnectAction);
            registerKeyBinding(Binding.RESUME, resumeAction);
            registerKeyBinding(Binding.TOGGLE_BREAKPOINT, toggleBreakpointAction);
            registerKeyBinding(Binding.INSPECT, inspectAction);
            registerKeyBinding(Binding.RUN_TO_LINE, runToLineAction);
            registerKeyBinding(Binding.DROP_TO_FRAME, dropToFrameAction);
            registerKeyBinding(Binding.STEP_INTO, stepIntoAction);
            registerKeyBinding(Binding.STEP_OVER, stepOverAction);
            registerKeyBinding(Binding.STEP_RETURN, stepReturnAction);
            registerKeyBinding(Binding.SEARCH, searchAction);
            registerKeyBinding(Binding.PREFERENCES, preferencesAction);
            registerKeyBinding(Binding.ABOUT, aboutAction);
            applyConfiguredKeyBindings(configuration.getPreferences());
            updateExitShortcutBinding(configuration.getPreferences());

            // Menu //
            JMenuBar menuBar = new JMenuBar();
            JMenu menu = new JMenu("File");
            menuBar.add(menu);
            menu.add(openAction);
            menu.add(compareAction);
            menu.add(compareJDAction);
            menu.addSeparator();
            menu.add(closeAction);
            menu.addSeparator();
            menu.add(saveAction);
            menu.add(saveAllSourcesAction);
            menu.addSeparator();
            recentFiles.addMenuListener(new MenuListener() {
                @Override
                public void menuSelected(MenuEvent e) {
                    refreshRecentFilesCheckmarks();
                }

                @Override
                public void menuDeselected(MenuEvent e) {
                    // checkmarks only need refreshing on open
                }

                @Override
                public void menuCanceled(MenuEvent e) {
                    // checkmarks only need refreshing on open
                }
            });
            menu.add(recentFiles);
            if (!PlatformService.getInstance().isMac()) {
                menu.addSeparator();
                menu.add(exitAction);
            }
            menu = new JMenu("Edit");
            menuBar.add(menu);
            menu.add(copyAction);
            menu.add(pasteAction);
            menu.addSeparator();
            menu.add(selectAllAction);
            menu.addSeparator();
            menu.add(findAction);
            menu = new JMenu("Navigation");
            menuBar.add(menu);
            menu.add(openTypeAction);
            menu.add(openTypeHierarchyAction);
            menu.addSeparator();
            menu.add(quickOutlineAction);
            menu.addSeparator();
            menu.add(goToAction);
            menu.addSeparator();
            menu.add(backwardAction);
            menu.add(forwardAction);
            menu = new JMenu("Search");
            menuBar.add(menu);
            menu.add(searchAction);
            menu.add(new JCheckBoxMenuItem(toggleHighlightAction));
            menu = new JMenu("Debug");
            menuBar.add(menu);
            menu.add(remoteDebugAction);
            menu.addSeparator();
            menu.add(resumeAction);
            menu.add(disconnectAction);
            menu.addSeparator();
            menu.add(stepIntoAction);
            menu.add(stepOverAction);
            menu.add(stepReturnAction);
            menu.add(dropToFrameAction);
            menu.add(runToLineAction);
            menu.addSeparator();
            menu.add(toggleBreakpointAction);
            menu.add(inspectAction);
            menu = new JMenu("Help");
            menuBar.add(menu);
            if (browser) {
                menu.add(jdWebSiteAction);
                menu.add(jdGuiIssuesActionAction);
                menu.add(jdCoreIssuesActionAction);
                menu.addSeparator();
            }
            menu.add(keyBindingsAction);
            menu.add(preferencesAction);
            menu.add(securedPreferencesAction);
            menu.add(eclipsePreferencesAction);
            menu.add(mavenCentralHelperAction);
            menu.addSeparator();
            menu.add(aboutAction);
            mainFrame.setJMenuBar(menuBar);

            // Icon bar //
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            toolBar = new JToolBar();
            toolBar.setFloatable(false);
            toolBar.setRollover(true);
            toolBar.add(new IconButton(openAction));
            toolBar.addSeparator();
            toolBar.add(new IconButton(openTypeAction));
            toolBar.add(new IconButton(searchAction));
            toolBar.add(new IconButton(remoteDebugAction));
            toolBar.add(new IconButton(disconnectAction));
            toolBar.add(new IconButton(resumeAction));
            toolBar.add(new IconButton(stepIntoAction));
            toolBar.add(new IconButton(stepOverAction));
            toolBar.add(new IconButton(stepReturnAction));
            toolBar.add(new IconButton(dropToFrameAction));
            toolBar.add(new IconButton(runToLineAction));
            toolBar.add(newToolbarToggleButton(toggleHighlightAction));
            toolBar.addSeparator();
            toolBar.add(new IconButton(backwardAction));
            toolBar.add(new IconButton(forwardAction));
            panel.add(toolBar, BorderLayout.PAGE_START);

            mainTabbedPanel = new MainTabbedPanel<>(api);
            mainTabbedPanel.getPageChangedListeners().add(new PageChangeListener() {
                protected JComponent currentPage;

                @Override
                public <U extends JComponent & UriGettable> void pageChanged(U page) {
                    if (currentPage != page) {
                        // Update current page
                        currentPage = page;
                        currentPageChangedCallback.accept((T) page);

                        invokeLater(() -> {
                            if (page == null) {
                                // Update title
                                mainFrame.setTitle(JAVA_DECOMPILER);
                                // Update menu
                                saveAction.setEnabled(false);
                                copyAction.setEnabled(false);
                                selectAllAction.setEnabled(false);
                                openTypeHierarchyAction.setEnabled(false);
                                quickOutlineAction.setEnabled(false);
                                goToAction.setEnabled(false);
                                // Update find panel
                                findPanel.setVisible(false);
                            } else {
                                // Update title
                                String path = page.getUri().getPath();
                                int index = path.lastIndexOf('/');
                                String name = index == -1 ? path : path.substring(index + 1);
                                mainFrame.setTitle(name != null ? name + " - Java Decompiler" : JAVA_DECOMPILER);
                                // Update history
                                history.add(page.getUri());
                                // Update history actions
                                updateHistoryActions();
                                // Update menu
                                saveAction.setEnabled(page instanceof ContentSavable);
                                compareJDAction.setEnabled(page instanceof DynamicPage dp && api.getSource(dp.getEntry()) == null);
                                copyAction.setEnabled(page instanceof ContentCopyable);
                                selectAllAction.setEnabled(page instanceof ContentSelectable);
                                findAction.setEnabled(page instanceof ContentSearchable);
                                openTypeHierarchyAction.setEnabled(page instanceof FocusedTypeGettable);
                                quickOutlineAction.setEnabled(page instanceof FocusedTypeGettable);
                                goToAction.setEnabled(page instanceof LineNumberNavigable);
                                // Update find panel
                                if (findPanel.isVisible()) {
                                    findPanel.setVisible(page instanceof ContentSearchable);
                                }
                            }
                        });
                    }
                }
            });
            mainTabbedPanel.getTabbedPane().addChangeListener(new ChangeListener() {
                protected int lastTabCount;

                @Override
                public void stateChanged(ChangeEvent e) {
                    int tabCount = mainTabbedPanel.getTabbedPane().getTabCount();
                    boolean enabled = tabCount > 0;

                    closeAction.setEnabled(enabled);
                    openTypeAction.setEnabled(enabled);
                    searchAction.setEnabled(enabled);
                    saveAllSourcesAction.setEnabled(mainTabbedPanel.getTabbedPane().getSelectedComponent() instanceof SourcesSavable);

                    if (tabCount < lastTabCount) {
                        panelClosedCallback.run();
                    }

                    lastTabCount = tabCount;
                }
            });
            mainTabbedPanel.preferencesChanged(configuration.getPreferences());
            panel.add(mainTabbedPanel, BorderLayout.CENTER);
            panel.add(createDebugVariablesPanel(), BorderLayout.LINE_END);

            panel.add(findPanel, BorderLayout.PAGE_END);
            mainFrame.add(panel);
        });
    }

    public void show(Point location, Dimension size, boolean maximize) {
        invokeLater(() -> {
            // Set position, resize and show
            mainFrame.setLocation(location);
            mainFrame.setSize(size);
            mainFrame.setExtendedState(maximize ? Frame.MAXIMIZED_BOTH : 0);
            mainFrame.setVisible(true);
        });
    }

    public JFrame getMainFrame() {
        return mainFrame;
    }

    public void showFindPanel() {
        showFindPanel(null);
    }

    public void showFindPanel(String initialText) {
        invokeLater(() -> {
            JTextField editor = getFindEditor();
            findPanel.setVisible(true);
            if (initialText != null) {
                editor.setText(initialText);
            }
            editor.requestFocusInWindow();
            editor.selectAll();
        });
    }

    public void setFindBackgroundColor(boolean wasFound) {
        invokeLater(() -> findComboBox.getEditor().getEditorComponent().setBackground(wasFound ? findBackgroundColor : findErrorBackgroundColor));
    }

    public void addMainPanel(String title, Supplier<Icon> iconSupplier, String tip, T component) {
        invokeLater(() -> mainTabbedPanel.addPage(title, iconSupplier, tip, component));
    }

    public List<T> getMainPanels() {
        return mainTabbedPanel.getPages();
    }

    public T getSelectedMainPanel() {
        return (T) mainTabbedPanel.getTabbedPane().getSelectedComponent();
    }

    public void closeCurrentTab() {
        invokeLater(() -> {
            Component component = mainTabbedPanel.getTabbedPane().getSelectedComponent();
            if (component instanceof PageClosable pc) {
                if (!pc.closePage()) {
                    mainTabbedPanel.removeComponent(component);
                }
            } else {
                mainTabbedPanel.removeComponent(component);
            }
        });
    }

    public void updateRecentFilesMenu(List<File> files) {
        invokeLater(() -> {
            recentFiles.removeAll();

            Set<URI> openUris = new HashSet<>();
            for (T panel : getMainPanels()) {
                URI uri = panel.getUri();
                if (uri != null) {
                    openUris.add(uri);
                }
            }

            for (File file : files) {
                JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(reduceRecentFilePath(file.getAbsolutePath()));
                menuItem.putClientProperty("recentFile", file);
                menuItem.setSelected(openUris.contains(file.toURI()));
                menuItem.addActionListener(e -> openFilesCallback.accept(file));
                recentFiles.add(menuItem);
            }
        });
    }

    private void refreshRecentFilesCheckmarks() {
        Set<URI> openUris = new HashSet<>();
        for (T panel : getMainPanels()) {
            URI uri = panel.getUri();
            if (uri != null) {
                openUris.add(uri);
            }
        }
        for (Component comp : recentFiles.getMenuComponents()) {
            if (comp instanceof JCheckBoxMenuItem item) {
                File f = (File) item.getClientProperty("recentFile");
                if (f != null) {
                    item.setSelected(openUris.contains(f.toURI()));
                }
            }
        }
    }

    public String getFindText() {
        Document doc = getFindEditor().getDocument();

        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            assert ExceptionUtil.printStackTrace(e);
            return "";
        }
    }

    public ContentSearchable.SearchType getSearchType() {
        return new ContentSearchable.SearchType(
            searchUIOptions.isMatchCaseButtonSelected(),
            searchUIOptions.isWholeWordButtonSelected(),
            searchUIOptions.isRegexButtonSelected(),
            searchUIOptions.isMarkAllButtonSelected(),
            searchUIOptions.isWrapButtonSelected()
        );
    }

    public void updateHistoryActions() {
        invokeLater(() -> {
            backwardAction.setEnabled(history.canBackward());
            forwardAction.setEnabled(history.canForward());
        });
    }

    public void setDebugStepActionsEnabled(boolean enabled) {
        // Run directly if already on the EDT (called from SwingWorker.done() etc.)
        // to avoid nested invokeLater races with onSuspended's invokeLater.
        Runnable r = () -> {
            if (resumeAction != null) {
                resumeAction.setEnabled(enabled);
                stepIntoAction.setEnabled(enabled);
                stepOverAction.setEnabled(enabled);
                stepReturnAction.setEnabled(enabled);
                dropToFrameAction.setEnabled(enabled);
                runToLineAction.setEnabled(enabled);
                inspectAction.setEnabled(enabled);
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            invokeLater(r);
        }
    }

    public void setDebugConnectedActionsEnabled(boolean enabled) {
        Runnable r = () -> {
            if (disconnectAction != null) {
                disconnectAction.setEnabled(enabled);
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            invokeLater(r);
        }
    }

    public void setRemoveBreakpointCallback(Consumer<String> callback) {
        this.removeBreakpointCallback = callback;
    }

    public void setRemoveAllBreakpointsCallback(Runnable callback) {
        this.removeAllBreakpointsCallback = callback;
    }

    public void setBreakpointNavigateCallback(Consumer<String> callback) {
        this.breakpointNavigateCallback = callback;
    }

    public void setAddExceptionBreakpointCallback(Runnable callback) {
        this.addExceptionBreakpointCallback = callback;
    }

    public void setSkipAllBreakpointsCallback(Consumer<Boolean> callback) {
        this.skipAllBreakpointsCallback = callback;
    }

    public void setInspectCallback(Runnable callback) {
        this.inspectCallback = callback;
    }

    public void selectDebugTab(int index) {
        invokeLater(() -> {
            if (debugTabbedPane != null && index < debugTabbedPane.getTabCount()) {
                debugTabbedPane.setSelectedIndex(index);
            }
        });
    }

    /**
     * Eclipse-style "Inspect" popup: a small rounded panel with the evaluated expression as a one-column tree
     * ({@code "expr"= value}), the full value of the selected row below it and a hint to move it to Expressions.
     */
    public void showInspectPopup(DebugVariableNode node, java.awt.Point screenLocation, Runnable showInExpressions) {
        invokeLater(() -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Inspect");
            DefaultMutableTreeNode top = buildTreeNode(node);
            root.add(top);
            DefaultTreeModel model = new DefaultTreeModel(root);
            JTree tree = createInspectTree(model, root);

            // full value of the selected row
            javax.swing.JTextArea detail = new javax.swing.JTextArea(node.getDetailText());
            detail.setFont(tree.getFont());
            detail.setEditable(false);
            detail.setLineWrap(false);
            detail.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            JScrollPane detailScroll = new JScrollPane(detail);
            detailScroll.setPreferredSize(new Dimension(0, 58));
            detailScroll.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
            detailScroll.getViewport().setBackground(java.awt.Color.WHITE);
            tree.addTreeSelectionListener(e -> {
                if (e.getPath() != null && ((DefaultMutableTreeNode) e.getPath().getLastPathComponent()).getUserObject() instanceof DebugVariableNode selected) {
                    detail.setText(selected.getDetailText());
                    detail.setCaretPosition(0);
                }
            });
            javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, new JScrollPane(tree), detailScroll);
            split.setBorder(null);
            split.setResizeWeight(1.0);
            split.setDividerSize(5);

            javax.swing.KeyStroke inspectKey = inspectAction == null ? null : (javax.swing.KeyStroke) inspectAction.getValue(Action.ACCELERATOR_KEY);
            JPanel header = createInspectHeader();
            JPanel inner = new JPanel(new BorderLayout());
            inner.setOpaque(false);
            inner.add(header, BorderLayout.NORTH);
            inner.add(split, BorderLayout.CENTER);
            inner.add(createInspectHint(inspectKey), BorderLayout.SOUTH);
            ShadowPopupPanel shadowPanel = new ShadowPopupPanel(inner);

            javax.swing.JDialog popup = createInspectDialog(shadowPanel, screenLocation);
            installPopupDrag(header, popup);
            popup.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowLostFocus(java.awt.event.WindowEvent e) {
                    popup.dispose();
                }
            });
            installInspectKeys(shadowPanel.getCard(), popup, inspectKey, showInExpressions);
            popup.setVisible(true);
            popup.toFront();
            tree.setSelectionPath(new javax.swing.tree.TreePath(top.getPath()));
            if (!node.isLeaf()) {
                tree.expandPath(new javax.swing.tree.TreePath(top.getPath())); // objects open one level, as in Eclipse
            }
            tree.requestFocusInWindow();
        });
    }

    private JTree createInspectTree(DefaultTreeModel model, DefaultMutableTreeNode root) {
        JTree tree = new LinelessTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(20);
        tree.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 0));
        tree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getTreeCellRendererComponent(JTree t, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean focus) {
                // Eclipse marks the selected row with a light blue box and a blue outline, text stays black
                setBackgroundSelectionColor(new java.awt.Color(0xCCE4F7));
                setBorderSelectionColor(null); // the look and feel would draw a dotted focus line: use a plain outline instead
                setTextSelectionColor(java.awt.Color.BLACK);
                super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focus);
                setBorder(selected
                    ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new java.awt.Color(0x3399FF)), BorderFactory.createEmptyBorder(0, 2, 0, 2))
                    : BorderFactory.createEmptyBorder(1, 3, 1, 3));
                if (((DefaultMutableTreeNode) value).getUserObject() instanceof DebugVariableNode variable) {
                    boolean topLevel = ((DefaultMutableTreeNode) value).getParent() == root;
                    setText((topLevel ? "\"" + variable.name + "\"" : variable.name) + "= " + variable.valueText);
                    setIcon(topLevel ? newImageIcon("/org/jd/gui/images/insp_obj.png") : debugVariableIcons.apply(variable.kind));
                } else {
                    setIcon(null);
                }
                return this;
            }
        });
        tree.addTreeWillExpandListener(lazyChildrenLoader(model));
        return tree;
    }

    /** Fills a node with the fields or elements of its value when it is expanded for the first time. */
    private TreeWillExpandListener lazyChildrenLoader(DefaultTreeModel model) {
        return new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (treeNode.getUserObject() instanceof DebugVariableNode variable && !variable.isChildrenLoaded()) {
                    loadChildrenAsync(model, treeNode, variable);
                }
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) throws ExpandVetoException { /* no-op */ }
        };
    }

    private static JLabel createInspectHint(javax.swing.KeyStroke inspectKey) {
        JLabel hint = new JLabel(inspectKey == null ? " " : "Press " + KeyBindings.toDisplayText(inspectKey) + " to Move to Expressions View", javax.swing.SwingConstants.RIGHT);
        hint.setForeground(java.awt.Color.GRAY);
        hint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0xD0D0D0)), BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return hint;
    }

    private static JPanel createInspectHeader() {
        JLabel grip = new JLabel("\u22EE ", javax.swing.SwingConstants.RIGHT); // vertical ellipsis: drag handle, as in Eclipse
        grip.setForeground(java.awt.Color.GRAY);
        grip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
        grip.setToolTipText("Drag to move");
        JPanel header = new JPanel(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 20));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0xD0D0D0)));
        header.add(grip, BorderLayout.EAST);
        return header;
    }

    private javax.swing.JDialog createInspectDialog(ShadowPopupPanel shadowPanel, java.awt.Point screenLocation) {
        javax.swing.JDialog popup = new javax.swing.JDialog(mainFrame);
        popup.setUndecorated(true);
        try {
            popup.setBackground(new java.awt.Color(0, 0, 0, 0)); // translucent window: the shadow and rounded corners show through
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException _) {
            // opaque square window where translucency is not supported
        }
        popup.setContentPane(shadowPanel);
        popup.setSize(312, 262);
        java.awt.Rectangle screen = mainFrame.getGraphicsConfiguration().getBounds();
        popup.setLocation(Math.clamp(screenLocation.x, screen.x, Math.max(screen.x, screen.x + screen.width - popup.getWidth())),
                Math.clamp(screenLocation.y + 4, screen.y, Math.max(screen.y, screen.y + screen.height - popup.getHeight())));
        return popup;
    }

    private static void installPopupDrag(JComponent handle, javax.swing.JDialog popup) {
        java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
            private java.awt.Point origin;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                origin = e.getLocationOnScreen();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                java.awt.Point now = e.getLocationOnScreen();
                popup.setLocation(popup.getX() + now.x - origin.x, popup.getY() + now.y - origin.y);
                origin = now;
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
    }

    /** Escape closes the popup, the Inspect shortcut moves the expression to the Expressions view. */
    private static void installInspectKeys(JComponent content, javax.swing.JDialog popup, javax.swing.KeyStroke inspectKey, Runnable showInExpressions) {
        javax.swing.InputMap inputMap = content.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = content.getActionMap();
        inputMap.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        actionMap.put("close", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                popup.dispose();
            }
        });
        if (inspectKey != null) {
            inputMap.put(inspectKey, "watch");
            actionMap.put("watch", new javax.swing.AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    popup.dispose();
                    showInExpressions.run();
                }
            });
        }
    }

    public void setRunToLineCallback(Runnable callback) {
        this.runToLineCallback = callback;
    }

    public void setDropToFrameCallback(Runnable callback) {
        this.dropToFrameCallback = callback;
    }

    public void setToggleBreakpointCallback(Runnable callback) {
        this.toggleBreakpointCallback = callback;
    }

    public void setBreakpointEnabledCallback(java.util.function.BiConsumer<String, Boolean> callback) {
        this.breakpointEnabledCallback = callback;
    }

    public void setAddExpressionCallback(Consumer<String> callback) {
        this.addExpressionCallback = callback;
    }

    public void setRemoveExpressionCallback(Consumer<String> callback) {
        this.removeExpressionCallback = callback;
    }

    public void setRemoveAllExpressionsCallback(Runnable callback) {
        this.removeAllExpressionsCallback = callback;
    }

    /** Replaces the watch expressions with freshly evaluated nodes (keeps the expanded ones open). */
    public void showDebugExpressions(List<DebugVariableNode> expressions) {
        invokeLater(() -> {
            java.util.Set<String> expanded = new java.util.HashSet<>();
            DefaultMutableTreeNode oldRoot = (DefaultMutableTreeNode) debugExpressionsTreeModel.getRoot();
            for (int i = 0; i < oldRoot.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) oldRoot.getChildAt(i);
                if (debugExpressionsTree.isExpanded(new javax.swing.tree.TreePath(child.getPath())) && child.getUserObject() instanceof DebugVariableNode node) {
                    expanded.add(node.name);
                }
            }
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Expressions");
            for (DebugVariableNode node : expressions) {
                root.add(buildTreeNode(node));
            }
            root.add(new DefaultMutableTreeNode(DebugValueTree.ADD_ROW));
            debugExpressionsTreeModel.setRoot(root);
            for (int i = 0; i < root.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                if (child.getUserObject() instanceof DebugVariableNode node && expanded.contains(node.name)) {
                    debugExpressionsTree.expandPath(new javax.swing.tree.TreePath(child.getPath()));
                }
            }
        });
    }

    public void showDebugVariables(String title, List<DebugVariableNode> variables) {
        invokeLater(() -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(title != null ? title : VARIABLES_TITLE);
            for (DebugVariableNode node : variables) {
                root.add(buildTreeNode(node));
            }
            debugVariablesTreeModel.setRoot(root);
            if (!variables.isEmpty()) {
                debugTabbedPane.setSelectedIndex(0);
            }
            debugVariablesPanel.setVisible(true);
            debugVariablesPanel.revalidate();
            debugVariablesPanel.repaint();
            mainFrame.validate();
        });
    }

    private final java.util.Set<DebugVariableNode> loadingNodes = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Reading the fields or elements of a value is a round trip to the debuggee: do it in the background and fill
     * the node when it arrives, so the window stays responsive. The "Loading..." placeholder shows meanwhile.
     */
    private void loadChildrenAsync(DefaultTreeModel model, DefaultMutableTreeNode treeNode, DebugVariableNode node) {
        if (!loadingNodes.add(node)) {
            return;
        }
        new javax.swing.SwingWorker<List<DebugVariableNode>, Void>() {
            @Override
            protected List<DebugVariableNode> doInBackground() {
                return node.getChildren();
            }

            @Override
            protected void done() {
                loadingNodes.remove(node);
                try {
                    List<DebugVariableNode> children = get();
                    treeNode.removeAllChildren();
                    for (DebugVariableNode child : children) {
                        treeNode.add(buildTreeNode(child));
                    }
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException | RuntimeException e) {
                    treeNode.removeAllChildren();
                    treeNode.add(new DefaultMutableTreeNode(new DebugVariableNode("<error>", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()))));
                }
                model.nodeStructureChanged(treeNode);
            }
        }.execute();
    }

    private static DefaultMutableTreeNode buildTreeNode(DebugVariableNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        if (!node.isLeaf()) {
            treeNode.add(new DefaultMutableTreeNode("Loading...")); // placeholder triggers expand arrow
        }
        return treeNode;
    }

    public void showDebugBreakpoints(List<String[]> breakpoints) {
        invokeLater(() -> {
            List<BreakpointListPanel.Row> rows = new java.util.ArrayList<>();
            for (String[] bp : breakpoints) {
                // layout of a row: typeName:lineNumber, state, label, enabled
                rows.add(new BreakpointListPanel.Row(bp[0], bp[bp.length > 2 ? 2 : 0], bp[1],
                        bp.length <= 3 || Boolean.parseBoolean(bp[3])));
            }
            breakpointListPanel.setBreakpoints(rows);
            debugVariablesPanel.setVisible(true);
            debugVariablesPanel.revalidate();
            debugVariablesPanel.repaint();
            mainFrame.validate();
        });
    }

    private JTextField getFindEditor() {
        return (JTextField)findComboBox.getEditor().getEditorComponent();
    }

    /** Eclipse icons: grey "L" for locals (objects included), JDT field icons for members. */
    private java.util.function.Function<DebugVariableNode.Kind, Icon> newVariableIcons() {
        java.util.Map<DebugVariableNode.Kind, Icon> icons = new java.util.EnumMap<>(DebugVariableNode.Kind.class);
        icons.put(DebugVariableNode.Kind.LOCAL, newImageIcon("/org/jd/gui/images/localvariable_obj.png"));
        icons.put(DebugVariableNode.Kind.FIELD_PUBLIC, newImageIcon("/org/jd/gui/images/field_public_obj.png"));
        icons.put(DebugVariableNode.Kind.FIELD_PROTECTED, newImageIcon("/org/jd/gui/images/field_protected_obj.png"));
        icons.put(DebugVariableNode.Kind.FIELD_PRIVATE, newImageIcon("/org/jd/gui/images/field_private_obj.png"));
        icons.put(DebugVariableNode.Kind.FIELD_DEFAULT, newImageIcon("/org/jd/gui/images/field_default_obj.png"));
        return icons::get;
    }

    private static final String VARIABLES_TITLE = "Variables";
    private static final String EXPRESSIONS_TITLE = "Expressions";

    private JPanel createDebugVariablesPanel() {
        debugVariablesPanel = new JPanel(new BorderLayout(0, 4));
        debugVariablesPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        debugVariablesPanel.setPreferredSize(new Dimension(440, 0));
        debugVariablesPanel.setVisible(false);

        debugTabbedPane = new JTabbedPane();

        // --- Variables tab ---
        debugVariablesTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode(VARIABLES_TITLE));
        java.util.function.Function<DebugVariableNode.Kind, Icon> variableIcons = newVariableIcons();
        debugVariableIcons = variableIcons;
        DebugValueTree variablesTree = new DebugValueTree(debugVariablesTreeModel, variableIcons, null);
        debugVariablesTree = variablesTree;
        debugVariablesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        debugVariablesTree.addTreeWillExpandListener(lazyChildrenLoader(debugVariablesTreeModel));
        debugTabbedPane.addTab(VARIABLES_TITLE, newImageIcon("/org/jd/gui/images/variable_tab.png"), variablesTree.wrapInScrollPane());

        debugTabbedPane.addTab("Breakpoints", newImageIcon("/org/jd/gui/images/breakpoint_view.png"), createBreakpointsTab());

        // --- Expressions tab ---
        DefaultMutableTreeNode initialExpressionsRoot = new DefaultMutableTreeNode(EXPRESSIONS_TITLE);
        initialExpressionsRoot.add(new DefaultMutableTreeNode(DebugValueTree.ADD_ROW));
        debugExpressionsTreeModel = new DefaultTreeModel(initialExpressionsRoot);
        DebugValueTree expressionsTree = new DebugValueTree(debugExpressionsTreeModel, variableIcons,
                newImageIcon("/org/jd/gui/images/expression_obj.png"));
        debugExpressionsTree = expressionsTree;
        debugExpressionsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        debugExpressionsTree.addTreeWillExpandListener(lazyChildrenLoader(debugExpressionsTreeModel));

        JButton addExpressionButton = new JButton("Add...");
        addExpressionButton.setToolTipText("Add a watch expression");
        addExpressionButton.setFocusable(false);
        Runnable addExpressionAction = () -> {
            Object input = javax.swing.JOptionPane.showInputDialog(mainFrame, "Expression:", "Add Watch Expression",
                    javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (input != null && addExpressionCallback != null) {
                addExpressionCallback.accept(input.toString());
            }
        };
        addExpressionButton.addActionListener(e -> addExpressionAction.run());
        expressionsTree.setAddRowAction(addExpressionAction);
        JButton removeExpressionButton = new JButton("Remove");
        removeExpressionButton.setToolTipText("Remove the selected watch expression");
        removeExpressionButton.setFocusable(false);
        removeExpressionButton.addActionListener(e -> {
            javax.swing.tree.TreePath path = debugExpressionsTree.getSelectionPath();
            if (path != null && path.getPathCount() >= 2 && removeExpressionCallback != null
                    && ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject() instanceof DebugVariableNode node) {
                removeExpressionCallback.accept(node.name);
            }
        });
        JButton removeAllExpressionsButton = new JButton(newImageIcon("/org/jd/gui/images/delete_config.png"));
        removeAllExpressionsButton.setToolTipText("Remove all watch expressions");
        removeAllExpressionsButton.setFocusable(false);
        removeAllExpressionsButton.addActionListener(e -> {
            if (removeAllExpressionsCallback != null) {
                removeAllExpressionsCallback.run();
            }
        });
        JPanel expressionsToolbar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
        expressionsToolbar.add(addExpressionButton);
        expressionsToolbar.add(removeExpressionButton);
        expressionsToolbar.add(removeAllExpressionsButton);

        JPanel expressionsTabPanel = new JPanel(new BorderLayout());
        expressionsTabPanel.add(expressionsToolbar, BorderLayout.NORTH);
        expressionsTabPanel.add(expressionsTree.wrapInScrollPane(), BorderLayout.CENTER);
        debugTabbedPane.addTab(EXPRESSIONS_TITLE, newImageIcon("/org/jd/gui/images/watchlist_view.png"), expressionsTabPanel);

        debugVariablesPanel.add(debugTabbedPane, BorderLayout.CENTER);
        return debugVariablesPanel;
    }

    private JPanel createBreakpointsTab() {
        breakpointListPanel = new BreakpointListPanel(new BreakpointListPanel.Icons(
                newImageIcon("/org/jd/gui/images/brkp_obj.png"),
                newImageIcon("/org/jd/gui/images/brkpd_obj.png"),
                newImageIcon("/org/jd/gui/images/jexception_obj.png"),
                newImageIcon("/org/jd/gui/images/jexceptiond_obj.png"),
                newImageIcon("/org/jd/gui/images/bp_remove.png"),
                newImageIcon("/org/jd/gui/images/bp_remove_all.png"),
                newImageIcon("/org/jd/gui/images/bp_goto_file.png"),
                newImageIcon("/org/jd/gui/images/bp_skip_all.png"),
                newImageIcon("/org/jd/gui/images/exc_catch.png")));
        breakpointListPanel.setAddExceptionCallback(() -> {
            if (addExceptionBreakpointCallback != null) {
                addExceptionBreakpointCallback.run();
            }
        });
        breakpointListPanel.setRemoveCallback(key -> {
            if (removeBreakpointCallback != null) {
                removeBreakpointCallback.accept(key);
            }
        });
        breakpointListPanel.setRemoveAllCallback(() -> {
            if (removeAllBreakpointsCallback != null) {
                removeAllBreakpointsCallback.run();
            }
        });
        breakpointListPanel.setEnabledCallback((key, enabled) -> {
            if (breakpointEnabledCallback != null) {
                breakpointEnabledCallback.accept(key, enabled);
            }
        });
        breakpointListPanel.setNavigateCallback(key -> {
            if (breakpointNavigateCallback != null) {
                breakpointNavigateCallback.accept(key);
            }
        });
        breakpointListPanel.setSkipAllCallback(skip -> {
            if (skipAllBreakpointsCallback != null) {
                skipAllBreakpointsCallback.accept(skip);
            }
        });
        return breakpointListPanel;
    }

    // --- Utils --- //
    static String reduceRecentFilePath(String path) {
        int lastSeparatorPosition = path.lastIndexOf(File.separatorChar);

        if (lastSeparatorPosition == -1 || lastSeparatorPosition < Constants.RECENT_FILE_MAX_LENGTH) {
            return path;
        }

        int length = Constants.RECENT_FILE_MAX_LENGTH / 2 - 2;
        String left = path.substring(0, length);
        String right = path.substring(path.length() - length);

        return left + "..." + right;
    }

    // --- URIOpener --- //
    @Override
    public boolean openUri(URI uri) {
        boolean success = mainTabbedPanel.openUri(uri);

        if (success) {
            closeAction.setEnabled(true);
            openTypeAction.setEnabled(true);
        }

        return success;
    }

    // --- PreferencesChangeListener --- //
    @Override
    public void preferencesChanged(Map<String, String> preferences) {
        if (toggleHighlightAction != null) {
            toggleHighlightAction.putValue(Action.SELECTED_KEY, isSelectedWordHighlightEnabled(preferences));
        }
        applyConfiguredKeyBindings(preferences);
        updateExitShortcutBinding(preferences);
        updateFindBindings(preferences);
        mainTabbedPanel.preferencesChanged(preferences);
        repaint();
    }

    public void repaint() {
        mainFrame.repaint();
    }

    private static boolean isSelectedWordHighlightEnabled(Map<String, String> preferences) {
        return Boolean.parseBoolean(preferences.getOrDefault(
            GuiPreferences.SELECTED_WORD_HIGHLIGHT_ENABLED,
            GuiPreferences.DEFAULT_SELECTED_WORD_HIGHLIGHT_ENABLED
        ));
    }

    private static JToggleButton newToolbarToggleButton(Action action) {
        JToggleButton button = new JToggleButton(action);
        button.setText("Mark All");
        button.setFocusPainted(false);
        return button;
    }

    /**
     * The look and feel binds F6 (toggle focus) and F8 (start resize) on every split pane. Those bindings are
     * resolved before menu accelerators, so they would swallow the Step Over and Resume shortcuts.
     */
    private static void releaseDebugFunctionKeys() {
        Object inputMap = UIManager.get("SplitPane.ancestorInputMap");
        if (inputMap instanceof InputMap map) {
            map.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
            map.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0));
        }
    }

    private void registerKeyBinding(Binding binding, Action action) {
        keyBindingActions.put(binding, action);
    }

    private void applyConfiguredKeyBindings(Map<String, String> preferences) {
        KeyBindings.apply(preferences, keyBindingActions);
        // Toolbar buttons show their shortcut in the tooltip, e.g. "Resume execution (F8)"
        for (Map.Entry<Binding, Action> entry : keyBindingActions.entrySet()) {
            Action action = entry.getValue();
            if (action.getValue(Action.SMALL_ICON) == null || !(action.getValue(Action.SHORT_DESCRIPTION) instanceof String)) {
                continue;
            }
            String base = (String) action.getValue(BASE_DESCRIPTION_KEY);
            if (base == null) {
                base = (String) action.getValue(Action.SHORT_DESCRIPTION);
                action.putValue(BASE_DESCRIPTION_KEY, base);
            }
            KeyStroke keyStroke = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            action.putValue(Action.SHORT_DESCRIPTION, keyStroke == null ? base : base + " (" + KeyBindings.toDisplayText(keyStroke) + ")");
        }
    }

    private void updateExitShortcutBinding(Map<String, String> preferences) {
        if ((mainFrame == null) || (exitAction == null) || !PlatformService.getInstance().isMac()) {
            return;
        }

        JRootPane rootPane = mainFrame.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        if (installedExitKeyStroke != null) {
            inputMap.remove(installedExitKeyStroke);
        }

        installedExitKeyStroke = KeyBindings.getConfiguredKeyStroke(preferences, Binding.EXIT);
        if (installedExitKeyStroke != null) {
            inputMap.put(installedExitKeyStroke, EXIT_ACTION_KEY);
        }
        actionMap.put(EXIT_ACTION_KEY, exitAction);
    }

    private void updateFindBindings(Map<String, String> preferences) {
        if (findEditor == null) {
            return;
        }

        SearchUIOptions.configureIncrementalSearchField(findEditor, preferences);
        SearchUIOptions.configureFindButton(findNextButton, preferences);
        SearchUIOptions.configureFindButton(findPreviousButton, preferences);
        searchUIOptions.refreshTooltips(preferences);
        searchUIOptions.bindOptionKeyStrokes(findEditor, preferences);
        SearchUIOptions.bindSearchNavigationKeyStrokes(findEditor, preferences, findNextShortcutAction, findPreviousShortcutAction);
    }
}
