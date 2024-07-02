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

import Jama.Matrix;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.spot.Spot;

/**
 * 2D Kalaman filter corresponding to directed motion
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class KF2dDirected extends KalmanFilter implements Predictor2D {
    public KF2dDirected() {
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
        final Matrix x0;
//		 first state estimate
        x0 = new Matrix(4, 1);
        x0.set(0, 0, firstElement.get(0, 0));  // X_t
        x0.set(1, 0, 0);  // vx
        x0.set(2, 0, firstElement.get(1, 0));  // Y_t
        x0.set(3, 0, 0);  // vy
        return x0;
    }

    @Override
    Matrix getP0() {
        final double[][] p0 = {
                {0.1, 0.0, 0.0, 0.0},  // X_t
                //{0.0, 0.1, 0.0, 0.1},  // vx // TODO: 18/10/2023 Seems to be an error
                {0.0, 0.1, 0.0, 0.0},  // vx
                {0.0, 0.0, 0.1, 0.0},  // Y_t
                //{0.0, 0.1, 0.0, 0.1}  // vy // TODO: 18/10/2023 Seems to be an error
                {0.0, 0.1, 0.0, 0.0}  // vy
        };
        return new Matrix(p0);
    }

    @Override
    public void setTrackingCovariances(final double @NotNull [] trackingCov) {
        Q.set(0, 0, trackingCov[0]);
        Q.set(2, 2, trackingCov[1]);
        Q0 = Q;
    }

    @Override
    public Spot buildSpotFromState(final Matrix state) {
        if (state == null)
            return null;
        return new Spot(state.get(0, 0), state.get(2, 0), 0);
    }

    @Override
    public Predictor copy() {
        final KF2dDirected kf = new KF2dDirected();
        copyMyDataInKF(kf);
        return kf;
    }

    @Override
    public Predictor copyInit() {
        final KF2dDirected kf = new KF2dDirected();
        kf.setTrackingCovariances(Q0);
        kf.t = t;
        kf.setUpdateCovariances(this.updateCovariances);
        return kf;
    }

    @Override
    public VirtualSpot getCurrentPredictedStateAsSpot() {
        return new VirtualSpot(x_pre.get(0, 0), x_pre.get(2, 0), 0);
    }

    private void initMatrices() {
        final double[][] f = {
                {1.0, 1.0, 0.0, 0.0},  // X_t
                {0.0, 1.0, 0.0, 0.0},  // vx
                {0.0, 0.0, 1.0, 1.0},  // Y_t
                {0.0, 0.0, 0.0, 1.0}};  // vy
        F = new Matrix(f);
        // dynamic noise
        final double[][] q = {
                {2, 0.0, 0.0, 0.0},  // X_t
                //{0.0, 3.0, 0.0, 3.0},  // vx // TODO: 18/10/2023 Seems to be an error
                {0.0, 3.0, 0.0, 0.0},  // vx // TODO: 18/10/2023 Seems to be an error
                {0.0, 0.0, 2, 0.0},  // Y_t
                //{0.0, 3.0, 0.0, 3.0}}; // vy // TODO: 18/10/2023 Seems to be an error
                {0.0, 0.0, 0.0, 3.0}}; // vy
        Q = new Matrix(q);

//		 observation
        final double[][] h = {
                {1.0, 0.0, 0.0, 0.0},  // X_t
                {0.0, 0.0, 1.0, 0.0}};  // X_t
        H = new Matrix(h);
        // observation noise
        final double[][] r = {
                {2, 0.0},  // X_t
                {0.0, 2}};  // Y_t
        R = new Matrix(r);
    }

    @Override
    public void correct(final @NotNull Matrix z) {
        if (this.t - t0 == 1) {
            //update the velocity with the observed displacement
            final Matrix x0 = x_est;
            x0.set(1, 0, z.get(0, 0) - x_est.get(0, 0));
            x0.set(3, 0, z.get(1, 0) - x_est.get(2, 0));
            init(x0, P_est);

        }
        if (z.getRowDimension() != H.getRowDimension())
            throw new IllegalArgumentException("row dimension of z (is " + z.getRowDimension() + " should be " + H.getRowDimension() + ") is not the same as row dimension of H");
        if (z.getColumnDimension() != 1)
            throw new IllegalArgumentException("z is not a vector");
        z_err = z.minus(z_pre);             // innovation (measurement error)
//		System.out.println("======");
//		System.out.println(x_est.get(0, 0) +" "+ x_est.get(1, 0)+" "+x_est.get(2, 0)+" "+x_est.get(3, 0));
        x_est = x_pre.plus(W.times(z_err)); // update state estimation
//		System.out.println(x_est.get(0, 0) +" "+ x_est.get(1, 0)+" "+x_est.get(2, 0)+" "+x_est.get(3, 0));
//		System.out.println("======");
        P_est = P_pre.minus(W.times(S).times(W.transpose())); // update state covariance of innovation P(k+1|k+1)
        if (this.t - t0 != 1)
            covupdate();
    }

    @Override
    public double getXCoordEstimated() {
        return x_est.get(0, 0);
    }

    @Override
    public double getYCoordEstimated() {
        return x_est.get(2, 0);
    }

    @Override
    public double getXCoordPredicted() {
        return x_pre.get(0, 0);
    }

    @Override
    public double getYCoordPredicted() {
        return x_pre.get(2, 0);
    }

    @Override
    public Matrix getStateErrorCovariance2D(final int t) {
        Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
        for (int i = this.t + 1; i < t; i++)
            Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
        final Matrix Ppre2D = new Matrix(2, 2);
        Ppre2D.set(0, 0, Ppre.get(0, 0));
        Ppre2D.set(0, 1, Ppre.get(0, 2));
        Ppre2D.set(1, 0, Ppre.get(2, 0));
        Ppre2D.set(1, 1, Ppre.get(2, 2));
        return Ppre2D;
    }

    @Override
    public Matrix getCurrentStateErrorCovariance2D() {
        final Matrix P2D = new Matrix(2, 2);
        P2D.set(0, 0, P_est.get(0, 0));
        P2D.set(0, 1, P_est.get(0, 2));
        P2D.set(1, 0, P_est.get(2, 0));
        P2D.set(1, 1, P_est.get(2, 2));
        return P2D;
    }

    @Override
    public Matrix getCurrentEstimatedState2D() {
        final Matrix xest2D = new Matrix(2, 1);
        xest2D.set(0, 0, x_est.get(0, 0));
        xest2D.set(1, 0, x_est.get(2, 0));
        return xest2D;
    }

    @Override
    public void setCurrentEstimatedState2D(final @NotNull Matrix m) {
        x_est.set(0, 0, m.get(0, 0));
        x_est.set(2, 0, m.get(1, 0));
    }

    @Override
    public void setCurrentStateErrorCovariance2D(final @NotNull Matrix m) {
        P_est.set(0, 0, m.get(0, 0));
        P_est.set(0, 2, m.get(0, 1));
        P_est.set(2, 0, P_est.get(1, 0));
        P_est.set(2, 2, P_est.get(1, 1));
    }

    @Override
    public Matrix getCurrentPredictedState2D() {
        final Matrix xpre2D = new Matrix(2, 1);
        xpre2D.set(0, 0, x_pre.get(0, 0));
        xpre2D.set(1, 0, x_pre.get(2, 0));
        return xpre2D;
    }
}