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

package plugins.nchenouard.particletracking.filtering;

import Jama.Matrix;

/**
 * Predictor in the 3D case
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public interface Predictor3D extends Predictor {
    /**
     * Get the estimation of the current x coordinate
     *
     * @return double
     */
    double getXCoordEstimated();

    /**
     * Get the estimation of the current y coordinate
     *
     * @return double
     */
    double getYCoordEstimated();

    /**
     * Get the estimation of the current z coordinate
     *
     * @return double
     */
    double getZCoordEstimated();

    /**
     * Get the estimation of the predicted x coordinate
     *
     * @return double
     */
    double getXCoordPredicted();

    /**
     * Get the estimation of the predicted y coordinate
     *
     * @return double
     */
    double getYCoordPredicted();

    /**
     * Get the estimation of the predicted z coordinate
     *
     * @return double
     */
    double getZCoordPredicted();

    /**
     * Set the state error covariance matrix
     *
     * @param m Matrix
     */
    void setCurrentStateErrorCovariance3D(Matrix m);

    /**
     * Set the current state estimate
     *
     * @param m Matrix
     */
    void setCurrentEstimatedState3D(Matrix m);

    /**
     * Get the state error covariance matrix at a given time
     *
     * @param t int
     * @return Matrix
     */
    Matrix getStateErrorCovariance3D(int t);

    /**
     * Get the current state estimate
     *
     * @return Matrix
     */
    Matrix getCurrentEstimatedState3D();

    /**
     * Get the current state error covariance matrix
     *
     * @return Matrix
     */
    Matrix getCurrentStateErrorCovariance3D();

    /**
     * Get the predicted state estimate
     *
     * @return Matrix
     */
    Matrix getCurrentPredictedState3D();
}