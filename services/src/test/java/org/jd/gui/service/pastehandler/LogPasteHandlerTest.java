/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.service.pastehandler;

import org.jd.gui.api.API;
import org.jd.gui.util.decompiler.GuiPreferences;
import org.jd.gui.view.component.LogPage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasting a text opens it as a log. */
class LogPasteHandlerTest {

    @Test
    void eachPastedTextIsALogWithAnAddressOfItsOwn() {
        List<LogPage> pages = new ArrayList<>();
        Map<String, String> preferences = new HashMap<>();
        preferences.put(GuiPreferences.ERROR_BACKGROUND_COLOR, "0xFF6666");
        API api = (API) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {API.class}, (proxy, method, args) -> {
            if ("addPanel".equals(method.getName())) {
                pages.add((LogPage) args[4]);
            }
            return "getPreferences".equals(method.getName()) ? preferences : null;
        });
        LogPasteHandler handler = new LogPasteHandler();
        assertThat(handler.accept("at demo.A.run(A.java:3)")).isTrue();

        handler.paste(api, "at demo.A.run(A.java:3)");
        handler.paste(api, null);

        assertThat(pages).hasSize(2);
        for (LogPage page : pages) {
            // the name of the log is the path of its address: without one, it would be the start of every address
            assertThat(page.getUri().getPath()).isEqualTo("/" + page.getFileName()).matches("/clipboard-\\d+\\.log");
            assertThat(page.getUri().getAuthority()).isNull();
        }
        assertThat(pages.get(0).getUri()).isNotEqualTo(pages.get(1).getUri());
    }
}
