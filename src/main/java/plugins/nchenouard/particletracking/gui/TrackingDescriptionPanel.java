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
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Panel describing the tracking method
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class TrackingDescriptionPanel extends JPanel {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Tracking");

    JTextArea methodDescription = new JTextArea("This plugin implements the Multiple Hypothesis Tracking algorithm described in:\n Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin, Multiple Hypothesis Tracking for Cluttered Biological Image Sequences, IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013.\n Pubmed link: : http://www.ncbi.nlm.nih.gov/pubmed/23689865. \nPlease reference and cite properly.");
    final protected JSpinner numberNewTrackSpinner = new JSpinner();
    final protected JSpinner numberInitialTrackSpinner = new JSpinner();
    final protected JSpinner spinnerMeanTrackLength = new JSpinner();
    JSpinner spinnerXYDisplacement = new JSpinner();

    public TrackingDescriptionPanel() {
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
        final JPanel textPanel = new JPanel();
        textPanel.setLayout(new GridLayout(1, 1, 0, 0));
        methodDescription.setLineWrap(true);
        textPanel.add(methodDescription);
        methodDescription.setEditable(false);
        textPanel.setBorder(new TitledBorder("Reference"));
        pane.add(textPanel, c);

        final JPanel quickSettingsPanel = new JPanel();
        quickSettingsPanel.setLayout(new GridLayout(4, 1, 0, 0));

        numberNewTrackSpinner.setModel(new SpinnerNumberModel(20d, 0d, null, 1d));
        final JPanel spinnerNewTrackPanel = new JPanel();
        spinnerNewTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerNewTrackPanel.add(new JLabel("Expected number of new tracks per frame"));
        spinnerNewTrackPanel.add(numberNewTrackSpinner);
        quickSettingsPanel.add(spinnerNewTrackPanel);

        numberInitialTrackSpinner.setModel(new SpinnerNumberModel(20d, 0d, null, 1d));
        final JPanel spinnerInitialTrackPanel = new JPanel();
        spinnerInitialTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerInitialTrackPanel.add(new JLabel("Expected number of objects in the first frame"));
        spinnerInitialTrackPanel.add(numberInitialTrackSpinner);
        quickSettingsPanel.add(spinnerInitialTrackPanel);

        spinnerMeanTrackLength.setModel(new SpinnerNumberModel(20d, 1d, null, 1d));
        final JPanel spinnerPanel = new JPanel();
        spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerPanel.add(new JLabel("Expected track length"));
        spinnerPanel.add(spinnerMeanTrackLength);
        quickSettingsPanel.add(spinnerPanel);

        spinnerXYDisplacement.setModel(new SpinnerNumberModel(4d, .5d, null, .5d));
        final JPanel displacementPanel = new JPanel();
        displacementPanel.setLayout(new GridLayout(2, 1, 0, 0));
        displacementPanel.add(new JLabel("Average length of displacement between two frames"));
        displacementPanel.add(spinnerXYDisplacement);
        quickSettingsPanel.add(displacementPanel);

        quickSettingsPanel.setBorder(new TitledBorder("Quick settings"));

        c.gridx = 0;
        c.gridy = 1;
        pane.add(quickSettingsPanel, c);

        this.add(pane, BorderLayout.NORTH);
    }
}
