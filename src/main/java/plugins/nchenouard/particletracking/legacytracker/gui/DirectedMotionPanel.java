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

import plugins.nchenouard.particletracking.filtering.KF2dDirected;
import plugins.nchenouard.particletracking.filtering.KF3dDirected;
import plugins.nchenouard.particletracking.filtering.Predictor;

import javax.swing.*;
import java.awt.*;

/**
 * Panel to configure and create a predictor for constant speed motion
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class DirectedMotionPanel extends PredictorPanel {
    public final static String name = "Directed motion";

    private final FilterCovariancesPanel covPanel = new FilterCovariancesPanel();

    public DirectedMotionPanel() {
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


    @Override
    public Predictor buildPredictor(final int dim) {
        return buildPredictor(dim, true);
    }

    @Override
    public Predictor buildPredictor(final int dim, final boolean covUpdate) {
        final Predictor predictor;
        if (dim == 2) {
            predictor = new KF2dDirected();
            predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY()});
            ((KF2dDirected) predictor).setUpdateCovariances(covUpdate);
        }
        else {
            predictor = new KF3dDirected();
            predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY(), covPanel.getTrackingCovZ()});
            ((KF3dDirected) predictor).setUpdateCovariances(covUpdate);
        }
        return predictor;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setDefaultValues() {
        covPanel.setDefaultValues();
    }
}
