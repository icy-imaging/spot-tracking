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

package plugins.nchenouard.particletracking.legacytracker.gui;

import plugins.nchenouard.particletracking.filtering.Predictor;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for the configuration of a single motion predictor
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class SingleMotionPanel extends JPanel {
    private final PredictorPanel[] predictorJPs = {new RandomWalkPanel(), new DirectedMotionPanel()};
    private final String[] modelNames;
    private final JComboBox<String> trackingFilters;
    private final CardLayout predictorCardLayout = new CardLayout();
    JPanel predictorsPanel = new JPanel(predictorCardLayout);

    public SingleMotionPanel() {
        final GridBagLayout layout = new GridBagLayout();
        this.setLayout(layout);
        modelNames = new String[predictorJPs.length];
        for (int i = 0; i < predictorJPs.length; i++) {
            modelNames[i] = predictorJPs[i].getName();
            predictorsPanel.add(predictorJPs[i], predictorJPs[i].getName());
        }
        trackingFilters = new JComboBox<>(modelNames);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        this.add(new JLabel("Motion type"), constraints);
        constraints.gridy++;

        this.add(trackingFilters, constraints);
        constraints.gridy++;

        setDefaultValues();
        trackingFilters.addActionListener(e -> predictorCardLayout.show(predictorsPanel, modelNames[trackingFilters.getSelectedIndex()]));
        this.add(predictorsPanel, constraints);
        constraints.gridy++;
    }

    private void setDefaultValues() {
        final int defaultModel = 0;
        trackingFilters.setSelectedIndex(defaultModel);
        predictorCardLayout.show(predictorsPanel, modelNames[defaultModel]);
        for (final PredictorPanel pp : predictorJPs)
            pp.setDefaultValues();
    }

    public Predictor buildPredictor(final int dim, final boolean covUpdate) {
        return predictorJPs[trackingFilters.getSelectedIndex()].buildPredictor(dim, covUpdate);
    }
}
