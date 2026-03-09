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

import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Family object represents a tree of track segments with a shared root node (association)
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class Family {
    MHTrack2s rootNode;
    ValidatedTrack rootTrack;
    double trackScore = 0;
    double rootScore = 0;
    HashSet<Association> usedAssociations = new HashSet<>();
    LinkedList<MHTrack2s> leafs = new LinkedList<>();
    LinkedList<Hypothesis> hypotheses = new LinkedList<>();
    int rootTime;
    MHTrack2s lastSelectedLeaf = null;
    IdCluster cluster = null;

    protected void recursUpdate(final @NotNull MHTrack2s trk) {
        //since it is validated confirm the track
        trk.confirmed = true;
        //update used associations
        usedAssociations.add(trk.association);
        //update followingSegments
        if (trk.followingSegments.isEmpty())
            leafs.add(trk);
        else
            for (final MHTrack2s trk2 : trk.followingSegments)
                recursUpdate(trk2);
    }

    public void updateFamily(final MHTrack2s track, final ValidatedTrack validatedTrack, final int leafTime) {
        this.rootNode = track;
        this.rootTrack = validatedTrack;
        this.rootTime = rootNode.association.t;
        this.trackScore = rootScore;
        this.rootScore = rootNode.score;
        //build leafs and used spots
        for (final MHTrack2s trk : leafs)
            trk.hypothesis = null;
        leafs.clear();
        usedAssociations.clear();
        recursUpdate(track);
    }

    Vector<Hypothesis> getCompatibleHypothesis(final @NotNull GlobalHypothesis gh) {
        final Vector<Hypothesis> hypList = new Vector<>();
        if (!gh.usedAssociations.contains(this.rootNode.association)) {
            if (!rootNode.followingSegments.isEmpty())
                for (final MHTrack2s trk : rootNode.followingSegments)
                    recursCompatibleHypotheses(gh, trk, hypList);
            else if (rootNode.hypothesis != null)
                hypList.add(rootNode.hypothesis);
        }
        return hypList;
    }

    void recursCompatibleHypotheses(final @NotNull GlobalHypothesis gh, final @NotNull MHTrack2s node, final Vector<Hypothesis> hypList) {
        if (!gh.usedAssociations.contains(node.association)) {
            if (!node.followingSegments.isEmpty())
                for (final MHTrack2s trk : node.followingSegments) {
                    recursCompatibleHypotheses(gh, trk, hypList);
                }
            else if (node.hypothesis != null)
                hypList.add(node.hypothesis);
        }
    }

    @Contract(pure = true)
    private boolean belowBound(final @NotNull GlobalHypothesis gh, final double bestScore, final @NotNull MHTrack2s node) {
        return gh.score + node.bestScore < bestScore;
    }

    Vector<Hypothesis> getCompatibleHypothesis(final GlobalHypothesis gh, final double bestScore) {
//		long ml1 = System.currentTimeMillis();
//		if (bestHyp==null)
//			return getCompatibleHypothesis(gh);
        final ThreadCounter tc = new ThreadCounter();
//		ExecutorService executor = Executors.newFixedThreadPool(4);
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        tc.reset();
        final Vector<Hypothesis> hypList = new Vector<>();
        if (!belowBound(gh, bestScore, this.rootNode)) {
            if (!gh.usedAssociations.contains(this.rootNode.association)) {
                if (!rootNode.followingSegments.isEmpty())
                    for (final MHTrack2s trk : rootNode.followingSegments)
//						recursCompatibleHypotheses(gh, bestScore, trk, hypList);
                    {
                        tc.increaseCount();
                        executor.execute(new RecursCompatibleThr(gh, bestScore, trk, hypList, tc, executor));
                    }
                else if (rootNode.hypothesis != null)
                    hypList.add(rootNode.hypothesis);
            }
        }
        tc.lock.lock();
        try {
            while (tc.cntThreads != 0)
                tc.countAndDeathEquality.await();
        }
        catch (final InterruptedException e) {
            IcyLogger.error(this.getClass(), e);
        }
        finally {
            tc.lock.unlock();
        }
        executor.shutdown();
//		long ml2 = System.currentTimeMillis();
//		HMMMHTracker.compatibleHypTime+=(ml2-ml1);
        return hypList;
    }

    ArrayList<Hypothesis> getCompatibleHypothesisSingleThread(final GlobalHypothesis gh, final double bestScore) {
        final ArrayList<Hypothesis> hypList = new ArrayList<>();
        final Stack<MHTrack2s> waitingTracks = new Stack<>();
        if (!belowBound(gh, bestScore, this.rootNode)) {
            if (!gh.usedAssociations.contains(this.rootNode.association)) {
                if (!rootNode.followingSegments.isEmpty())
                    waitingTracks.addAll(rootNode.followingSegments);
                else if (rootNode.hypothesis != null)
                    hypList.add(rootNode.hypothesis);
            }
        }
        while (!waitingTracks.isEmpty()) {
            recursCompatibleHypotheses(gh, bestScore, waitingTracks.pop(), hypList, waitingTracks);
        }
        hypList.sort(new HypothesisComparator());
        return hypList;
    }

    void getCompatibleHypothesisSingleThread(final GlobalHypothesis gh, final double bestScore, final ArrayList<Hypothesis> hypList, final Stack<MHTrack2s> waitingTracks) {
        if (!belowBound(gh, bestScore, this.rootNode)) {
            if (!gh.usedAssociations.contains(this.rootNode.association)) {
                if (!rootNode.followingSegments.isEmpty())
                    waitingTracks.addAll(rootNode.followingSegments);
                else if (rootNode.hypothesis != null)
                    hypList.add(rootNode.hypothesis);
            }
        }
        while (!waitingTracks.isEmpty()) {
            recursCompatibleHypotheses(gh, bestScore, waitingTracks.pop(), hypList, waitingTracks);
        }
        hypList.sort(new HypothesisComparator());
    }

    void recursCompatibleHypotheses(final GlobalHypothesis gh, final double bestScore, final MHTrack2s node, final Vector<Hypothesis> hypList, final ThreadCounter tc, final ExecutorService executor) {
        if (!belowBound(gh, bestScore, this.rootNode)) {
            if (!gh.usedAssociations.contains(node.association)) {
                if (!node.followingSegments.isEmpty())
                    for (final MHTrack2s trk : node.followingSegments)
//						recursCompatibleHypotheses(gh, bestScore, trk, hypList);
                    {
                        tc.increaseCount();
                        executor.execute(new RecursCompatibleThr(gh, bestScore, trk, hypList, tc, executor));
                    }
                else if (node.hypothesis != null)
                    hypList.add(node.hypothesis);
            }
        }
    }

    void recursCompatibleHypotheses(final GlobalHypothesis gh, final double bestScore, final MHTrack2s node, final ArrayList<Hypothesis> hypList, final Stack<MHTrack2s> taskList) {
        if (!belowBound(gh, bestScore, this.rootNode)) {
            if (!gh.usedAssociations.contains(node.association)) {
                if (!node.followingSegments.isEmpty())
                    taskList.addAll(node.followingSegments);
                else if (node.hypothesis != null)
                    hypList.add(node.hypothesis);
            }
        }
    }

    public void buildHypothesesAndResetBestScores() {
        final ArrayList<Association> measurementsList = new ArrayList<>();
        measurementsList.add(rootNode.association);
        rootNode.resetBestScore();

        final Hypothesis h = new Hypothesis(measurementsList, rootNode);
        if (rootNode.followingSegments.isEmpty()) {
            if (rootNode.confirmed) {
                hypotheses.add(h);
                rootNode.hypothesis = h;
            }
        }
        else
            recursBuildHyp(h, hypotheses);

        usedAssociations.clear();
        for (final Hypothesis hyp : hypotheses)
            usedAssociations.addAll(hyp.usedAssociations);
        //find best scores for each node
        correctHypothesesScore();
        putScoresOnNodes();
    }

    private void correctHypothesesScore() {
        //	System.out.println("DIFF "+rootScore+" "+trackScore+" "+Math.log(rootNode.associationLikelihood));
        for (final Hypothesis hyp : hypotheses) {
            double bnf = 0;//best non feasible score
            for (final Association a : hyp.usedAssociations)
                bnf += a.getBestScore();
            Objects.requireNonNull(hyp.leaf).bestScore = hyp.leaf.score - bnf - rootScore;
        }
    }

    private void putScoresOnNodes() {
        for (final Hypothesis h : hypotheses) {
            //h.leaf.bestScore = h.leaf.score;
            recursPutScoresOnNodes(Objects.requireNonNull(h.leaf));
        }
    }

    private void recursPutScoresOnNodes(final @NotNull MHTrack2s node) {
        if (node.precedingSegment != null) {
            if (node.precedingSegment.bestScore < node.bestScore) {
                node.precedingSegment.bestScore = node.bestScore;
                recursPutScoresOnNodes(node.precedingSegment);
            }
        }
    }

    protected void recursBuildHyp(final @NotNull Hypothesis h, final LinkedList<Hypothesis> hList) {
        for (final MHTrack2s nextTrk : Objects.requireNonNull(h.leaf).followingSegments) {
            nextTrk.resetBestScore();
            final ArrayList<Association> associationList = new ArrayList<>(h.usedAssociations);
            associationList.add(nextTrk.association);
            final Hypothesis nextH = new Hypothesis(associationList, nextTrk);
            if (nextTrk.followingSegments.isEmpty()) {
                if (nextTrk.confirmed) {
                    hList.add(nextH);
                    nextTrk.hypothesis = nextH;
                }
            }
            else
                recursBuildHyp(nextH, hList);
        }
    }

    protected void printHypotheses() {
		/*PluginConsole.println("Family hypothesis ");
		for (Hypothesis h:hypothesis)
		{
			PluginConsole.print("H: ");
			for (Association a:h.usedAssociations)
				PluginConsole.print("["+a.id +" "+a.isPrediction+" "+a.t+"]");
			PluginConsole.println("score "+h.leaf.score);
		}*/
    }

    class RecursCompatibleThr implements Runnable {
        ThreadCounter tc;
        ExecutorService executor;
        GlobalHypothesis gh;
        MHTrack2s trk;
        Vector<Hypothesis> hypList;
        double bestScore;

        @Contract(pure = true)
        public RecursCompatibleThr(
                final GlobalHypothesis gh,
                final double bestScore,
                final MHTrack2s trk,
                final Vector<Hypothesis> hypList,
                final ThreadCounter tc,
                final ExecutorService executor
        ) {
            this.tc = tc;
            this.gh = gh;
            this.trk = trk;
            this.hypList = hypList;
            this.bestScore = bestScore;
            this.executor = executor;
        }

        @Override
        public void run() {
            recursCompatibleHypotheses(gh, bestScore, trk, hypList, tc, executor);
            tc.increaseDeath();
        }
    }

    static class HypothesisComparator implements Comparator<Hypothesis> {
        @Override
        public int compare(final @NotNull Hypothesis o1, final @NotNull Hypothesis o2) {
            if (Objects.requireNonNull(o1.leaf).score > Objects.requireNonNull(o2.leaf).score)
                return 1;
            else if (o1.leaf.score < o2.leaf.score)
                return -1;
            return 0;
        }
    }
}
