/*
 * © 2008-2019 Emmanuel Dupuy
 * © 2021-2022 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.api.feature;

import org.jd.gui.api.API;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public interface SourcesSavable {
    String getSourceFileName();

    int getFileCount();

    void save(API api, Path path, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction, BooleanSupplier isCancelledFunction);
}
