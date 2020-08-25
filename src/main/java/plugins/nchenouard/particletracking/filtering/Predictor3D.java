package plugins.nchenouard.particletracking.filtering;

import Jama.Matrix;

/**
 * Predictor in the 3D case
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public interface Predictor3D extends Predictor
{
	/** get the estimation of the current x coordinate*/
	public double getXCoordEstimated();
	/** get the estimation of the current y coordinate*/
	public double getYCoordEstimated();
	/** get the estimation of the current z coordinate*/
	public double getZCoordEstimated();
	/** get the estimation of the predicted x coordinate*/
	public double getXCoordPredicted();
	/** get the estimation of the predicted y coordinate*/
	public double getYCoordPredicted();
	/** get the estimation of the predicted z coordinate*/
	public double getZCoordPredicted();
	/** set the state error covariance matrix*/
	public void setCurrentStateErrorCovariance3D(Matrix m);
	/** set the current state estimate*/
	public void setCurrentEstimatedState3D(Matrix m);
	/** get the state error covariance matrix at a given time*/
	public Matrix getStateErrorCovariance3D(int t);
	/** get the current state estimate*/
	public Matrix  getCurrentEstimatedState3D();
	/** get the current state error covariance matrix*/
	public Matrix getCurrentStateErrorCovariance3D();
	/** get the predicted state estimate*/
	public Matrix getCurrentPredictedState3D();
}