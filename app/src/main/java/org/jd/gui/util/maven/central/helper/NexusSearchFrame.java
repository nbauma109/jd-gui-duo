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
package org.jd.gui.util.maven.central.helper;

import org.jd.gui.api.API;
import org.jd.gui.util.ImageUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * We provide a top-level frame hosting a {@link NexusSearchPanel}
 * bound to a given {@link NexusSearch} implementation.
 *
 * This frame is designed for integration into the main application,
 * and should be constructed and displayed by higher-level controllers.
 */
public final class NexusSearchFrame extends JFrame {

	private static final long serialVersionUID = 1L;

	public NexusSearchFrame(API api) {
        super("Repository Search");

        ImageUtil.addJDIconsToFrame(this);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        NexusSearchPanel panel = new NexusSearchPanel(api);
        panel.setPreferredSize(new Dimension(1000, 600));

        add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }
}