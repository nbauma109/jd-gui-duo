package org.jd.gui.util.loader;

import org.jd.gui.api.model.Container.Entry;
import org.jd.gui.util.decompiler.ContainerLoader;
import org.jd.gui.util.decompiler.GuiPreferences;

import com.heliosdecompiler.transformerapi.common.Loader;

import java.net.URI;
import java.util.Map;

public final class LoaderUtils {

    private LoaderUtils() {
    }

    public static Loader createLoader(Map<String, String> preferences, org.jd.core.v1.api.loader.Loader loader, URI jarURI) {
        boolean advancedClassLookup = Boolean.parseBoolean(preferences.getOrDefault(GuiPreferences.ADVANCED_CLASS_LOOKUP, Boolean.FALSE.toString()));
        return new Loader(loader::canLoad, loader::load, advancedClassLookup ? jarURI : null);
    }

    public static Loader createLoader(Map<String, String> preferences, ContainerLoader loader, Entry entry) {
        URI jarURI = entry.getContainer().getRoot().getParent().getUri();
        return createLoader(preferences, loader, jarURI);
    }
}
