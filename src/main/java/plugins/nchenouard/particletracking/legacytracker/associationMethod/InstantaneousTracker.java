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

package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import fr.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.Tracker;
import plugins.nchenouard.spot.Spot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Vector;

/**
 * Implementation of the tracking algorithm described in:
 * Genovesio A, Liedl T, Emiliani V, Parak WJ, Coppey-Moisan M, Olivo-Marin JC.
 * Multiple particle tracking in 3-D+t microscopy: method and application to the
 * tracking of endocytosed quantum dots. IEEE Trans Image Process. 2006
 * May;15(5):1062-70. PubMed PMID: 16671288.
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com). Based on Auguste Genovesio original code.
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class InstantaneousTracker implements Tracker {
    class PostProcessThr extends Thread {
        final AssignProblem p;
        final int t;

        PostProcessThr(final AssignProblem p, final int t) {
            this.p = p;
            this.t = t;
        }

        @Override
        public void run() {
            postProcess(p, t);
        }
    }

    class SolveProblemThr extends Thread {
        final AssignProblem p;

        public SolveProblemThr(final AssignProblem p) {
            this.p = p;
        }

        @Override
        public void run() {
            assignementSolver.solve(p);
        }
    }

    ArrayList<Track> tracks;
    final Predictor predictor;
    boolean gateLikelihood;// = true;

    double gateFactor;// = 4;
    double totalGateLikelihood;// = 0.99;
    final AssignProblemSolver assignementSolver;
    public final static String[] associationMethodsNames = {"Max Likelihood", "Optimal Bayesian", "Greedy Nearest Neighbor"};

    int trackCount = 0;
    int maxConsecutivePred;

    public InstantaneousTracker(
            final AssignProblemSolver assignementSolver,
            final @NotNull Predictor predictor,
            final boolean gateLikelihood,
            final double gateFactor,
            final int maxConsecutivePred
    ) {
        this.assignementSolver = assignementSolver;
        this.predictor = predictor;
        this.gateLikelihood = gateLikelihood;
        this.gateFactor = gateFactor;
        this.totalGateLikelihood = predictor.getTotalGateLikelihood(gateLikelihood, gateFactor);
        tracks = new ArrayList<>();
        this.maxConsecutivePred = maxConsecutivePred;
    }

    protected double[][] computeLikelihoodTable(final int t, final @NotNull ArrayList<Track> activeTracks, final @NotNull ArrayList<Spot> spots) {
        final int numTracks = activeTracks.size();
        final int numDetect = spots.size();
        if (numTracks == 0 || numDetect == 0)
            return null;
//		computing likelihood table
        final double[][] likelihoods = new double[numDetect][numTracks];
        for (int i = 0; i < numTracks; i++) {
            final Track track = activeTracks.get(i);
            for (int j = 0; j < numDetect; j++)
                likelihoods[j][i] = track.likelihood(spots.get(j), gateLikelihood, gateFactor);
        }
//		for(int i=0; i < likelihoods.length; i++)
//		{
//		for (int j=0; j < likelihoods[i].length; j++)
//		{
//		PluginConsole.print(" "+likelihoods[i][j]);
//		}
//		PluginConsole.println();
//		}
        return likelihoods;
    }

    protected double[][] computeLikelihoodTable(final int t, final @NotNull ArrayList<Track> activeTracks, final @NotNull ArrayList<Spot> spots, final double gateFactor) {
        final int numTracks = activeTracks.size();
        final int numDetect = spots.size();
        if (numTracks == 0 || numDetect == 0)
            return null;
//		computing likelihood table
        final double[][] likelihoods = new double[numDetect][numTracks];
        for (int i = 0; i < numTracks; i++) {
            final Track track = activeTracks.get(i);
            for (int j = 0; j < numDetect; j++)
                likelihoods[j][i] = track.likelihood(spots.get(j), gateLikelihood, gateFactor);
        }
//		for(int i=0; i < likelihoods.length; i++)
//		{
//		for (int j=0; j < likelihoods[i].length; j++)
//		{
//		PluginConsole.print(" "+likelihoods[i][j]);
//		}
//		PluginConsole.println();
//		}
        return likelihoods;
    }

    protected double[][] computeInnovationTable(final int t, final @NotNull ArrayList<Track> activeTracks, final @NotNull ArrayList<Spot> spots) {
        final int numTracks = activeTracks.size();
        final int numDetect = spots.size();

        if (numTracks == 0 || numDetect == 0)
            return null;

        final double[][] innovations = new double[numDetect][numTracks];
        for (int i = 0; i < numTracks; i++) {
            final Track track = activeTracks.get(i);
            for (int j = 0; j < numDetect; j++)
                innovations[j][i] = track.normalizedInnovation(spots.get(j));
        }
        return innovations;
    }

    protected ArrayList<AssignProblem> createIndependentProblems(
            final int t,
            final double[][] likelihoods,
            final double[][] innovations,
            final @NotNull ArrayList<Track> activeTracks,
            final @NotNull ArrayList<Spot> detections
    ) {
        // TODO: check that likelihood is not null ie detections.size() > 0
        final int n_pred = activeTracks.size();
        final int n_meas = detections.size();

        if (n_meas == 0) {
            final ArrayList<AssignProblem> pbList = new ArrayList<>();
            for (final Track trk : activeTracks) {
                final AssignProblem pb = new AssignProblem();
                pb.tracks.add(trk);
                pb.t = t;
            }
            return pbList;
        }

        final ArrayList<HashSet<Integer>[]> accumulator = new ArrayList<>(n_pred);

        final boolean[] trackIsBusy = new boolean[n_pred];
        final boolean[] measIsBusy = new boolean[n_meas];

//		initialize tracks and measurements to not busy
        /*for (int j = 0; j < n_pred; j++)
            trackIsBusy[j] = false;
        for (int i = 0; i < n_meas; i++)
            measIsBusy[i] = false;*/
//		recursive construction of connected tracks/measurements (in validation gates)
        for (int j = 0; j < n_pred; j++) {
            if (!trackIsBusy[j]) {
                @SuppressWarnings("unchecked") final HashSet<Integer>[] hs = (HashSet<Integer>[]) new HashSet[2];
                hs[0] = new HashSet<>(5); // prediction
                hs[1] = new HashSet<>(5); // measurement
                recursAddTrack(j, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
                accumulator.add(hs);
            }
        }
//		for all measurements (should stay only lonely measurements from now)
        for (int i = 0; i < n_meas; i++) {
            if (!measIsBusy[i]) {
                @SuppressWarnings("unchecked") final HashSet<Integer>[] hs = (HashSet<Integer>[]) new HashSet[2];
                hs[0] = new HashSet<>(5); // prediction
                hs[1] = new HashSet<>(5); // measurement
                recursAddMeasurement(i, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
                accumulator.add(hs);
            }
        }
//		AssignProblem' creation
        final ArrayList<AssignProblem> problems = new ArrayList<>();
        for (final HashSet<Integer>[] hs : accumulator) {
            final AssignProblem pb = new AssignProblem();
            final Object[] trackTable = hs[0].toArray();
            for (final Object o : trackTable)
                pb.tracks.add(activeTracks.get((Integer) o));
            final Object[] detectTable = hs[1].toArray();
            for (final Object o : detectTable)
                pb.detections.add(detections.get(((Integer) o).intValue()));
            final double[][] compactLikelihoods = new double[detectTable.length][trackTable.length];
            final double[][] compactInnovations = new double[detectTable.length][trackTable.length];
            for (int j = 0; j < detectTable.length; j++) {
                for (int i = 0; i < trackTable.length; i++) {
                    compactLikelihoods[j][i] = likelihoods[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
                    compactInnovations[j][i] = innovations[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
                }
            }
            pb.likelihoods = compactLikelihoods;
            pb.innovations = compactInnovations;
            pb.t = t;
            if (assignementSolver.numMaxMeasurements <= 0 && assignementSolver.numMaxTracks <= 0)
                problems.add(pb);
            else smallAssignProblemCreation(assignementSolver.numMaxMeasurements, assignementSolver.numMaxTracks, pb, problems, gateFactor);
        }
        return problems;
    }

    protected void smallAssignProblemCreation(final int maxNumMeasurements, final int maxNumTracks, final AssignProblem pb, final ArrayList<AssignProblem> problems, final double gFactor) {
        boolean valid = (maxNumMeasurements <= 0 || pb.detections.size() < maxNumMeasurements);
        valid = valid && (maxNumTracks <= 0 || pb.tracks.size() < maxNumTracks);
        if (valid) {
            problems.add(pb);
        }
        else {
            //try to reduce gateFactor to reduce problem's size
            final double gFactor2 = gFactor * 0.75;
            final int n_pred = pb.tracks.size();
            final int n_meas = pb.detections.size();
            final double[][] likelihoods = computeLikelihoodTable(pb.t, pb.tracks, pb.detections, gFactor2);
            final double[][] innovations = computeInnovationTable(pb.t, pb.tracks, pb.detections);

            final ArrayList<HashSet<Integer>[]> accumulator = new ArrayList<>(n_pred);
            final boolean[] trackIsBusy = new boolean[n_pred];
            final boolean[] measIsBusy = new boolean[n_meas];

//			initialize tracks and measurements to not busy
            /*for (int j = 0; j < n_pred; j++)
                trackIsBusy[j] = false;
            for (int i = 0; i < n_meas; i++)
                measIsBusy[i] = false;*/
//			recursive construction of connected tracks/measurements (in validation gates)
            for (int j = 0; j < n_pred; j++) {
                if (!trackIsBusy[j]) {
                    @SuppressWarnings("unchecked") final HashSet<Integer>[] hs = (HashSet<Integer>[]) new HashSet[2];
                    hs[0] = new HashSet<>(5); // prediction
                    hs[1] = new HashSet<>(5); // measurement
                    recursAddTrack(j, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
                    accumulator.add(hs);
                }
            }
//			for all measurements (should stay only lonely measurements from now)
            for (int i = 0; i < n_meas; i++) {
                if (!measIsBusy[i]) {
                    @SuppressWarnings("unchecked") final HashSet<Integer>[] hs = (HashSet<Integer>[]) new HashSet[2];
                    hs[0] = new HashSet<>(5); // prediction
                    hs[1] = new HashSet<>(5); // measurement
                    recursAddMeasurement(i, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
                    accumulator.add(hs);
                }
            }
//			AssignProblem' creation
            for (final HashSet<Integer>[] hs : accumulator) {
                final AssignProblem pb2 = new AssignProblem();
                final Object[] trackTable = hs[0].toArray();
                for (final Object o : trackTable) {
                    pb2.tracks.add(pb.tracks.get((Integer) o));
                }
                final Object[] detectTable = hs[1].toArray();
                for (final Object o : detectTable) {
                    pb2.detections.add(pb.detections.get((Integer) o));
                }
                final double[][] compactLikelihoods = new double[detectTable.length][trackTable.length];
                final double[][] compactInnovations = new double[detectTable.length][trackTable.length];
                for (int j = 0; j < detectTable.length; j++) {
                    for (int i = 0; i < trackTable.length; i++) {
                        compactLikelihoods[j][i] = likelihoods[(Integer) detectTable[j]][(Integer) trackTable[i]];
                        compactInnovations[j][i] = innovations[(Integer) detectTable[j]][(Integer) trackTable[i]];
                    }
                }
                pb2.likelihoods = compactLikelihoods;
                pb2.innovations = compactInnovations;
                pb2.t = pb.t;
                smallAssignProblemCreation(assignementSolver.numMaxMeasurements, assignementSolver.numMaxTracks, pb2, problems, gFactor2);
            }
        }
    }

    protected Track createTrack(final Spot spot, final int t) {
        final Predictor new_predictor = predictor.copyInit();
        trackCount++;
        final Track track = new Track(trackCount, new_predictor, t, gateFactor);
        track.initTrack(spot, t);
        return track;
    }

    protected ArrayList<Track> filterTracks(final @NotNull ArrayList<Track> tracks, final int t) {
        synchronized (tracks) {
            final ArrayList<Track> activeTracks = new ArrayList<>();
            for (final Track track : tracks)
                if (track.isStillActive(maxConsecutivePred))
                    activeTracks.add(track);
            return activeTracks;
        }
    }

    public ArrayList<Track> getTracks() {
        return tracks;
    }


    protected void initTracks(final @NotNull ArrayList<Spot> spots, final int t) {
        for (final Spot spot : spots) {
            final Track track = createTrack(spot, t);
            if (track != null)
                synchronized (tracks) {
                    tracks.add(track);
                }
        }
    }

    protected void postProcess(final @NotNull AssignProblem pb, final int t) {
//		Create new tracks
        initTracks(pb.tracksToBeCreated, t);
        // Prolongate lonely tracks
        prolongateTracks(pb.tracksToBeProlongated, t);
    }

    protected void postProcessProblems(final ArrayList<AssignProblem> problems, final int t, boolean parallel) {
        parallel = false;
        if (parallel) {
            final LinkedList<Thread> listThreads = new LinkedList<>();
            for (final AssignProblem p : problems) {
                final Thread b = new PostProcessThr(p, t);
                b.start();
                listThreads.add(b);
            }
            for (final Thread thr : listThreads)
                try {
                    thr.join();
                }
                catch (final InterruptedException e) {
                    IcyLogger.error(this.getClass(), e);
                }
            listThreads.clear();
        }
        else
            for (final AssignProblem p : problems) {
                postProcess(p, t);
            }
    }


    protected void preProcess(final ArrayList<AssignProblem> problems, final AssignProblem pb, final int t) {
        //
    }

    protected void preProcessProblems(final @NotNull ArrayList<AssignProblem> problems, final int t, final boolean parallel) {
        for (final AssignProblem p : problems) {
            preProcess(problems, p, t);
        }
    }

    protected void prolongateTracks(final @NotNull ArrayList<Track> tracks, final int t) {
        for (final Track track : tracks)
            track.prolongate(t);
    }

    protected void recursAddMeasurement(
            final int measIndex,
            final double[][] likelihood,
            final HashSet<Integer> pred,
            final HashSet<Integer> meas,
            final boolean[] trackIsBusy,
            final boolean @NotNull [] measIsBusy
    ) {
        if (!measIsBusy[measIndex]) {
            measIsBusy[measIndex] = true;
            meas.add(measIndex);
            for (int j = 0; j < likelihood[measIndex].length; j++) {
                if (likelihood[measIndex][j] > 0) {
                    recursAddTrack(j, likelihood, pred, meas, trackIsBusy, measIsBusy);
                }
            }
        }
    }

    protected void recursAddTrack(
            final int trackIndex,
            final double[][] likelihood,
            final HashSet<Integer> pred,
            final HashSet<Integer> meas,
            final boolean @NotNull [] trackIsBusy,
            final boolean[] measIsBusy
    ) {
        if (!trackIsBusy[trackIndex]) {
            trackIsBusy[trackIndex] = true;
            pred.add(trackIndex);
            for (int i = 0; i < likelihood.length; i++) {
                if (likelihood[i][trackIndex] > 0) {
                    recursAddMeasurement(i, likelihood, pred, meas, trackIsBusy, measIsBusy);
                }
            }
        }
    }

    //Processor processor = new Processor(1000000, 8);

    // FIXME: 04/09/2023 Parallel process not working
    protected void solveProblems(final @NotNull ArrayList<AssignProblem> problems, boolean parallel) {
        for (final AssignProblem p : problems)
            assignementSolver.solve(p);
    }

    @Override
    public void track(final int t, final Vector<Spot> spotsVector) {
        final ArrayList<Spot> spots = new ArrayList<>(spotsVector);
//		filter active tracks
        final ArrayList<Track> activeTracks = filterTracks(tracks, t);
//		PluginConsole.println("active tracks "+activeTracks.size());
        if (!activeTracks.isEmpty()) {
            //compute likelihoodTable
            final double[][] likelihoods = computeLikelihoodTable(t, activeTracks, spots);
            //compute innovations //useful for hypothesis tests pruning
            final double[][] innovations = computeInnovationTable(t, activeTracks, spots);
            //create independent problems
            final ArrayList<AssignProblem> problems = createIndependentProblems(t, likelihoods, innovations, activeTracks, spots);
            final boolean parallel = true;
            preProcessProblems(problems, t, parallel);
            solveProblems(problems, parallel);
            postProcessProblems(problems, t, parallel);
        }
        else
            initTracks(spots, t);
    }
}
