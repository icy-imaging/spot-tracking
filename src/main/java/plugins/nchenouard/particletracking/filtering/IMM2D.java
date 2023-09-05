
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
import java.util.List;

/**
 * This class is an implementation of the Interacting Multiple Model estimator (IMM) for 2D predictors
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class IMM2D implements Predictor {
    public enum LikelihoodTypes {IMM_MAX_LIKELIHOOD, IMM_WEIGHTED_LIKELIHOOD}

    public double inertia;
    protected int maxLikelihoodIndex = 0;
    int r;                         // number of models
    LikelihoodTypes likelihoodType;

    protected Predictor2D[] filter_i;  // filter bank
    protected double[][] p_ij;      // probability to switch from model i to model j
    protected double[] m_est_i;   // conditional model i probability
    protected double[][] m_est_ij;  // conditional model probability to switch from model i to model j
    protected double[] m_pre_i;   // predicted conditional model i probability
    protected double[] l_i;       // likelihood of model j

    protected Matrix x_est;     // state estimate
    protected Matrix P_est;     // state error covariance
    protected Matrix x_pre;     // state prediction
    protected Matrix z_pre;     // measurement prediction

    int dim = 2;

    /**
     * IMM2D's constructor
     *
     * @param predictorType LikelihoodTypes
     * @param immInertia    immInertia
     * @param predictors    List of Predictor2D
     */
    public IMM2D(final LikelihoodTypes predictorType, final double immInertia, final List<Predictor2D> predictors) {
        r = predictors.size();
        inertia = immInertia;
        x_est = new Matrix(dim, 1);
        P_est = new Matrix(dim, dim);

        this.likelihoodType = predictorType;
        filter_i = new Predictor2D[r];
        p_ij = new double[r][r];
        m_est_i = new double[r];
        m_est_ij = new double[r][r];
        m_pre_i = new double[r];
        l_i = new double[r];
        final double mi = 1.0 / r;
        for (int i = 0; i < r; i++) {
            m_pre_i[i] = mi;
            m_est_i[i] = mi;
            for (int j = 0; j < r; j++) {
                if (i == j)
                    p_ij[i][j] = inertia;
                else
                    p_ij[i][j] = (1 - inertia) / (r - 1.0);
            }
        }
        int cnt = 0;
        for (final Predictor2D p : predictors) {
            this.setModelAt(p, cnt);
            cnt++;
        }
    }

    /**
     * IMM2D's constructor
     *
     * @param toCopy IMM2D object
     */
    public IMM2D(final IMM2D toCopy) {
        this.dim = toCopy.dim;
        this.r = toCopy.r;
        this.likelihoodType = toCopy.likelihoodType;
        this.inertia = toCopy.inertia;
        this.x_est = toCopy.x_est.copy();
        if (z_pre != null)
            this.z_pre = toCopy.z_pre.copy();
        if (x_pre != null)
            this.x_pre = toCopy.x_pre.copy();
        this.P_est = toCopy.P_est.copy();
        this.l_i = new double[r];
        this.filter_i = new Predictor2D[r];
        for (int i = 0; i < filter_i.length; i++)
            this.filter_i[i] = (Predictor2D) toCopy.filter_i[i].copy();
        this.p_ij = new double[r][r];
        this.m_est_ij = new double[r][r];
        this.m_pre_i = new double[r];
        this.m_est_i = new double[r];
        System.arraycopy(toCopy.m_est_i, 0, this.m_est_i, 0, this.m_est_i.length);
        System.arraycopy(toCopy.m_pre_i, 0, this.m_pre_i, 0, this.m_pre_i.length);
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                this.p_ij[i][j] = toCopy.p_ij[i][j];
                this.m_est_ij[i][j] = toCopy.m_est_ij[i][j];
            }
        }
        this.maxLikelihoodIndex = toCopy.maxLikelihoodIndex;
    }

    @Override
    public Predictor copy() {
        return new IMM2D(this);
    }

    @Override
    public Predictor copyInit() {
        final ArrayList<Predictor2D> predictors = new ArrayList<>(r);
        for (final Predictor2D p : filter_i)
            predictors.add((Predictor2D) p.copyInit());
        return new IMM2D(likelihoodType, inertia, predictors);
    }

    @Override
    public Object clone() {
        return new IMM2D(this);
    }

    /**
     * Check if the matrix is correct
     *
     * @param z matrix
     */
    @Override
    public void correct(final Matrix z) {
        if (z != null) {
            /* ***************** STEP 2a : FILTER CORRECT ************************** */
            for (final Predictor p : filter_i)
                p.correct(z);
            /* ***************** STEP 3 : LIKELIHOOD COMPUTATION ******************* */
            // the likelihood tab is computed again !!
            likelihood(z, false, 10);
            /* ***************** STEP 4 : MODEL PROBABILITY UPDATES **************** */
            // compute normalization factor
            double c = 0;
            for (int i = 0; i < r; i++)
                c += m_pre_i[i] * l_i[i];
            for (int i = 0; i < r; i++)
                m_est_i[i] = m_pre_i[i] * l_i[i] / c;
            /* ***************** STEP 5 : COMBINATION OF THE STATE ESTIMATE ********* */
            double xNew = 0;
            double yNew = 0;
            for (int i = 0; i < r; i++) {
                xNew += filter_i[i].getXCoordEstimated() * m_est_i[i];
                yNew += filter_i[i].getYCoordEstimated() * m_est_i[i];
            }
            x_est.set(0, 0, xNew);
            x_est.set(1, 0, yNew);
            P_est.timesEquals(0.0);
            for (int i = 0; i < r; i++) {
                final Matrix m0 = filter_i[i].getCurrentEstimatedState2D().minus(x_est);
                P_est.plusEquals(filter_i[i].getCurrentStateErrorCovariance2D().plus(m0.times(m0.transpose())).times(m_est_i[i]));
                //m0 = getCurrentEstimatedState(filter_i[i], dim).minus(x_est);
                //P_est.plusEquals(getStateErrorCovariance(filter_i[i], dim).plus(m0.times(m0.transpose())).times(m_est_i[i]));
            }
        }
    }

    /**
     * Nicolas's method to improve the detection through tracking
     *
     * @param gateFactor double
     * @return 2D array of double
     */
    @Override
    public double[][] getCurrentCubicGate(final double gateFactor) {
        final double[][] bounds = filter_i[0].getCurrentCubicGate(gateFactor);
        for (int i = 1; i < r; i++) {
            final double[][] tmp = filter_i[i].getCurrentCubicGate(gateFactor);
            for (int k = 0; k < tmp.length; k++) {
                bounds[k][0] = Math.min(bounds[k][0], tmp[k][0]);
                bounds[k][1] = Math.max(bounds[k][1], tmp[k][1]);
            }
        }
        return bounds;
    }

    @Override
    public ArrayList<Area> getCurrentGate(final double gateFactor) {
        final ArrayList<Area> areas = new ArrayList<>();
        for (int i = 0; i < r; i++) {
            areas.addAll(filter_i[i].getCurrentGate(gateFactor));
        }
        return areas;
    }

    @Override
    public Matrix getCurrentEstimatedState() {
        return x_est;
    }

    @Override
    public Matrix getCurrentPredictedMeasurement() {
        return z_pre;
    }

    @Override
    public Matrix getCurrentPredictedState() {
        return x_pre;
    }

    @Override
    public Matrix getCurrentStateErrorCovariance() {
        return P_est;
    }

    @Override
    public void init(final Matrix x0, final Matrix P0) {
        for (int i = 0; i < r; i++) {
            filter_i[i].init(x0, P0);
        }
    }

    @Override
    public void initWithFirstElement(final Matrix firstElement, final int t) {
        for (final Predictor2D p2D : filter_i)
            p2D.initWithFirstElement(firstElement, t);
    }

    @Override
    public double likelihood(final Spot s, final boolean gated, final double gateFactor) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMax(s, gated, gateFactor);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMean(s, gated, gateFactor);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    /**
     * Likelihood method for abstract class Predictor
     *
     * @param z          Matrix
     * @param gated      bool
     * @param gateFactor double
     * @return double
     **/
    @Override
    public double likelihood(final Matrix z, final boolean gated, final double gateFactor) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMax(z, gated, gateFactor);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMean(z, gated, gateFactor);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    /**
     * LikelihoodMax = max{likelihood for each model}
     *
     * @param s          Spot
     * @param gated      bool
     * @param gateFactor double
     * @return double
     **/
    public double likelihoodMax(final Spot s, final boolean gated, final double gateFactor) {
        for (int i = 0; i < r; i++)
            l_i[i] = filter_i[i].likelihood(s, gated, gateFactor);
        return l_i[maxLikelihoodIndex];
    }

    /**
     * LikelihoodMax = max{likelihood for each model}
     *
     * @param z          Matrix
     * @param gated      bool
     * @param gateFactor double
     * @return double
     **/
    public double likelihoodMax(final Matrix z, final boolean gated, final double gateFactor) {
        for (int i = 0; i < r; i++)
            l_i[i] = filter_i[i].likelihood(z, gated, gateFactor);
        return l_i[maxLikelihoodIndex];
    }

    /**
     * LikelihoodMean = sum(l_i*)
     *
     * @param s          Spot
     * @param gated      bool
     * @param gateFactor double
     * @return double
     **/
    public double likelihoodMean(final Spot s, final boolean gated, final double gateFactor) {
        double mean = 0;
        for (int i = 0; i < r; i++) {
            l_i[i] = filter_i[i].likelihood(s, gated, gateFactor);
            mean += l_i[i] * m_est_i[i];
        }
        return mean;
    }

    /**
     * LikelihoodMean = sum(l_i*)
     *
     * @param z          Matrix
     * @param gated      bool
     * @param gateFactor double
     * @return double
     **/
    public double likelihoodMean(final Matrix z, final boolean gated, final double gateFactor) {
        double mean = 0;
        for (int i = 0; i < r; i++) {
            l_i[i] = filter_i[i].likelihood(z, gated, gateFactor);
            mean += l_i[i] * m_est_i[i];
        }
        return mean;
    }

    public int maxLikelihoodIndex() {
        double max = Double.NEGATIVE_INFINITY;
        int index = 0;
        for (int i = 0; i < r; i++) {
            if (max < l_i[i]) {
                max = l_i[i];
                index = i;
            }
        }
        return index;
    }

    @Override
    public void predict() {
        /* ***************** STEP 1 : MIX ************************************** */
        // update : m_pre_i
        for (int j = 0; j < r; j++) {
            m_pre_i[j] = 0.0;
            for (int i = 0; i < r; i++) {
                m_pre_i[j] += p_ij[i][j] * m_est_i[i];
            }
        }
        // update : m_est_ij
        for (int j = 0; j < r; j++) {
            for (int i = 0; i < r; i++) {
                m_est_ij[i][j] = p_ij[i][j] * m_est_i[i] / m_pre_i[j];
            }
        }

        final Matrix[] x_est_0j = new Matrix[r];
        final Matrix[] P_est_0j = new Matrix[r];
        // MIXING estimates : x_est_0j
        for (int j = 0; j < r; j++) {
            x_est_0j[j] = new Matrix(2, 1);
            for (int i = 0; i < r; i++) {
                x_est_0j[j].plusEquals(filter_i[i].getCurrentEstimatedState2D().times(m_est_ij[i][j]));
            }
        }
        // MIXING error covariances : P_est_0j
        for (int j = 0; j < r; j++) {
            P_est_0j[j] = new Matrix(2, 2);
            for (int i = 0; i < r; i++) {
                Matrix m0 = filter_i[i].getCurrentEstimatedState2D().minus(x_est_0j[j]);
                m0 = filter_i[i].getCurrentStateErrorCovariance2D().plus(m0.times(m0.transpose()));
                P_est_0j[j].plusEquals(m0.times(m_est_ij[i][j]));
            }
        }
        maxLikelihoodIndex = maxLikelihoodIndex();//update max likelihood index
        /* ***************** STEP 2a : FILTER PREDICT ************************** */

        for (int i = 0; i < r; i++) {
            filter_i[i].setCurrentEstimatedState2D(x_est_0j[i]);
            filter_i[i].setCurrentStateErrorCovariance2D(P_est_0j[i]);
            filter_i[i].predict();
        }
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD: {
                x_pre = filter_i[maxLikelihoodIndex].getCurrentPredictedState2D();
                z_pre = filter_i[maxLikelihoodIndex].getCurrentPredictedMeasurement();
                break;
            }
            case IMM_WEIGHTED_LIKELIHOOD: {
                x_pre = filter_i[0].getCurrentPredictedState2D().times(m_est_i[0]);
                z_pre = filter_i[0].getCurrentPredictedMeasurement().times(m_est_i[0]);
                for (int i = 1; i < r; i++) {
                    x_pre.plusEquals(filter_i[i].getCurrentPredictedState2D().times(m_est_i[i]));
                    z_pre.plusEquals(filter_i[i].getCurrentPredictedMeasurement().times(m_est_i[i]));
                }
                break;
            }
            default:
                System.err.println("Unknown likelihood type in IMM2D");
        }
    }

    @Override
    public void predict(final int t) {
        /* ***************** STEP 1 : MIX ************************************** */
        // update : m_pre_i
        for (int j = 0; j < r; j++) {
            m_pre_i[j] = 0.0;
            for (int i = 0; i < r; i++) {
                m_pre_i[j] += p_ij[i][j] * m_est_i[i];
            }
        }
        // update : m_est_ij
        for (int j = 0; j < r; j++) {
            for (int i = 0; i < r; i++) {
                m_est_ij[i][j] = p_ij[i][j] * m_est_i[i] / m_pre_i[j];
            }
        }
        final Matrix[] x_est_0j = new Matrix[r];  // mixed estimate for the filter matched to model j (filters input)
        final Matrix[] P_est_0j = new Matrix[r];
        // MIXING estimates : x_est_0j
        for (int j = 0; j < r; j++) {
            x_est_0j[j] = new Matrix(2, 1);
            for (int i = 0; i < r; i++) {
                x_est_0j[j].plusEquals(filter_i[i].getCurrentEstimatedState2D().times(m_est_ij[i][j]));
            }
        }

        // MIXING error covariances : P_est_0j
        for (int j = 0; j < r; j++) {
            P_est_0j[j] = new Matrix(2, 2);
            for (int i = 0; i < r; i++) {
                Matrix m0 = filter_i[i].getCurrentEstimatedState2D().minus(x_est_0j[j]);
                m0 = filter_i[i].getCurrentStateErrorCovariance2D().plus(m0.times(m0.transpose()));
                P_est_0j[j].plusEquals(m0.times(m_est_ij[i][j]));
            }
        }
        maxLikelihoodIndex = maxLikelihoodIndex();//update max likelihood index
        /* ***************** STEP 2a : FILTER PREDICT ************************** */

        for (int i = 0; i < r; i++) {
            filter_i[i].setCurrentEstimatedState2D(x_est_0j[i]);
            filter_i[i].setCurrentStateErrorCovariance2D(P_est_0j[i]);
            filter_i[i].predict(t);
        }
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD: {
                x_pre = filter_i[maxLikelihoodIndex].getCurrentPredictedState2D();
                z_pre = filter_i[maxLikelihoodIndex].getCurrentPredictedMeasurement();
                break;
            }
            case IMM_WEIGHTED_LIKELIHOOD: {
                x_pre = filter_i[0].getCurrentPredictedState2D().times(m_est_i[0]);
                z_pre = filter_i[0].getCurrentPredictedMeasurement().times(m_est_i[0]);
                for (int i = 1; i < r; i++) {
                    x_pre.plusEquals(filter_i[i].getCurrentPredictedState2D().times(m_est_i[i]));
                    z_pre.plusEquals(filter_i[i].getCurrentPredictedMeasurement().times(m_est_i[i]));
                }
                break;
            }
            default:
                System.err.println("Unknown likelihood type in IMM2D");
        }
    }

    public void setModelAt(final Predictor2D predictor, final int i) {
        filter_i[i] = predictor;
    }

    /**
     * Same covariance for all models
     *
     * @param trackingCov array of double
     */
    @Override
    public void setTrackingCovariances(final double[] trackingCov) {
        for (final Predictor2D p : filter_i)
            p.setTrackingCovariances(trackingCov);
    }

    @Override
    public double loglikelihood(final Matrix z) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMax(z, false, 10);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMean(z, false, 10);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return Math.log(l);
    }

    @Override
    public double loglikelihood(final Spot s) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMax(s, false, 10);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMean(s, false, 10);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return Math.log(l); //TODO compute directly log likelihood without using an exponential
    }

    @Override
    public double likelihoodOfState(final Matrix x) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMaxOfState(x);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMeanOfState(x);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    @Override
    public double loglikelihoodOfState(final Matrix x) {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = likelihoodMaxOfState(x);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                l = likelihoodMeanOfState(x);
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return Math.log(l);
    }

    /**
     * LikelihoodMax = max{likelihood for each model}
     *
     * @param x Matrix
     * @return double
     **/
    public double likelihoodMaxOfState(final Matrix x) {
        return filter_i[maxLikelihoodIndex].likelihoodOfState(x);
    }


    /**
     * LikelihoodMean = sum(l_i*)
     *
     * @param x Matrix
     * @return double
     **/
    public double likelihoodMeanOfState(final Matrix x) {
        double mean = 0;
        for (int i = 0; i < r; i++) {
            mean += filter_i[i].likelihoodOfState(x) * m_est_i[i];
        }
        return mean;
    }

    @Override
    public double likelihoodOfPredictedState() {
        final double l;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = filter_i[maxLikelihoodIndex].likelihoodOfPredictedState();
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                double mean = 0;
                for (int i = 0; i < r; i++) {
                    mean += filter_i[i].likelihoodOfPredictedState() * m_est_i[i];
                }
                l = mean;
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    @Override
    public Matrix buildMeasurementMatrix(final Spot spot) {
        return filter_i[0].buildMeasurementMatrix(spot);
    }

    @Override
    public void initWithFirstElement(final Spot firstElement, final int t) {
        for (int i = 0; i < r; i++) {
            filter_i[i].initWithFirstElement(firstElement, t);
        }
        predict();
    }

    public plugins.nchenouard.particletracking.VirtualSpot getPredictedStateAsSpot() {
        return new plugins.nchenouard.particletracking.VirtualSpot(x_pre.get(0, 0), x_pre.get(1, 0), 0);
    }

    @Override
    public Spot buildSpotFromState(final Matrix state) {
        return new Spot(state.get(0, 0), state.get(1, 0), 0);
    }

    @Override
    public double getTotalGateLikelihood(final boolean gated, final double gateFactor) {
        double likelihood = 0;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                return filter_i[maxLikelihoodIndex].getTotalGateLikelihood(gated, gateFactor);
            case IMM_WEIGHTED_LIKELIHOOD:
                for (int i = 0; i < r; i++)
                    likelihood += filter_i[i].getTotalGateLikelihood(gated, gateFactor) * m_est_i[i];
                return likelihood;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
    }

    public double[] getWeights() {
        return l_i;
    }

    @Override
    public double normalizedInnovation(final Spot s) {
        final Matrix m = buildMeasurementMatrix(s);
        return normalizedInnovation(m);
    }

    @Override
    public double normalizedInnovation(final Matrix m) {
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                return filter_i[maxLikelihoodIndex].normalizedInnovation(m);
            case IMM_WEIGHTED_LIKELIHOOD:
                double innov = 0;
                for (int i = 0; i < r; i++)
                    innov += filter_i[i].normalizedInnovation(m) * m_est_i[i];
                return innov;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
    }

    public LikelihoodMap[] getLikelihoodMapsIMM(final double gateFactor, final int t) {

        final ArrayList<Integer> enabledFilters = new ArrayList<>();
        for (int i = 0; i < r; i++) {
            if (filter_i[i] instanceof KF2dDirected || filter_i[i] instanceof KF2dRandomWalk)
                enabledFilters.add(i);
        }
        if (!enabledFilters.isEmpty()) {
            final LikelihoodMap[] maps = new LikelihoodMap[enabledFilters.size()];
            int cnt = 0;
            for (final Integer i : enabledFilters) {
                maps[cnt] = ((KalmanFilter) filter_i[i]).getLikelihoodMap(gateFactor, t);
                cnt++;
            }
            int minX = maps[0].getOffsetX();
            int minY = maps[0].getOffsetY();
            int minZ = maps[0].getOffsetZ();
            int maxX = maps[0].getOffsetX() + maps[0].getWidth() - 1;
            int maxY = maps[0].getOffsetY() + maps[0].getHeight() - 1;
            int maxZ = maps[0].getOffsetZ() + maps[0].getDepth() - 1;
            for (int i = 1; i < maps.length; i++) {
                minX = Math.min(minX, maps[i].getOffsetX());
                minY = Math.min(minY, maps[i].getOffsetY());
                minZ = Math.min(minZ, maps[i].getOffsetZ());
                maxX = Math.max(maxX, maps[i].getOffsetX() + maps[i].getWidth() - 1);
                maxY = Math.max(maxY, maps[i].getOffsetY() + maps[i].getHeight() - 1);
                maxZ = Math.max(maxZ, maps[i].getOffsetZ() + maps[i].getDepth() - 1);
            }
            return maps;
        }
        else
            throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
    }

    public LikelihoodMap getLikelihoodMap(final double gateFactor, final int t) {
        if (likelihoodType == LikelihoodTypes.IMM_MAX_LIKELIHOOD)
            return ((KalmanFilter) filter_i[maxLikelihoodIndex]).getLikelihoodMap(gateFactor, t);
        else if (likelihoodType == LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD) {
            final LikelihoodMap[] maps = new LikelihoodMap[filter_i.length];
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                maps[cnt] = ((KalmanFilter) filter_i[cnt]).getLikelihoodMap(gateFactor, t);
                cnt++;
            }
            int minX = maps[0].getOffsetX();
            int minY = maps[0].getOffsetY();
            int minZ = maps[0].getOffsetZ();
            int maxX = maps[0].getOffsetX() + maps[0].getWidth() - 1;
            int maxY = maps[0].getOffsetY() + maps[0].getHeight() - 1;
            int maxZ = maps[0].getOffsetZ() + maps[0].getDepth() - 1;
            for (int i = 1; i < maps.length; i++) {
                minX = Math.min(minX, maps[i].getOffsetX());
                minY = Math.min(minY, maps[i].getOffsetY());
                minZ = Math.min(minZ, maps[i].getOffsetZ());
                maxX = Math.max(maxX, maps[i].getOffsetX() + maps[i].getWidth() - 1);
                maxY = Math.max(maxY, maps[i].getOffsetY() + maps[i].getHeight() - 1);
                maxZ = Math.max(maxZ, maps[i].getOffsetZ() + maps[i].getDepth() - 1);
            }
            final LikelihoodMap lmap = new LikelihoodMap(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, minX, minY, minZ);
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                final LikelihoodMap map = maps[cnt];
                final double p = m_est_i[cnt];
                for (int z = map.getOffsetZ(); z < map.getOffsetZ() + map.getDepth(); z++)
                    for (int y = map.getOffsetY(); y < map.getOffsetY() + map.getHeight(); y++)
                        for (int x = map.getOffsetX(); x < map.getOffsetX() + map.getWidth(); x++)
                            lmap.addLikelihood(x, y, z, map.getLikelihood(x, y, z) * p);
            }
            return lmap;
        }
        else
            throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
    }

    public LikelihoodMap getLikelihoodMap(final boolean gated, final double gateFactor, final int t, final int width, final int height, final int depth) {
        if (likelihoodType == LikelihoodTypes.IMM_MAX_LIKELIHOOD) {
            return ((KalmanFilter) filter_i[maxLikelihoodIndex]).getLikelihoodMap(gated, gateFactor, t, width, height, depth);
        }
        else if (likelihoodType == LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD) {

            final LikelihoodMap[] maps = new LikelihoodMap[filter_i.length];
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                maps[cnt] = ((KalmanFilter) filter_i[cnt]).getLikelihoodMap(gated, gateFactor, t, width, height, depth);
                cnt++;
            }
            final LikelihoodMap lmap = new LikelihoodMap(width, height, depth, 0, 0, 0);
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                final LikelihoodMap map = maps[cnt];
                final double p = m_est_i[cnt];
                for (int z = map.getOffsetZ(); z < map.getOffsetZ() + map.getDepth(); z++)
                    for (int y = map.getOffsetY(); y < map.getOffsetY() + map.getHeight(); y++)
                        for (int x = map.getOffsetX(); x < map.getOffsetX() + map.getWidth(); x++)
                            lmap.addLikelihood(x, y, z, map.getLikelihood(x, y, z) * p);
            }
            return lmap;
        }
        else
            throw new IllegalArgumentException("Unknown IMM likelihood type.");
    }

    @Override
    public void update(final Matrix z, final int t) {
        correct(z);
        if (z != null)
            updateTime(t);
        predict(t);
    }

    @Override
    public void update(final Spot s, final int t) {
        final Matrix z = buildMeasurementMatrix(s);
        update(z, t);
    }

    @Override
    public void updateTime(final int t) {
        for (int c = 0; c < r; c++)
            filter_i[c].updateTime(t);
    }

    @Override
    public VirtualSpot getCurrentPredictedStateAsSpot() {
        switch (likelihoodType) {
            case IMM_WEIGHTED_LIKELIHOOD: {
                double x = 0;
                double y = 0;
                double z = 0;
                for (int c = 0; c < r; c++) {
                    final VirtualSpot prediction = filter_i[c].getCurrentPredictedStateAsSpot();
                    x += prediction.mass_center.x * m_est_i[c];
                    y += prediction.mass_center.y * m_est_i[c];
                    z += prediction.mass_center.z * m_est_i[c];
                }
                return new VirtualSpot(x, y, z);
            }
            case IMM_MAX_LIKELIHOOD: {
                return filter_i[maxLikelihoodIndex].getCurrentPredictedStateAsSpot();
            }
            default:
                System.err.println("Unknown likelihood type in IMM2D");
                return null;
        }
    }

    /**
     * Compute the minimum likelihood in the gate at time t
     *
     * @param gateFactor double
     * @param t          int
     * @return double
     */
    @Override
    public double getMinLikelihoodInGate(final double gateFactor, final int t) {
        double l = 0;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = filter_i[maxLikelihoodIndex].getMinLikelihoodInGate(gateFactor, t);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                for (int i = 0; i < r; i++)
                    l += filter_i[i].getMinLikelihoodInGate(gateFactor, t) * m_est_i[i];
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    /**
     * Compute the minimum likelihood in the gate
     *
     * @param gateFactor double
     * @return double
     */
    @Override
    public double getCurrentMinLikelihoodInGate(final double gateFactor) {
        double l = 0;
        switch (likelihoodType) {
            case IMM_MAX_LIKELIHOOD:
                l = filter_i[maxLikelihoodIndex].getCurrentMinLikelihoodInGate(gateFactor);
                break;
            case IMM_WEIGHTED_LIKELIHOOD:
                for (int i = 0; i < r; i++)
                    l += filter_i[i].getCurrentMinLikelihoodInGate(gateFactor) * m_est_i[i];
                break;
            default:
                throw new IllegalArgumentException("Unknown IMM likelihood type.");
        }
        return l;
    }

    //TODO implement inherited functions
    @Override
    public Matrix getEstimatedState(final int t) {
        return null;
    }

    @Override
    public Matrix getPredictedMeasurement(final int t) {
        return null;
    }

    @Override
    public Matrix getPredictedState(final int t) {
        return null;
    }

    @Override
    public VirtualSpot getPredictedStateAsSpot(final int t) {
        return null;
    }

    @Override
    public Matrix getStateErrorCovariance(final int t) {
        return null;
    }

    @Override
    public ArrayList<Area> getGates(final double gateFactor, final int t) {
        return null;
    }
}

