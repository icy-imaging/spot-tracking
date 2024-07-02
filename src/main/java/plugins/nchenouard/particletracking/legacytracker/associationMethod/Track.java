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

package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import Jama.Matrix;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.filtering.*;
import plugins.nchenouard.spot.Spot;

import java.util.ArrayList;

/**
 * Track object
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com). Based on Auguste Genovesio original code.
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class Track {
    public static class Association {
        Matrix state;
        Spot spot;
        boolean isPrediction;

        @Contract(pure = true)
        Association(final Matrix state, final Spot spot, final boolean isPrediction) {
            this.state = state;
            this.spot = spot;
            this.isPrediction = isPrediction;
        }
    }

    protected int id = -1;
    protected ArrayList<Association> associations;
    protected int lastConsecutivePredictions = 0; // nb of consecutive virtual detections
    protected int realValues = 0; // ? may be a boolean telling if the detection is a true one.

    protected Predictor predictor = null; // Kalman filter or something like that ( imm (...) )
    protected double gateFactor;

    protected int firstIndex;// = -1; // first index in the sequence
    protected int lastIndex;// = -1; // last  index in the sequence

    @Contract(pure = true)
    public Track() {
        this.associations = new ArrayList<>();
        this.firstIndex = 0;
        this.lastIndex = 0;
        gateFactor = 3;
    }

    @Contract(pure = true)
    public Track(final int id, final Predictor predictor, final int firstIndex, final double gateFactor) {
        this.id = id;
        this.associations = new ArrayList<>();
        this.predictor = predictor;
        this.firstIndex = firstIndex;
        this.lastIndex = firstIndex;
        this.gateFactor = gateFactor;
    }

    public void add(final Spot spot, final boolean isPrediction) {
        final Matrix measurement = predictor.buildMeasurementMatrix(spot);
        add(measurement, spot, isPrediction);
    }

    public void add(final @NotNull Matrix state, final Spot spot, final boolean isPrediction) {
        if (state.getColumnDimension() != 1)
            throw new IllegalArgumentException("state must be a vector");
        if (isPrediction)
            lastConsecutivePredictions++;
        else {
            realValues++;
            lastConsecutivePredictions = 0;
        }
        associations.add(new Association(state, spot, isPrediction));
        lastIndex++;
    }

    public void associate(final Spot spot, final int t) {
        predictor.update(spot, t);
        add(getEstimatedState(), spot, false);
    }

    public Matrix getEstimatedState() {
        return predictor.getCurrentEstimatedState();
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getId() {
        return id;
    }

    public int getLastConsecutivePredictions() {
        return lastConsecutivePredictions;
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public Matrix getPredictedState() {
        return predictor.getCurrentPredictedState();
    }

    public Matrix getStateAtFrame(final int frameIndex) {
        if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex - firstIndex) || associations.get(frameIndex - firstIndex) == null)
            return null;
        return associations.get(frameIndex - firstIndex).state;
    }

    public Spot getSpotAtFrame(final int frameIndex) {
        if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex - firstIndex) || associations.get(frameIndex - firstIndex) == null)
            return null;
        return associations.get(frameIndex - firstIndex).spot;
    }

    //return the estimated state at a given time as a spot
    public Spot getStateAsSpot(final Matrix state) {
        return predictor.buildSpotFromState(state);
    }

    public double getTotalGateLikelihood() {
        return predictor.getTotalGateLikelihood(true, gateFactor);
    }

    public boolean isPredictionAtFrame(final int frameIndex) {
        if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex - firstIndex) || associations.get(frameIndex - firstIndex) == null)
            return true;
        return associations.get(frameIndex - firstIndex).isPrediction;
    }

    public void initTrack(final Spot firstSpot, final int t) {
        predictor.initWithFirstElement(firstSpot, t);
        add(firstSpot, false);
    }

    public boolean isStillActive(final int maxPred) {
        return (lastConsecutivePredictions < maxPred);
    }

    public double likelihood(final Spot s, final boolean gated, final double gateFactor) {
        return predictor.likelihood(s, gated, gateFactor);
    }

    public double loglikelihood(final Spot s) {
        return predictor.loglikelihood(s);
    }

    public double minLikelihoodInGate() {
        return predictor.getCurrentMinLikelihoodInGate(gateFactor);
    }

    public double normalizedInnovation(final Spot s) {
        return predictor.normalizedInnovation(s);
    }

    public void prolongate(final int t) {
        predictor.update((Spot) null, t);
        add(getPredictedState(), predictor.getCurrentPredictedStateAsSpot(), true);
    }

    public LikelihoodMap getLikelihoodMap(final double gateFactor, final int t) {
        if (predictor instanceof IMM2D)
            return ((IMM2D) predictor).getLikelihoodMap(gateFactor, t);
        if (predictor instanceof IMM3D)
            return ((IMM3D) predictor).getLikelihoodMap(gateFactor, t);
        if (predictor instanceof KalmanFilter)
            return ((KalmanFilter) predictor).getLikelihoodMap(gateFactor, t);
        else return null;
    }

    public LikelihoodMap[] getLikelihoodMapsIMM(final double gateFactor, final int t) {
        if (predictor instanceof IMM2D)
            return ((IMM2D) predictor).getLikelihoodMapsIMM(gateFactor, t);
        if (predictor instanceof IMM3D)
            return ((IMM3D) predictor).getLikelihoodMapsIMM(gateFactor, t);
        else return null;
    }

    public double[] getIMMWeights() {
        if (predictor instanceof IMM2D)
            return ((IMM2D) predictor).getWeights();
        if (predictor instanceof IMM3D)
            return ((IMM3D) predictor).getWeights();
        return null;
    }
}
