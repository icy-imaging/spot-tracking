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
import icy.system.IcyExceptionHandler;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;

/**
 * 2D Kalman filter corresponding to random walk (diffusion)
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class KF2dRandomWalk extends KalmanFilter implements Predictor2D {
    double refVarX = 9;
    double refVarY = 9;

    public KF2dRandomWalk() {
        initMatrices();
    }

    @Override
    public Matrix buildMeasurementMatrix(final Spot s) {
        if (s == null)
            return null;
        return new Matrix(new double[][]{{s.mass_center.x}, {s.mass_center.y}});
    }

    @Override
    Matrix buildX0(final Matrix firstElement) {
        final Matrix x0 = new Matrix(2, 1);
        x0.set(0, 0, firstElement.get(0, 0));  // X_t
        x0.set(1, 0, firstElement.get(1, 0));  // Y_t
        return x0;
    }

    @Override
    Matrix getP0() {
        final double[][] p0 = {
                {0.1, 0.0},  // X_t
                {0.0, 0.1},  // Y_t
        };
        return new Matrix(p0);
    }

    @Override
    public void setTrackingCovariances(final double[] trackingCov) {
        Q.set(0, 0, trackingCov[0]);
        Q.set(1, 1, trackingCov[1]);
        refVarX = trackingCov[0];
        refVarY = trackingCov[1];

        Q0 = Q;
    }

    @Override
    public Spot buildSpotFromState(final Matrix state) {
        if (state == null)
            return null;
        return new Spot(state.get(0, 0), state.get(1, 0), 0);
    }

    @Override
    public Predictor copy() {
        final KF2dRandomWalk kf = new KF2dRandomWalk();
        copyMyDataInKF(kf);
        return kf;
    }

    @Override
    public Predictor copyInit() {
        final KF2dRandomWalk kf = new KF2dRandomWalk();
        kf.setTrackingCovariances(Q0);
        kf.t = t;
        kf.setUpdateCovariances(this.updateCovariances);
        return kf;
    }

    @Override
    public VirtualSpot getCurrentPredictedStateAsSpot() {
        return new VirtualSpot(x_pre.get(0, 0), x_pre.get(1, 0), 0);
    }

    private void initMatrices() {
        final double[][] f =
                {
                        {1.0, 0.0},  // X_t
                        {0.0, 1.0},  // Y_t
                };
        F = new Matrix(f);
        // dynamic noise
        final double[][] q = {
                {2, 0.0},  // X_t
                {0.0, 2},  // Y_t
        };
        Q = new Matrix(q);

//		 observation
        final double[][] h = {
                {1.0, 0.0},  // X_t
                {0.0, 1.0}};  // X_t
        H = new Matrix(h);
        // observation noise
        final double[][] r = {
                {2, 0.0},  // X_t
                {0.0, 2}};  // Y_t
        R = new Matrix(r);
    }

    @Override
    public double getXCoordEstimated() {
        return x_est.get(0, 0);
    }

    @Override
    public double getYCoordEstimated() {
        return x_est.get(1, 0);
    }

    @Override
    public double getXCoordPredicted() {
        return x_pre.get(0, 0);
    }

    @Override
    public double getYCoordPredicted() {
        return x_pre.get(1, 0);
    }

    @Override
    public Matrix getStateErrorCovariance2D(final int t) {
        Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
        for (int i = this.t + 1; i < t; i++)
            Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
        return Ppre;
    }

    @Override
    public Matrix getCurrentStateErrorCovariance2D() {
        return P_est;
    }

    @Override
    public Matrix getCurrentEstimatedState2D() {
        return x_est;
    }

    @Override
    public void setCurrentEstimatedState2D(final Matrix m) {
        this.x_est = m;
    }

    @Override
    public void setCurrentStateErrorCovariance2D(final Matrix m) {
        this.P_est = m;
    }

    @Override
    public Matrix getCurrentPredictedState2D() {
        return x_pre;
    }

    @Override
    protected void covupdate() {
        if (updateCovariances) {
            //factor used for the update of Q: Q = (1-alpha)*Q + alpha*(x_est - x_pre)*(x_est - x_pre)T
            final double alpha = 0.15;
            try {
                if ((x_est.minus(x_pre)).normF() != 0) {
                    //Matrix err = (x_est.minus(x_pre)).times(x_est.minus(x_pre).transpose());
//					err.set(0, 1, 0d);
//					err.set(1, 0, 0d);
//					double errM = err.get(0, 0) + err.get(1, 1);
//					err.set(0, 0, errM);
//					err.set(1, 1, errM);
//					Q=Q.times(1d-alpha).plus(err.times(alpha));

                    final Matrix err = x_est.minus(x_pre);
                    final double dist2 = Math.pow(err.get(0, 0), 2);// + Math.pow(err.get(1, 0), 2);
                    Q.set(0, 0, Math.min(Math.max((1d - alpha) * Q.get(0, 0) + alpha * dist2, refVarX / 2), 2 * refVarX));
                    Q.set(1, 1, Math.min(Math.max((1d - alpha) * Q.get(1, 1) + alpha * dist2, refVarY / 2), 2 * refVarY));
//					System.out.println(Q.get(0, 0) +" "+ Q.get(1, 1));

                    //double newVar = (err.get(0, 0) + err.get(1, 1))/2;
                    //double newVar = err.det();
                    //System.out.println("det "+(x_est.minus(x_pre)).times((x_est.minus(x_pre)).transpose()).det());
                    //				Q.set(0, 0, (1d-alpha)*Q.get(0, 0) + alpha*newVar);
                    //			Q.set(1, 1, (1d-alpha)*Q.get(1, 1) + alpha*newVar);
//					
//					Q=Q.times(1d-alpha).plus( ((x_est.minus(x_pre)).times((x_est.minus(x_pre)).transpose())).times(alpha));
//					Q.set(0, 1,0d);
//					Q.set(0, 0,1d);
//					double newVar = (Q.get(0, 0)) + Math.sqrt(Q.get(1, 1))/2;
//					Q.set(1, 1, newVar);
//					Q.set(0, 0, newVar);
//					System.out.println(newVar);
//					System.out.println(Math.sqrt(Q.get(0, 0)));// + Math.sqrt(Q.get(1, 1))/2);
                }
//				for (int j = 0; j<Q.getRowDimension(); j++)
//				{
//					System.out.println();
//				for (int i = 0; i <Q.getColumnDimension(); i++)
//				{
//					System.out.print(Q.get(j, i)+" ");
//				}
//				}
            }
            catch (final RuntimeException e) {
                System.out.println("error in KalmanFiter.covupdate:");
                if (x_est == null) System.out.println("xest null");
                if (x_pre == null) System.out.println("xpre null");
                if (Q == null) System.out.println("Q null");
                if (Q0 == null) System.out.println("Q0 null");
                IcyExceptionHandler.showErrorMessage(e, true);
            }
        }
    }
}