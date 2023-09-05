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

package plugins.nchenouard.particletracking.filtering;

import Jama.Matrix;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;

import java.awt.geom.Area;
import java.util.ArrayList;

/**
 * This interface represent the model of a dynamic estimator.
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public interface Predictor {
    /***
     * Build the measurement matrix from a spot
     * @param spot Spot - the given spot
     * @return Matrix
     */
    Matrix buildMeasurementMatrix(Spot spot);

    /**
     * build a instance of Spot from a state
     *
     * @param state matrix state
     * @return Spot
     */
    Spot buildSpotFromState(Matrix state);

    Object clone();

    /**
     * Get a copy of the predictor: all matrices and various vectors are copied in new instances
     *
     * @return Predictor
     */
    Predictor copy();

    /**
     * Get a copy of the predictor, only parameters are copied, not measurement
     *
     * @return Predictor
     */
    Predictor copyInit();

    /**
     * Correct the filters with the given measurement
     *
     * @param z matrix
     */
    void correct(Matrix z);

    /**
     * Get the cube that contains the gate
     *
     * @param gateFactor double
     * @return 2D array of double
     */
    double[][] getCurrentCubicGate(double gateFactor);

    /**
     * Get the estimated state x(t|t)
     *
     * @return Matrix
     */
    Matrix getCurrentEstimatedState();

    /**
     * Get the current gate as an area
     *
     * @param gateFactor double
     * @return ArrayList of Area
     */
    ArrayList<Area> getCurrentGate(double gateFactor);

    /**
     * Compute the minimum likelihood in the gate
     *
     * @param gateFactor double
     * @return double
     */
    double getCurrentMinLikelihoodInGate(double gateFactor);

    /**
     * Get the predicted measurement z(t|t-1)
     *
     * @return Matrix
     */
    Matrix getCurrentPredictedMeasurement();

    /**
     * Get the predicted state x(t|t-1)
     *
     * @return Matrix
     */
    Matrix getCurrentPredictedState();

    /**
     * Get the current predicted state xt|t-1 as an instance of Spot
     *
     * @return Matrix
     */
    VirtualSpot getCurrentPredictedStateAsSpot();

    /**
     * Get the state error covariance matrix P(t|t)
     *
     * @return Matrix
     */
    Matrix getCurrentStateErrorCovariance();

    /**
     * Get the estimated state x(t|t)
     *
     * @param t int
     * @return Matrix
     */
    Matrix getEstimatedState(int t);

    /**
     * Get the gate at index i
     *
     * @param gateFactor double
     * @param t          int
     * @return ArrayList of Area
     **/
    ArrayList<Area> getGates(double gateFactor, int t);

    /**
     * Compute the minimum likelihood in the gate at time t
     *
     * @param gateFactor double
     * @param t          int
     * @return double
     */
    double getMinLikelihoodInGate(double gateFactor, int t);

    /**
     * Get the predicted measurement z(t|t-1)
     *
     * @param t int
     * @return Matrix
     */
    Matrix getPredictedMeasurement(int t);

    /**
     * Get the predicted state x(t|t-1)
     *
     * @param t int
     * @return Matrix
     */
    Matrix getPredictedState(int t);

    /**
     * Get the predicted state x(t|t-1) as a instance of Spot
     *
     * @param t int
     * @return VirtualSpot
     */
    VirtualSpot getPredictedStateAsSpot(int t);

    /**
     * Get the state error covariance matrix P(t|t)
     *
     * @param t int
     * @return Matrix
     */
    Matrix getStateErrorCovariance(int t);

    /**
     * Get the total likelihood contained in the gate
     *
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    double getTotalGateLikelihood(boolean gated, double gateFactor);

    /**
     * Initialize the state, state error covariance
     *
     * @param x0 Matrix
     * @param P0 Matrix
     */
    void init(Matrix x0, Matrix P0);

    /**
     * Initialize matrices with first measurement
     *
     * @param firstElement Matrix
     * @param t            int
     **/
    void initWithFirstElement(Matrix firstElement, int t);

    /**
     * Initialize matrices with first spot
     *
     * @param firstElement Matrix
     * @param t            int
     **/
    void initWithFirstElement(Spot firstElement, int t);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param z          Matrix
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    double likelihood(Matrix z, boolean gated, double gateFactor);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param s          Spot
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    double likelihood(Spot s, boolean gated, double gateFactor);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param z Matrix
     * @return double
     */
    double loglikelihood(Matrix z);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param s Spot
     * @return double
     */
    double loglikelihood(Spot s);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param x Matrix
     * @return double
     */
    double likelihoodOfState(Matrix x);

    /**
     * Compute the likelihood of the given measurement (after a prediction has been made)
     *
     * @param x Matrix
     * @return double
     */
    double loglikelihoodOfState(Matrix x);

    /**
     * Get the state of the likelihood
     *
     * @return double
     */
    double likelihoodOfPredictedState();

    /**
     * Compute the normalized innovation for the measurement m
     *
     * @param m Matrix
     * @return double
     */
    double normalizedInnovation(Matrix m);

    /**
     * Compute the normalized innovation for the spot s
     *
     * @param s Spot
     * @return double
     */
    double normalizedInnovation(Spot s);

    /**
     * Build the prediction
     */
    void predict();

    /**
     * Build the prediction at time t
     *
     * @param t int
     */
    void predict(int t);

    /**
     * Set the covariances errors of the transition noise
     *
     * @param trackingCov array of double
     */
    void setTrackingCovariances(double[] trackingCov);

    /**
     * Update the filters with the state z at time t
     *
     * @param z Matrix
     * @param t int
     */
    void update(Matrix z, int t);

    /**
     * Update the filters with the spot at time t
     *
     * @param s Spot
     * @param t int
     */
    void update(Spot s, int t);

    /**
     * Update the filters without spot at time t
     *
     * @param t int
     */
    void updateTime(int t);
}

