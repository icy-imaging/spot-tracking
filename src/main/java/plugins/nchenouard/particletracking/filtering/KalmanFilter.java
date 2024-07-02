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

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;

/**
 * This abstract class provide a structure for a linear Kalman Filtering estimator (KF)
 * version 1.0 was from Auguste Genovesio
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public abstract class KalmanFilter implements Predictor {
    protected final static double _2PI = 2.0 * Math.PI;
    /**
     * predicted state
     */
    protected Matrix x_pre;
    /**
     * estimated state
     */
    protected Matrix x_est;
    /**
     * predicated covariance matrix
     */
    protected Matrix P_pre;
    /**
     * estimated covariance matrix
     */
    protected Matrix P_est;
    /**
     * predicted measurement
     */
    protected Matrix z_pre;
    /**
     * measurement error
     */
    protected Matrix z_err;
    /**
     * process matrix
     */
    protected Matrix F;
    /**
     * noise matrix
     */
    protected Matrix H;
    /**
     * process noise covariance
     */
    protected Matrix Q;
    /**
     * measurement noise covariance
     */
    protected Matrix R;
    /**
     * covariance of the innovation of the process
     */
    protected Matrix S;
    /**
     * inverse of of innovation matrix
     */
    protected Matrix S_1;
    protected Matrix W;
    /**
     * initial process noise covariance
     */
    protected Matrix Q0;

    /**
     * update the process covariance during the tracking
     */
    boolean updateCovariances = true;

    /**
     * frame at which the estimation has started
     */
    protected int t0 = 0;
    /**
     * current frame
     */
    protected int t = 0;

    /**
     * default constructor
     */
    public KalmanFilter() {
    }

    /**
     * Constructor specifying the process matrices
     *
     * @param F Matrix
     * @param H Matrix
     * @param Q Matrix
     * @param R Matrix
     */
    public KalmanFilter(final @NotNull Matrix F, final Matrix H, final Matrix Q, final Matrix R) {
        if (F.getRowDimension() != F.getColumnDimension())
            throw new IllegalArgumentException("F (transition) is not square");
        if (Q.getRowDimension() != Q.getColumnDimension())
            throw new IllegalArgumentException("Q (transition noise covariance) is not square");
        if (R.getRowDimension() != R.getColumnDimension())
            throw new IllegalArgumentException("R (measurement noise covariance) is not square");

        if (F.getRowDimension() != Q.getRowDimension())
            throw new IllegalArgumentException("row dimension of F is not the same as row dimension of Q");
        if (F.getColumnDimension() != Q.getColumnDimension())
            throw new IllegalArgumentException("column dimension of F is not the same as column dimension of Q");

        if (F.getRowDimension() != H.getColumnDimension())
            throw new IllegalArgumentException("row dimension of F is not the same as column dimension of H");
        if (H.getRowDimension() != R.getRowDimension())
            throw new IllegalArgumentException("column dimension of H is not the same as column dimension of R");
        this.F = F;
        this.H = H;
        this.Q = Q;
        this.R = R;
    }

    /**
     * Build the measurement matrix corresponding to a spot
     *
     * @param s Spot
     * @return Matrix
     */
    @Override
    abstract public Matrix buildMeasurementMatrix(Spot s);

    /**
     * Compute the predicted covariance matrix
     *
     * @param t int
     * @return Matrix
     */
    public Matrix buildPredCovMatrix(final int t) {
        if (t > this.t) {
            Matrix P = F.times(P_est).times(F.transpose()).plus(Q);
            for (int i = this.t + 1; i < t; i++) {
                P = F.times(P).times(F.transpose()).plus(Q);
            }
            return P;
        }
        else if (t == this.t)
            return P_pre;
        else
            throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
    }

    /**
     * Build the ellipse corresponding to the search area defined by the gate factor
     *
     * @param z_pre      Matrix
     * @param S          Matrix
     * @param gateFactor double
     * @return Area
     */
    //TODO: in 3D we should return a 3 ellipsoid
    private Area builtEllipse(final Matrix z_pre, Matrix S, final double gateFactor) {
        S = S.getMatrix(0, 1, 0, 1);
        //int sx = (int) (gateFactor*Math.sqrt(S.get(0, 0)));
        //int sy =  (int) (gateFactor*Math.sqrt(S.get(1, 1)));
        final EigenvalueDecomposition evd = S.eig();
        final Matrix D = evd.getD();
        final Matrix V = evd.getV();
        final double norm1 = Math.sqrt(Math.pow(V.get(0, 0), 2) + Math.pow(V.get(1, 0), 2));
        final double norm2 = Math.sqrt(Math.pow(V.get(0, 1), 2) + Math.pow(V.get(1, 1), 2));
        final double cos = V.get(0, 0) / norm1;
        final double sin = V.get(1, 0) / norm2;
        final double alpha;
        if (sin >= 0)
            alpha = Math.acos(cos);
        else
            alpha = -Math.acos(cos);
        final int zx = (int) (z_pre.get(0, 0));
        final int zy = (int) (z_pre.get(1, 0));
        final int l1 = (int) (gateFactor * Math.sqrt(D.get(0, 0)));
        final int l2 = (int) (gateFactor * Math.sqrt(D.get(1, 1)));
        final AffineTransform atx = new AffineTransform();
        atx.rotate(alpha, zx, zy);
        atx.translate(-l1, -l2);
        return new Area(new Ellipse2D.Double(zx, zy, l1 * 2, l2 * 2)).createTransformedArea(atx);
    }

    @Override
    public Object clone() {
        final KalmanFilter newKf = (KalmanFilter) copyInit();

        if (x_pre != null) newKf.x_pre = (Matrix) this.x_pre.clone();
        if (x_est != null) newKf.x_est = (Matrix) this.x_est.clone();

        if (P_pre != null) newKf.P_pre = (Matrix) this.P_pre.clone();
        if (P_est != null) newKf.P_est = (Matrix) this.P_est.clone();
        if (z_pre != null) newKf.z_pre = (Matrix) this.z_pre.clone();
        if (z_err != null) newKf.z_err = (Matrix) this.z_err.clone();

        if (F != null) newKf.F = (Matrix) this.F.clone();
        if (H != null) newKf.H = (Matrix) this.H.clone();
        if (Q != null) newKf.Q = (Matrix) this.Q.clone();
        if (R != null) newKf.R = (Matrix) this.R.clone();
        if (S != null) newKf.S = (Matrix) this.S.clone();
        if (S_1 != null) newKf.S_1 = (Matrix) this.S_1.clone();
        if (W != null) newKf.W = (Matrix) this.W.clone();

        if (Q0 != null) newKf.Q0 = (Matrix) this.Q0.clone();

        return newKf;
    }

    /**
     * Copy all the data contained in this KF into another KF
     *
     * @param kf KalmanFilter
     */
    protected void copyMyDataInKF(final @NotNull KalmanFilter kf) {
        kf.setTrackingCovariances(Q0.copy());
        if (x_pre != null) kf.x_pre = x_pre.copy();
        if (x_est != null) kf.x_est = x_est.copy();
        if (P_pre != null) kf.P_pre = P_pre.copy();
        if (P_est != null) kf.P_est = P_est.copy();
        if (z_pre != null) kf.z_pre = z_pre.copy();
        if (z_err != null) kf.z_err = z_err.copy();
        if (F != null) kf.F = F.copy();
        if (H != null) kf.H = H.copy();
        if (Q != null) kf.Q = Q.copy();
        if (R != null) kf.R = R.copy();
        if (S != null) kf.S = S.copy();
        if (S_1 != null) kf.S_1 = S_1.copy();
        if (W != null) kf.W = W.copy();
        if (Q0 != null) kf.Q0 = Q0.copy();
        kf.setUpdateCovariances(this.updateCovariances);
        kf.t = t;
    }

    /**
     * Correct the current predictions with the inputed measurement
     *
     * @param z Matrix
     */
    @Override
    public void correct(final @NotNull Matrix z) {
        if (z.getRowDimension() != H.getRowDimension())
            throw new IllegalArgumentException("row dimension of z (is " + z.getRowDimension() + " should be " + H.getRowDimension() + ") is not the same as row dimension of H");
        if (z.getColumnDimension() != 1)
            throw new IllegalArgumentException("z is not a vector");
        z_err = z.minus(z_pre);             // innovation (measurement error)
        x_est = x_pre.plus(W.times(z_err)); // update state estimation
        P_est = P_pre.minus(W.times(S).times(W.transpose())); // update state covariance of innovation P(k+1|k+1)
        covupdate();
    }

    /**
     * Update the covariance matrices
     */
    protected void covupdate() {
        if (updateCovariances) {
            //factor used for the update of Q: Q = b*Q + a*(x_est - x_pre)*(x_est - x_pre)T + c*Q0
            final double a = 0.15;  // error
            final double b = 0.75;  // Q
            final double c = 0.10;  // Q0
            try {
                if ((x_est.minus(x_pre)).normF() != 0) {
                    Q = Q.times(b).plus(((x_est.minus(x_pre)).times((x_est.minus(x_pre)).transpose())).times(a)).plus(Q0.times(c));
                }
                final double maxIncrease = 2;
                final double maxDecrease = 0.5;
                for (int i = 0; i < Q.getColumnDimension(); i++)
                    for (int j = 0; j < Q.getRowDimension(); j++) {
                        Q.set(i, j, Math.min(Q0.get(i, j) * maxIncrease, Math.max(Q0.get(i, j) * maxDecrease, Q.get(i, j))));
                    }
            }
            catch (final RuntimeException e) {
                IcyLogger.error(this.getClass(), e, "Error in KalmanFiter.covupdate:");
                if (x_est == null) IcyLogger.error(this.getClass(), "xest null");
                if (x_pre == null) IcyLogger.error(this.getClass(), "xpre null");
                if (Q == null) IcyLogger.error(this.getClass(), "Q null");
                if (Q0 == null) IcyLogger.error(this.getClass(), "Q0 null");
            }
        }
    }

    /**
     * Get the current gate as a cubic area
     *
     * @param gateFactor double
     * @return 2D array of double
     */
    @Override
    public double[][] getCurrentCubicGate(final double gateFactor) {
        //TODO 3D
        final double[][] bounds = new double[2][2];//pour l'instant test en 2D
        bounds[0][0] = z_pre.get(0, 0) - gateFactor * Math.sqrt(S.get(0, 0));
        bounds[0][1] = z_pre.get(0, 0) + gateFactor * Math.sqrt(S.get(0, 0));
        bounds[1][0] = z_pre.get(1, 0) - gateFactor * Math.sqrt(S.get(1, 1));
        bounds[1][1] = z_pre.get(1, 0) + gateFactor * Math.sqrt(S.get(1, 1));
        return bounds;
    }

    /**
     * Get the current estimate of the target state
     *
     * @return Matrix
     */
    @Override
    public Matrix getCurrentEstimatedState() {
        return x_est;
    }

    /**
     * Get the current gates as a list of areas
     *
     * @param gateFactor double
     * @return ArrayList of Area
     */
    @Override
    public ArrayList<Area> getCurrentGate(final double gateFactor) {
        final ArrayList<Area> gates = new ArrayList<>();
        gates.add(builtEllipse(z_pre, S, gateFactor));
        return gates;
    }

    /**
     * Get the current predicted measurement
     * {@link KalmanFilter#z_pre}
     *
     * @return Matrix
     */
    @Override
    public Matrix getCurrentPredictedMeasurement() {
        return z_pre;
    }

    /**
     * Get the current predicted target state
     * {@link KalmanFilter#x_pre}
     *
     * @return Matrix
     */
    @Override
    public Matrix getCurrentPredictedState() {
        return x_pre;
    }

    /**
     * Get the current state error covariance
     * {@link KalmanFilter#P_est}
     *
     * @return Matrix
     */
    @Override
    public Matrix getCurrentStateErrorCovariance() {
        return P_est;
    }

    /**
     * Get the measurement error covariance at a given frame
     *
     * @param t int
     * @return Matrix
     */
    public Matrix getMeasurementErrorCovariance(final int t) {
        return H.times(getStateErrorCovariance(t)).times(H.transpose()).plus(R);
    }

    /**
     * Build the target position likelihood map at a given frame for a specified area
     *
     * @param gated      bool
     * @param gateFactor double
     * @param t          int
     * @param width      int
     * @param height     int
     * @param depth      int
     * @return LikelihoodMap
     */
    public LikelihoodMap getLikelihoodMap(final boolean gated, final double gateFactor, final int t, final int width, final int height, final int depth) {
        if (t <= this.t)
            throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
        if (this instanceof final Predictor2D p2D) {
            final double xPred = p2D.getXCoordPredicted();
            final double yPred = p2D.getYCoordPredicted();
            final Matrix covMeas = getMeasurementErrorCovariance(t);
            //build the cubic gate in 2D
            int minX = 0;
            int maxX = width - 1;
            int minY = 0;
            int maxY = height - 1;
            if (gated) {
                minX = (int) Math.max(xPred - gateFactor * Math.sqrt(covMeas.get(0, 0)), minX);
                maxX = (int) Math.min(Math.floor(xPred + gateFactor * Math.sqrt(covMeas.get(0, 0))) + 1, maxX);
                minY = (int) Math.max(yPred - gateFactor * Math.sqrt(covMeas.get(1, 1)), minY);
                maxY = (int) Math.min(Math.floor(yPred + gateFactor * Math.sqrt(covMeas.get(1, 1))) + 1, maxY);
            }
            final LikelihoodMap map = new LikelihoodMap(width, height);
            for (int y = minY; y <= maxY; y++)
                for (int x = minX; x <= maxX; x++) {
                    final Spot s = new Spot(x, y, 0);
                    final double l = likelihood(s, false, 4);
                    map.setLikelihood(x, y, l);
                }
            return map;
        }
        else if (this instanceof KF3dRandomWalk || this instanceof KF3dDirected) {
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

    /**
     * Build the target position likelihood map at a given frame
     *
     * @param gateFactor double
     * @param t          int
     * @return LikelihoodMap
     */
    public LikelihoodMap getLikelihoodMap(final double gateFactor, final int t) {
        if (t <= this.t)
            throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
        if (this instanceof final Predictor2D p2D) {
            final double xPred = p2D.getXCoordPredicted();
            final double yPred = p2D.getYCoordPredicted();
            final Matrix covMeas = getMeasurementErrorCovariance(t);
            //build the cubic gate in 2D
            final int minX = (int) (xPred - gateFactor * Math.sqrt(covMeas.get(0, 0)));
            final int maxX = (int) (Math.floor(xPred + gateFactor * Math.sqrt(covMeas.get(0, 0))));
            final int minY = (int) (yPred - gateFactor * Math.sqrt(covMeas.get(1, 1)));
            final int maxY = (int) (Math.floor(yPred + gateFactor * Math.sqrt(covMeas.get(1, 1))) + 1);
            final LikelihoodMap map = new LikelihoodMap(maxX - minX + 1, maxY - minY + 1, minX, minY);
            for (int y = minY; y <= maxY; y++)
                for (int x = minX; x <= maxX; x++) {
                    final Spot s = new Spot(x, y, 0);
                    final double l = likelihood(s, false, 4);
                    map.setLikelihood(x, y, l);
                }
            return map;
        }
        else if (this instanceof KF3dRandomWalk || this instanceof KF3dDirected) {
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

    /**
     * Get the predicted state at a given frame
     *
     * @param t int
     * @return Matrix
     */
    @Override
    public Matrix getPredictedState(final int t) {
        if (t > this.t) {
            Matrix pred = F.times(x_est);
            for (int i = this.t + 1; i < t; i++) {
                pred = F.times(pred);
            }
            return pred;
        }
        else if (t == this.t)
            return x_pre;
        else
            throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
    }

    /**
     * Compute the total target state likelihood contained into the search gate
     *
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    //TODO real computation instead of approximated values
    @Override
    public double getTotalGateLikelihood(final boolean gated, final double gateFactor) {
        if (gated) {
            if (gateFactor < 1d)
                return .5d;
            if (gateFactor < 2d)
                return .7d;
            if (gateFactor < 3d)
                return .85d;
            if (gateFactor < 4d)
                return .95d;
            if (gateFactor < 5d)
                return .99d;
            if (gateFactor < 8d)
                return .999d;
            else return 1d;
        }
        else return 1d;
    }

    /**
     * Initialize the KF with a first state and a first state covariance matrix
     *
     * @param x0 Matrix
     * @param P0 Matrix
     */
    @Override
    public void init(final @NotNull Matrix x0, final Matrix P0) {
        if (x0.getColumnDimension() != 1)
            throw new IllegalArgumentException("x0 is not a vector");
        if (x0.getRowDimension() != F.getRowDimension())
            throw new IllegalArgumentException("row dimension of x0 (is " + x0.getRowDimension() + " should be " + F.getRowDimension() + ") is not the same as row dimension of F");
        if (P0.getRowDimension() != F.getColumnDimension())
            throw new IllegalArgumentException("row dimension of P0 (is " + P0.getRowDimension() + " should be " + F.getColumnDimension() + ") is not the same as column dimension of F");
        this.x_est = x0;
        this.P_est = P0;
        predict();
    }

    /**
     * Check if the predictor is initialized
     *
     * @return bool
     */
    public boolean initialized() {
        return (x_est != null) && (P_est != null);
    }

    /**
     * Build the first state matrix for a first measurement matrix
     *
     * @param firstElement Matrix
     * @return Matrix
     */
    abstract Matrix buildX0(Matrix firstElement);

    /**
     * Get the initial state covariance
     *
     * @return Matrix
     */
    abstract Matrix getP0();

    /**
     * Initialize the KF with a first measurement at a given frame
     *
     * @param firstElement Matrix
     * @param t            int
     */
    @Override
    public void initWithFirstElement(final Matrix firstElement, final int t) {
        this.t = t;
        this.t0 = t;
        final Matrix x0 = buildX0(firstElement);
        final Matrix P0 = getP0();
        init(x0, P0);
    }

    /**
     * Initialize the KF with a first spot at a given time
     *
     * @param spot Spot
     * @param t    int
     */
    @Override
    public void initWithFirstElement(final Spot spot, final int t) {
        initWithFirstElement(buildMeasurementMatrix(spot), t);
    }

    /**
     * Compute the likelihood of a measurement
     *
     * @param z          Matrix
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    @Override
    public double likelihood(final @NotNull Matrix z, final boolean gated, final double gateFactor) {
        final Matrix z_err = z.minus(z_pre); // local
        // first, check if it's in the rectangular gate (faster)
        if (gated)
            for (int i = 0; i < z_err.getRowDimension(); i++)
                if (Math.abs(z_err.get(i, 0)) > gateFactor * Math.sqrt(S.get(i, i)))
                    return 0;
        // then, check if it's in the ellipsoide gate
        final double d2 = mahalanobis(z);
        if (gated && d2 > Math.pow(gateFactor, 2))
            return 0;
        // at the end, if needed, compute likelihood
        final double d1 = 1.0 / (Math.sqrt(S.times(_2PI).det()));
        return d1 * Math.exp(-0.5 * d2);
    }

    /**
     * Compute the likelihood of a spot as a measurement
     *
     * @param s          Spot
     * @param gated      bool
     * @param gateFactor double
     * @return double
     */
    @Override
    public double likelihood(final Spot s, final boolean gated, final double gateFactor) {
        final Matrix z = buildMeasurementMatrix(s);
        return likelihood(z, gated, gateFactor);
    }

    /**
     * Compute the likelihood of a spot as a measurement at a given frame
     *
     * @param s          Spot
     * @param gated      bool
     * @param gateFactor double
     * @param t          int
     * @return double
     */
    public double likelihood(final Spot s, final boolean gated, final double gateFactor, final int t) {
        if (t >= this.t) {
            final Matrix z = buildMeasurementMatrix(s);
            final Matrix predState = getPredictedState(t);
            final Matrix predMeasurement = H.times(predState);
            final Matrix z_err = z.minus(predMeasurement);
            final Matrix covPred = buildPredCovMatrix(t);
            final Matrix covMeas = H.times(covPred).times(H.transpose()).plus(R);
            // first, check if it's in the rectangular gate (faster)
            if (gated)
                for (int i = 0; i < z_err.getRowDimension(); i++)
                    if (Math.abs(z_err.get(i, 0)) > gateFactor * Math.sqrt(covMeas.get(i, i)))
                        return 0;
            // then, check if it's in the ellipsoide gate
            final double d2 = z_err.transpose().times(covMeas.inverse()).times(z_err).get(0, 0);
            if (gated && d2 > Math.pow(gateFactor, 2))
                return 0;
            // at the end, if needed, compute likelihood
            final double d1 = 1.0 / (Math.sqrt(covMeas.times(_2PI).det()));
            return d1 * Math.exp(-0.5 * d2);
        }
        else
            throw new IllegalArgumentException("cannot get past prediction in Kalman Filter");
    }

    /**
     * Compute the likelihood of a measurement in logarithmic scale
     *
     * @param z Matrix
     * @return double
     */
    @Override
    public double loglikelihood(final @NotNull Matrix z) {
        final Matrix z_err = z.minus(z_pre);
        return -(Math.log(S.times(_2PI).det()) + z_err.transpose().times(S_1).times(z_err).get(0, 0)) * 0.5;
    }

    /**
     * Compute the likelihood of a spot as a measurement in logarithmic scale
     *
     * @param ds Spot
     * @return double
     */
    @Override
    public double loglikelihood(final Spot ds) {
        final Matrix z = buildMeasurementMatrix(ds);
        return loglikelihood(z);
    }

    /**
     * Compute the likelihood of a state matrix in logarithmic scale
     *
     * @param x Matrix
     * @return double
     */
    @Override
    public double loglikelihoodOfState(final @NotNull Matrix x) {
        final Matrix x_err = x.minus(x_pre);
        final Matrix P_1 = P_pre.inverse();
        return -(Math.log(P_pre.times(_2PI).det()) + x_err.transpose().times(P_1).times(x_err).get(0, 0)) * 0.5;
    }

    @Override
    public double likelihoodOfState(final @NotNull Matrix x) {
        final Matrix x_err = x.minus(x_pre); // local
        final Matrix P_1 = P_pre.inverse();
        // first, check if it's in the rectangular gate (faster)
        return Math.exp(x_err.transpose().times(P_1).times(x_err).get(0, 0) / 2) / Math.sqrt(P_pre.times(_2PI).det());
    }

    @Override
    public double likelihoodOfPredictedState() {
        return 1.d / Math.sqrt(P_pre.times(_2PI).det());
    }

    /**
     * Compute mahalanobis distance between a measurement and the prediction
     *
     * @param z Matrix
     * @return double
     */
    protected double mahalanobis(final @NotNull Matrix z) {
        final Matrix z_err = z.minus(z_pre); // local
        return z_err.transpose().times(S_1).times(z_err).get(0, 0);
    }

    /**
     * Compute mahalanobis distance between a measurement (spot) and the prediction
     *
     * @param s Matrix
     * @return double
     */
    protected double mahalanobis(final Spot s) {
        final Matrix z = buildMeasurementMatrix(s);
        return mahalanobis(z);
    }

    /**
     * Get the minimum likelihood value for a measurement in the gate at a given time
     *
     * @param gateFactor double
     * @param t          int
     * @return double
     */
    @Override
    public double getMinLikelihoodInGate(final double gateFactor, final int t) {
        final Matrix covPred = buildPredCovMatrix(t);
        final Matrix covMeas = H.times(covPred).times(H.transpose()).plus(R);
        // first, check if it's in the rectangular gate (faster)
        final double d2 = Math.pow(gateFactor, 2);
        final double d1 = 1.0 / (Math.sqrt(covMeas.times(_2PI).det()));
        return d1 * Math.exp(-0.5 * d2);
    }

    /**
     * Get the minimum likelihood value for a measurement in the gate at the current time
     *
     * @param gateFactor double
     * @return double
     */
    @Override
    public double getCurrentMinLikelihoodInGate(final double gateFactor) {
        final double d2 = Math.pow(gateFactor, 2);
        final double d1 = 1.0 / (Math.sqrt(S.times(_2PI).det()));
        return d1 * Math.exp(-0.5 * d2);
    }

    /**
     * Compute the normalized innovation of the process for a given measurement
     * {@link KalmanFilter#mahalanobis(Matrix)}
     *
     * @return double
     */
    @Override
    public double normalizedInnovation(final Matrix m) {
        return this.mahalanobis(m);
    }

    /**
     * Compute the normalized innovation of the process for a given spot
     * {@link KalmanFilter#mahalanobis(Spot)}
     *
     * @return double
     */
    @Override
    public double normalizedInnovation(final Spot s) {
        return this.mahalanobis(s);
    }

    /**
     * Predict next state and update covariance matrices
     */
    @Override
    public void predict() {
        x_pre = F.times(x_est); // state prediction x~(k+1|k)
        z_pre = H.times(x_pre); // measurement prediction z~(k+1|k)
        P_pre = F.times(P_est).times(F.transpose()).plus(Q);// state prediction covariance P(k+1|k)
        S = H.times(P_pre).times(H.transpose()).plus(R);// innovation covariance S(k+1)
        S_1 = S.inverse();
        W = P_pre.times(H.transpose()).times(S_1);
    }

    /**
     * predict the state and update covariance matrices at a given time
     */
    @Override
    public void predict(final int t) {
        if (t == this.t)
            predict();
        else if (t > this.t) {
            x_pre = F.times(x_est);
            for (int i = this.t + 1; i < t; i++)
                x_pre = F.times(x_pre);
            z_pre = H.times(x_pre);
            P_pre = F.times(P_est).times(F.transpose()).plus(Q);
            for (int i = this.t + 1; i < t; i++)
                P_pre = F.times(P_pre).times(F.transpose()).plus(Q);
            S = H.times(P_pre).times(H.transpose()).plus(R);// innovation covariance S(k+1)
            S_1 = S.inverse();
            W = P_pre.times(H.transpose()).times(S_1);
        }
        else
            throw new IllegalArgumentException("cannot predict in the past in Kalman Filter");
    }

    /**
     * Set the covariance values of the filters
     *
     * @param trackingCov array of double
     */
    @Override
    public abstract void setTrackingCovariances(double[] trackingCov);

    /**
     * Set the covariance matrix of the filters
     *
     * @param Q0 Matrix
     */
    public void setTrackingCovariances(final Matrix Q0) {
        this.Q = Q0;
        this.Q0 = Q0;
    }

    /**
     * Specify wether to update the covariance of the filter during the tracking
     *
     * @param update bool
     */
    public void setUpdateCovariances(final boolean update) {
        this.updateCovariances = update;
    }

    /**
     * Update the filtering process with a given measurement at a given frame
     *
     * @param z Matrix
     * @param t int
     */
    @Override
    public void update(final Matrix z, final int t) {
        if (z != null) {
            correct(z);
            this.t = t;
        }
        predict(t);
    }

    /**
     * Update the filtering process with a given spot at a given frame
     *
     * @param s Spot
     * @param t int
     */
    @Override
    public void update(final Spot s, final int t) {
        final Matrix z = buildMeasurementMatrix(s);
        update(z, t);
    }

    // TODO: shift time

    /**
     * Get the estimated state at a future frame
     *
     * @param t int
     * @return Matrix
     */
    @Override
    public Matrix getEstimatedState(final int t) {
        return null;
    }

    /**
     * Get the predicted mesurement at a future frame
     *
     * @param t int
     * @return Matrix
     */
    @Override
    public Matrix getPredictedMeasurement(final int t) {
        if (this.t == t) {
            return z_pre;
        }
        else if (t > this.t) {
            final Matrix pred = getPredictedState(t);
            return H.times(pred);
        }
        else
            throw new IllegalArgumentException("cannot get past predicted measurement in Kalman Filter");
    }

    /**
     * Get the predicted mesurement at a future frame as a spot
     *
     * @param t int
     * @return VirtualSpot
     */
    @Override
    public VirtualSpot getPredictedStateAsSpot(final int t) {
        return null;
    }

    /**
     * Compute the state error covariance at a future frame
     *
     * @param t int
     * @return Matrix
     */
    @Override
    public Matrix getStateErrorCovariance(final int t) {
        Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
        for (int i = this.t + 1; i < t; i++)
            Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
        return Ppre;
    }

    /**
     * Compute the search gates at a future frame
     *
     * @param gateFactor double
     * @param i          int
     * @return ArrayList of Area
     */
    //FIXME
    @Override
    public ArrayList<Area> getGates(final double gateFactor, final int i) {
        return null;
    }

    /**
     * Modify the filter current time
     *
     * @param t int
     */
    @Override
    public void updateTime(final int t) {
        this.t = t;
    }
}