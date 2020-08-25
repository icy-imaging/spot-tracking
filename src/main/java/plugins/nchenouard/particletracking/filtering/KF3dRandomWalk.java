package plugins.nchenouard.particletracking.filtering;

import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

/**
 * 3D Kalman filter corresponding to random walk motion (diffusion)
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class KF3dRandomWalk extends KalmanFilter implements Predictor3D
{
	public KF3dRandomWalk()
	{
		initMatrices();
	}
	
	@Override
	public Matrix buildMeasurementMatrix(Spot s) {
		if (s == null)
			return null;
		return new Matrix(new double[][]{{s.mass_center.x},{s.mass_center.y},{s.mass_center.z}});
}

	@Override
	Matrix buildX0(Matrix firstElement) {
		Matrix x0 = new Matrix(3,1);
		x0.set(0, 0,firstElement.get(0,0));  // X_t 
		x0.set(1, 0,firstElement.get(1,0));  // Y_t
		x0.set(2, 0,firstElement.get(2,0));  // Y_t
		return x0;
	}

	@Override
	Matrix getP0() {
		double[][] p0 = {
				{ 0.1, 0.0, 0.0},  // X_t
				{ 0.0, 0.1, 0.0},  // Y_t
				{ 0.0, 0.0, 0.1}  // Z_t
		};
		return new Matrix(p0);
		}

	@Override
	public void setTrackingCovariances(double[] trackingCov) {
		Q.set(0,0,trackingCov[0]);
		Q.set(1,1,trackingCov[1]);
		Q.set(2,2,trackingCov[2]);
		Q0 = Q;
	}

	public Spot buildSpotFromState(Matrix state) {
		if (state == null)
			return null;
		return new Spot(state.get(0, 0), state.get(1, 0), state.get(2,0));
	}

	public Predictor copy() {
		KF3dRandomWalk kf =  new KF3dRandomWalk();
		copyMyDataInKF(kf);
		return kf;
	}

	public Predictor copyInit() {
		KF3dRandomWalk kf =  new KF3dRandomWalk();
		kf.setTrackingCovariances(Q0);
		kf.t = t;
		kf.setUpdateCovariances(this.updateCovariances);
		return kf;
	}

	public VirtualSpot getCurrentPredictedStateAsSpot() {
		return new VirtualSpot(x_pre.get( 0 , 0), x_pre.get(1 , 0), x_pre.get(2, 0));
	}
	private void initMatrices()
	{
		double[][] f =
		{
				{ 1.0, 0.0, 0.0},  // X_t
				{ 0.0, 1.0, 0.0},  // Y_t
				{ 0.0, 0.0, 1.0},  // Y_t
		}; 
		F = new Matrix(f);
		// dynamic noise
		double[][] q = {
				{ 2, 0.0, 0.0},  // X_t
				{ 0.0, 2, 0.0},  // Y_t
				{ 0.0, 0.0, 2},  // Y_t
			};
		Q = new Matrix(q);
//		 observation
		double[][] h = {
				{ 1.0, 0.0, 0.0},  // X_t
				{ 0.0, 1.0, 0.0}, // Y_t
				{ 0.0, 0.0, 1.0} // Z_t
				};
		H = new Matrix(h);
		// observation noise
		double[][] r = {
				{ 2, 0.0, 0.0},  // X_t
				{ 0.0, 2, 0.0}, // Y_t
				{0.0, 0.0, 2}
			}; 
		R = new Matrix(r);
	}
	
	public double getXCoordEstimated()
	{
		return x_est.get(0,0);
	}
	
	public double getYCoordEstimated()
	{
		return x_est.get(1,0);
	}
	
	public double getZCoordEstimated()
	{
		return x_est.get(2,0);
	}
	
	public double getXCoordPredicted()
	{
		return x_pre.get(0,0);
	}
	
	public double getYCoordPredicted()
	{
		return x_pre.get(1,0);
	}
	
	public double getZCoordPredicted()
	{
		return x_pre.get(2,0);
	}

	@Override
	public void setCurrentStateErrorCovariance3D(Matrix m) {
		this.P_est = m;		
	}

	@Override
	public void setCurrentEstimatedState3D(Matrix m) {
		this.x_est = m;		
	}

	@Override
	public Matrix getStateErrorCovariance3D(int t) {
		Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
		for (int i = this.t+1; i < t; i++)
			Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
		return Ppre;	}

	@Override
	public Matrix getCurrentEstimatedState3D() {
		return x_est;
	}

	@Override
	public Matrix getCurrentStateErrorCovariance3D() {
		return P_est;
	}

	@Override
	public Matrix getCurrentPredictedState3D() {
		return x_pre;
	}
	
}
