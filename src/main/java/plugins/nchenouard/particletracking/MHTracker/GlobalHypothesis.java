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

package plugins.nchenouard.particletracking.MHTracker;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * A Global Hypothesis is essentially a set of Hypothesis objects with related methods
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class GlobalHypothesis {
    HashSet<Association> usedAssociations;
    LinkedList<Association> realAssociations;//devient inutile?
    LinkedList<Hypothesis> hyps;
    int[] usedRealSpotsNum;
    volatile double score;
    int firstT;
    int lastT;
    HMMMHTracker tracker;

    GlobalHypothesis(final int firstT, final int lastT, final HMMMHTracker tracker) {
        usedAssociations = new HashSet<>();
        realAssociations = new LinkedList<>();
        hyps = new LinkedList<>();
        score = 0;
        this.usedRealSpotsNum = new int[lastT - firstT + 1];
        this.firstT = firstT;
        this.lastT = lastT;
        this.tracker = tracker;
    }

    GlobalHypothesis(
            final HashSet<Association> usedAssociations,
            final LinkedList<Association> realAssociations,
            final LinkedList<Hypothesis> hyps,
            final double score,
            final int[] usedRSpots,
            final int firstT,
            final int lastT,
            final HMMMHTracker tracker
    ) {
        this.usedAssociations = usedAssociations;
        this.realAssociations = realAssociations;
        this.hyps = hyps;
        this.score = score;
        this.usedRealSpotsNum = usedRSpots;
        this.firstT = firstT;
        this.lastT = lastT;
        this.tracker = tracker;
    }

    public void addHypothesis(final Hypothesis h) {
        hyps.add(h);
        usedAssociations.addAll(h.usedAssociations);
        for (final Association a : h.usedAssociations)
            if (!a.isPrediction) {
                realAssociations.add(a);
                this.usedRealSpotsNum[a.t - firstT] = this.usedRealSpotsNum[a.t - firstT] + 1;
            }
        assert h.leaf != null;
        //this.score += h.leaf.score;
        setScore(getScore() + h.leaf.score);
    }

    public GlobalHypothesis copy() {
        final int[] rsCopy = new int[this.usedRealSpotsNum.length];
        System.arraycopy(this.usedRealSpotsNum, 0, rsCopy, 0, this.usedRealSpotsNum.length);
        return new GlobalHypothesis(
                new HashSet<>(usedAssociations),
                new LinkedList<>(realAssociations),
                new LinkedList<>(hyps),
                score,
                rsCopy,
                firstT,
                lastT,
                this.tracker
        );
    }

    public void copyInto(final GlobalHypothesis ghCopy) {
        System.arraycopy(this.usedRealSpotsNum, 0, ghCopy.usedRealSpotsNum, 0, this.usedRealSpotsNum.length);
        ghCopy.usedAssociations.addAll(this.usedAssociations);
        ghCopy.realAssociations.addAll(this.realAssociations);
        ghCopy.hyps.addAll(this.hyps);
        ghCopy.score = this.score;
        ghCopy.firstT = this.firstT;
        ghCopy.lastT = this.lastT;
        ghCopy.tracker = this.tracker;
    }

    public void clear() {
        usedAssociations.clear();
        realAssociations.clear();
        this.score = 0;
        hyps.clear();
    }

    public int getRealSpotsNumAtT(final int t) {
        if (t >= firstT && t <= lastT) {
            return usedRealSpotsNum[t - firstT];
        }
        else return 0;
    }

    public int getNumNewTracks(final int t) {
        int numNT = 0;
        for (final Hypothesis h : hyps)//TODO: store num new track in each hypothesis
        {
            assert h.leaf != null;
            if (h.leaf.family.rootTrack == null && h.leaf.family.rootNode.association.t == t)
                numNT++;
        }
        return numNT;
    }

    public void penalizeFD(final int t, final double pFA, final HashSet<Association> originalRealAssociations) {
        for (final Association a : originalRealAssociations) {
            if (a.t == t && !realAssociations.contains(a)) {
                //	this.score+= (Math.log(pFA) - a.getBestScore());
                //this.score += Math.log(pFA);
                setScore(getScore() + Math.log(pFA));
            }
        }

    }

    public void penalizeAllFD(final double pFA, final HashSet<Association> originalRealAssociations) {
        for (final Association a : originalRealAssociations) {
            if (!realAssociations.contains(a)) {
                //	this.score+= (Math.log(pFA) - a.getBestScore());
                //this.score += Math.log(pFA);
                setScore(getScore() + Math.log(pFA));
            }
        }
    }

    public double getScore() {
        return score;
    }

    public void setScore(final double score) {
        this.score = score;
    }
}
