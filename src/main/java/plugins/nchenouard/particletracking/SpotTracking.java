package plugins.nchenouard.particletracking;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import icy.gui.frame.progress.CancelableProgressFrame;
import icy.util.XMLUtil;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.particletracking.MHTracker.HMMMHTracker;
import plugins.nchenouard.particletracking.filtering.IMM2D;
import plugins.nchenouard.particletracking.filtering.IMM3D;
import plugins.nchenouard.particletracking.filtering.KF2dDirected;
import plugins.nchenouard.particletracking.filtering.KF2dRandomWalk;
import plugins.nchenouard.particletracking.filtering.KF3dDirected;
import plugins.nchenouard.particletracking.filtering.KF3dRandomWalk;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.filtering.Predictor2D;
import plugins.nchenouard.particletracking.filtering.Predictor3D;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.InstantaneousTracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.SolveMLAssociation;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.Track;
import plugins.nchenouard.spot.DetectionResult;
import plugins.nchenouard.spot.Spot;

public class SpotTracking {
    public static String ID_CONFIG = "MHTconfiguration";

    @SuppressWarnings("unused")
    synchronized static boolean loadOptimizationLibray() {
        if (SpotTrackingPlugin.optimizationLibraryLoaded)
            return true;

        // this actually load the library
        new SpotTrackingPlugin();

        return SpotTrackingPlugin.optimizationLibraryLoaded;
    }

    /**
     * Computes and return best estimated parameters from given set of Detections objects.
     * @param detections DetectionResult
     * @param directedMotion bool
     * @param singleMotion bool
     * @param updateMotion bool
     * @return MHTparameterSet
     */
    public static MHTparameterSet estimateParameters(DetectionResult detections, boolean directedMotion,
                                                     boolean singleMotion, boolean updateMotion) {
        final MHTparameterSet parameters = new MHTparameterSet();

        estimateParameters(parameters, detections, directedMotion, singleMotion, updateMotion);

        return parameters;
    }

    /**
     * Computes and return best estimated parameters from given set of Detections objects.
     * @param parameters MHTparameterSet
     * @param detections DectectionResult
     * @param directedMotion bool
     * @param singleMotion bool
     * @param updateMotion bool
     * @return bool
     */
    public static boolean estimateParameters(MHTparameterSet parameters, DetectionResult detections,
                                             boolean directedMotion, boolean singleMotion, boolean updateMotion) {
        parameters.isDirectedMotion = directedMotion;
        parameters.isSingleMotion = singleMotion;
        parameters.isUpdateMotion = updateMotion;

        // nothing to estimate
        if (detections == null)
            return false;

        // first get an estimation of parameters based on analysis of distance between detections
        parameters.detectionResults = detections;

        // fill the detection distance list between subsequent detections
        final int maxNumDetections = 10000;
        int numDetections = 0;
        int numT = 0;
        for (int t = parameters.detectionResults.getFirstFrameTime(); t < parameters.detectionResults
                .getLastFrameTime(); t++) {
            numDetections += parameters.detectionResults.getDetectionsAtT(t).size();
            numT++;
            if (numDetections > maxNumDetections)
                break;
        }

        final double[] distTabXY = new double[numDetections];
        final double[] distTabZ = new double[numDetections];
        int t = parameters.detectionResults.getFirstFrameTime();
        int cntDist = 0;

        while (t < parameters.detectionResults.getFirstFrameTime() + numT) {
            final Vector<Spot> dList1 = parameters.detectionResults.getDetectionsAtT(t);
            final Vector<Spot> dList2 = parameters.detectionResults.getDetectionsAtT(t + 1);

            if (!dList1.isEmpty() && !dList2.isEmpty()) {
                for (Spot s1 : dList1) {
                    double minDist = Double.MAX_VALUE;
                    Spot minSpot = null;

                    for (Spot s2 : dList2) {
                        double d = squaredDistance(s1, s2);
                        if (d < minDist) {
                            minDist = d;
                            minSpot = s2;
                        }
                    }

                    if ((minDist < Double.MAX_VALUE) && (minSpot != null)) {
                        // /distTab[cntDist] = minDist;
                        distTabXY[cntDist] = (s1.mass_center.x - minSpot.mass_center.x)
                                * (s1.mass_center.x - minSpot.mass_center.x)
                                + (s1.mass_center.y - minSpot.mass_center.y)
                                * (s1.mass_center.y - minSpot.mass_center.y);
                        distTabZ[cntDist] += (s1.mass_center.z - minSpot.mass_center.z)
                                * (s1.mass_center.z - minSpot.mass_center.z);
                        cntDist++;
                    }
                }
            }
            t++;
        }

        // get a first estimation of the diffusion as the underestimated median of the squared distances
        Arrays.sort(distTabXY);
        Arrays.sort(distTabZ);
        double varDiffXY = distTabXY[(int) (distTabXY.length * 0.45)];
        double varDiffZ = distTabZ[(int) (distTabXY.length * 0.45)];

        // then run the legacy tracker to get a refined estimation
        int maxConsecutivePred = 1;
        int gateFactor = 3;
        boolean gateLikelihood = true;
        Predictor predictor = null;
        if (singleMotion) {
            if (varDiffZ > 0) {
                if (directedMotion)
                    predictor = new KF3dDirected();
                else
                    predictor = new KF3dRandomWalk();
                predictor.setTrackingCovariances(new double[]{varDiffXY, varDiffXY, varDiffZ});
            }
            else {
                if (directedMotion)
                    predictor = new KF2dDirected();
                else
                    predictor = new KF2dRandomWalk();
                predictor.setTrackingCovariances(new double[]{varDiffXY, varDiffXY});
            }
        }
        else {
            if (varDiffZ > 0) {
                final List<Predictor3D> predictors = new ArrayList<Predictor3D>();

                predictors.add(new KF3dRandomWalk());
                predictors.add(new KF3dDirected());
                predictors.get(0).setTrackingCovariances(new double[]{varDiffXY, varDiffXY, varDiffZ});
                predictors.get(1).setTrackingCovariances(new double[]{varDiffXY, varDiffXY, varDiffZ});

                if (parameters.useMostLikelyModel)
                    predictor = new IMM3D(IMM3D.LikelihoodTypes.IMM_MAX_LIKELIHOOD, parameters.immInertia, predictors);
                else
                    predictor = new IMM3D(IMM3D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD, parameters.immInertia,
                            predictors);
            }
            else {
                final List<Predictor2D> predictors = new ArrayList<Predictor2D>();

                predictors.add(new KF2dRandomWalk());
                predictors.add(new KF2dDirected());
                predictors.get(0).setTrackingCovariances(new double[]{varDiffXY, varDiffXY});
                predictors.get(1).setTrackingCovariances(new double[]{varDiffXY, varDiffXY});

                if (parameters.useMostLikelyModel)
                    predictor = new IMM2D(IMM2D.LikelihoodTypes.IMM_MAX_LIKELIHOOD, parameters.immInertia, predictors);
                else
                    predictor = new IMM2D(IMM2D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD, parameters.immInertia,
                            predictors);
            }
        }

        final SolveMLAssociation assignementSolver = new SolveMLAssociation(true);
        final InstantaneousTracker tracker = new InstantaneousTracker(assignementSolver, predictor, gateLikelihood,
                gateFactor, maxConsecutivePred);

        for (int tt = parameters.detectionResults.getFirstFrameTime(); tt < parameters.detectionResults
                .getLastFrameTime(); tt++)
            tracker.track(tt, parameters.detectionResults.getDetectionsAtT(tt));

        final List<Track> tracks = tracker.getTracks();
        // consider tracks with length < 3 to be wrong
        final List<Track> trueTracks = new ArrayList<Track>();
        final List<Track> falseTracks = new ArrayList<Track>();

        for (Track tr : tracks) {
            if (tr.getLastIndex() - tr.getFirstIndex() > 2)
                trueTracks.add(tr);
            else
                falseTracks.add(tr);
        }

        if (trueTracks.isEmpty())
            return false;

        // compute track length
        int sumTrackLength = 0;
        for (Track tr : trueTracks)
            sumTrackLength += (tr.getLastIndex() - tr.getFirstIndex() + 1);

        parameters.meanTrackLength = sumTrackLength / trueTracks.size();

        // compute number of tracks at first frame, and number of new tracks in other frames
        int numTrackFirstFrame = 0;
        int numNewTracks = 0;
        for (Track tr : trueTracks) {
            if (tr.getFirstIndex() == 0)
                numTrackFirstFrame++;
            else
                numNewTracks++;
        }

        parameters.numberInitialObjects = numTrackFirstFrame;
        parameters.numberNewObjects = (numNewTracks / (1d + parameters.detectionResults.getLastFrameTime()
                - parameters.detectionResults.getFirstFrameTime()));

        // compute number of false detections per frame
        int numFalseDetections = 0;
        for (Track tr : falseTracks) {
            for (int tt = tr.getFirstIndex(); tt < tr.getLastIndex() + 1; tt++) {
                if (tr.isPredictionAtFrame(tt))
                    numFalseDetections++;
            }
        }

        parameters.numberOfFalseDetections = (int) (Math.round(numFalseDetections / (1d
                + parameters.detectionResults.getLastFrameTime() - parameters.detectionResults.getFirstFrameTime())));

        // re estimate diffusion //TODO: do this with MSDs to account for directed periods
        // if (isSingleMotion && !isDirectedMotion)
        {
            // just use squared displacement
            double sumDistSq = 0;
            double sumDistSqZ = 0;
            int numDist = 0;
            for (Track tr : trueTracks) {
                for (int f = tr.getFirstIndex(); f < tr.getLastIndex(); f++) {
                    if (!tr.isPredictionAtFrame(f) && !tr.isPredictionAtFrame(f + 1)) {
                        Spot s1 = tr.getSpotAtFrame(f);
                        Spot s2 = tr.getSpotAtFrame(f + 1);
                        sumDistSq += (s1.mass_center.x - s2.mass_center.x) * (s1.mass_center.x - s2.mass_center.x)
                                + (s1.mass_center.y - s2.mass_center.y) * (s1.mass_center.y - s2.mass_center.y);
                        sumDistSqZ += (s1.mass_center.z - s2.mass_center.z) * (s1.mass_center.z - s2.mass_center.z);
                        numDist++;
                        if (numDist > maxNumDetections)
                            break;
                    }
                }
                if (numDist > maxNumDetections)
                    break;
            }

            parameters.displacementXY = Math.sqrt(sumDistSq / numDist);
            parameters.displacementZ = Math.sqrt(sumDistSqZ / numDist);
        }

        // estimate detection rate
        int numTrueDetections = 0;
        int numVirtualDetections = 0;
        for (Track tr : trueTracks) {
            for (int f = tr.getFirstIndex(); f < tr.getLastIndex(); f++) {
                if (tr.isPredictionAtFrame(f))
                    numVirtualDetections++;
                else
                    numTrueDetections++;
            }
        }

        parameters.detectionRate = numTrueDetections / ((double) numTrueDetections + numVirtualDetections);

        return true;
    }

    /**
     * Load parameter from the given file
     * @param file File
     * @return MHTparameterSet
     */
    public static MHTparameterSet loadParameters(File file) {
        final Document document = XMLUtil.loadDocument(file);
        final Element root = XMLUtil.getRootElement(document);

        if (root == null)
            throw new IllegalArgumentException("can't find: <root> tag.");

        final Element configurationElement = XMLUtil.getElements(root, ID_CONFIG).get(0);
        if (configurationElement == null)
            throw new IllegalArgumentException("can't find: <root><" + ID_CONFIG + "> tag.");

        return MHTparameterSet.loadFromXML(configurationElement);
    }

    /**
     * Load parameter from the given file path
     * @param path String
     * @return MHTparameterSet
     */
    public static MHTparameterSet loadParameters(String path) {
        return loadParameters(new File(path));
    }

    /**
     * Save parameters into given file path
     * @param value MHTparameterSet
     * @param f file
     */
    public static void saveParameters(MHTparameterSet value, File f) {
        saveParameters(value, f.getAbsolutePath());
    }

    /**
     * Save parameters into given file path
     * @param parameters MHTparameterSet
     * @param path String
     */
    public static void saveParameters(MHTparameterSet parameters, String path) {
        final Document document = XMLUtil.createDocument(true);
        final Element root = XMLUtil.getRootElement(document);
        final Element configurationElement = XMLUtil.setElement(root, ID_CONFIG);

        if (parameters != null)
            parameters.saveToXML(configurationElement);

        XMLUtil.saveDocument(document, path);
    }

    /**
     * Execute tracking process given a set of detection and tracking parameters and return corresponding TrackGroup
     * result.
     * @param parameters MHTparameterSet
     * @param dr DetectionResult
     * @param useLPSolver bool
     * @param multiThread bool
     * @param showProgress bool
     * @return TrackGroup
     */
    public static TrackGroup executeTracking(MHTparameterSet parameters, DetectionResult dr, boolean useLPSolver,
                                             boolean multiThread, boolean showProgress) throws IllegalArgumentException {
        if (dr == null)
            throw new IllegalArgumentException(
                    "Tracking requires to specify a set of detections. You can use the Spot Detector plugin to create some.");
        if (dr.getNumberOfDetection() < 1)
            throw new IllegalArgumentException("A non empty set of detections needs to process the tracking.");

        // load the optimization library if not already done
        loadOptimizationLibray();

        // build the tracker
        final HMMMHTracker mhtracker = buildTracker(parameters, dr, useLPSolver, multiThread);
        final CancelableProgressFrame progress;

        if (showProgress) {
            progress = new CancelableProgressFrame("Processing...");
            progress.setLength(dr.getLastFrameTime());
        }
        else
            progress = null;

        for (int t = dr.getFirstFrameTime(); t <= dr.getLastFrameTime(); t++) {
            if (progress != null) {
                progress.setMessage("Track extraction at frame " + t);
                progress.setPosition(t);

                // cancel requested ??
                if (progress.isCancelRequested())
                    break;
            }

            // process track for frame T
            mhtracker.track(t, dr.getDetectionsAtT(t));
        }

        if (progress != null)
            progress.close();

        final List<TrackSegment> tracks = mhtracker.getCompleteTracks();
        final TrackGroup result = new TrackGroup(dr.getSequence());

        result.setDescription(parameters.trackGroupName);
        for (TrackSegment ts : tracks)
            result.addTrackSegment(ts);

        return result;
    }

    /**
     * Execute tracking process given a set of detection and tracking parameters and return corresponding TrackGroup
     * result.
     * @param parameters MHTparameterSet
     * @param dr DetectionResult
     * @param showProgress bool
     * @return TrackGroup
     */
    public static TrackGroup executeTracking(MHTparameterSet parameters, DetectionResult dr, boolean showProgress)
            throws IllegalArgumentException {
        return executeTracking(parameters, dr, true, true, showProgress);
    }

    protected static HMMMHTracker buildTracker(MHTparameterSet parameters, DetectionResult dr, final int dim,
                                               final double volume, boolean useLPSolve, boolean multithreaded) throws IllegalArgumentException {
        final double probaDetect = parameters.detectionRate;
        if (probaDetect < 0 || probaDetect > 1)
            throw new IllegalArgumentException(
                    "The probability of detection for each particle has to lie between 0 and 1");

        final double densityFalseDetection = parameters.numberOfFalseDetections / volume;
        if (densityFalseDetection < 0)
            throw new IllegalArgumentException("The expected number of false detections needs to be a positive number");

        final double gateFactor = parameters.gateFactor;
        if (gateFactor <= 0)
            throw new IllegalArgumentException("The gate factor needs to be a positive number");

        final double densityInitialTrack = parameters.numberInitialObjects / volume;
        if (densityInitialTrack < 0)
            throw new IllegalArgumentException("The expected number of initial needs to be a positive number");

        final double densityNewTrack = parameters.numberNewObjects / volume;
        if (densityNewTrack < 0)
            throw new IllegalArgumentException(
                    "The expected number of new tracks per frame needs to be a positive number");

        final int treeDepth = parameters.mhtDepth;
        if (treeDepth < 1)
            throw new IllegalArgumentException("The tree depth needs to be an integer greater or equal to 1");

        /* build the predictor */
        Predictor pred = null;
        if (parameters.isSingleMotion) {
            // single motion
            if (parameters.isDirectedMotion) {
                if (dim == 3) {
                    final double[] trackingCovariances = new double[3];

                    trackingCovariances[0] = parameters.displacementXY;
                    trackingCovariances[1] = parameters.displacementXY;
                    trackingCovariances[2] = parameters.displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number.");

                    final KF3dDirected predictor = new KF3dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);

                    pred = predictor;
                }
                else {
                    final double[] trackingCovariances = new double[2];

                    trackingCovariances[0] = parameters.displacementXY;
                    trackingCovariances[1] = parameters.displacementXY;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number.");

                    final KF2dDirected predictor = new KF2dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);
                    pred = predictor;
                }
            }
            else {
                if (dim == 3) {
                    final double[] trackingCovariances = new double[3];
                    final double displacementXY = parameters.displacementXY;
                    final double displacementZ = parameters.displacementZ;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    trackingCovariances[2] = displacementZ * displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF3dRandomWalk predictor = new KF3dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);
                    pred = predictor;
                }
                else {
                    double[] trackingCovariances = new double[2];
                    final double displacementXY = parameters.displacementXY;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF2dRandomWalk predictor = new KF2dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);
                    pred = predictor;
                }
            }
        }
        else {
            // multi motion
            if (dim == 3) {
                // 3D
                final double immInertia = parameters.immInertia;
                if (immInertia < 0 || immInertia > 1)
                    throw new IllegalArgumentException(
                            "The inertia for model switching must be a number lying between 0 and 1.");

                final IMM3D.LikelihoodTypes predictorType = parameters.useMostLikelyModel
                        ? IMM3D.LikelihoodTypes.IMM_MAX_LIKELIHOOD
                        : IMM3D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
                final List<Predictor3D> p3DList = new ArrayList<Predictor3D>();

                // first model
                if (parameters.isDirectedMotion) {
                    final double[] trackingCovariances = new double[3];
                    final double displacementXY = parameters.displacementXY;
                    final double displacementZ = parameters.displacementZ;

                    // FIXME: why we are using square distance here while we were using single distance for single
                    // motion model ??
                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    trackingCovariances[2] = displacementZ * displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number.");

                    final KF3dDirected predictor = new KF3dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);

                    p3DList.add(predictor);
                }
                else {
                    final double[] trackingCovariances = new double[3];
                    final double displacementXY = parameters.displacementXY;
                    final double displacementZ = parameters.displacementZ;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    trackingCovariances[2] = displacementZ * displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF3dRandomWalk predictor = new KF3dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);

                    p3DList.add(predictor);
                }
                // second model
                if (parameters.isDirectedMotion2) {
                    final double[] trackingCovariances = new double[3];
                    final double displacementXY = parameters.displacementXY2;
                    final double displacementZ = parameters.displacementZ2;

                    // FIXME: why we are using square distance here while we were using single distance for single
                    // motion model ??
                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    trackingCovariances[2] = displacementZ * displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number.");

                    final KF3dDirected predictor = new KF3dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion2);

                    p3DList.add(predictor);
                }
                else {
                    final double[] trackingCovariances = new double[3];
                    final double displacementXY = parameters.displacementXY2;
                    final double displacementZ = parameters.displacementZ2;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    trackingCovariances[2] = displacementZ * displacementZ;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF3dRandomWalk predictor = new KF3dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion2);

                    p3DList.add(predictor);
                }

                pred = new IMM3D(predictorType, immInertia, p3DList);
            }
            else {
                // 2D
                final double immInertia = parameters.immInertia;
                if (immInertia < 0 || immInertia > 1)
                    throw new IllegalArgumentException(
                            "The inertia for model switching must be a number lying between 0 and 1.");

                final IMM2D.LikelihoodTypes predictorType = parameters.useMostLikelyModel
                        ? IMM2D.LikelihoodTypes.IMM_MAX_LIKELIHOOD
                        : IMM2D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
                final List<Predictor2D> p2DList = new ArrayList<Predictor2D>();

                // first model
                if (parameters.isDirectedMotion) {
                    final double[] trackingCovariances = new double[2];
                    final double displacementXY = parameters.displacementXY;

                    // FIXME: why we are using square distance here while we were using single distance for single
                    // motion model ??
                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF2dDirected predictor = new KF2dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);

                    p2DList.add(predictor);
                }
                else {
                    final double[] trackingCovariances = new double[2];
                    final double displacementXY = parameters.displacementXY;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF2dRandomWalk predictor = new KF2dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion);

                    p2DList.add(predictor);
                }
                // second model
                if (parameters.isDirectedMotion2) {
                    final double[] trackingCovariances = new double[2];
                    final double displacementXY = parameters.displacementXY2;

                    // FIXME: why we are using square distance here while we were using single distance for single
                    // motion model ??
                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF2dDirected predictor = new KF2dDirected();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion2);

                    p2DList.add(predictor);
                }
                else {
                    final double[] trackingCovariances = new double[2];
                    final double displacementXY = parameters.displacementXY2;

                    trackingCovariances[0] = displacementXY * displacementXY / 2;
                    trackingCovariances[1] = displacementXY * displacementXY / 2;
                    if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
                        throw new IllegalArgumentException(
                                "The expected particle displacement has to be a positive number");

                    final KF2dRandomWalk predictor = new KF2dRandomWalk();

                    predictor.setTrackingCovariances(trackingCovariances);
                    predictor.setUpdateCovariances(parameters.isUpdateMotion2);

                    p2DList.add(predictor);
                }

                pred = new IMM2D(predictorType, immInertia, p2DList);
            }
        }

        // build the mht tracker
        final List<Predictor> predictors = new ArrayList<Predictor>();
        predictors.add(pred);

        final HMMMHTracker tracker = new HMMMHTracker(predictors, volume, gateFactor, probaDetect,
                densityFalseDetection, densityInitialTrack, densityNewTrack, treeDepth, dim, multithreaded, useLPSolve);

        // set the existence parameters
        final double meanTrackLength = parameters.meanTrackLength;
        final double pc = parameters.confirmationThreshold;
        final double pt = parameters.terminationThreshold;

        if (meanTrackLength <= 0)
            throw new IllegalArgumentException("The expected track length must be a positive number.");
        if (pc < 0 || pc > 1)
            throw new IllegalArgumentException(
                    "The confirmation probability threshold must be a number lying between 0 and 1.");
        if (pt < 0 || pt > 1)
            throw new IllegalArgumentException(
                    "The termination probability threshold must be a number lying between 0 and 1.");

        tracker.changeExistenceSettings(1 / meanTrackLength, pc, pt);

        return tracker;
    }

    public static HMMMHTracker buildTracker(MHTparameterSet parameters, DetectionResult dr, boolean useLPSolver,
                                            boolean multiThread) throws IllegalArgumentException {
        int dim = 2;
        double volume = 256 * 256;

        // initialize dimension and volume
        if (dr.getSequence() != null) {
            if (dr.getSequence().getSizeZ() > 1)
                dim = 3;
            volume = dr.getSequence().getSizeX() * dr.getSequence().getSizeY() * dr.getSequence().getSizeZ();
        }
        else {
            // check detections to see if they all have the same z coordinate
            // double minX = Double.MAX_VALUE;
            // double minY = Double.MAX_VALUE;
            // double minZ = Double.MAX_VALUE;
            double minX = 0; // assume the sequence coordinates start at 0
            double minY = 0;
            double minZ = 0;
            double maxX = 0;
            double maxY = 0;
            double maxZ = 0;

            for (int t = dr.getFirstFrameTime(); t <= dr.getLastFrameTime(); t++) {
                for (Spot s : dr.getDetectionsAtT(t)) {
                    minX = Math.min(minX, s.mass_center.x);
                    minY = Math.min(minY, s.mass_center.y);
                    minZ = Math.min(minZ, s.mass_center.z);
                    maxX = Math.max(maxX, s.mass_center.x);
                    maxY = Math.max(maxY, s.mass_center.y);
                    maxZ = Math.max(maxZ, s.mass_center.z);
                }
            }
            if (maxZ > minZ) {
                dim = 3;
                volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            }
            else {
                dim = 2;
                volume = (maxX - minX + 1) * (maxY - minY + 1);
            }
        }

        return buildTracker(parameters, dr, dim, volume, SpotTrackingPlugin.optimizationLibraryLoaded && useLPSolver,
                multiThread);
    }

    public static HMMMHTracker buildTracker(MHTparameterSet parameters, DetectionResult dr)
            throws IllegalArgumentException {
        return buildTracker(parameters, dr, true, true);
    }

    static double squaredDistance(Spot s1, Spot s2) {
        return (s1.mass_center.x - s2.mass_center.x) * (s1.mass_center.x - s2.mass_center.x)
                + (s1.mass_center.y - s2.mass_center.y) * (s1.mass_center.y - s2.mass_center.y)
                + (s1.mass_center.z - s2.mass_center.z) * (s1.mass_center.z - s2.mass_center.z);
    }

}
