/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy and other contributors.
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
import org.jd.gui.view.component.IconButton;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import static org.jd.gui.util.ImageUtil.newImageIcon;
import static org.jd.gui.util.swing.SwingUtil.invokeLater;
import static org.jd.gui.util.swing.SwingUtil.newAction;

import de.cismet.custom.visualdiff.PlatformService;

@SuppressWarnings("unchecked")
public class MainView<T extends JComponent & UriGettable> implements UriOpenable, PreferencesChangeListener {

    private static final String JAVA_DECOMPILER = "Java Decompiler";
    private static final String EXIT_ACTION_KEY = "MainView.exit";
    private final History history;
    private final Consumer<File> openFilesCallback;
    private final EnumMap<Binding, Action> keyBindingActions = new EnumMap<>(Binding.class);
    private JFrame mainFrame;
    private final JMenu recentFiles = new JMenu("Recent Files");
    private Action closeAction;
    private Action exitAction;
    private Action openTypeAction;
    private Action quickOutlineAction;
    private Action backwardAction;
    private Action forwardAction;
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

            for (File file : files) {
                JMenuItem menuItem = new JMenuItem(reduceRecentFilePath(file.getAbsolutePath()));
                menuItem.addActionListener(e -> openFilesCallback.accept(file));
                recentFiles.add(menuItem);
            }
        });
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

    private JTextField getFindEditor() {
        return (JTextField)findComboBox.getEditor().getEditorComponent();
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
        button.setText(null);
        button.setFocusPainted(false);
        button.setHideActionText(true);
        return button;
    }

    private void registerKeyBinding(Binding binding, Action action) {
        keyBindingActions.put(binding, action);
    }

    private void applyConfiguredKeyBindings(Map<String, String> preferences) {
        KeyBindings.apply(preferences, keyBindingActions);
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
