/*
 * Copyright (c) 2023 GPLv3.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.view.data;

import java.util.Map;

public record PreferenceKey(String engineName, Map<String, String> preferences) {
}
