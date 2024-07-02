/*
 * Copyright (c) 2010-2024. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.nchenouard.particletracking.gui;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Panel for parameter file import and export
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class ConfigurationFilePanel extends JPanel {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Configuration file");

    public JButton exportConfigurationFileButton = new JButton("Save the current configuration");
    public JButton loadConfigurationFileButton = new JButton("Load a configuration file");
//	public JButton runMultipleConfigurationFileButton = new JButton("Run tracking for multiple configuration files");

    public ConfigurationFilePanel() {
        this.setLayout(new BorderLayout());

        final JPanel pane = new JPanel();
        final GridBagLayout gbl = new GridBagLayout();
        pane.setLayout(gbl);
        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.gridwidth = 1;
        c.insets = new Insets(2, 2, 2, 2);

        c.gridx = 0;
        c.gridy = 0;
        pane.add(exportConfigurationFileButton, c);

        c.gridx = 0;
        c.gridy = 1;
        pane.add(loadConfigurationFileButton, c);

//		c.gridx = 0;
//		c.gridy = 2;
//		pane.add(runMultipleConfigurationFileButton, c);

        this.add(pane, BorderLayout.NORTH);
    }
}
