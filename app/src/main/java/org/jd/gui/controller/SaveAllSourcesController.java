/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
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
package org.jd.gui.controller;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.SourcesSavable;
import org.jd.gui.util.swing.AbstractSwingWorker;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import javax.swing.JFrame;
import javax.swing.SwingWorker;

public class SaveAllSourcesController {
    private final API api;
    private final JFrame mainFrame;

    public SaveAllSourcesController(API api, JFrame mainFrame) {
        this.api = api;
        this.mainFrame = mainFrame;
    }

    public void show(SourcesSavable savable, File file) {
        SwingWorker<Void, Void> saveAllSourcesWorker = new SaveAllSourcesWorker(api, savable, file);
        // Execute background task
        saveAllSourcesWorker.execute();
    }

    private final class SaveAllSourcesWorker extends AbstractSwingWorker<Void, Void> {
        private final SourcesSavable savable;
        private final File file;

        private SaveAllSourcesWorker(API api, SourcesSavable savable, File file) {
            super(api, mainFrame, "Saving...");
            this.savable = savable;
            this.file = file;
        }

        @Override
        protected Void doInBackground() throws Exception {

            try {
                Path path = Paths.get(file.toURI());
                Files.deleteIfExists(path);

                trySave(path);

                if (isCancelled()) {
                    Files.deleteIfExists(path);
                }
            } catch (Exception t) {
                assert ExceptionUtil.printStackTrace(t);
            }

            return null;
        }

        @Override
        protected void done() {
            /*
             * Throwable instances from the Error hierarchy (i.e. NoSuchMethodError, etc...)
             * are reported by Future.get() as cause of an ExecutionException.
             * FutureTask.run() catches Throwable and stores it in an outcome Object which
             * becomes the cause of the thrown ExecutionException.
             */
            super.done();
            try {
                get();
            } catch (InterruptedException e) {
                assert ExceptionUtil.printStackTrace(e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }

        private void trySave(Path path) {
            try {
                savable.save(api, path, this::getProgressPercentage, this::setProgressPercentage, this::isCancelled);
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
    }
}
