

package plugins.nchenouard.particletracking.filtering;

import java.awt.geom.Area;
import java.util.ArrayList;

import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

/**
 * This interface represent the model of a dynamic estimator.
 * @version 1.0, 11/10/2003 author: Auguste Genovesio
 * @version 2.0, 27/05/2008
 *
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public interface Predictor
{		
		/**build the measurement matrix from a spot*/
		public Matrix buildMeasurementMatrix(Spot spot);
		/**build a instance of Spot from a state*/
		public Spot buildSpotFromState(Matrix state);
		public Object clone();
		/**get a copy of the predictor: all matrices and various vectors are copied in new instances*/
		public Predictor copy();
		/**get a copy of the predictor, only parameters are copied, not measurement*/
		public Predictor copyInit();
		/**correct the filters with the given measurement*/
		public void correct(Matrix z);
		/**get the cube that contains the gate*/
		public double[][] getCurrentCubicGate(double gateFactor);
		/** Get the estimated state x(t|t) */
		public Matrix  getCurrentEstimatedState();
		/** get the current gate as an area*/
		public ArrayList<Area> getCurrentGate(double gateFactor);
		/** Compute the minimum likelihood in the gate*/
		public double getCurrentMinLikelihoodInGate(double gateFactor);
		/** Get the predicted measurement z(t|t-1) */
		public Matrix  getCurrentPredictedMeasurement();
		/** Get the predicted state x(t|t-1) */
		public Matrix   getCurrentPredictedState();
		/** get the current predicted state xt|t-1 as an instance of Spot*/
		public VirtualSpot getCurrentPredictedStateAsSpot();
		/** Get the state error covariance matrix P(t|t) */
		public Matrix  getCurrentStateErrorCovariance();
		/** Get the estimated state x(t|t) */
		public Matrix  getEstimatedState(int t);
		/**Get the gate at index i**/
		public ArrayList<Area> getGates(double gateFactor, int t);
		/** Compute the minimum likelihood in the gate at time t*/
		public double getMinLikelihoodInGate(double gateFactor, int t);
		/** Get the predicted measurement z(t|t-1) */
		public Matrix  getPredictedMeasurement(int t);
		/** Get the predicted state x(t|t-1) */
		public Matrix   getPredictedState(int t);
		/** Get the predicted state x(t|t-1) as a instance of Spot*/
		public VirtualSpot getPredictedStateAsSpot(int t);
		/** Get the state error covariance matrix P(t|t) */
		public Matrix  getStateErrorCovariance(int t);
		/** get the total likelihood contained in the gate*/
		public double getTotalGateLikelihood(boolean gated, double gateFactor);
		/** Initialize the state, state error covariance */
		public void init(Matrix x0, Matrix P0);
		/**Initialize matrices with first measurement**/
		public void initWithFirstElement(Matrix firstElement, int t);
		/**Initialize matrices with first spot**/
		public void initWithFirstElement(Spot firstElement, int t);
		/** Compute the likelihood of the given measurement (after a prediction has been made) */
		public double   likelihood(Matrix z, boolean gated, double gateFactor);
		/** Compute the likelihood of the given measurement (after a prediction has been made) */
		public double   likelihood(Spot s, boolean gated, double gateFactor);
		/** Compute the likelihood of the given measurement (after a prediction has been made) */
		public double   loglikelihood(Matrix z);
		/** Compute the likelihood of the given measurement (after a prediction has been made) */
		public double   loglikelihood(Spot s);
		
		public double   likelihoodOfState(Matrix x);
		/** Compute the likelihood of the given measurement (after a prediction has been made) */
		public double   loglikelihoodOfState(Matrix x);
		public double likelihoodOfPredictedState();
		
		/**compute the normalized innovation for the measurement m*/
		public double normalizedInnovation(Matrix m);
		/**compute the normalized innovation for the spot s*/
		public double normalizedInnovation(Spot s);
		/** build the prediction*/
		public void predict();
		/** build the prediction at time t*/
		public void predict(int t);
		/** Set the covariances errors of the transition noise */
		public void setTrackingCovariances(double[] trackingCov);
		/**update the filters with the state z at time t*/
		public void     update(Matrix z, int t);
		/**update the filters with the spot at time t*/
		public void     update(Spot s, int t);
		/**update the filters without spot at time t*/
		public void updateTime(int t);
}

