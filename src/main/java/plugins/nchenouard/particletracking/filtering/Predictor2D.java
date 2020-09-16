package plugins.nchenouard.particletracking.filtering;

import Jama.Matrix;

/**
 * Predictor in the 2D case
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public interface Predictor2D extends Predictor {
    /**
     * Get the estimation of the current x coordinate
     * @return double
     */
    double getXCoordEstimated();

    /**
     * Get the estimation of the current y coordinate
     * @return double
     */
    double getYCoordEstimated();

    /**
     * Get the estimation of the predicted x coordinate
     * @return double
     */
    double getXCoordPredicted();

    /**
     * Get the estimation of the predicted y coordinate
     * @return double
     */
    double getYCoordPredicted();

    /**
     * Set the state error covariance matrix
     * @param m Matrix
     */
    void setCurrentStateErrorCovariance2D(Matrix m);

    /**
     * Set the current state estimate
     * @param m Matrix
     */
    void setCurrentEstimatedState2D(Matrix m);

    /**
     * Get the state error covariance matrix at a given time
     * @param t int
     * @return Matrix
     */
    Matrix getStateErrorCovariance2D(int t);

    /**
     * Get the current state estimate
     * @return Matrix
     */
    Matrix getCurrentEstimatedState2D();

    /**
     * Get the current state error covariance matrix
     * @return Matrix
     */
    Matrix getCurrentStateErrorCovariance2D();

    /**
     * Get the predicted state estimate
     * @return Matrix
     */
    Matrix getCurrentPredictedState2D();
}
