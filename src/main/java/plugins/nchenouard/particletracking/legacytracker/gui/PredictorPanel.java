/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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

/**
 * Standard panel for a motion predictor
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public abstract class PredictorPanel extends JPanel {
    /**
     * Build a predictor with the dimension given by the parameter dim
     *
     * @param dim int
     * @return Predictor
     */
    public abstract Predictor buildPredictor(int dim);

    /**
     * Build a predictor with the dimension given by the parameter dim
     *
     * @param dim       int
     * @param covUpdate bool
     * @return Predictor
     */
    public abstract Predictor buildPredictor(int dim, boolean covUpdate);

    /**
     * Reset values of the parameters of the predictors to their default value
     */
    public abstract void setDefaultValues();

    /**
     * Name of the predictor
     *
     * @return String
     */
    @Override
    public abstract String getName();
}
