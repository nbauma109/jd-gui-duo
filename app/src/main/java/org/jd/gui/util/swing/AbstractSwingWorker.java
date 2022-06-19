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
package org.jd.gui.util.swing;

import java.awt.Component;
import java.beans.PropertyChangeEvent;

import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;

public abstract class AbstractSwingWorker<T, V> extends SwingWorker<T, V> {

    private final ProgressMonitor progressMonitor;
    private double progressPercentage;

    protected AbstractSwingWorker(Component component, String message) {
        progressMonitor = new ProgressMonitor(component, message, getProgressMessage(0), 0, 100);
        addPropertyChangeListener(e -> onPropertyChange(e, progressMonitor));
    }

    private void onPropertyChange(PropertyChangeEvent evt, ProgressMonitor progressMonitor) {
        if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            progressMonitor.setProgress(progress);
            String message = getProgressMessage(progress);
            progressMonitor.setNote(message);
            if (progressMonitor.isCanceled()) {
                cancel(true);
            }
        }
    }

    @Override
    protected void done() {
        progressMonitor.close();
    }
    
    private static String getProgressMessage(int progress) {
        return String.format("Completed %d%%.%n", progress);
    }

    public double getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(double progressPercentage) {
        super.setProgress((int) Math.round(progressPercentage));
        this.progressPercentage = progressPercentage;
    }
}
