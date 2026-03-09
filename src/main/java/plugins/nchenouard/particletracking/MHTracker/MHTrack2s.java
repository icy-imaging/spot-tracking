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

package plugins.nchenouard.particletracking.MHTracker;

import Jama.Matrix;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.spot.Spot;

import java.util.LinkedList;

/**
 * Track with two perceivability states:
 * - state 0: do not exist
 * - state 1: exists
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class MHTrack2s {
    Predictor predictor;
    double likelihood; //likelihood of the associations until now
    double associationLikelihood; // likelihood of the current association
    double score; // score of the associations until now
    double bestScore = -Double.MAX_VALUE; //bestScore of the following nodes

    Family family = null;
    Association association;
    LinkedList<MHTrack2s> followingSegments = new LinkedList<>();
    MHTrack2s precedingSegment = null;
    HMMMHTracker tracker;
    Hypothesis hypothesis = null;

    //HMM part
    double p00 = 1;
    double p01 = 0.0;

    /* some reference set of value */
//	public double p10 = 0.025;
//	public double p11 = 0.975;
    // from quia
    // gives 0.66
    public static final double QUIA_P10 = 0.025d; // not sure about these values
    public static final double QUIA_P11 = 0.975d;
    public static final double QUIA_Pc = 0.8d;
    public static final double QUIA_Pt = 0.05d;

    public static final double PAMI_P10 = 0.15d;
    public static final double PAMI_P11 = 0.85d;
    public static final double PAMI_Pc = 0.8d;
    public static final double PAMI_Pt = 0.05d;

    /* initial values */
    public static double INIT_P10 = PAMI_P10;
    public static double INIT_P11 = PAMI_P11;
    public static double INIT_Pc = PAMI_Pc;
    public static double INIT_Pt = PAMI_Pt;

    /* track-specific values */
    protected double p10 = INIT_P10;
    protected double p11 = INIT_P11;
    protected double pc = INIT_Pc; //confirmation threshold
    protected double pt = INIT_Pt; // termination threshold

    protected boolean confirmed = false;
    protected boolean terminated = false;

    protected double pObs;
    protected double pPredObs;

    boolean verbose = false;

    public void println(final String str) {
        if (verbose)
            IcyLogger.trace(this.getClass(), str);
    }

    public MHTrack2s(final double p1_0, final HMMMHTracker tracker, final Predictor predictor, final Association association, final Family family) {
        this.pObs = p1_0;
        this.pPredObs = p11 * pObs + p01 * (1 - pObs);

        if (pObs > pc)
            this.confirmed = true;
        else if (pObs < pt)
            this.terminated = true;
        this.predictor = predictor;
        this.association = association;
        predictor.initWithFirstElement(association.spot, association.t);
        this.precedingSegment = null;
        this.family = family;
        this.tracker = tracker;
        this.likelihood = 1d / tracker.volume;//uniform distribution of a new track
        this.associationLikelihood = likelihood;
        this.score = Math.log(this.likelihood);
        association.setNewScore(score);
    }

    protected MHTrack2s() {
        //
    }

    public MHTrack2s copy() {
        final MHTrack2s mtrack = new MHTrack2s();
        mtrack.predictor = this.predictor.copy();
        mtrack.likelihood = this.likelihood;
        mtrack.associationLikelihood = this.associationLikelihood;
        mtrack.score = this.score;
        mtrack.family = this.family;
        mtrack.association = this.association;
        mtrack.precedingSegment = this.precedingSegment;
        mtrack.tracker = this.tracker;
        mtrack.hypothesis = this.hypothesis;
        mtrack.confirmed = this.confirmed;
        mtrack.terminated = this.terminated;
        mtrack.pObs = this.pObs;
        mtrack.pPredObs = this.pPredObs;
        mtrack.p00 = this.p00;
        mtrack.p10 = this.p10;
        mtrack.p01 = this.p01;
        mtrack.p11 = this.p11;
        return mtrack;
    }

    public MHTrack2s createPotentialTrackWithPrediction(final int t) {
        final MHTrack2s trk = this.copy();
        trk.precedingSegment = this;
        trk.followingSegments.clear();
        trk.hypothesis = null;
        this.followingSegments.add(trk);
        trk.prolongate(t);
        return trk;
    }

    public MHTrack2s createPotentialTrack(final Association a, final int t) {
        final MHTrack2s trk = this.copy();
        trk.precedingSegment = this;
        trk.followingSegments.clear();
        trk.hypothesis = null;
        this.followingSegments.add(trk);
        trk.associate(a, t);
        return trk;
    }

    public void applyLastAssociation() {
        if (!association.isPrediction)
            predictor.update(association.state, association.t);
        else
            predictor.update((Matrix) null, association.t);
    }

    /**
     * Test if spot is in the search gate of the track
     *
     * @param a Association
     * @return bool
     */
    public boolean inGate(final @NotNull Association a) {
        final double dist = predictor.normalizedInnovation(a.state); // TODO: check the normalized innovation here
        return (dist <= tracker.gateFactor);
    }

    public double kineticLikelihood(final Spot s, final boolean gated, final double gateFactor) {
        return predictor.likelihood(s, gated, gateFactor);
    }

    public double falseAlarmLikelihood() {
        return tracker.pFA;
    }

    public void associate(final @NotNull Association a, final int t) {
        association = a;
        final double kL = tracker.pDpG * kineticLikelihood(a.spot, true, tracker.gateFactor);
        final double faL = falseAlarmLikelihood();

        associationLikelihood = kL * pPredObs + faL * (1 - pPredObs);
        likelihood *= associationLikelihood;
        final double s = Math.log(associationLikelihood);
        score += s;
        a.setNewScore(s);

        println("associate pobs " + pObs + " | " + kL * pPredObs / associationLikelihood);
        pObs = kL * pPredObs / associationLikelihood;
        pPredObs = p11 * pObs + p01 * (1 - pObs);

        if (pObs > pc)
            this.confirmed = true;
        if (pObs < pt)
            this.terminated = true;
    }

    public void prolongate(final int t) {
        final VirtualSpot pred = this.predictor.getCurrentPredictedStateAsSpot();
        association = new Association(null, pred, true, t, 0);

        final double kL = (1 - tracker.pDpG);//*predictor.likelihoodOfPredictedState();
        final double faL = 1;

        associationLikelihood = kL * pPredObs + faL * (1 - pPredObs);
//		println("prolongate "+kL+" "+faL+" "+associationLikelihood);
        likelihood *= associationLikelihood;
        score += Math.log(associationLikelihood);

//		println("prolongate kl "+kL+" faL "+1);
        println("prolongate pobs " + pObs + " | " + kL * pPredObs / associationLikelihood);
        pObs = kL * pPredObs / associationLikelihood;
//		println("pPredobs "+pPredObs+" | "+p11*pObs + p01*(1-pObs));
        pPredObs = p11 * pObs + p01 * (1 - pObs);

//		
        if (pObs > pc)
            this.confirmed = true;
        if (pObs < pt)
            this.terminated = true;
    }

    public void resetBestScore() {
        this.bestScore = -Double.MAX_VALUE;
    }
}
