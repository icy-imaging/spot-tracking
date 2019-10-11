package plugins.nchenouard.particletracking.filtering;

import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

/**
 * 2D Kalaman filter corresponding to directed motion
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class KF2dDirected extends KalmanFilter implements Predictor2D
{
	public KF2dDirected()
	{
		initMatrices();
	}
	
	@Override
	public Matrix buildMeasurementMatrix(Spot s) {
		if (s == null)
			return null;
		return new Matrix(new double[][]{{s.mass_center.x},{s.mass_center.y}});
	}

	@Override
	Matrix buildX0(Matrix firstElement) {
		Matrix x0;
//		 first state estimate
		x0 = new Matrix(4,1);
		x0.set(0, 0,firstElement.get(0,0));  // X_t 
		x0.set(1, 0,0);  // vx
		x0.set(2, 0,firstElement.get(1,0));  // Y_t
		x0.set(3, 0,0);  // vy
		return x0;
	}

	@Override
	Matrix getP0() {
		double[][] p0 = {
				{ 0.1, 0.0, 0.0, 0.0},  // X_t
				{ 0.0, 0.1, 0.0, 0.1},  // vx
				{ 0.0, 0.0, 0.1, 0.0},  // Y_t
				{ 0.0, 0.1, 0.0, 0.1}  // vy
		};
		return new Matrix(p0);
	}

	@Override
	public void setTrackingCovariances(double[] trackingCov)
	{
		Q.set(0,0,trackingCov[0]);
		Q.set(2,2,trackingCov[1]);
		Q0 = Q;
	}

	public Spot buildSpotFromState(Matrix state) {
		if (state == null)
			return null;
		return new Spot(state.get(0, 0), state.get(2, 0), 0);
	}

	public Predictor copy() {
		KF2dDirected kf =  new KF2dDirected();
		copyMyDataInKF(kf);
		return kf;
	}

	public Predictor copyInit() {
		KF2dDirected kf =  new KF2dDirected();
		kf.setTrackingCovariances(Q0);
		kf.t = t;
		kf.setUpdateCovariances(this.updateCovariances);
		return kf;
	}

	public VirtualSpot getCurrentPredictedStateAsSpot()
	{
		return new VirtualSpot(x_pre.get( 0 , 0), x_pre.get(2 , 0), 0);
	}

	private void initMatrices()
	{
		double[][] f = {
					{ 1.0, 1.0, 0.0, 0.0},  // X_t
					{ 0.0, 1.0, 0.0, 0.0},  // vx
					{ 0.0, 0.0, 1.0, 1.0},  // Y_t
					{ 0.0, 0.0, 0.0, 1.0}};  // vy
		F = new Matrix(f);
		// dynamic noise
		double[][] q = {
				{ 2, 0.0, 0.0, 0.0},  // X_t
				{ 0.0, 3.0, 0.0, 3.0},  // vx
				{ 0.0, 0.0, 2, 0.0},  // Y_t
				{ 0.0, 3.0, 0.0, 3.0}}; // vy
		Q = new Matrix(q);
		
//		 observation
		double[][] h = {
				{ 1.0, 0.0, 0.0, 0.0},  // X_t
				{ 0.0, 0.0, 1.0, 0.0}};  // X_t
		H = new Matrix(h);
		// observation noise
		double[][] r = {
				{ 2, 0.0},  // X_t
				{ 0.0, 2}};  // Y_t
		R = new Matrix(r);
	}
	
	@Override
	public void correct(Matrix z)
	{
		if (this.t-t0==1)
		{
			//update the velocity with the observed displacement
			Matrix x0 = x_est;
			x0.set(1, 0, z.get(0, 0)-x_est.get(0, 0));
			x0.set(3, 0, z.get(1, 0)-x_est.get(2, 0));
			init(x0, P_est);
			
		}
		if(z.getRowDimension() != H.getRowDimension())
			throw new IllegalArgumentException("row dimension of z (is "+z.getRowDimension()+" should be "+H.getRowDimension()+") is not the same as row dimension of H");
		if(z.getColumnDimension() != 1)
			throw new IllegalArgumentException("z is not a vector");
		z_err = z.minus(z_pre);             // innovation (measurement error)
//		System.out.println("======");
//		System.out.println(x_est.get(0, 0) +" "+ x_est.get(1, 0)+" "+x_est.get(2, 0)+" "+x_est.get(3, 0));
		x_est = x_pre.plus(W.times(z_err)); // update state estimation	
//		System.out.println(x_est.get(0, 0) +" "+ x_est.get(1, 0)+" "+x_est.get(2, 0)+" "+x_est.get(3, 0));
//		System.out.println("======");
		P_est = P_pre.minus(W.times(S).times(W.transpose())); // update state covariance of innovation P(k+1|k+1)
		if (this.t-t0!=1)
			covupdate();
	}
	
	public double getXCoordEstimated()
	{
		return x_est.get(0,0);
	}
	
	public double getYCoordEstimated()
	{
		return x_est.get(2,0);
	}
	
	public double getXCoordPredicted()
	{
		return x_pre.get(0,0);
	}
	public double getYCoordPredicted()
	{
		return x_pre.get(2,0);
	}

	@Override
	public Matrix getStateErrorCovariance2D(int t) {
		Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
		for (int i = this.t+1; i < t; i++)
			Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
		Matrix Ppre2D = new Matrix(2,2);
		Ppre2D.set(0, 0, Ppre.get(0, 0));
		Ppre2D.set(0, 1, Ppre.get(0, 2));
		Ppre2D.set(1, 0, Ppre.get(2, 0));
		Ppre2D.set(1, 1, Ppre.get(2, 2));
		return Ppre2D;
	}

	@Override
	public Matrix getCurrentStateErrorCovariance2D() {
		Matrix P2D = new Matrix(2,2);
		P2D.set(0, 0, P_est.get(0, 0));
		P2D.set(0, 1, P_est.get(0, 2));
		P2D.set(1, 0, P_est.get(2, 0));
		P2D.set(1, 1, P_est.get(2, 2));
		return P2D;
	}

	@Override
	public Matrix getCurrentEstimatedState2D() {
		Matrix xest2D = new Matrix(2,1);
		xest2D.set(0, 0, x_est.get(0, 0));
		xest2D.set(1, 0, x_est.get(2, 0));
		return xest2D;
	}

	@Override
	public void setCurrentEstimatedState2D(Matrix m) {
		x_est.set(0, 0, m.get(0, 0));
		x_est.set(2, 0, m.get(1, 0));
	}

	@Override
	public void setCurrentStateErrorCovariance2D(Matrix m)
	{
		P_est.set(0, 0, m.get(0, 0));
		P_est.set(0, 2, m.get(0, 1));
		P_est.set(2, 0, P_est.get(1, 0));
		P_est.set(2, 2, P_est.get(1, 1));
	}

	@Override
	public Matrix getCurrentPredictedState2D()
	{
		Matrix xpre2D = new Matrix(2,1);
		xpre2D.set(0, 0, x_pre.get(0, 0));
		xpre2D.set(1, 0, x_pre.get(2, 0));
		return xpre2D;
	}
}