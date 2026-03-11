/*
 * Copyright (c) 2026 GPLv3
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.service.extension.ExtensionService;
import org.jd.gui.spi.EclipsePreferencesPanel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EclipsePreferencesPanelService {
    protected static final EclipsePreferencesPanelService PREFERENCES_PANEL_SERVICE = new EclipsePreferencesPanelService();

    public static EclipsePreferencesPanelService getInstance() { return PREFERENCES_PANEL_SERVICE; }

    private final Collection<EclipsePreferencesPanel> providers;

    protected EclipsePreferencesPanelService() {
        Collection<EclipsePreferencesPanel> list = ExtensionService.getInstance().load(EclipsePreferencesPanel.class);
        Iterator<EclipsePreferencesPanel> iterator = list.iterator();

        while (iterator.hasNext()) {
            if (!iterator.next().isActivated()) {
                iterator.remove();
            }
        }

        Map<String, EclipsePreferencesPanel> map = new HashMap<>();

        for (EclipsePreferencesPanel panel : list) {
            map.put(panel.getPreferencesGroupTitle() + '$' + panel.getPreferencesPanelTitle(), panel);
        }

        providers = map.values();
    }

    public Collection<EclipsePreferencesPanel> getProviders() {
        return providers;
    }
}
