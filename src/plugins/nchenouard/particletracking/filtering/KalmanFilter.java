package plugins.nchenouard.particletracking.filtering;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;

import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;


/**
 * This abstract class provide a structure for a linear Kalman Filtering estimator (KF) 
 * version 1.0 was from Auguste Genovesio
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public abstract class KalmanFilter implements Predictor
{

	protected final static double _2PI = 2.0 * Math.PI;
	/**predicted state*/
	protected Matrix x_pre;
	 /**estimated state*/
	protected Matrix x_est;
	/**predicated covariance matrix*/
	protected Matrix P_pre;
	/**estimated covariance matrix*/
	protected Matrix P_est;
	/**predicted measurement*/
	protected Matrix z_pre;
	/**measurement error*/
	protected Matrix z_err;
	/**process matrix*/
	protected Matrix F;
	/**noise matrix*/
	protected Matrix H;
	/**process noise covariance*/
	protected Matrix Q;
	/**measurement noise covariance*/
	protected Matrix R;
	/**covariance of the innovation of the process*/
	protected Matrix S;
	/**inverse of of innovation matrix*/
	protected Matrix S_1;
	protected Matrix W;
	/**initial process noise covariance*/
	protected Matrix Q0;

	/** update the process covariance during the tracking*/
	boolean updateCovariances = true;
	
	/** frame at which the estimation has started*/
	protected int t0 = 0;
	/** current frame*/
	protected int t = 0;
	
	/** default constructor*/
	public KalmanFilter(){}

	/** constructor specifying the process matrices*/
	public KalmanFilter(Matrix F, Matrix H, Matrix Q, Matrix R)
	{
		if(F.getRowDimension() != F.getColumnDimension())
			throw new IllegalArgumentException("F (transition) is not square");
		if(Q.getRowDimension() != Q.getColumnDimension())
			throw new IllegalArgumentException("Q (transition noise covariance) is not square");
		if(R.getRowDimension() != R.getColumnDimension())
			throw new IllegalArgumentException("R (measurement noise covariance) is not square");

		if(F.getRowDimension() != Q.getRowDimension())
			throw new IllegalArgumentException("row dimension of F is not the same as row dimension of Q");
		if(F.getColumnDimension() != Q.getColumnDimension())
			throw new IllegalArgumentException("column dimension of F is not the same as column dimension of Q");

		if(F.getRowDimension() != H.getColumnDimension())
			throw new IllegalArgumentException("row dimension of F is not the same as column dimension of H");
		if(H.getRowDimension() != R.getRowDimension())
			throw new IllegalArgumentException("column dimension of H is not the same as column dimension of R");
		this.F = F;
		this.H = H;
		this.Q = Q;
		this.R = R;
	};
	
	/** build the measurement matrix corresponding to a spot*/
	abstract public Matrix buildMeasurementMatrix(Spot s);
	
	/** compute the predicted covariance matrix*/
	public Matrix buildPredCovMatrix(int t)
	{
		if (t>this.t)
		{
			Matrix P = F.times(P_est).times(F.transpose()).plus(Q);
			for (int i = this.t+1; i < t; i++)
			{
				P = F.times(P).times(F.transpose()).plus(Q);
			}
			return P;
		}
		else if (t==this.t)
			return P_pre;
		else
			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
	}
	
	/** build the ellipse corresponding to the search area defined by the gate factor*/
	//TODO: in 3D we should return a 3 ellipsoid
	private Area builtEllipse(Matrix z_pre, Matrix S, double gateFactor)
	{
		S = S.getMatrix(0,1,0,1);
		//int sx = (int) (gateFactor*Math.sqrt(S.get(0, 0)));
		//int sy =  (int) (gateFactor*Math.sqrt(S.get(1, 1)));
		EigenvalueDecomposition evd = S.eig();
		Matrix D = evd.getD();
		Matrix V = evd.getV(); 
		double norm1 = Math.sqrt(Math.pow(V.get(0,0),2)+Math.pow(V.get(1,0),2));
		double norm2 = Math.sqrt(Math.pow(V.get(0,1),2)+Math.pow(V.get(1,1),2));
		double cos   = V.get(0,0)/norm1;
		double sin   = V.get(1,0)/norm2;
		double alpha;
		if(sin >= 0)
			alpha =   Math.acos(cos);
		else
			alpha = - Math.acos(cos);
		int zx   = (int) (z_pre.get(0,0));
		int zy   = (int) (z_pre.get(1,0));
		int l1 = (int) (gateFactor*Math.sqrt(D.get(0,0)));
		int l2 = (int) (gateFactor*Math.sqrt(D.get(1,1)));
		AffineTransform atx = new AffineTransform(); 
		atx.rotate(alpha, zx, zy);
		atx.translate(-l1, -l2);
		Area gate = new Area(new Ellipse2D.Double(zx, zy, l1*2, l2*2)).createTransformedArea(atx); 
		return gate;
	} 
	
	@Override
	public Object clone()
	{
		KalmanFilter newKf= (KalmanFilter) copyInit();

		if ( x_pre != null ) newKf.x_pre = (Matrix) this.x_pre.clone();
		if ( x_est != null ) newKf.x_est = (Matrix) this.x_est.clone();

		if ( P_pre != null ) newKf.P_pre = (Matrix) this.P_pre.clone();
		if ( P_est != null ) newKf.P_est = (Matrix) this.P_est.clone();
		if ( z_pre != null ) newKf.z_pre = (Matrix) this.z_pre.clone();
		if ( z_err != null ) newKf.z_err = (Matrix) this.z_err.clone();

		if ( F != null ) newKf.F = (Matrix) this.F.clone();
		if ( H != null ) newKf.H = (Matrix) this.H.clone();
		if ( Q != null ) newKf.Q = (Matrix) this.Q.clone();
		if ( R != null ) newKf.R = (Matrix) this.R.clone();
		if ( S != null ) newKf.S = (Matrix) this.S.clone();
		if ( S_1 != null ) newKf.S_1 = (Matrix) this.S_1.clone();
		if ( W != null ) newKf.W = (Matrix) this.W.clone();

		if ( Q0 != null ) newKf.Q0 = (Matrix) this.Q0.clone();

		return newKf;
	}
	
	/** copy all the data contained in this KF into another KF*/
	protected void copyMyDataInKF(KalmanFilter kf)
	{
		kf.setTrackingCovariances(Q0.copy());
		if (x_pre!=null) kf.x_pre = x_pre.copy();
		if (x_est!=null) kf.x_est = x_est.copy();
		if (P_pre!=null) kf.P_pre = P_pre.copy();
		if (P_est!=null)kf.P_est = P_est.copy();
		if (z_pre!=null) kf.z_pre = z_pre.copy();
		if (z_err!=null) kf.z_err = z_err.copy();
		if (F!=null) kf.F = F.copy();
		if (H!=null) kf.H = H.copy();
		if (Q!=null) kf.Q = Q.copy();
		if (R!=null) kf.R = R.copy();
		if (S!=null) kf.S = S.copy();
		if (S_1!=null) kf.S_1 = S_1.copy();
		if (W!=null) kf.W = W.copy();
		if (Q0!=null) kf.Q0 = Q0.copy();
		kf.setUpdateCovariances(this.updateCovariances);
		kf.t = t;
	}
	
	/** correct the current predictions with the inputed measurement*/
	public void correct(Matrix z)
	{
		if(z.getRowDimension() != H.getRowDimension())
			throw new IllegalArgumentException("row dimension of z (is "+z.getRowDimension()+" should be "+H.getRowDimension()+") is not the same as row dimension of H");
		if(z.getColumnDimension() != 1)
			throw new IllegalArgumentException("z is not a vector");
		z_err = z.minus(z_pre);             // innovation (measurement error)
		x_est = x_pre.plus(W.times(z_err)); // update state estimation	
		P_est = P_pre.minus(W.times(S).times(W.transpose())); // update state covariance of innovation P(k+1|k+1)
		covupdate();
	}
	
	/** update the covariance matrices*/
	protected void covupdate()
	{
		if (updateCovariances)
		{
			//factor used for the update of Q: Q = b*Q + a*(x_est - x_pre)*(x_est - x_pre)T + c*Q0
			double a= 0.15;  // error
			double b= 0.75;  // Q
			double c= 0.10;  // Q0
			try {
				if ((x_est.minus(x_pre)).normF() != 0)
				{ 
					Q=Q.times(b).plus( ((x_est.minus(x_pre)).times((x_est.minus(x_pre)).transpose())).times(a)).plus(Q0.times(c));
				}
				double maxIncrease = 2;
				double maxDecrease = 0.5;
				for (int i = 0; i <Q.getColumnDimension(); i++)
					for (int j = 0; j < Q.getRowDimension(); j++)
					{
						Q.set(i, j, Math.min(Q0.get(i, j)*maxIncrease, Math.max(Q0.get(i, j)*maxDecrease, Q.get(i, j))));
					}
			} catch (RuntimeException e) {
				System.out.println("error in KalmanFiter.covupdate:");
				if (x_est == null) System.out.println("xest null");
				if (x_pre == null) System.out.println("xpre null");
				if (Q == null) System.out.println("Q null");
				if (Q0 == null) System.out.println("Q0 null");
				e.printStackTrace();
			}
		}
	}
	
	/** get the current gate as a cubic area*/
	public double[][] getCurrentCubicGate(double gateFactor)
	{
		//TODO 3D
		double[][] bounds = new double[2][2];//pour l'instant test en 2D
		bounds[0][0] = z_pre.get(0, 0) - gateFactor*Math.sqrt(S.get(0,0));
		bounds[0][1] = z_pre.get(0, 0) + gateFactor*Math.sqrt(S.get(0,0));
		bounds[1][0] = z_pre.get(1, 0) - gateFactor*Math.sqrt(S.get(1,1));
		bounds[1][1] = z_pre.get(1, 0) + gateFactor*Math.sqrt(S.get(1,1));
		return bounds;
	}

	/** get the current estimate of the target state*/
	public Matrix getCurrentEstimatedState()
	{
		return x_est;
	}
	
	/** get the current gates as a list of areas*/
	public ArrayList<Area> getCurrentGate(double gateFactor)
	{
		ArrayList<Area> gates = new ArrayList<Area>();
		gates.add(builtEllipse(z_pre, S, gateFactor));
		return gates;
	}
	
	/** get the current predicted measurement*/
	public Matrix  getCurrentPredictedMeasurement()
	{
		return z_pre;
	}
	
	/** get the current predicted target state*/
	public Matrix getCurrentPredictedState()
	{
		return x_pre;
	}

	/** get the current state error covariance*/
	public Matrix getCurrentStateErrorCovariance()
	{
		return P_est;
	}
	
	/** get the measurement error covariance at a given frame*/
	public Matrix getMeasurementErrorCovariance(int t)
	{
		return H.times(getStateErrorCovariance(t)).times(H.transpose()).plus(R);
	}
	
	/** build the target position likelihood map at a given frame for a specified area*/
	public LikelihoodMap getLikelihoodMap(boolean gated, double gateFactor, int t, int width, int height, int depth)
	{
		if (t<=this.t)
			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
		if (this instanceof Predictor2D)
		{
			Predictor2D p2D = (Predictor2D) this;
			double xPred = p2D.getXCoordPredicted();
			double yPred = p2D.getYCoordPredicted();
			Matrix covMeas = getMeasurementErrorCovariance(t);
			//build the cubic gate in 2D
			int  minX = 0;
			int maxX = width-1;
			int minY = 0;
			int maxY = height -1;
			if (gated)
			{
				minX = (int)Math.max(xPred - gateFactor*Math.sqrt(covMeas.get(0,0)), minX);
				maxX = (int)Math.min(Math.floor(xPred + gateFactor*Math.sqrt(covMeas.get(0,0)))+1, maxX);
				minY = (int)Math.max(yPred - gateFactor*Math.sqrt(covMeas.get(1,1)), minY);
				maxY = (int)Math.min(Math.floor(yPred + gateFactor*Math.sqrt(covMeas.get(1,1)))+1, maxY);
			}
			LikelihoodMap map = new LikelihoodMap(width, height);
			for (int y = minY; y <=maxY; y++)
				for (int x = minX; x <=maxX; x++)
				{
					Spot s = new Spot(x, y, 0);
					double l = likelihood(s, false, 4);
					map.setLikelihood(x, y, l);
				}
			return map;
		}
		else if (this instanceof KF3dRandomWalk || this instanceof KF3dDirected)
		{
			return null;
//			Predictor3D p3D = (Predictor3D) this;
//			double xPred = p3D.getXCoordPredicted();
//			double yPred = p3D.getXCoordPredicted();
//			Matrix covMeas = p3D.getMeasurementErrorCovariance2D(t);
//			int  minX = 0;
//			int maxX = width-1;
//			int minY = 0;
//			int maxY = height -1;
//			int minZ = 0;
//			int maxZ = height -1;
//			if (gated)
//			{
//			minX = (int)Math.max(predMeasurement.get(0, 0) - gateFactor*Math.sqrt(covMeas.get(0,0)), minX);
//			maxX = (int)Math.min(Math.floor(predMeasurement.get(0, 0) + gateFactor*Math.sqrt(covMeas.get(0,0)))+1, maxX);
//			minY = (int)Math.max(predMeasurement.get(1, 0) - gateFactor*Math.sqrt(covMeas.get(1,1)), minY);
//			maxY = (int)Math.min(Math.floor(predMeasurement.get(1, 0) + gateFactor*Math.sqrt(covMeas.get(1,1)))+1, maxY);
//			minZ = (int)Math.max(predMeasurement.get(2, 0) - gateFactor*Math.sqrt(covMeas.get(2,2)), minZ);
//			maxZ = (int)Math.min(Math.floor(predMeasurement.get(2, 0) + gateFactor*Math.sqrt(covMeas.get(2,2)))+1, maxZ);
//			}
//			LikelihoodMap map = new LikelihoodMap(width, height, depth);
//			for (int z = minZ; z <=maxZ; z++)
//			for (int y = minY; y <=maxY; y++)
//			for (int x = minX; x <=maxX; x++)
//			{
//			Spot s = new Spot(x, y, z);
//			Matrix z_meas = buildMeasurementMatrix(s);
//			Matrix z_err = z_meas.minus(predMeasurement);
//			//check if it's in the ellipsoide gate
//			double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
//			if(d2 <= Math.pow(gateFactor,2))
//			{
//			// at the end, if needed, compute likelihood  	
//			double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
//			map.setLikelihood(x, y, z, d1 * Math.exp(-0.5 * d2));
//			}
//			}
//			return map;
		}
		else
			throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
	}

	/** build the target position likelihood map at a given frame*/
	public LikelihoodMap getLikelihoodMap(double gateFactor, int t)
	{
		if (t<=this.t)
			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
		if (this instanceof Predictor2D)
		{
			Predictor2D p2D = (Predictor2D) this;
			double xPred = p2D.getXCoordPredicted();
			double yPred = p2D.getYCoordPredicted();
			Matrix covMeas = getMeasurementErrorCovariance(t);
			//build the cubic gate in 2D
			int minX = (int)(xPred - gateFactor*Math.sqrt(covMeas.get(0,0)));
			int maxX = (int)(Math.floor(xPred + gateFactor*Math.sqrt(covMeas.get(0,0))));
			int minY = (int)(yPred - gateFactor*Math.sqrt(covMeas.get(1,1)));
			int maxY = (int)(Math.floor(yPred + gateFactor*Math.sqrt(covMeas.get(1,1)))+1);
			LikelihoodMap map = new LikelihoodMap(maxX-minX+1, maxY-minY+1, minX, minY);
			for (int y = minY; y <=maxY; y++)
				for (int x = minX; x <=maxX; x++)
				{
					Spot s = new Spot(x, y, 0);
					double l = likelihood(s, false, 4);
					map.setLikelihood(x, y, l);
				}
			return map;
		}
		else if (this instanceof KF3dRandomWalk || this instanceof KF3dDirected)
		{
			return null;
//			Predictor3D p3D = (Predictor3D) this;
//			double xPred = p3D.getXCoordPredicted();
//			double yPred = p3D.getXCoordPredicted();
//			Matrix covMeas = p3D.getMeasurementErrorCovariance2D(t);
//			int  minX = 0;
//			int maxX = width-1;
//			int minY = 0;
//			int maxY = height -1;
//			int minZ = 0;
//			int maxZ = height -1;
//			if (gated)
//			{
//			minX = (int)Math.max(predMeasurement.get(0, 0) - gateFactor*Math.sqrt(covMeas.get(0,0)), minX);
//			maxX = (int)Math.min(Math.floor(predMeasurement.get(0, 0) + gateFactor*Math.sqrt(covMeas.get(0,0)))+1, maxX);
//			minY = (int)Math.max(predMeasurement.get(1, 0) - gateFactor*Math.sqrt(covMeas.get(1,1)), minY);
//			maxY = (int)Math.min(Math.floor(predMeasurement.get(1, 0) + gateFactor*Math.sqrt(covMeas.get(1,1)))+1, maxY);
//			minZ = (int)Math.max(predMeasurement.get(2, 0) - gateFactor*Math.sqrt(covMeas.get(2,2)), minZ);
//			maxZ = (int)Math.min(Math.floor(predMeasurement.get(2, 0) + gateFactor*Math.sqrt(covMeas.get(2,2)))+1, maxZ);
//			}
//			LikelihoodMap map = new LikelihoodMap(width, height, depth);
//			for (int z = minZ; z <=maxZ; z++)
//			for (int y = minY; y <=maxY; y++)
//			for (int x = minX; x <=maxX; x++)
//			{
//			Spot s = new Spot(x, y, z);
//			Matrix z_meas = buildMeasurementMatrix(s);
//			Matrix z_err = z_meas.minus(predMeasurement);
//			//check if it's in the ellipsoide gate
//			double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
//			if(d2 <= Math.pow(gateFactor,2))
//			{
//			// at the end, if needed, compute likelihood  	
//			double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
//			map.setLikelihood(x, y, z, d1 * Math.exp(-0.5 * d2));
//			}
//			}
//			return map;
		}
		else
			throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
		
//		if (t>this.t)
//		{
//		//first build the likelihood function
//			Matrix predState = getPredictedState(t);
//			Matrix predMeasurement = H.times(predState);
//			Matrix covPred = buildPredCovMatrix(t);
//			Matrix covMeas = H.times(covPred).times(H.transpose()).plus(R);
//
//			if (this instanceof KF2dDirected)
//			{
//				//build the cubic gate in 2D
//				int  minX = (int)(predMeasurement.get(0, 0) - gateFactor*Math.sqrt(covMeas.get(0,0)));
//				int maxX = (int)(Math.floor(predMeasurement.get(0, 0) + gateFactor*Math.sqrt(covMeas.get(0,0)))+1);
//				int minY = (int)(predMeasurement.get(1, 0) - gateFactor*Math.sqrt(covMeas.get(1,1)));
//				int maxY = (int)(Math.floor(predMeasurement.get(1, 0) + gateFactor*Math.sqrt(covMeas.get(1,1)))+1);
//				LikelihoodMap map = new LikelihoodMap(maxX-minX+1, maxY-minY+1, minX, minY);
//				for (int y = minY; y <=maxY; y++)
//					for (int x = minX; x <=maxX; x++)
//					{
//						Spot s = new Spot(x, y, 0);
//						Matrix z = buildMeasurementMatrix(s);
//						Matrix z_err = z.minus(predMeasurement);
//						//check if it's in the ellipsoide gate
//						double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
//						if(d2 <= Math.pow(gateFactor,2))
//						{
//							// at the end, if needed, compute likelihood  	
//							double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
//							map.setLikelihood(x, y, d1 * Math.exp(-0.5 * d2));
//						}
//					}
//				return map;
//			}
//			if (this instanceof KF2d)
//			{
//				//build the cubic gate in 2D
//				int  minX = (int)(predMeasurement.get(0, 0) - gateFactor*Math.sqrt(covMeas.get(0,0)));
//				int maxX = (int)(Math.floor(predMeasurement.get(0, 0) + gateFactor*Math.sqrt(covMeas.get(0,0)))+1);
//				int minY = (int)(predMeasurement.get(1, 0) - gateFactor*Math.sqrt(covMeas.get(1,1)));
//				int maxY = (int)(Math.floor(predMeasurement.get(1, 0) + gateFactor*Math.sqrt(covMeas.get(1,1)))+1);
//				LikelihoodMap map = new LikelihoodMap(maxX-minX+1, maxY-minY+1, minX, minY);
//				for (int y = minY; y <=maxY; y++)
//					for (int x = minX; x <=maxX; x++)
//					{
//						Spot s = new Spot(x, y, 0);
//						Matrix z = buildMeasurementMatrix(s);
//						Matrix z_err = z.minus(predMeasurement);
//						//check if it's in the ellipsoide gate
//						double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
//						if(d2 <= Math.pow(gateFactor,2))
//						{
//							// at the end, if needed, compute likelihood  	
//							double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
//							map.setLikelihood(x, y, d1 * Math.exp(-0.5 * d2));
//						}
//					}
//				return map;
//			}
//			else if (this instanceof KF3d)
//			{
////				build the cubic gate in 3D
//				int  minX = (int)(predMeasurement.get(0, 0) - gateFactor*Math.sqrt(covMeas.get(0,0)));
//				int maxX = (int)(Math.floor(predMeasurement.get(0, 0) + gateFactor*Math.sqrt(covMeas.get(0,0)))+1);
//				int minY = (int)(predMeasurement.get(1, 0) - gateFactor*Math.sqrt(covMeas.get(1,1)));
//				int maxY = (int)(Math.floor(predMeasurement.get(1, 0) + gateFactor*Math.sqrt(covMeas.get(1,1)))+1);
//				int minZ = (int)(predMeasurement.get(2, 0) - gateFactor*Math.sqrt(covMeas.get(2,2)));
//				int maxZ = (int)(Math.floor(predMeasurement.get(2, 0) + gateFactor*Math.sqrt(covMeas.get(2,2)))+1);
//				LikelihoodMap map = new LikelihoodMap(maxX-minX+1, maxY-minY+1, maxZ-minZ+1, minX, minY, minZ);
//				for (int z = minZ; z <=maxZ; z++)
//					for (int y = minY; y <=maxY; y++)
//						for (int x = minX; x <=maxX; x++)
//						{
//							Spot s = new Spot(x, y, z);
//							Matrix z_meas = buildMeasurementMatrix(s);
//							Matrix z_err = z_meas.minus(predMeasurement);
//							//check if it's in the ellipsoide gate
//							double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
//							if(d2 <= Math.pow(gateFactor,2))
//							{
//								// at the end, if needed, compute likelihood  	
//								double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
//								map.setLikelihood(x, y, z, d1 * Math.exp(-0.5 * d2));
//							}
//						}
//				return map;
//			}
//			else
//				throw new IllegalArgumentException("invalid KalmanFilter type to invoke getLikelihoodMap");
//		}
//		else
//			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
	}

	/** get the predicted state at a given frame*/
	public Matrix getPredictedState(int t)
	{
		if (t>this.t)
		{
			Matrix pred = F.times(x_est);
			for (int i = this.t+1; i < t; i++)
			{
				pred = F.times(pred);
			}
			return pred;
		}
		else if (t==this.t)
			return x_pre;
		else
			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
	};

	/** compute the total target state likelihood contained into the search gate*/
	//TODO real computation instead of approximated values
	public double getTotalGateLikelihood(boolean gated, double gateFactor)
	{
		if (gated)
		{
			if (gateFactor<1)
				return 0.5;
			if (gateFactor<2)
				return 0.7;
			if (gateFactor<3)
				return 0.85;
			if (gateFactor<4)
				return 0.95;
			if (gateFactor<5)
				return 0.99;
			if(gateFactor<8)
				return 0.999;
			else return 1;
		}
		else return 1;
	}

	/** initialize the KF with a first state and a first state covariance matrix*/
	public void init(Matrix x0, Matrix P0)
	{
		if(x0.getColumnDimension() != 1)
			throw new IllegalArgumentException("x0 is not a vector");
		if(x0.getRowDimension() != F.getRowDimension())
			throw new IllegalArgumentException("row dimension of x0 (is "+x0.getRowDimension()+" should be "+F.getRowDimension()+") is not the same as row dimension of F");
		if(P0.getRowDimension() != F.getColumnDimension())
			throw new IllegalArgumentException("row dimension of P0 (is "+P0.getRowDimension()+" should be "+F.getColumnDimension()+") is not the same as column dimension of F");
		this.x_est = x0;
		this.P_est = P0;
		predict();
	}

	/** check if the predictor is initializd*/
	public boolean initialized()
	{
		return (x_est != null) && (P_est != null);
	}
	
	/** build the first state matrix for a first measurement matrix*/
	abstract Matrix buildX0(Matrix firstElement);
	
	/** get the initial state covariance*/
	abstract Matrix getP0();
	
	/** initialize the KF with a first measurement at a given frame*/
	public void initWithFirstElement(Matrix firstElement, int t)
	{
		this.t = t;
		this.t0 = t;
		Matrix x0 = buildX0(firstElement);
		Matrix P0 = getP0();
		init(x0, P0);
	}
	
	/** initialize the KF with a first spot at a given time*/
	public void initWithFirstElement(Spot spot, int t)
	{
		initWithFirstElement(buildMeasurementMatrix(spot), t);
	}

	/** compute the likelihood of a measurement*/
	public double likelihood(Matrix z, boolean gated, double gateFactor)
	{
		Matrix z_err = z.minus(z_pre); // local
		// first, check if it's in the rectangular gate (faster)
		if(gated)		
			for(int i=0; i<z_err.getRowDimension(); i++)
				if(Math.abs(z_err.get(i,0)) > gateFactor*Math.sqrt(S.get(i,i)) )
					return 0;
		// then, check if it's in the ellipsoide gate
		double d2 = mahalanobis(z);
		if(gated && d2 > Math.pow(gateFactor,2)) 
			return 0;
		// at the end, if needed, compute likelihood  	
		double d1 = 1.0 / ( Math.sqrt(S.times(_2PI).det()));
		return d1 * Math.exp(-0.5 * d2);
	}

	/**
	 * compute the likelihood of a spot as a measurement
	 */
	public double   likelihood(Spot s, boolean gated, double gateFactor)
	{
		Matrix z = buildMeasurementMatrix(s);
		return likelihood(z, gated, gateFactor);
	}

	/**
	 * compute the likelihood of a spot as a measurement at a given frame
	 */
	public double likelihood(Spot s, boolean gated, double gateFactor, int t)
	{
		if (t>=this.t)
		{
			Matrix z = buildMeasurementMatrix(s);
			Matrix predState = getPredictedState(t);
			Matrix predMeasurement = H.times(predState);
			Matrix z_err = z.minus(predMeasurement);
			Matrix covPred = buildPredCovMatrix(t);
			Matrix covMeas = H.times(covPred).times(H.transpose()).plus(R);
			// first, check if it's in the rectangular gate (faster)
			if(gated)		
				for(int i=0; i<z_err.getRowDimension(); i++)
					if(Math.abs(z_err.get(i,0)) > gateFactor*Math.sqrt(covMeas.get(i,i)) )
						return 0;
			// then, check if it's in the ellipsoide gate
			double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0,0);
			if(gated && d2 > Math.pow(gateFactor,2)) 
				return 0;
			// at the end, if needed, compute likelihood  	
			double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
			return d1 * Math.exp(-0.5 * d2);
		}
		else
			throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
	}
	
	/** compute the likelihood of a measurement in logarithmic scale*/
	public double loglikelihood(Matrix z)
	{
		Matrix z_err = z.minus(z_pre);
		return -(Math.log(S.times(_2PI).det()) + z_err.transpose().times(S_1).times(z_err).get(0,0))*0.5;
	}

	/** compute the likelihood of a spot as a measurement in logarithmic scale*/
	public double loglikelihood(Spot ds)
	{
		Matrix z = buildMeasurementMatrix(ds);
		return loglikelihood(z);
	}
	
	/** compute the likelihood of a state matrix in logarithmic scale*/
	@Override
	public double   loglikelihoodOfState(Matrix x)
	{
			Matrix x_err = x.minus(x_pre);
			Matrix P_1 = P_pre.inverse();
			return -(Math.log(P_pre.times(_2PI).det()) + x_err.transpose().times(P_1).times(x_err).get(0,0))*0.5;
	}
	@Override
	public double   likelihoodOfState(Matrix x)
	{
		Matrix x_err = x.minus(x_pre); // local
		Matrix P_1 = P_pre.inverse();
		// first, check if it's in the rectangular gate (faster)
		return Math.exp(x_err.transpose().times(P_1).times(x_err).get(0,0)/2) / Math.sqrt(P_pre.times(_2PI).det());
	}
	
	@Override
	public double likelihoodOfPredictedState()
	{
		return 1.d / Math.sqrt(P_pre.times(_2PI).det());
	}
	
	/** compute mahalanobis distance between a measurement and the prediction*/
	protected double mahalanobis(Matrix z)
	{
		Matrix z_err = z.minus(z_pre); // local
		return z_err.transpose().times(S_1).times(z_err).get(0,0);
	}
	
	/** compute mahalanobis distance between a measurement (spot) and the prediction*/
	protected double mahalanobis(Spot s)
	{
		Matrix z = buildMeasurementMatrix(s);
		return mahalanobis(z);
	}
	
	/** get the minimum likelihood value for a measurement in the gate at a given time*/
	public double getMinLikelihoodInGate(double gateFactor, int t)
	{
		Matrix covPred = buildPredCovMatrix(t);
		Matrix covMeas = H.times(covPred).times(H.transpose()).plus(R);
		// first, check if it's in the rectangular gate (faster)
		double d2 = Math.pow(gateFactor,2);
		double d1 = 1.0 / ( Math.sqrt(covMeas.times(_2PI).det()));
		return d1 * Math.exp(-0.5 * d2);
	}
	
	/** get the minimum likelihood value for a measurement in the gate at the current time*/
	public double getCurrentMinLikelihoodInGate(double gateFactor)
	{
		double d2 = Math.pow(gateFactor,2);
		double d1 = 1.0 / ( Math.sqrt(S.times(_2PI).det()));
		return d1 * Math.exp(-0.5 * d2);
	}
	
	/** compute the normalized innovation of the process for a given measurement*/
	public double normalizedInnovation(Matrix m)
	{
		return this.mahalanobis(m);	
	}
	
	/** compute the normalized innovation of the process for a given spot*/
	public double normalizedInnovation(Spot s)
	{
		return this.mahalanobis(s);	
	}
	
	/** predict next state and update covariance matrices*/
	public void predict()
	{
			x_pre = F.times(x_est); // state prediction x~(k+1|k)
			z_pre = H.times(x_pre); // measurement prediction z~(k+1|k)
			P_pre = F.times(P_est).times(F.transpose()).plus(Q);// state prediction covariance P(k+1|k)
			S = H.times(P_pre).times(H.transpose()).plus(R);// innovation covariance S(k+1)
			S_1 = S.inverse();
			W = P_pre.times(H.transpose()).times(S_1);
	}
	
	/** predict the state and update covariance matrices at a given time*/
	public void predict(int t)
	{
		if (t== this.t)
			predict();
		else if (t > this.t)
		{
			x_pre = F.times(x_est);
			for (int i = this.t+1; i < t; i++)
				x_pre = F.times(x_pre);
			z_pre = H.times(x_pre);
			P_pre = F.times(P_est).times(F.transpose()).plus(Q);
			for (int i = this.t+1; i < t; i++)
				P_pre = F.times(P_pre).times(F.transpose()).plus(Q);
			S = H.times(P_pre).times(H.transpose()).plus(R);// innovation covariance S(k+1)
			S_1 = S.inverse();
			W = P_pre.times(H.transpose()).times(S_1);
		}
		else
			throw new IllegalArgumentException("cannot predict in the past in Kalman Filter");
	}

	/** set the covariance values of the filters*/
	public abstract void setTrackingCovariances(double[] trackingCov);
	
	/** set the covariance matrix of the filters*/
	public void setTrackingCovariances(Matrix Q0)
	{
		this.Q = Q0;
		this.Q0 = Q0;
	}
	
	/** specify wether to update the covariance of the filter during the tracking*/
	public void setUpdateCovariances(boolean update)
	{
		this.updateCovariances = update;
	}
	
	/** update the filtering process with a given measurement at a given frame*/
	public void update(Matrix z, int t)
	{
		if (z!=null)
		{
			correct(z);
			this.t = t;
		}
		predict(t);
	}

	/** update the filtering process with a given spot at a given frame*/
	public void update(Spot s, int t)
	{
		Matrix z = buildMeasurementMatrix(s);
		update(z, t);
	}
	
	//***************** TODO: shift time
	/** get the estimated state at a future frame*/
	public Matrix getEstimatedState(int t) {
		return null;
	}
	
	/** get the predicted mesurement at a future frame*/
	public Matrix getPredictedMeasurement(int t) {
		if (this.t == t)
		{
			return z_pre;
		}
		else if (t > this.t)
		{
			Matrix pred = getPredictedState(t);
			return H.times(pred);
		}
		else
			throw new IllegalArgumentException("cannot get past predicted measurement in Kalman Filter");
	}

	/** get the predicted mesurement at a future frame as a spot*/
	public VirtualSpot getPredictedStateAsSpot(int t) {
		return null;
	}
	
	/** compute the state error covariance at a future frame*/
	public Matrix getStateErrorCovariance(int t)
	{
		Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
		for (int i = this.t+1; i < t; i++)
			Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
		return Ppre;
	}
	
	/** compute the search gates at a future frame*/
	//FIXME
	public ArrayList<Area> getGates(double gateFactor, int i)
	{
		return null;
	}
	
	/** modify the filter current time*/
	public void updateTime(int t)
	{
		this.t = t;
	}
}