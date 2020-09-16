
package plugins.nchenouard.particletracking.filtering;

import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

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
    public static enum LikelihoodTypes {IMM_MAX_LIKELIHOOD, IMM_WEIGHTED_LIKELIHOOD}

    ;

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
	 * @param predictorType LikelihoodTypes
	 * @param immInertia immInertia
	 * @param predictors List of Predictor2D
	 */
    public IMM2D(LikelihoodTypes predictorType, double immInertia, List<Predictor2D> predictors) {
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
        double mi = 1.0 / r;
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
        for (Predictor2D p : predictors) {
            this.setModelAt(p, cnt);
            cnt++;
        }
    }

	/**
	 * IMM2D's constructor
	 * @param toCopy IMM2D object
	 */
	public IMM2D(IMM2D toCopy) {
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

    public Predictor copy() {
        return new IMM2D(this);
    }

    public Predictor copyInit() {
        ArrayList<Predictor2D> predictors = new ArrayList<Predictor2D>(r);
        for (Predictor2D p : filter_i)
            predictors.add((Predictor2D) p.copyInit());
        return new IMM2D(likelihoodType, inertia, predictors);
    }

    public Object clone() {
        return new IMM2D(this);
    }

	/**
	 * Check if the matrix is correct
	 * @param z matrix
	 */
	public void correct(Matrix z) {
        if (z != null) {
            /* ***************** STEP 2a : FILTER CORRECT ************************** */
            for (Predictor p : filter_i)
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
                Matrix m0 = filter_i[i].getCurrentEstimatedState2D().minus(x_est);
                P_est.plusEquals(filter_i[i].getCurrentStateErrorCovariance2D().plus(m0.times(m0.transpose())).times(m_est_i[i]));
                //m0 = getCurrentEstimatedState(filter_i[i], dim).minus(x_est);
                //P_est.plusEquals(getStateErrorCovariance(filter_i[i], dim).plus(m0.times(m0.transpose())).times(m_est_i[i]));
            }
        }
    }

	/**
	 * Nicolas's method to improve the detection through tracking
	 * @param gateFactor double
	 * @return 2D array of double
	 */
    public double[][] getCurrentCubicGate(double gateFactor) {
        double[][] bounds = filter_i[0].getCurrentCubicGate(gateFactor);
        for (int i = 1; i < r; i++) {
            double[][] tmp = filter_i[i].getCurrentCubicGate(gateFactor);
            for (int k = 0; k < tmp.length; k++) {
                bounds[k][0] = Math.min(bounds[k][0], tmp[k][0]);
                bounds[k][1] = Math.max(bounds[k][1], tmp[k][1]);
            }
        }
        return bounds;
    }

    public ArrayList<Area> getCurrentGate(double gateFactor) {
        ArrayList<Area> areas = new ArrayList<Area>();
        for (int i = 0; i < r; i++) {
            areas.addAll(filter_i[i].getCurrentGate(gateFactor));
        }
        return areas;
    }

    public Matrix getCurrentEstimatedState() {
        return x_est;
    }

    public Matrix getCurrentPredictedMeasurement() {
        return z_pre;
    }

    public Matrix getCurrentPredictedState() {
        return x_pre;
    }

    public Matrix getCurrentStateErrorCovariance() {
        return P_est;
    }

    public void init(Matrix x0, Matrix P0) {
        for (int i = 0; i < r; i++) {
            filter_i[i].init(x0, P0);
        }
    }

    public void initWithFirstElement(Matrix firstElement, int t) {
        for (Predictor2D p2D : filter_i)
            p2D.initWithFirstElement(firstElement, t);
    }

    public double likelihood(Spot s, boolean gated, double gateFactor) {
        double l;
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
	 * @param z Matrix
	 * @param gated bool
	 * @param gateFactor double
	 * @return double
     **/
    public double likelihood(Matrix z, boolean gated, double gateFactor) {
        double l;
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
	 * @param s Spot
	 * @param gated bool
	 * @param gateFactor double
	 * @return double
     **/
    public double likelihoodMax(Spot s, boolean gated, double gateFactor) {
        for (int i = 0; i < r; i++)
            l_i[i] = filter_i[i].likelihood(s, gated, gateFactor);
        return l_i[maxLikelihoodIndex];
    }

    /**
     * LikelihoodMax = max{likelihood for each model}
	 * @param z Matrix
	 * @param gated bool
	 * @param gateFactor double
	 * @return double
     **/
    public double likelihoodMax(Matrix z, boolean gated, double gateFactor) {
        for (int i = 0; i < r; i++)
            l_i[i] = filter_i[i].likelihood(z, gated, gateFactor);
        return l_i[maxLikelihoodIndex];
    }

    /**
     * LikelihoodMean = sum(l_i*)
	 * @param s Spot
	 * @param gated bool
	 * @param gateFactor double
	 * @return double
     **/
    public double likelihoodMean(Spot s, boolean gated, double gateFactor) {
        double mean = 0;
        for (int i = 0; i < r; i++) {
            l_i[i] = filter_i[i].likelihood(s, gated, gateFactor);
            mean += l_i[i] * m_est_i[i];
        }
        return mean;
    }

    /**
     * LikelihoodMean = sum(l_i*)
	 * @param z Matrix
	 * @param gated bool
	 * @param gateFactor double
     * @return double
     **/
    public double likelihoodMean(Matrix z, boolean gated, double gateFactor) {
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

        Matrix[] x_est_0j = new Matrix[r];
        Matrix[] P_est_0j = new Matrix[r];
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

    public void predict(int t) {
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
        Matrix[] x_est_0j = new Matrix[r];  // mixed estimate for the filter matched to model j (filters input)
        Matrix[] P_est_0j = new Matrix[r];
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

    public void setModelAt(Predictor2D predictor, int i) {
        filter_i[i] = predictor;
    }

	/**
	 * Same covariance for all models
	 * @param trackingCov array of double
	 */
	public void setTrackingCovariances(double[] trackingCov)
    {
        for (Predictor2D p : filter_i)
            p.setTrackingCovariances(trackingCov);
    }

    public double loglikelihood(Matrix z) {
        double l;
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

    public double loglikelihood(Spot s) {
        double l;
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
    public double likelihoodOfState(Matrix x) {
        double l;
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
    public double loglikelihoodOfState(Matrix x) {
        double l;
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
	 * @param x Matrix
	 * @return double
     **/
    public double likelihoodMaxOfState(Matrix x) {
        return filter_i[maxLikelihoodIndex].likelihoodOfState(x);
    }


    /**
     * LikelihoodMean = sum(l_i*)
	 * @param x Matrix
	 * @return double
     **/
    public double likelihoodMeanOfState(Matrix x) {
        double mean = 0;
        for (int i = 0; i < r; i++) {
            mean += filter_i[i].likelihoodOfState(x) * m_est_i[i];
        }
        return mean;
    }

    @Override
    public double likelihoodOfPredictedState() {
        double l;
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

    public Matrix buildMeasurementMatrix(Spot spot) {
        return filter_i[0].buildMeasurementMatrix(spot);
    }

    public void initWithFirstElement(Spot firstElement, int t) {
        for (int i = 0; i < r; i++) {
            filter_i[i].initWithFirstElement(firstElement, t);
        }
        predict();
    }

    public plugins.nchenouard.particletracking.VirtualSpot getPredictedStateAsSpot() {
        return new plugins.nchenouard.particletracking.VirtualSpot(x_pre.get(0, 0), x_pre.get(1, 0), 0);
    }

    public Spot buildSpotFromState(Matrix state) {
        return new Spot(state.get(0, 0), state.get(1, 0), 0);
    }

    public double getTotalGateLikelihood(boolean gated, double gateFactor) {
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

    public double normalizedInnovation(Spot s) {
        Matrix m = buildMeasurementMatrix(s);
        return normalizedInnovation(m);
    }

    public double normalizedInnovation(Matrix m) {
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

    public LikelihoodMap[] getLikelihoodMapsIMM(double gateFactor, int t) {

        ArrayList<Integer> enabledFilters = new ArrayList<Integer>();
        for (int i = 0; i < r; i++) {
            if (filter_i[i] instanceof KF2dDirected || filter_i[i] instanceof KF2dRandomWalk)
                enabledFilters.add(new Integer(i));
        }
        if (enabledFilters.size() > 0) {
            LikelihoodMap[] maps = new LikelihoodMap[enabledFilters.size()];
            int cnt = 0;
            for (Integer i : enabledFilters) {
                maps[cnt] = ((KalmanFilter) filter_i[i.intValue()]).getLikelihoodMap(gateFactor, t);
                cnt++;
            }
            int minX = maps[0].getOffsetX();
            int minY = maps[0].getOffsetY();
            int minZ = maps[0].getOffsetZ();
            int maxX = maps[0].getOffsetX() + maps[0].getWidth() - 1;
            int maxY = maps[0].getOffsetY() + maps[0].getHeight() - 1;
            int maxZ = maps[0].getOffsetZ() + maps[0].getDepth() - 1;
            for (int i = 1; i < maps.length; i++) {
                minX = (int) Math.min(minX, maps[i].getOffsetX());
                minY = (int) Math.min(minY, maps[i].getOffsetY());
                minZ = (int) Math.min(minZ, maps[i].getOffsetZ());
                maxX = (int) Math.max(maxX, maps[i].getOffsetX() + maps[i].getWidth() - 1);
                maxY = (int) Math.max(maxY, maps[i].getOffsetY() + maps[i].getHeight() - 1);
                maxZ = (int) Math.max(maxZ, maps[i].getOffsetZ() + maps[i].getDepth() - 1);
            }
            return maps;
        }
        else
            throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
    }

    public LikelihoodMap getLikelihoodMap(double gateFactor, int t) {
        if (likelihoodType == LikelihoodTypes.IMM_MAX_LIKELIHOOD)
            return ((KalmanFilter) filter_i[maxLikelihoodIndex]).getLikelihoodMap(gateFactor, t);
        else if (likelihoodType == LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD) {
            LikelihoodMap[] maps = new LikelihoodMap[filter_i.length];
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
                minX = (int) Math.min(minX, maps[i].getOffsetX());
                minY = (int) Math.min(minY, maps[i].getOffsetY());
                minZ = (int) Math.min(minZ, maps[i].getOffsetZ());
                maxX = (int) Math.max(maxX, maps[i].getOffsetX() + maps[i].getWidth() - 1);
                maxY = (int) Math.max(maxY, maps[i].getOffsetY() + maps[i].getHeight() - 1);
                maxZ = (int) Math.max(maxZ, maps[i].getOffsetZ() + maps[i].getDepth() - 1);
            }
            LikelihoodMap lmap = new LikelihoodMap(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, minX, minY, minZ);
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                LikelihoodMap map = maps[cnt];
                double p = m_est_i[cnt];
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

    public LikelihoodMap getLikelihoodMap(boolean gated, double gateFactor, int t, int width, int height, int depth) {
        if (likelihoodType == LikelihoodTypes.IMM_MAX_LIKELIHOOD) {
            return ((KalmanFilter) filter_i[maxLikelihoodIndex]).getLikelihoodMap(gated, gateFactor, t, width, height, depth);
        }
        else if (likelihoodType == LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD) {

            LikelihoodMap[] maps = new LikelihoodMap[filter_i.length];
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                maps[cnt] = ((KalmanFilter) filter_i[cnt]).getLikelihoodMap(gated, gateFactor, t, width, height, depth);
                cnt++;
            }
            LikelihoodMap lmap = new LikelihoodMap(width, height, depth, 0, 0, 0);
            for (int cnt = 0; cnt < filter_i.length; cnt++) {
                LikelihoodMap map = maps[cnt];
                double p = m_est_i[cnt];
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

    public void update(Matrix z, int t) {
        correct(z);
        if (z != null)
            updateTime(t);
        predict(t);
    }

    public void update(Spot s, int t) {
        Matrix z = buildMeasurementMatrix(s);
        update(z, t);
    }

    public void updateTime(int t) {
        for (int c = 0; c < r; c++)
            filter_i[c].updateTime(t);
    }

    public VirtualSpot getCurrentPredictedStateAsSpot() {
        switch (likelihoodType) {
            case IMM_WEIGHTED_LIKELIHOOD: {
                double x = 0;
                double y = 0;
                double z = 0;
                for (int c = 0; c < r; c++) {
                    VirtualSpot prediction = filter_i[c].getCurrentPredictedStateAsSpot();
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
	 * @param gateFactor double
	 * @param t int
	 * @return double
     */
    public double getMinLikelihoodInGate(double gateFactor, int t) {
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
	 * @param gateFactor double
	 * @return double
     */
    public double getCurrentMinLikelihoodInGate(double gateFactor) {
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

    //FIXME implement inherited functions
    public Matrix getEstimatedState(int t) {
        return null;
    }

    public Matrix getPredictedMeasurement(int t) {
        return null;
    }

    public Matrix getPredictedState(int t) {
        return null;
    }

    public VirtualSpot getPredictedStateAsSpot(int t) {
        return null;
    }

    public Matrix getStateErrorCovariance(int t) {
        return null;
    }

    public ArrayList<Area> getGates(double gateFactor, int t) {
        return null;
    }
}

