/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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
 * 3D Kalman filter corresponding to random walk motion (diffusion)
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class KF3dRandomWalk extends KalmanFilter implements Predictor3D {
    public KF3dRandomWalk() {
        initMatrices();
    }

    @Override
    public Matrix buildMeasurementMatrix(final Spot s) {
        if (s == null)
            return null;
        return new Matrix(new double[][]{{s.mass_center.x}, {s.mass_center.y}, {s.mass_center.z}});
    }

    @Override
    Matrix buildX0(final Matrix firstElement) {
        final Matrix x0 = new Matrix(3, 1);
        x0.set(0, 0, firstElement.get(0, 0));  // X_t
        x0.set(1, 0, firstElement.get(1, 0));  // Y_t
        x0.set(2, 0, firstElement.get(2, 0));  // Y_t
        return x0;
    }

    @Override
    Matrix getP0() {
        final double[][] p0 = {
                {0.1, 0.0, 0.0},  // X_t
                {0.0, 0.1, 0.0},  // Y_t
                {0.0, 0.0, 0.1}  // Z_t
        };
        return new Matrix(p0);
    }

    @Override
    public void setTrackingCovariances(final double @NotNull [] trackingCov) {
        Q.set(0, 0, trackingCov[0]);
        Q.set(1, 1, trackingCov[1]);
        Q.set(2, 2, trackingCov[2]);
        Q0 = Q;
    }

    @Override
    public Spot buildSpotFromState(final Matrix state) {
        if (state == null)
            return null;
        return new Spot(state.get(0, 0), state.get(1, 0), state.get(2, 0));
    }

    @Override
    public Predictor copy() {
        final KF3dRandomWalk kf = new KF3dRandomWalk();
        copyMyDataInKF(kf);
        return kf;
    }

    @Override
    public Predictor copyInit() {
        final KF3dRandomWalk kf = new KF3dRandomWalk();
        kf.setTrackingCovariances(Q0);
        kf.t = t;
        kf.setUpdateCovariances(this.updateCovariances);
        return kf;
    }

    @Override
    public VirtualSpot getCurrentPredictedStateAsSpot() {
        return new VirtualSpot(x_pre.get(0, 0), x_pre.get(1, 0), x_pre.get(2, 0));
    }

    private void initMatrices() {
        final double[][] f =
                {
                        {1.0, 0.0, 0.0},  // X_t
                        {0.0, 1.0, 0.0},  // Y_t
                        {0.0, 0.0, 1.0},  // Y_t
                };
        F = new Matrix(f);
        // dynamic noise
        final double[][] q = {
                {2, 0.0, 0.0},  // X_t
                {0.0, 2, 0.0},  // Y_t
                {0.0, 0.0, 2},  // Y_t
        };
        Q = new Matrix(q);
//		 observation
        final double[][] h = {
                {1.0, 0.0, 0.0},  // X_t
                {0.0, 1.0, 0.0}, // Y_t
                {0.0, 0.0, 1.0} // Z_t
        };
        H = new Matrix(h);
        // observation noise
        final double[][] r = {
                {2, 0.0, 0.0},  // X_t
                {0.0, 2, 0.0}, // Y_t
                {0.0, 0.0, 2}
        };
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
    public double getZCoordEstimated() {
        return x_est.get(2, 0);
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
    public double getZCoordPredicted() {
        return x_pre.get(2, 0);
    }

    @Override
    public void setCurrentStateErrorCovariance3D(final Matrix m) {
        this.P_est = m;
    }

    @Override
    public void setCurrentEstimatedState3D(final Matrix m) {
        this.x_est = m;
    }

    @Override
    public Matrix getStateErrorCovariance3D(final int t) {
        Matrix Ppre = F.times(P_est).times(F.transpose()).plus(Q);
        for (int i = this.t + 1; i < t; i++)
            Ppre = F.times(Ppre).times(F.transpose()).plus(Q);
        return Ppre;
    }

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
