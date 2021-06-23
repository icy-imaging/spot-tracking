package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.particletracking.DetectionSpotTrack;
import plugins.nchenouard.particletracking.SpotTrackingPlugin;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.StoppableTracker;
import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Spot;

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
public class HMMMHTracker implements StoppableTracker {
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

    boolean parallelComputing = true;
    boolean useLPSolve = true;

    private LinkedList<ValidatedTrack> validatedTracks;
    private ArrayList<Family> families;
    private LinkedList<Cluster> trackClusters;
    private ArrayList<Association> currentAssociations;

    public volatile static long totalElapseTime = 0;
    public volatile static long compatibleElapseTime = 0;
    public volatile static long tracksFormationTime = 0;
    public volatile static long createClustersTime = 0;
    public volatile static long hypothesesFormationTime = 0;
    public volatile static long MLFormationTime = 0;
    public volatile static long compatibleHypTime = 0;
    public volatile static long buildGHTime = 0;
    public volatile static long applyHypTime = 0;

    class FamilyAndAssociation {
        Association a;
        Family f;

        public FamilyAndAssociation(Association a, Family f) {
            this.a = a;
            this.f = f;
        }
    }

    public HMMMHTracker(List<Predictor> predictors, double volume, double gateFactor, double probaDetect,
                        double densityFalseDetection, double densityInitialTrack, double densityNewTrack, int treeDepth, int dim,
                        boolean useMultithreading, boolean uselPSolve) {
        this.volume = volume;
        this.initPredictors = predictors;
        this.gateFactor = Math.max(gateFactor, 0);
        this.Pd = probaDetect;
        this.K = Math.max(treeDepth, 0);

        totalGateLikelihood = predictors.get(0).getTotalGateLikelihood(true, gateFactor);
        pDpG = Pd * totalGateLikelihood;
        pFA = densityFalseDetection;
        pNT = densityNewTrack;
        pNT0 = densityInitialTrack;

        this.validatedTracks = new LinkedList<ValidatedTrack>();
        this.families = new ArrayList<Family>();
        this.trackClusters = new LinkedList<Cluster>();
        this.currentAssociations = new ArrayList<Association>();

        this.useLPSolve = uselPSolve;
        this.parallelComputing = useMultithreading;
    }

    public void changeExistenceSettings(double p10, double confirmationThreshold, double terminationThreshold) {
        this.p10 = p10;
        this.p11 = 1 - p10;
        this.pc = confirmationThreshold;
        this.pt = terminationThreshold;
        MHTrack2s.INIT_P10 = p10;
        MHTrack2s.INIT_P11 = 1 - p10;
        MHTrack2s.INIT_Pc = this.pc;
        MHTrack2s.INIT_Pt = this.pt;
    }

    protected void hypothesisFormation() {
        if (parallelComputing) {
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService service = Executors.newFixedThreadPool(processors);
            // ExecutorService service = Executors.newSingleThreadExecutor();
            final CountDownLatch latch = new CountDownLatch(families.size());
            for (final Family f : families) {
                service.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            f.buildHypothesesAndResetBestScores();
                        }
                        finally {
                            latch.countDown();
                        }
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (InterruptedException E) {
                E.printStackTrace();
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
            for (Family f1 : families)
                f1.buildHypothesesAndResetBestScores();
    }

    /**
     * Merge clusters of tracks with common observations
     * @param t int
     */
    protected void createClusters(int t) {
        trackClusters.clear();
        LinkedList<ConstructionCluster> clusters = new LinkedList<>();
        for (Family f : families) {
            boolean confirmed = false;
            for (MHTrack2s leaf : f.leafs) {
                if (leaf.confirmed) {
                    confirmed = true;
                    break;
                }
            }
            if (confirmed) {
                ConstructionCluster cl = new ConstructionCluster();
                cl.families.add(f);
                for (Association a : f.usedAssociations)
                    if (!a.isPrediction)
                        cl.associations.add(a);
                clusters.add(cl);
            }
        }
        LinkedList<ConstructionCluster> finishedClusters = new LinkedList<ConstructionCluster>();
        while (!clusters.isEmpty()) {
            ConstructionCluster cl = clusters.poll();
            boolean fused = false;
            for (ConstructionCluster cl2 : clusters) {
                if (cl != cl2) {
                    for (Association a : cl2.associations) {
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

        for (ConstructionCluster cl : finishedClusters) {
            Cluster cluster;
            if (useLPSolve && SpotTrackingPlugin.optimizationLibraryLoaded)
                cluster = new ClusterSolveLP(this, t - K, t, K, pFA);
            else
                cluster = new ClusterSolveLPCustom(this, t - K, t, K, pFA);

            for (Family f : cl.families) {
                ArrayList<Family> cFamily = cluster.concurrentFamilies.get(f.rootTime);
                if (cFamily == null) {
                    cFamily = new ArrayList<Family>();
                    cluster.concurrentFamilies.put(f.rootTime, cFamily);
                }
                cFamily.add(f);
                for (Association a : f.usedAssociations)
                    if (!(a.isPrediction))
                        cluster.realAssociations.add(a);
            }
            cluster.initRealSpotsNum();
            trackClusters.add(cluster);
        }
    }

    /**
     * Merge clusters of tracks with common observations
     * @param t int
     */
    protected void createClusters2(int t) {
        int numCluster = 0;
        HashSet<Association> associations = new HashSet<Association>();
        // create an association table between spots and families
        ArrayList<FamilyAndAssociation> faTable = new ArrayList<FamilyAndAssociation>();
        for (Family f : families) {
            for (Association a : f.usedAssociations) {
                // if (!(s instanceof VirtualSpot))//acceleate but families with only virtual mesurements will be lost
                // with this
                // {
                faTable.add(new FamilyAndAssociation(a, f));
                associations.add(a);
                // }
            }
        }
        println("create clusters #spots " + associations.size());
        HashMap<Family, Integer> familyTable = new HashMap<Family, Integer>();
        HashMap<Association, Integer> associationTable = new HashMap<Association, Integer>();
        for (Association as : associations) {
            Integer idCl;
            if (associationTable.containsKey(as))
                idCl = associationTable.get(as);
            else
                idCl = numCluster++;
            // find FamilyAndAssociations that use this association
            ArrayList<FamilyAndAssociation> associationsList = new ArrayList<FamilyAndAssociation>();
            for (FamilyAndAssociation fa : faTable) {
                if (fa.a == as)
                    associationsList.add(fa);
            }

            for (FamilyAndAssociation a : associationsList) {
                // if the family is already in a cluster, then change the id of this cluster to the current one.
                if (familyTable.containsKey(a.f)) {
                    Integer prevCluster = familyTable.get(a.f);
                    if (prevCluster != idCl) {
                        for (Entry<Family, Integer> e : familyTable.entrySet()) {
                            if (e.getValue() == prevCluster)
                                familyTable.put((Family) e.getKey(), idCl);
                        }
                        for (Entry<Association, Integer> e : associationTable.entrySet()) {
                            if (e.getValue() == prevCluster)
                                associationTable.put((Association) e.getKey(), idCl);
                        }
                    }
                }
                familyTable.put(a.f, idCl); // set the family's cluster
                // if the association is already in a cluser, then change the id of this cluster to the current one
                if (associationTable.containsKey(a)) {
                    Integer prevCluster = associationTable.get(a);
                    if (prevCluster != idCl) {
                        for (Entry<Family, Integer> e : familyTable.entrySet()) {
                            if (e.getValue() == prevCluster)
                                familyTable.put((Family) e.getKey(), idCl);
                        }
                        for (Entry<Association, Integer> e : associationTable.entrySet()) {
                            if (e.getValue() == prevCluster)
                                associationTable.put((Association) e.getKey(), idCl);
                        }
                    }
                }
                // set the association cluster
                associationTable.put(a.a, idCl);
            }
        }
        trackClusters.clear();
        HashMap<Integer, Cluster> clustersTable = new HashMap<Integer, Cluster>();
        for (Entry<Association, Integer> e : associationTable.entrySet()) {
            Cluster cl;
            if (clustersTable.containsKey(e.getValue())) // if association is in the table get the cluster
                cl = clustersTable.get(e.getValue());
            else
            // else create an new cluster
            {
                if (useLPSolve && SpotTrackingPlugin.optimizationLibraryLoaded)
                    cl = new ClusterSolveLP(this, t - K, t, K, pFA);
                else
                    cl = new ClusterSolveLPCustom(this, t - K, t, K, pFA);
                clustersTable.put((Integer) e.getValue(), cl);
            }
            Association a = (Association) e.getKey();
            if (!(a.isPrediction))
                cl.realAssociations.add(a);
        }
        for (Entry<Family, Integer> e : familyTable.entrySet()) {
            Cluster cl;
            if (clustersTable.containsKey(e.getValue()))
                cl = clustersTable.get(e.getValue());
            else {
                if (useLPSolve)
                    cl = new ClusterSolveLP(this, t - K, t, K, pFA);
                else
                    cl = new ClusterSolveLPCustom(this, t - K, t, K, pFA);
                clustersTable.put((Integer) e.getValue(), cl);
            }
            ArrayList<Family> cFamily = cl.concurrentFamilies.get(((Family) e.getKey()).rootTime);
            if (cFamily == null) {
                cFamily = new ArrayList<Family>();
                cl.concurrentFamilies.put(new Integer(((Family) e.getKey()).rootTime), cFamily);
            }
            cFamily.add((Family) e.getKey());
        }
        trackClusters.addAll(clustersTable.values());
        for (Cluster cl : trackClusters)
            cl.initRealSpotsNum();

    }

    public ArrayList<TrackSegment> getTracks() {
        ArrayList<TrackSegment> tsList = new ArrayList<TrackSegment>();
        LinkedList<ValidatedTrack> validated = new LinkedList<ValidatedTrack>(validatedTracks);
        for (ValidatedTrack track : validated) {
            ArrayList<Detection> detections = new ArrayList<Detection>();
            for (Association a : track.associations) {
                // Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z,
                // a.t);
                Detection detect = new DetectionSpotTrack(a.spot, a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
                TrackSegment motherTS = new TrackSegment(detections);
                tsList.add(motherTS);
                // following ones are potential track segments
                if (track.family.rootNode != null) {
                    for (MHTrack2s ts : track.family.rootNode.followingSegments) {
                        TrackSegment ts2 = recursBuildTrackSegments(ts, tsList);
                        motherTS.addNext(ts2);
                    }
                }
            }
        }
        for (Family f : families) {
            if (f.rootNode != null && f.rootTrack == null) {
                ArrayList<Detection> detections = new ArrayList<Detection>();
                Association a = f.rootNode.association;
//              Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z, a.t);
                Detection detect = new DetectionSpotTrack(a.spot, a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
                TrackSegment ts = new TrackSegment(detections);
                tsList.add(ts);
                for (MHTrack2s next : f.rootNode.followingSegments) {
                    TrackSegment ts2 = recursBuildTrackSegments(next, tsList);
                    ts.addNext(ts2);
                }
            }
        }
        return tsList;
    }

    public ArrayList<TrackSegment> getCompleteTracks() {
        ArrayList<TrackSegment> tsList = new ArrayList<TrackSegment>();
        LinkedList<ValidatedTrack> validated = new LinkedList<ValidatedTrack>(validatedTracks);
        for (ValidatedTrack track : validated) {
            ArrayList<Detection> detections = new ArrayList<Detection>();
            for (Association a : track.associations) {
                Detection detect = new DetectionSpotTrack(a.spot, a.t);
                // Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z,
                // a.t);
                if (a.isPrediction)
                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                else
                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                detections.add(detect);
            }
            if (track.family != null) {
                Family f = track.family;
                if (f.lastSelectedLeaf != null) {
                    MHTrack2s mht = f.lastSelectedLeaf;
                    if (!track.associations.contains(mht.association)) {
                        MHTrack2s node = mht;
                        LinkedList<Detection> detList = new LinkedList<Detection>();
                        boolean stop = false;
                        while (node != null && !stop) {
                            stop = (node == f.rootNode);
                            if (!stop) {
                                Association a = node.association;
//                              Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y,
//                              a.spot.mass_center.z, a.t);
                                Detection detect = new DetectionSpotTrack(a.spot, a.t);
                                if (a.isPrediction)
                                    detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                                else
                                    detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
                                detList.addFirst(detect);
                                node = node.precedingSegment;
                            }
                        }
                        for (Detection d : detList)
                            detections.add(d);
                    }
                }
            }
            // remove last virtual detections of a track segment
            boolean stop = false;
            while (!stop && !detections.isEmpty()) {
                Detection d = detections.get(detections.size() - 1);
                if (d.getDetectionType() == Detection.DETECTIONTYPE_VIRTUAL_DETECTION)
                    detections.remove(detections.size() - 1);
                else
                    stop = true;
            }
            TrackSegment motherTS = new TrackSegment(detections);
            tsList.add(motherTS);
        }
        return tsList;
    }

    boolean verbose = false;
    private double p1_0;

    protected void println(String text) {
        if (verbose)
            System.out.println(text);
    }

    class BuildHypTask implements FixedThreadPool.Task {
        Cluster clusterTask;
        int tTask;

        public BuildHypTask(Cluster cluster, int tt) {
            this.clusterTask = cluster;
            this.tTask = tt;
        }

        @Override
        public void run() {
            clusterTask.buildAndApplyBestHypTree(tTask);
        }

    }

    protected void buildAndApplyGlobalHyp(final int t) {
        // ArrayList<Family> fCopy = families;
        ArrayList<Family> fCopy = new ArrayList<Family>(families);
        families.clear();
        // families = new ArrayList<Family>();
        // retrieve families that are not in a Cluster and are less deep than K
        LinkedList<Family> fCluster = new LinkedList<Family>();
        for (Cluster cl : trackClusters)
            for (ArrayList<Family> fList : cl.concurrentFamilies.values())
                fCluster.addAll(fList);
        for (Family f : fCopy)
            if (!(fCluster.contains(f) || f.rootTime <= t - K + 1))
                families.add(f);
        println("num families before hyp selection : " + families.size());

        if (parallelComputing) {
            int processors = Runtime.getRuntime().availableProcessors();
            // ExecutorService service = Executors.newSingleThreadExecutor();
            ExecutorService service = Executors.newFixedThreadPool(processors);
            final CountDownLatch latch = new CountDownLatch(trackClusters.size());
            // final Lock latchLock = new ReentrantLock();
            for (final Cluster c : trackClusters) {
                service.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            c.buildAndApplyBestHypTree(t);
                        }
                        finally {
                            try {
                                latch.countDown();
                            }
                            finally {
                            }
                        }
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
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
            for (Cluster cl1 : trackClusters)
                cl1.buildAndApplyBestHypTree(t);
        }
        println("num families after hypothesis selection : " + families.size());
    }

    protected TrackSegment recursBuildTrackSegments(MHTrack2s trk, ArrayList<TrackSegment> tsList) {
        Association a = trk.association;
//        Detection detect = new Detection(a.spot.mass_center.x, a.spot.mass_center.y, a.spot.mass_center.z, a.t);
        Detection detect = new DetectionSpotTrack(a.spot, a.t);
        if (a.isPrediction)
            detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
        else
            detect.setDetectionType(Detection.DETECTIONTYPE_REAL_DETECTION);
        ArrayList<Detection> detections = new ArrayList<Detection>();
        detections.add(detect);
        TrackSegment ts = new TrackSegment(detections);
        tsList.add(ts);
        for (MHTrack2s next : trk.followingSegments) {
            TrackSegment ts2 = recursBuildTrackSegments(next, tsList);
            ts2.addNext(ts);
        }
        return ts;
    }

    protected ArrayList<Association> createAssociations(Vector<Spot> spotsVector, int t) {
        ArrayList<Association> associations = new ArrayList<Association>(spotsVector.size());
        // for (Predictor predictor:initPredictors)
        Predictor predictor = initPredictors.get(0);
        double lpFA = Math.log(pFA);
        for (Spot s : spotsVector)
            associations.add(new Association(predictor.buildMeasurementMatrix(s), s, false, t, lpFA));
        return associations;
    }

    public double computeFalseDetectionsPenalization(int numFD) {
        return Math.pow(pFA, numFD);
    }

    public double computeNewTrackPenalization(int numNT, int t) {
        return 1;
    }

    public void track(int t, Vector<Spot> spotsVector) {
        long begin = System.currentTimeMillis();
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
        this.currentAssociations = createAssociations(spotsVector, t);

        // -----try to confirm candidate tracks with spots and create new candidates from spots, maintain confirmed
        // tracks or terminate them
        println("begin TracksFormation");
        tracksFormation(currentAssociations, t);
        println("-- time " + t + " num families " + families.size());

        long t2 = System.currentTimeMillis();
        tracksFormationTime += (t2 - t1);

        // ----- for each family, hypotheses are created based on confirmed tracks
        println("begin HypothesisFormation");
        t1 = System.currentTimeMillis();
        hypothesisFormation();
        t2 = System.currentTimeMillis();
        hypothesesFormationTime += (t2 - t1);

        if (verbose) {
            int cnt = 0;
            for (Family f : families)
                cnt += f.hypotheses.size();
            println("t " + t + "  - h " + cnt);
        }
        // -----create clusters of tracks with common observations
        println("begin ClustersFormation");
        t1 = System.currentTimeMillis();
        createClusters(t);
        t2 = System.currentTimeMillis();
        createClustersTime += (t2 - t1);

        println("-- time " + t + " num clusters " + trackClusters.size());
        if (verbose)
            for (Cluster c : trackClusters) {
                int tmp = 0;
                for (ArrayList<Family> a : c.concurrentFamilies.values())
                    tmp += a.size();
                println("*num families " + tmp);
            }
        // -----build global hypotheses from clusters, select best ones
        println("begin BHFormation");
        t1 = System.currentTimeMillis();
        buildAndApplyGlobalHyp(t);
        t2 = System.currentTimeMillis();
        buildGHTime += (t2 - t1);

        // -----prune a track based on its a posteriori probability given by global hypotheses
        tracksGlobalPruning();

        // -----merge too closely resembling tracks
        tracksMerging();

        // -----update surviving track
        tracksUpdatingAndPruning();

        long end = System.currentTimeMillis();
        totalElapseTime += (end - begin);
    }

    protected void tracksGlobalPruning() {
    }

    protected void tracksFormation(final ArrayList<Association> associations, final int t) {
        if (parallelComputing) {
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService service = Executors.newFixedThreadPool(processors);
            // ExecutorService service = Executors.newSingleThreadExecutor();
            final CountDownLatch latch = new CountDownLatch(families.size());
            for (final Family f : families) {
                service.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            LinkedList<MHTrack2s> copyOfLeafs = new LinkedList<MHTrack2s>(f.leafs);
                            f.leafs.clear();
                            for (MHTrack2s trk : copyOfLeafs) {
                                if (!trk.terminated) {
                                    /* without track quality formation/termination */
                                    // try to continue with spots
                                    for (Association a : associations)
                                        tryToContinueTrack(trk, a, t, f);
                                    // try to continue with predictions
                                    MHTrack2s newTrack = trk.createPotentialTrackWithPrediction(t);
                                    f.leafs.add(newTrack);
                                }
                                else
                                    f.leafs.add(trk);
                            }
                        }
                        finally {
                            latch.countDown();
                        }
                    }
                });
            }
            service.shutdown();
            try {
                latch.await();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            // then try to create new candidate tracks just from previous and current spots
            // ExecutorService service2 = Executors.newSingleThreadExecutor();
            ExecutorService service2 = Executors.newFixedThreadPool(processors);
            final CountDownLatch latch2 = new CountDownLatch(associations.size() * initPredictors.size());
            for (final Association a : associations) {
                for (Predictor predictor : initPredictors) {
                    final Predictor predictor2 = predictor.copy();
                    service2.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Family f = new Family();
                                // create root track
                                MHTrack2s track = new MHTrack2s(p1_0, HMMMHTracker.this, predictor2, a, f);
                                f.rootNode = track;
                                f.leafs.add(track);
                                f.rootTime = a.t;
                                f.usedAssociations.add(track.association);
                                addFamily(f);
                            }
                            finally {
                                latch2.countDown();
                            }
                        }
                    });
                }
            }
            service2.shutdown();
            try {
                latch2.await();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
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
                LinkedList<MHTrack2s> copyOfLeafs = new LinkedList<MHTrack2s>(f.leafs);
                f.leafs.clear();
                for (MHTrack2s trk : copyOfLeafs) {
                    if (!trk.terminated) {
                        /* without track quality formation/termination */
                        // try to continue with spots
                        for (Association a : associations)
                            tryToContinueTrack(trk, a, t, f);
                        // try to continue with predictions
                        MHTrack2s newTrack = trk.createPotentialTrackWithPrediction(t);
                        f.leafs.add(newTrack);
                    }
                    else
                        f.leafs.add(trk);
                }
            }
            // then try to create new candidate tracks just from previous and current spots
            for (Association a : associations) {
                final Association a2 = a;
                for (Predictor predictor : initPredictors) {
                    Family f = new Family();
                    // create root track
                    MHTrack2s track = new MHTrack2s(p1_0, HMMMHTracker.this, predictor.copy(), a2, f);
                    f.rootNode = track;
                    f.leafs.add(track);
                    f.rootTime = a2.t;
                    f.usedAssociations.add(track.association);
                    families.add(f);
                }
            }
        }
    }

    protected void tracksMerging() {
    }

    protected void tracksUpdatingAndPruning() {
        for (Family f : families) {
            if (f != null)
                for (MHTrack2s track : f.leafs) {
                    track.applyLastAssociation();
                }
        }
        // TODO: tracks that have just been created may not have been updated
    }

    protected void tryToContinueTrack(MHTrack2s trk, Association a, int t, Family family) {
        if (trk.inGate(a))// TODO: check how the normalized innovation is computed by the predictor
        {
            MHTrack2s newTrack = trk.createPotentialTrack(a, t);
            family.leafs.add(newTrack);
        }
    }

    public void addFamily(Family f) {
        synchronized (families) {
            families.add(f);
        }
    }

    public void addFamilies(Collection<Family> fList) {
        synchronized (families) {
            families.addAll(fList);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        validatedTracks = null;
        families = null;
        trackClusters = null;
        super.finalize();
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
        System.out.println("======= MHT monitoring =======");
        System.out.println("total time " + totalElapseTime + " | 100");
        System.out.println("tracks formation " + tracksFormationTime + " | "
                + ((double) tracksFormationTime / totalElapseTime));
        System.out.println("create Clusters " + createClustersTime + " | "
                + ((double) createClustersTime / totalElapseTime));
        System.out.println("hypotheses formation " + hypothesesFormationTime + " | "
                + ((double) hypothesesFormationTime / totalElapseTime));
        System.out.println("build Global hypotheses " + buildGHTime + " | " + ((double) buildGHTime / totalElapseTime));
        System.out.println("ml formation time " + MLFormationTime + " | "
                + ((double) MLFormationTime / totalElapseTime));
        System.out.println("compatible hypotheses time " + compatibleHypTime + " | "
                + ((double) compatibleHypTime / totalElapseTime));
        System.out.println("compatibility " + compatibleElapseTime + " | "
                + ((double) compatibleElapseTime / totalElapseTime));
        System.out.println("apply hyp " + applyHypTime + " | " + ((double) applyHypTime / totalElapseTime));
    }

    @Override
    public void stopComputing() {
        // TODO Auto-generated method stub
    }

    @Override
    public void stopCurrentIteration() {
        // TODO Auto-generated method stub
    }

    public void addValidatedTrack(ValidatedTrack vtrack) {
        synchronized (validatedTracks) {
            validatedTracks.add(vtrack);
        }
    }
}
