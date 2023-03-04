/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view.component.panel;

import org.jd.gui.api.API;
import org.jd.gui.api.feature.PageChangeListener;
import org.jd.gui.api.feature.PageChangeable;
import org.jd.gui.api.feature.PreferencesChangeListener;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.service.platform.PlatformService;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

@SuppressWarnings("unchecked")
public class MainTabbedPanel<T extends JComponent & UriGettable> extends TabbedPanel<T> implements UriOpenable, PageChangeListener {

    private static final long serialVersionUID = 1L;
    private final transient List<PageChangeListener> pageChangedListeners = new ArrayList<>();
    // Flag to prevent the event cascades
    private boolean pageChangedListenersEnabled = true;

    public MainTabbedPanel(API api) {
        super(api);
    }

    @Override
    public void create() {
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        Color bg = darker(getBackground());

        if (PlatformService.getInstance().isWindows()) {
            setBackground(bg);
        }

        // panel //
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBackground(bg);

        Color fontColor = panel.getBackground().darker();

        panel.add(Box.createHorizontalGlue());

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(panel.getBackground());
        box.add(Box.createVerticalGlue());

        JLabel title = newLabel("No files are open", fontColor);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize()+8F));

        box.add(title);
        box.add(newLabel("Open a file with menu \"File > Open File...\"", fontColor));
        box.add(newLabel("Open recent files with menu \"File > Recent Files\"", fontColor));
        box.add(newLabel("Drag and drop files from " + getFileManagerLabel(), fontColor));
        box.add(Box.createVerticalGlue());

        panel.add(box);
        panel.add(Box.createHorizontalGlue());
        add("panel", panel);

        // tabs //
        tabbedPane = createTabPanel();
        tabbedPane.addChangeListener(e -> {
            if (pageChangedListenersEnabled) {
                JComponent subPage = (JComponent)tabbedPane.getSelectedComponent();

                if (subPage == null) {
                    // Fire page changed event
                    for (PageChangeListener listener : pageChangedListeners) {
                        listener.pageChanged(null);
                    }
                } else {
                    T page = (T)subPage.getClientProperty("currentPage");

                    if (page == null) {
                        page = (T)tabbedPane.getSelectedComponent();
                    }
                    // Fire page changed event
                    for (PageChangeListener listener : pageChangedListeners) {
                        listener.pageChanged(page);
                    }
                    // Update current sub-page preferences
                    if (subPage instanceof PreferencesChangeListener) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
                        PreferencesChangeListener pcl = (PreferencesChangeListener) subPage;
                        pcl.preferencesChanged(preferences);
                    }
                }
            }
        });
        add("tabs", tabbedPane);

        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, darker(darker(bg))));
    }

    protected String getFileManagerLabel() {
        return switch (PlatformService.getInstance().getOs()) {
            case LINUX   -> "your file manager";
            case MAC_OSX -> "the Finder";
            default      -> "Explorer";
        };
    }

    protected JLabel newLabel(String text, Color fontColor) {
        JLabel label = new JLabel(text);
        label.setForeground(fontColor);
        return label;
    }

    @Override
    public void addPage(String title, Supplier<Icon> iconSupplier, String tip, T page) {
        super.addPage(title, iconSupplier, tip, page);
        if (page instanceof PageChangeable) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            PageChangeable pc = (PageChangeable) page;
            pc.addPageChangeListener(this);
        }
    }

    public List<T> getPages() {
        int i = tabbedPane.getTabCount();
        List<T> pages = new ArrayList<>(i);
        while (i-- > 0) {
            pages.add((T)tabbedPane.getComponentAt(i));
        }
        return pages;
    }

    public List<PageChangeListener> getPageChangedListeners() {
        return pageChangedListeners;
    }

    // --- URIOpener --- //
    @Override
    public boolean openUri(URI uri) {
        try {
            // Disable page changed event
            pageChangedListenersEnabled = false;
            // Search & display main tab
            T page = showPage(uri);

            if (page != null) {
                if (page instanceof UriOpenable) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
                    UriOpenable uo = (UriOpenable) page;
                    // Enable page changed event
                    pageChangedListenersEnabled = true;
                    // Search & display sub tab
                    return uo.openUri(uri);
                }
                return true;
            }
        } finally {
            // Enable page changed event
            pageChangedListenersEnabled = true;
        }

        return false;
    }

    // --- PageChangedListener --- //
    @Override
    public <U extends JComponent & UriGettable> void pageChanged(U page) {
        // Store active page for current sub tabbed pane
        Component subPage = tabbedPane.getSelectedComponent();

        if (subPage != null) {
            ((JComponent)subPage).putClientProperty("currentPage", page);
        }

        if (page == null) {
            page = (U)subPage;
        }

        // Forward event
        for (PageChangeListener listener : pageChangedListeners) {
            listener.pageChanged(page);
        }
    }

    // --- PreferencesChangeListener --- //
    @Override
    public void preferencesChanged(Map<String, String> preferences) {
        super.preferencesChanged(preferences);

        // Update current sub-page preferences
        Component subPage = tabbedPane.getSelectedComponent();
        if (subPage instanceof PreferencesChangeListener) { // to convert to jdk16 pattern matching only when spotbugs #1617 and eclipse #577987 are solved
            PreferencesChangeListener pcl = (PreferencesChangeListener) subPage;
            pcl.preferencesChanged(preferences);
        }
    }
}
