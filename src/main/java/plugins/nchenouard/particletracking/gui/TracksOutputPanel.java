/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

import icy.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.nchenouard.particletracking.MHTparameterSet;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Panel for setting the output of the Spot Tracking Plugin to the Track Manager plugin
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class TracksOutputPanel extends JPanel {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Output");

    final protected JTextField trackGroupTF = new JTextField();

    public TracksOutputPanel() {
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
        final JPanel trackGroupPanel = new JPanel();
        trackGroupPanel.setLayout(new GridLayout(2, 1, 0, 0));
        trackGroupPanel.add(new JLabel("Name of the group of tracks that is generated:"));
        trackGroupTF.setText("mhtTracks-Run1");
        trackGroupPanel.add(trackGroupTF);
        pane.add(trackGroupPanel, c);

        this.add(pane, BorderLayout.NORTH);
    }

    // XML import/export

    public void saveToXML(final Node node) {
        final Element nodeOutput = XMLUtil.setElement(node, MHTparameterSet.ROOT_OUTPUT);
        if (nodeOutput != null) {
            XMLUtil.setAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, trackGroupTF.getText());
        }
    }

    public void loadFromFromXML(final Node node) {
        final Element nodeOutput = XMLUtil.getElement(node, MHTparameterSet.ROOT_OUTPUT);
        if (nodeOutput != null) {
            trackGroupTF.setText(XMLUtil.getAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, MHTparameterSet.defaultTrackGroupName));
        }
    }

    public void fillParameterSet(final MHTparameterSet parameterSet) {
        parameterSet.trackGroupName = trackGroupTF.getText();
    }

    public void setParameters(final MHTparameterSet parameterSet) {
        trackGroupTF.setText(parameterSet.trackGroupName);
    }
}
