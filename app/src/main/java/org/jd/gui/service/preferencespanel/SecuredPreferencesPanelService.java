/*
 * Copyright (c) 2026 GPLv3
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.preferencespanel;

import org.jd.gui.service.extension.ExtensionService;
import org.jd.gui.spi.SecuredPreferencesPanel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SecuredPreferencesPanelService {
    protected static final SecuredPreferencesPanelService PREFERENCES_PANEL_SERVICE = new SecuredPreferencesPanelService();

    public static SecuredPreferencesPanelService getInstance() { return PREFERENCES_PANEL_SERVICE; }

    private final Collection<SecuredPreferencesPanel> providers;

    protected SecuredPreferencesPanelService() {
        Collection<SecuredPreferencesPanel> list = ExtensionService.getInstance().load(SecuredPreferencesPanel.class);
        Iterator<SecuredPreferencesPanel> iterator = list.iterator();

        while (iterator.hasNext()) {
            if (!iterator.next().isActivated()) {
                iterator.remove();
            }
        }

        Map<String, SecuredPreferencesPanel> map = new HashMap<>();

        for (SecuredPreferencesPanel panel : list) {
            map.put(panel.getPreferencesGroupTitle() + '$' + panel.getPreferencesPanelTitle(), panel);
        }

        providers = map.values();
    }

    public Collection<SecuredPreferencesPanel> getProviders() {
        return providers;
    }
}
