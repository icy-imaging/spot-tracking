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

package plugins.nchenouard.particletracking.legacytracker.gui;

import plugins.nchenouard.particletracking.filtering.KF2dRandomWalk;
import plugins.nchenouard.particletracking.filtering.KF3dRandomWalk;
import plugins.nchenouard.particletracking.filtering.Predictor;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for defining the configuration of the random walk predictor
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class RandomWalkPanel extends PredictorPanel {
    /**
     * Name of the predictor
     */
    public final static String name = "Brownian motion";
    /**
     * Panel for tuning the filters co-variances
     */
    private final FilterCovariancesPanel covPanel = new FilterCovariancesPanel();

    /**
     * Standard constructor
     */
    public RandomWalkPanel() {
        final GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        this.add(new JLabel("Motion covariance"), constraints);
        constraints.gridy++;

        this.add(covPanel, constraints);
    }

    /**
     * Build the predictor as defined in the GUI
     */
    @Override
    public Predictor buildPredictor(final int dim) {
        return buildPredictor(dim, true);
    }

    /**
     * Build the predictor as defined in the GUI when specifying if the co-variances should be auto-updated later on
     */
    @Override
    public Predictor buildPredictor(final int dim, final boolean covUpdate) {
        final Predictor predictor;
        if (dim == 2) {
            predictor = new KF2dRandomWalk();
            predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY()});
            ((KF2dRandomWalk) predictor).setUpdateCovariances(covUpdate);
        }
        else {
            predictor = new KF3dRandomWalk();
            predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY(), covPanel.getTrackingCovZ()});
            ((KF3dRandomWalk) predictor).setUpdateCovariances(covUpdate);
        }
        return predictor;
    }

    /**
     * get the name of the predictor
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * reset the GUI to match the default configuration of the predictor
     */
    @Override
    public void setDefaultValues() {
        covPanel.setDefaultValues();
    }
}
