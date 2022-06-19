/*******************************************************************************
 * Copyright (C) 2022 GPLv3
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.util;

import org.jd.gui.api.model.Container;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class ProgressUtil {

    private ProgressUtil() {
    }

    public static void updateProgress(Container.Entry root, Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction) throws IOException {
        File file = new File(root.getUri());
        if (file.exists()) {
            double totalSize = Files.size(file.toPath());
            long entryLength = entry.compressedLength();
            double progress = 100 * entryLength / totalSize;
            double cumulativeProgress = getProgressFunction.getAsDouble() + progress;
            if (cumulativeProgress <= 100) {
                setProgressFunction.accept(cumulativeProgress);
            }
        } else {
            updateProgress(root.getParent(), entry, getProgressFunction, setProgressFunction);
        }
    }

    public static void updateProgress(Container.Entry entry, DoubleSupplier getProgressFunction, DoubleConsumer setProgressFunction) throws IOException {
        updateProgress(entry.getContainer().getRoot().getParent(), entry, getProgressFunction, setProgressFunction);
    }

}
