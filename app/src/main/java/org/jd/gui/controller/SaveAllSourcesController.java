/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

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
        SwingWorker<Void, Void> saveAllSourcesWorker = new SaveAllSourcesWorker(savable, file);
        // Execute background task
        saveAllSourcesWorker.execute();
    }

    private final class SaveAllSourcesWorker extends AbstractSwingWorker<Void, Void> {
        private final SourcesSavable savable;
        private final File file;

        private SaveAllSourcesWorker(SourcesSavable savable, File file) {
            super(mainFrame, "Saving...");
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
