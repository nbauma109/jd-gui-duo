/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.actions;

import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.service.extension.ExtensionService;
import org.jd.gui.spi.ContextualActionsFactory;

import java.util.*;

import javax.swing.Action;

public class ContextualActionsFactoryService {
    protected static final ContextualActionsFactoryService CONTEXTUAL_ACTIONS_FACTORY_SERVICE = new ContextualActionsFactoryService();

    public static ContextualActionsFactoryService getInstance() { return CONTEXTUAL_ACTIONS_FACTORY_SERVICE; }

    protected static final ActionNameComparator COMPARATOR = new ActionNameComparator();

    protected final Collection<ContextualActionsFactory> providers = ExtensionService.getInstance().load(ContextualActionsFactory.class);

    public Collection<Action> get(API api, Container.Entry entry, String fragment) {
        Map<String, List<Action>> mapActions = new HashMap<>();

        for (ContextualActionsFactory provider : providers) {
            Collection<Action> actions = provider.make(api, entry, fragment);

            for (Action action : actions) {
                String groupName = (String)action.getValue(ContextualActionsFactory.GROUP_NAME);
                mapActions.computeIfAbsent(groupName, k -> new ArrayList<>()).add(action);
            }
        }

        if (!mapActions.isEmpty()) {
            List<Action> result = new ArrayList<>();

            // Sort by group names
            List<String> groupNames = new ArrayList<>(mapActions.keySet());
            Collections.sort(groupNames);

            for (String groupName : groupNames) {
                if (! result.isEmpty()) {
                    // Add 'null' to mark a separator
                    result.add(null);
                }
                // Sort by names
                List<Action> actions = mapActions.get(groupName);
                Collections.sort(actions, COMPARATOR);
                result.addAll(actions);
            }

            return result;
        }
        return Collections.emptyList();
    }

    protected static class ActionNameComparator implements Comparator<Action> {
        @Override
        public int compare(Action a1, Action a2) {
            String n1 = (String)a1.getValue(Action.NAME);
            if (n1 == null) {
                n1 = "";
            }

            String n2 = (String)a2.getValue(Action.NAME);
            if (n2 == null) {
                n2 = "";
            }

            return n1.compareTo(n2);
        }
    }
}
