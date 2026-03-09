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
import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.particletracking.DetectionSpotTrack;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.StoppableTracker;
import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Spot;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main class for the implementation of the Multiple Hypothesis Tracking Algorithm described in:
 * Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
 * "Multiple Hypothesis Tracking for Cluttered Biological Image Sequences,"
 * IEEE Transactions on Pattern Analysis and Machine Intelligence,
 * vol. 35, no. 11, pp. 2736-3750, Nov., 2013
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.2
 * date 2014-09-12
 * license gpl v3.0
 */
public class HMMMHTracker implements StoppableTracker, AutoCloseable {
    final int K;// decision delay
    final double gateFactor;
    final double volume;

    final double Pd;// probability of detection
    final double totalGateLikelihood;
    final double pDpG;

    final double pFA;
    final double pNT;
    final double pNT0;

    double p10; // perceivable to non-perceivable transition probability
    double p11; // non-perceivable to perceivable transition probability
    double pc; // confirmation threshold
    double pt; // termination threshold

    public final List<Predictor> initPredictors;

    boolean parallelComputing;// = true;

    private final LinkedList<ValidatedTrack> validatedTracks;
    private final ArrayList<Family> families;
    private final LinkedList<Cluster> trackClusters;
    private final ArrayList<Association> currentAssociations;

    public volatile static long totalElapseTime = 0;
    public volatile static long compatibleElapseTime = 0;
    public volatile static long tracksFormationTime = 0;
    public volatile static long createClustersTime = 0;
    public volatile static long hypothesesFormationTime = 0;
    public volatile static long MLFormationTime = 0;
    public volatile static long compatibleHypTime = 0;
    public volatile static long buildGHTime = 0;
    public volatile static long applyHypTime = 0;

    static class FamilyAndAssociation {
        Association a;
        Family f;

        @Contract(pure = true)
        public FamilyAndAssociation(final Association a, final Family f) {
            this.a = a;
            this.f = f;
        }
    }

    public HMMMHTracker(
            final @NotNull List<Predictor> predictors,
            final double volume,
            final double gateFactor,
            final double probaDetect,
            final double densityFalseDetection,
            final double densityInitialTrack,
            final double densityNewTrack,
            final int treeDepth,
            final int dim,
            final boolean useMultithreading
    ) {
        this.volume = volume;
        this.initPredictors = predictors;
        this.gateFactor = Math.max(gateFactor, 0);
        this.Pd = probaDetect;
        this.K = Math.max(treeDepth, 0);

        totalGateLikelihood = predictors.getFirst().getTotalGateLikelihood(true, gateFactor);
        pDpG = Pd * totalGateLikelihood;
        pFA = densityFalseDetection;
        pNT = densityNewTrack;
        pNT0 = densityInitialTrack;

        this.validatedTracks = new LinkedList<>();
        this.families = new ArrayList<>();
        this.trackClusters = new LinkedList<>();
        this.currentAssociations = new ArrayList<>();

        this.parallelComputing = useMultithreading;
    }

    public void changeExistenceSettings(final double p10, final double confirmationThreshold, final double terminationThreshold) {
        this.p10 = p10;
        this.p11 = 1 - p10;
        this.pc = confirmationThreshold;
        this.pt = terminationThreshold;
        MHTrack2s.INIT_P10 = p10;
        MHTrack2s.INIT_P11 = 1 - p10;
        MHTrack2s.INIT_Pc = this.pc;
        MHTrack2s.INIT_Pt = this.pt;
    }

    @SuppressWarnings("resource")
    protected void hypothesisFormation() {
        if (parallelComputing) {
            final int processors = Runtime.getRuntime().availableProcessors();
            final ExecutorService service = Executors.newFixedThreadPool(processors);
            // ExecutorService service = Executors.newSingleThreadExecutor();
            final CountDownLatch latch = new CountDownLatch(families.size());
            for (final Family f : families) {
                service.execute(() -> {
                    try {
                        f.buildHypothesesAndResetBestScores();
                    }
                    finally {
                        latch.countDown();
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (final InterruptedException E) {
                IcyLogger.error(this.getClass(), E);
            }
            // FixedThreadPool.Task[] runArray = new FixedThreadPool.Task[families.size()];
            // int cnt = 0;
            // for (Family f1:families)
            // {
            // final Family f = f1;
            // runArray[cnt] = new FixedThreadPool.Task() {
            // @Override
            // public void run() {
            // if (f == null)
            // System.out.println("F1 NULL");
            // else
            // f.buildHypothesesAndResetBestScores();
            // }
            // };
            // cnt++;
            // }
            // int processors = Runtime.getRuntime().availableProcessors();
            // FixedThreadPool pool = new FixedThreadPool(runArray);
            // pool.runThreads(processors);
        }
        else
            for (final Family f1 : families)
                f1.buildHypothesesAndResetBestScores();
    }

    /**
     * Merge clusters of tracks with common observations
     *
     * @param t int
     */
    protected void createClusters(final int t) {
        trackClusters.clear();
        final LinkedList<ConstructionCluster> clusters = new LinkedList<>();
        for (final Family f : families) {
            boolean confirmed = false;
            for (final MHTrack2s leaf : f.leafs) {
                if (leaf.confirmed) {
                    confirmed = true;
                    break;
                }
            }
            if (confirmed) {
                final ConstructionCluster cl = new ConstructionCluster();
                cl.families.add(f);
                for (final Association a : f.usedAssociations)
                    if (!a.isPrediction)
                        cl.associations.add(a);
                clusters.add(cl);
            }
        }
        final LinkedList<ConstructionCluster> finishedClusters = new LinkedList<>();
        while (!clusters.isEmpty()) {
            final ConstructionCluster cl = clusters.poll();
            boolean fused = false;
            for (final ConstructionCluster cl2 : clusters) {
                if (cl != cl2) {
                    for (final Association a : cl2.associations) {
                        if (cl.associations.contains(a)) {
                            cl2.associations.addAll(cl.associations);
                            cl2.families.addAll(cl.families);
                            fused = true;
                            break;
                        }
                    }
                }
                if (fused)
                    break;
            }
            if (!fused)
                finishedClusters.add(cl);
        }

        for (final ConstructionCluster cl : finishedClusters) {
            final Cluster cluster;
            cluster = new ClusterSolveLPCustom(this, t - K, t, K, pFA);

            for (final Family f : cl.families) {
                final ArrayList<Family> cFamily = cluster.concurrentFamilies.computeIfAbsent(f.rootTime, k -> new ArrayList<>());
                cFamily.add(f);
                for (final Association a : f.usedAssociations)
                    if (!(a.isPrediction))
                        cluster.realAssociations.add(a);
            }
            cluster.initRealSpotsNum();
            trackClusters.add(cluster);
        }
    }

    /**
     * Merge clusters of tracks with common observations
     *
     * @param t int
     */
    protected void createClusters2(final int t) {
        int numCluster = 0;
        final HashSet<Association> associations = new HashSet<>();
        // create an association table between spots and families
        final ArrayList<FamilyAndAssociation> faTable = new ArrayList<>();
        for (final Family f : families) {
            for (final Association a : f.usedAssociations) {
                // if (!(s instanceof VirtualSpot))//acceleate but families with only virtual mesurements will be lost
                // with this
                // {
                faTable.add(new FamilyAndAssociation(a, f));
                associations.add(a);
                // }
            }
        }
        println("create clusters #spots " + associations.size());
        final HashMap<Family, Integer> familyTable = new HashMap<>();
        final HashMap<Association, Integer> associationTable = new HashMap<>();
        for (final Association as : associations) {
            final Integer idCl;
            if (associationTable.containsKey(as))
                idCl = associationTable.get(as);
            else
                idCl = numCluster++;
            // find FamilyAndAssociations that use this association
            final ArrayList<FamilyAndAssociation> associationsList = new ArrayList<>();
            for (final FamilyAndAssociation fa : faTable) {
                if (fa.a == as)
                    associationsList.add(fa);
            }

            for (final FamilyAndAssociation a : associationsList) {
                // if the family is already in a cluster, then change the id of this cluster to the current one.
                if (familyTable.containsKey(a.f)) {
                    final Integer prevCluster = familyTable.get(a.f);
                    if (!prevCluster.equals(idCl)) {
                        for (final Entry<Family, Integer> e : familyTable.entrySet()) {
                            if (e.getValue().equals(prevCluster))
                                familyTable.put(e.getKey(), idCl);
                        }
                        for (final Entry<Association, Integer> e : associationTable.entrySet()) {
                            if (e.getValue().equals(prevCluster))
                                associationTable.put(e.getKey(), idCl);
                        }
                    }
                }
                familyTable.put(a.f, idCl); // set the family's cluster
                // if the association is already in a cluser, then change the id of this cluster to the current one
                if (associationTable.containsKey(a.a)) {
                    final Integer prevCluster = associationTable.get(a.a);
                    if (!prevCluster.equals(idCl)) {
                        for (final Entry<Family, Integer> e : familyTable.entrySet()) {
                            if (e.getValue().equals(prevCluster))
                                familyTable.put(e.getKey(), idCl);
                        }
                        for (final Entry<Association, Integer> e : associationTable.entrySet()) {
                            if (e.getValue().equals(prevCluster))
                                associationTable.put(e.getKey(), idCl);
                        }
                    }
                }
                // set the association cluster
                associationTable.put(a.a, idCl);
            }
        }
        trackClusters.clear();
        final HashMap<Integer, Cluster> clustersTable = new HashMap<>();
        for (final Entry<Association, Integer> e : associationTable.entrySet()) {
            final Cluster cl;
            if (clustersTable.containsKey(e.getValue())) // if association is in the table get the cluster
                cl = clustersTable.get(e.getValue());
            else
            // else create an new cluster
            {
                cl = new ClusterSolveLPCustom(this, t - K, t, K, pFA);
                clustersTable.put(e.getValue(), cl);
            }
            final Association a = e.getKey();
            if (!(a.isPrediction))
                cl.realAssociations.add(a);
        }
        for (final Entry<Family, Integer> e : familyTable.entrySet()) {
            final Cluster cl;
            if (clustersTable.containsKey(e.getValue()))
                cl = clustersTable.get(e.getValue());
            else {
                cl = new ClusterSolveLPCustom(this, t - K, t, K, pFA);
                clustersTable.put(e.getValue(), cl);
            }
            final ArrayList<Family> cFamily = cl.concurrentFamilies.computeIfAbsent(e.getKey().rootTime, k -> new ArrayList<>());
            cFamily.add(e.getKey());
        }
        trackClusters.addAll(clustersTable.values());
        for (final Cluster cl : trackClusters)
            cl.initRealSpotsNum();

    }

    public ArrayList<TrackSegment> getTracks() {
        final ArrayList<TrackSegment> tsList = new ArrayList<>();
        final LinkedList<ValidatedTrack> validated = new LinkedList<>(validatedTracks);
        for (final ValidatedTrack track : validated) {
            final ArrayList<Detection> detections = new ArrayList<>();
            for (final Association a : track.associations) {
                // Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z,
                // a.t);
                final Detection detect = new DetectionSpotTrack(a.spot, a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
                final TrackSegment motherTS = new TrackSegment(detections);
                tsList.add(motherTS);
                // following ones are potential track segments
                if (track.family.rootNode != null) {
                    for (final MHTrack2s ts : track.family.rootNode.followingSegments) {
                        final TrackSegment ts2 = recursBuildTrackSegments(ts, tsList);
                        motherTS.addNext(ts2);
                    }
                }
            }
        }
        for (final Family f : families) {
            if (f.rootNode != null && f.rootTrack == null) {
                final ArrayList<Detection> detections = new ArrayList<>();
                final Association a = f.rootNode.association;
//              Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z, a.t);
                final Detection detect = new DetectionSpotTrack(a.spot, a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
                final TrackSegment ts = new TrackSegment(detections);
                tsList.add(ts);
                for (final MHTrack2s next : f.rootNode.followingSegments) {
                    final TrackSegment ts2 = recursBuildTrackSegments(next, tsList);
                    ts.addNext(ts2);
                }
            }
        }
        return tsList;
    }

    public ArrayList<TrackSegment> getCompleteTracks() {
        final ArrayList<TrackSegment> tsList = new ArrayList<>();
        final LinkedList<ValidatedTrack> validated = new LinkedList<>(validatedTracks);
        for (final ValidatedTrack track : validated) {
            final ArrayList<Detection> detections = new ArrayList<>();
            for (final Association a : track.associations) {
                final Detection detect = new DetectionSpotTrack(a.spot, a.t);
                // Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z,
                // a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
            }
            if (track.family != null) {
                final Family f = track.family;
                if (f.lastSelectedLeaf != null) {
                    final MHTrack2s mht = f.lastSelectedLeaf;
                    if (!track.associations.contains(mht.association)) {
                        MHTrack2s node = mht;
                        final LinkedList<Detection> detList = new LinkedList<>();
                        boolean stop = false;
                        while (node != null && !stop) {
                            stop = (node == f.rootNode);
                            if (!stop) {
                                final Association a = node.association;
//                              Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y,
//                              a.spot.mass_center.z, a.t);
                                final Detection detect = new DetectionSpotTrack(a.spot, a.t);
                                if (a.isPrediction)
                                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                                else
                                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                                detList.addFirst(detect);
                                node = node.precedingSegment;
                            }
                        }
                        detections.addAll(detList);
                    }
                }
            }
            // remove last virtual detections of a track segment
            boolean stop = false;
            while (!stop && !detections.isEmpty()) {
                final Detection d = detections.getLast();
                if (d.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION)
                    detections.removeLast();
                else
                    stop = true;
            }
            final TrackSegment motherTS = new TrackSegment(detections);
            tsList.add(motherTS);
        }
        return tsList;
    }

    boolean verbose = false;
    private double p1_0;

    protected void println(final String text) {
        if (verbose)
            IcyLogger.trace(this.getClass(), text);
    }

    static class BuildHypTask implements FixedThreadPool.Task {
        Cluster clusterTask;
        int tTask;

        @Contract(pure = true)
        public BuildHypTask(final Cluster cluster, final int tt) {
            this.clusterTask = cluster;
            this.tTask = tt;
        }

        @Override
        public void run() {
            clusterTask.buildAndApplyBestHypTree(tTask);
        }

    }

    @SuppressWarnings("resource")
    protected void buildAndApplyGlobalHyp(final int t) {
        // ArrayList<Family> fCopy = families;
        final ArrayList<Family> fCopy = new ArrayList<>(families);
        families.clear();
        // families = new ArrayList<Family>();
        // retrieve families that are not in a Cluster and are less deep than K
        final LinkedList<Family> fCluster = new LinkedList<>();
        for (final Cluster cl : trackClusters)
            for (final ArrayList<Family> fList : cl.concurrentFamilies.values())
                fCluster.addAll(fList);
        for (final Family f : fCopy)
            if (!(fCluster.contains(f) || f.rootTime <= t - K + 1))
                families.add(f);
        println("num families before hyp selection : " + families.size());

        if (parallelComputing) {
            final int processors = Runtime.getRuntime().availableProcessors();
            // ExecutorService service = Executors.newSingleThreadExecutor();
            final ExecutorService service = Executors.newFixedThreadPool(processors);
            final CountDownLatch latch = new CountDownLatch(trackClusters.size());
            // final Lock latchLock = new ReentrantLock();
            for (final Cluster c : trackClusters) {
                service.execute(() -> {
                    try {
                        c.buildAndApplyBestHypTree(t);
                    }
                    finally {
                        latch.countDown();
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (final InterruptedException e) {
                IcyLogger.error(this.getClass(), e);
            }

            // FixedThreadPool.Task[] runArray = new FixedThreadPool.Task[trackClusters.size()];
            // ArrayList<FixedThreadPool.Task> list = new ArrayList<FixedThreadPool.Task>();
            // for (Cluster c:trackClusters)
            // {
            // // final Cluster c1 = c;
            // // runArray[cnt] = new FixedThreadPool.Task() {
            // // @Override
            // // public void run() {
            // // c1.buildAndApplyBestHypTree(t);
            // // }
            // // };
            // // runArray[cnt] = new BuildHypTask(c, t);
            // // cnt++;
            // list.add(new BuildHypTask(c, t));
            // }
            // int cnt = 0;
            // for (FixedThreadPool.Task c:list)
            // {
            // runArray[cnt] = c;
            // cnt++;
            // }
            // int processors = Runtime.getRuntime().availableProcessors();
            // if (processors > runArray.length)
            // processors = runArray.length;
            // FixedThreadPool pool = new FixedThreadPool(runArray);
            // pool.runThreads(processors);
        }
        else {
            for (final Cluster cl1 : trackClusters)
                cl1.buildAndApplyBestHypTree(t);
        }
        println("num families after hypothesis selection : " + families.size());
    }

    protected TrackSegment recursBuildTrackSegments(final @NotNull MHTrack2s trk, final ArrayList<TrackSegment> tsList) {
        final Association a = trk.association;
//        Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z, a.t);
        final Detection detect = new DetectionSpotTrack(a.spot, a.t);
        if (a.isPrediction)
            detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
        else
            detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
        final ArrayList<Detection> detections = new ArrayList<>();
        detections.add(detect);
        final TrackSegment ts = new TrackSegment(detections);
        tsList.add(ts);
        for (final MHTrack2s next : trk.followingSegments) {
            final TrackSegment ts2 = recursBuildTrackSegments(next, tsList);
            ts2.addNext(ts);
        }
        return ts;
    }

    protected ArrayList<Association> createAssociations(final @NotNull Vector<Spot> spotsVector, final int t) {
        final ArrayList<Association> associations = new ArrayList<>(spotsVector.size());
        // for (Predictor predictor:initPredictors)
        final Predictor predictor = initPredictors.getFirst();
        final double lpFA = Math.log(pFA);
        for (final Spot s : spotsVector)
            associations.add(new Association(predictor.buildMeasurementMatrix(s), s, false, t, lpFA));
        return associations;
    }

    public double computeFalseDetectionsPenalization(final int numFD) {
        return Math.pow(pFA, numFD);
    }

    public double computeNewTrackPenalization(final int numNT, final int t) {
        return 1;
    }

    public void track(final int t, final Vector<Spot> spotsVector) {
        final long begin = System.currentTimeMillis();
        if (t == 0)
            this.p1_0 = pNT0 / (pFA + pNT0);
        else
            this.p1_0 = pNT / (pFA + pNT);
        if (verbose) {
            println("******************************" + " T " + t + " ******************************");
            println("p1_0 " + this.p1_0);
            println("-- time " + t + " num spots " + spotsVector.size());
        }
        long t1 = System.currentTimeMillis();
        // -----create a set of association from the new observed spots
        this.currentAssociations.clear();
        this.currentAssociations.addAll(createAssociations(spotsVector, t));
        //this.currentAssociations = createAssociations(spotsVector, t);

        // -----try to confirm candidate tracks with spots and create new candidates from spots, maintain confirmed
        // tracks or terminate them
        println("begin TracksFormation");
        tracksFormation(currentAssociations, t);
        println("-- time " + t + " num families " + families.size());

        long t2 = System.currentTimeMillis();
        //tracksFormationTime += (t2 - t1);
        setTracksFormationTime(getTracksFormationTime() + (t2 - t1));

        // ----- for each family, hypotheses are created based on confirmed tracks
        println("begin HypothesisFormation");
        t1 = System.currentTimeMillis();
        hypothesisFormation();
        t2 = System.currentTimeMillis();
        //hypothesesFormationTime += (t2 - t1);
        setHypothesesFormationTime(getHypothesesFormationTime() + (t2 - t1));

        if (verbose) {
            int cnt = 0;
            for (final Family f : families)
                cnt += f.hypotheses.size();
            println("t " + t + "  - h " + cnt);
        }
        // -----create clusters of tracks with common observations
        println("begin ClustersFormation");
        t1 = System.currentTimeMillis();
        createClusters(t);
        t2 = System.currentTimeMillis();
        //createClustersTime += (t2 - t1);
        setCreateClustersTime(getCreateClustersTime() + (t2 - t1));

        println("-- time " + t + " num clusters " + trackClusters.size());
        if (verbose)
            for (final Cluster c : trackClusters) {
                int tmp = 0;
                for (final ArrayList<Family> a : c.concurrentFamilies.values())
                    tmp += a.size();
                println("*num families " + tmp);
            }
        // -----build global hypotheses from clusters, select best ones
        println("begin BHFormation");
        t1 = System.currentTimeMillis();
        buildAndApplyGlobalHyp(t);
        t2 = System.currentTimeMillis();
        //buildGHTime += (t2 - t1);
        setBuildGHTime(getBuildGHTime() + (t2 - t1));

        // -----prune a track based on its a posteriori probability given by global hypotheses
        tracksGlobalPruning();

        // -----merge too closely resembling tracks
        tracksMerging();

        // -----update surviving track
        tracksUpdatingAndPruning();

        final long end = System.currentTimeMillis();

        //totalElapseTime += (end - begin);
        setTotalElapseTime(getTotalElapseTime() + (end - begin));
    }

    protected void tracksGlobalPruning() {
        //
    }

    @SuppressWarnings("resource")
    protected void tracksFormation(final ArrayList<Association> associations, final int t) {
        if (parallelComputing) {
            final int processors = Runtime.getRuntime().availableProcessors();
            final ExecutorService service = Executors.newFixedThreadPool(processors);
            // ExecutorService service = Executors.newSingleThreadExecutor();
            final CountDownLatch latch = new CountDownLatch(families.size());
            for (final Family f : families) {
                service.execute(() -> {
                    try {
                        final LinkedList<MHTrack2s> copyOfLeafs = new LinkedList<>(f.leafs);
                        f.leafs.clear();
                        for (final MHTrack2s trk : copyOfLeafs) {
                            if (!trk.terminated) {
                                /* without track quality formation/termination */
                                // try to continue with spots
                                for (final Association a : associations)
                                    tryToContinueTrack(trk, a, t, f);
                                // try to continue with predictions
                                final MHTrack2s newTrack = trk.createPotentialTrackWithPrediction(t);
                                f.leafs.add(newTrack);
                            }
                            else
                                f.leafs.add(trk);
                        }
                    }
                    finally {
                        latch.countDown();
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (final InterruptedException e) {
                IcyLogger.error(this.getClass(), e);
            }
            // then try to create new candidate tracks just from previous and current spots
            // ExecutorService service2 = Executors.newSingleThreadExecutor();
            final ExecutorService service2 = Executors.newFixedThreadPool(processors);
            final CountDownLatch latch2 = new CountDownLatch(associations.size() * initPredictors.size());
            for (final Association a : associations) {
                for (final Predictor predictor : initPredictors) {
                    final Predictor predictor2 = predictor.copy();
                    service2.execute(() -> {
                        try {
                            final Family f = new Family();
                            // create root track
                            final MHTrack2s track = new MHTrack2s(p1_0, HMMMHTracker.this, predictor2, a, f);
                            f.rootNode = track;
                            f.leafs.add(track);
                            f.rootTime = a.t;
                            f.usedAssociations.add(track.association);
                            addFamily(f);
                        }
                        finally {
                            latch2.countDown();
                        }
                    });
                }
            }
            service2.shutdown();
            try {
                latch2.await();
            }
            catch (final InterruptedException e) {
                IcyLogger.error(this.getClass(), e);
            }
            // LinkedList<Thread> thrList = new LinkedList<Thread>();
            // for (Family f:families)
            // {
            // final Family f2 = f;
            // Thread thr = new Thread()
            // {
            // public void run()
            // {
            // LinkedList<MHTrack2s> copyOfLeafs = new LinkedList<MHTrack2s>(f2.leafs);
            // f2.leafs.clear();
            // for(MHTrack2s trk:copyOfLeafs)
            // {
            // if (!trk.terminated)
            // {
            // /*without track quality formation/termination*/
            // //try to continue with spots
            // for (Association a:associations)
            // tryToContinueTrack(trk, a, t, f2);
            // //try to continue with predictions
            // MHTrack2s newTrack = trk.createPotentialTrackWithPrediction(t);
            // f2.leafs.add(newTrack);
            // }
            // else
            // f2.leafs.add(trk);
            // }
            // }
            // };
            // thrList.add(thr);
            // thr.start();
            // }
            // for (Thread thr:thrList)
            // {
            // try {
            // thr.join();
            // } catch (InterruptedException e) {
            // e.printStackTrace();
            // }
            // }
            // thrList.clear();
            // //then try to create new candidate tracks just from previous and current spots
            // for (Association a:associations)
            // {
            // final Association a2 = a;
            // Thread thr = new Thread()
            // {
            // public void run()
            // {
            // for (Predictor predictor:initPredictors)
            // {
            // Family f = new Family();
            // //create root track
            // MHTrack2s track = new MHTrack2s(p1_0, HMMMHTracker.this, predictor.copy(), a2, f);
            // // MHTrack2s track = new MHTrack3s(this, initPredictor.copy(), a, f);
            // f.rootNode = track;
            // f.leafs.add(track);
            // f.rootTime = a2.t;
            // f.usedAssociations.add(track.association);
            // families.add(f);
            // }
            // }
            // };
            // thrList.add(thr);
            // thr.start();
            // }
            // for (Thread thr:thrList)
            // try {
            // thr.join();
            // } catch (InterruptedException e) {
            // e.printStackTrace();
            // }
        }
        else {
            for (final Family f : families) {
                final LinkedList<MHTrack2s> copyOfLeafs = new LinkedList<>(f.leafs);
                f.leafs.clear();
                for (final MHTrack2s trk : copyOfLeafs) {
                    if (!trk.terminated) {
                        /* without track quality formation/termination */
                        // try to continue with spots
                        for (final Association a : associations)
                            tryToContinueTrack(trk, a, t, f);
                        // try to continue with predictions
                        final MHTrack2s newTrack = trk.createPotentialTrackWithPrediction(t);
                        f.leafs.add(newTrack);
                    }
                    else
                        f.leafs.add(trk);
                }
            }
            // then try to create new candidate tracks just from previous and current spots
            for (final Association a : associations) {
                for (final Predictor predictor : initPredictors) {
                    final Family f = new Family();
                    // create root track
                    final MHTrack2s track = new MHTrack2s(p1_0, HMMMHTracker.this, predictor.copy(), a, f);
                    f.rootNode = track;
                    f.leafs.add(track);
                    f.rootTime = a.t;
                    f.usedAssociations.add(track.association);
                    families.add(f);
                }
            }
        }
    }

    protected void tracksMerging() {
    }

    protected void tracksUpdatingAndPruning() {
        for (final Family f : families) {
            if (f != null)
                for (final MHTrack2s track : f.leafs) {
                    track.applyLastAssociation();
                }
        }
        // TODO: tracks that have just been created may not have been updated
    }

    protected void tryToContinueTrack(final @NotNull MHTrack2s trk, final Association a, final int t, final Family family) {
        if (trk.inGate(a))// TODO: check how the normalized innovation is computed by the predictor
        {
            final MHTrack2s newTrack = trk.createPotentialTrack(a, t);
            family.leafs.add(newTrack);
        }
    }

    public void addFamily(final Family f) {
        synchronized (families) {
            families.add(f);
        }
    }

    public void addFamilies(final Collection<Family> fList) {
        synchronized (families) {
            families.addAll(fList);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        validatedTracks.clear();
        families.clear();
        trackClusters.clear();
    }

    public static void resetMonitoring() {
        totalElapseTime = 0;
        compatibleElapseTime = 0;
        tracksFormationTime = 0;
        createClustersTime = 0;
        hypothesesFormationTime = 0;
        MLFormationTime = 0;
        compatibleHypTime = 0;
        buildGHTime = 0;
        applyHypTime = 0;
    }

    public static void printMonitoring() {
        IcyLogger.info(
                HMMMHTracker.class,
                "======= MHT monitoring =======",
                "total time " + totalElapseTime + " | 100",
                "tracks formation " + tracksFormationTime + " | " + ((double) tracksFormationTime / totalElapseTime),
                "create Clusters " + createClustersTime + " | " + ((double) createClustersTime / totalElapseTime),
                "hypotheses formation " + hypothesesFormationTime + " | " + ((double) hypothesesFormationTime / totalElapseTime),
                "build Global hypotheses " + buildGHTime + " | " + ((double) buildGHTime / totalElapseTime),
                "ml formation time " + MLFormationTime + " | " + ((double) MLFormationTime / totalElapseTime),
                "compatible hypotheses time " + compatibleHypTime + " | " + ((double) compatibleHypTime / totalElapseTime),
                "compatibility " + compatibleElapseTime + " | " + ((double) compatibleElapseTime / totalElapseTime),
                "apply hyp " + applyHypTime + " | " + ((double) applyHypTime / totalElapseTime)
        );
    }

    @Override
    public void stopComputing() {
    }

    @Override
    public void stopCurrentIteration() {
    }

    public void addValidatedTrack(final ValidatedTrack vtrack) {
        synchronized (validatedTracks) {
            validatedTracks.add(vtrack);
        }
    }

    public static long getTracksFormationTime() {
        return tracksFormationTime;
    }

    public static void setTracksFormationTime(final long tracksFormationTime) {
        HMMMHTracker.tracksFormationTime = tracksFormationTime;
    }

    public static long getHypothesesFormationTime() {
        return hypothesesFormationTime;
    }

    public static void setHypothesesFormationTime(final long hypothesesFormationTime) {
        HMMMHTracker.hypothesesFormationTime = hypothesesFormationTime;
    }

    public static long getCreateClustersTime() {
        return createClustersTime;
    }

    public static void setCreateClustersTime(final long createClustersTime) {
        HMMMHTracker.createClustersTime = createClustersTime;
    }

    public static long getBuildGHTime() {
        return buildGHTime;
    }

    public static void setBuildGHTime(final long buildGHTime) {
        HMMMHTracker.buildGHTime = buildGHTime;
    }

    public static long getTotalElapseTime() {
        return totalElapseTime;
    }

    public static void setTotalElapseTime(final long totalElapseTime) {
        HMMMHTracker.totalElapseTime = totalElapseTime;
    }

    public static long getMLFormationTime() {
        return MLFormationTime;
    }

    public static void setMLFormationTime(final long MLFormationTime) {
        HMMMHTracker.MLFormationTime = MLFormationTime;
    }

    public static long getApplyHypTime() {
        return applyHypTime;
    }

    public static void setApplyHypTime(final long applyHypTime) {
        HMMMHTracker.applyHypTime = applyHypTime;
    }

    public static long getCompatibleElapseTime() {
        return compatibleElapseTime;
    }

    public static void setCompatibleElapseTime(final long compatibleElapseTime) {
        HMMMHTracker.compatibleElapseTime = compatibleElapseTime;
    }
}
