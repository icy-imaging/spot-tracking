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

package plugins.nchenouard.particletracking.MHTracker;

import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.particletracking.filtering.Predictor;

/**
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class MHTrack3s extends MHTrack2s {
    /**
     * Track with three perceivability states:
     * - state 0: do not exist
     * - state 1: exists
     * - state 2: exists but has temporarily disappeared
     * <p>
     * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
     */

    public double p20 = 0.45d;
    public double p21 = 0.45d;
    public double p22 = 0.1d;
    public double p12 = 0.05d;
    public double p02 = 0d;
    public double p1;
    public double p2;
    public double p1Pred;
    public double p2Pred;

    MHTrack3s(final HMMMHTracker tracker, final Predictor predictor, final Association association, final Family family) {
        super(0.5, tracker, predictor, association, family);
        p00 = 1d;
        p01 = 0.0d;
        p10 = 0.05d;
        p11 = 0.9d;

        this.p1 = pObs;
        this.p2 = 0;
        this.p1Pred = p11 * p1 + p21 * p2 + p01 * (1 - pObs);
        this.p2Pred = p22 * p2 + p12 * p1 + p02 * (1 - pObs);
        this.pPredObs = p1Pred + p2Pred;
    }

    private MHTrack3s() {
        //
    }

    @Override
    public MHTrack2s copy() {
        final MHTrack3s mtrack = new MHTrack3s();
        mtrack.predictor = this.predictor.copy();
        mtrack.likelihood = this.likelihood;
        mtrack.associationLikelihood = this.associationLikelihood;
        mtrack.score = this.score;
        mtrack.family = this.family;
        mtrack.association = this.association;
//		mtrack.followingSegments = (LinkedList<MHTrack2s>) this.followingSegments.clone();
        mtrack.precedingSegment = this.precedingSegment;
        mtrack.tracker = this.tracker;
        mtrack.hypothesis = this.hypothesis;
        mtrack.confirmed = this.confirmed;
        mtrack.terminated = this.terminated;
        mtrack.pObs = this.pObs;
        mtrack.pPredObs = this.pPredObs;
        mtrack.p1 = this.p1;
        mtrack.p1Pred = this.p1Pred;
        mtrack.p2 = this.p2;
        mtrack.p2Pred = this.p2Pred;
        return mtrack;
    }

    @Override
    public void associate(final @NotNull Association a, final int t) {
        association = a;
        final double kL = tracker.totalGateLikelihood * kineticLikelihood(a.spot, true, tracker.gateFactor);
        final double faL = falseAlarmLikelihood();

        associationLikelihood = kL * p1Pred + faL * (1 - pPredObs + p2Pred);

        likelihood *= associationLikelihood;
        score += Math.log(associationLikelihood);

        p1 = kL * p1Pred / associationLikelihood;
        p2 = faL * p2Pred / associationLikelihood;
        pObs = p1 + p2;
        p1Pred = p11 * p1 + p21 * p2 + p01 * (1d - pObs);
        p2Pred = p22 * p2 + p12 * p1 + p02 * (1d - pObs);
        pPredObs = p1Pred + p2Pred;
        if (pObs > pc)
            this.confirmed = true;
        else if (pObs < pt)
            this.terminated = true;
    }

    @Override
    public void prolongate(final int t) {
        final VirtualSpot pred = this.predictor.getCurrentPredictedStateAsSpot();
        association = new Association(null, pred, true, t, 0);

        final double kL = 1d - tracker.totalGateLikelihood;
        final double faL = 1d;

        associationLikelihood = kL * p1Pred + faL * (1d - pPredObs + p2Pred);
        likelihood *= associationLikelihood;
        score += Math.log(associationLikelihood);

        p1 = kL * p1Pred / associationLikelihood;
        p2 = faL * p2Pred / associationLikelihood;
        pObs = p1 + p2;
        p1Pred = p11 * p1 + p21 * p2 + p01 * (1d - pObs);
        p2Pred = p22 * p2 + p12 * p1 + p02 * (1d - pObs);
        pPredObs = p1Pred + p2Pred;

        if (pObs > pc)
            this.confirmed = true;
        else if (pObs < pt)
            this.terminated = true;
    }
}
