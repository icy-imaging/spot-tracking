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

package plugins.nchenouard.particletracking;

import org.bioimageanalysis.icy.common.string.StringUtil;
import org.bioimageanalysis.icy.io.xml.XMLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.nchenouard.spot.DetectionResult;

/**
 * Set of parameters for the MHT tracking algorithms
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class MHTparameterSet {
    // detection parameters
    public DetectionResult detectionResults = null;
    public static final int defaultNumberOfFalseDetections = 20;
    public int numberOfFalseDetections;
    public static final double defaultDetectionRate = 0.9d;
    public double detectionRate;

    public final static String ROOT_DETECTION_INPUT = "detectionInput";
    public final static String DETECTION_SOURCE = "detectionSource";
    public final static String NO_SOURCE = "noSource";
    public final static String FALSE_DETECTION = "numberFalseDetection";
    public final static String DETECTION_RATE = "detectionRate";

    // target existence parameters
    public static final double defaultConfirmationThreshold = 0.5d;
    public double confirmationThreshold;
    public static final double defaultTerminationThreshold = 0.0001d;
    public double terminationThreshold;
    public static final double defaultMeanTrackLength = 20d;
    public double meanTrackLength;

    public final static String ROOT_TARGET_EXISTENCE = "targetExistence";
    public final static String TRACK_LENGTH = "trackLength";
    public final static String CONFIRMATION_THRESHOLD = "confirmationThreshold";
    public final static String TERMINATION_THRESHOLD = "terminationThreshold";

    // target motion parameters
    public static final boolean defaultIsSingleMotion = true;
    public boolean isSingleMotion;

    public static final double defaultDisplacementXY = 3;
    public double displacementXY;
    public static final double defaultDisplacementZ = 3;
    public double displacementZ;
    public static final boolean defaultIsUpdateMotion = false;
    public boolean isUpdateMotion;
    public static final boolean defaultIsDirectedMotion = false;
    public boolean isDirectedMotion;

    public static final double defaultDisplacementXY2 = 3;
    public double displacementXY2;
    public static final double defaultDisplacementZ2 = 3;
    public double displacementZ2;
    public static final boolean defaultIsUpdateMotion2 = false;
    public boolean isUpdateMotion2;
    public static final boolean defaultIsDirectedMotion2 = true;
    public boolean isDirectedMotion2;

    public static final boolean defaultUseMostLikelyModel = true;
    public boolean useMostLikelyModel;
    public static final double defaultIMMInertia = 0.8;
    public double immInertia;

    public final static String ROOT_MOTION_MODEL = "motionModel";
    public final static String IS_SINGLE_MOTION = "isSingleMotion";
    public final static String IS_MOST_LIKELY_MODEL = "isMostLikelyModel";
    public final static String IMM_INERTIA = "IMMinertia";

    public final static String XYDISPLACEMENT_1 = "yxDisplacement_1";
    public final static String ZDISPLACEMENT_1 = "zDisplacement_1";
    public final static String IS_DIRECTED_MOTION_1 = "isDirectedMotion_1";
    public final static String IS_UPDATE_MOTION_1 = "updateMotion_1";

    public final static String XYDISPLACEMENT_2 = "yxDisplacement_2";
    public final static String ZDISPLACEMENT_2 = "zDisplacement_2";
    public final static String IS_DIRECTED_MOTION_2 = "isDirectedMotion_2";
    @Deprecated(since = "4.0.0")
    public final static String IS_UPDATE_COVARIANCE_2 = "updateCovariance_2";
    public final static String IS_UPDATE_MOTION_2 = "updateMotion_2";

    // MHT algorithm
    public static final double defaultGateFactor = 4;
    public double gateFactor;
    public static final double defaultNumberInitialObjects = 20;
    public double numberInitialObjects;
    public static final int defaultMHTDepth = 4;
    public int mhtDepth;
    public static final double defaultNumberNewObjects = 10;
    public double numberNewObjects;

    public final static String ROOT_MHT = "mht";
    public final static String NUMBER_NEW_OBJECTS = "numberNewObjects";
    public final static String NUMBER_OBJECTS_FIRST_FRAME = "numberObjectsFirstFrame";
    public final static String MHT_DEPTH = "mhtDepth";
    public final static String GATE_FACTOR = "gateFactor";

    // tracks output
    public final static String ROOT_OUTPUT = "ouput";
    public final static String TRACK_GROUP_NAME = "trackGroupName";

    public String trackGroupName;
    public final static String defaultTrackGroupName = "mht-tracks-1";

    @Contract(pure = true)
    public MHTparameterSet() {
        numberOfFalseDetections = defaultNumberOfFalseDetections;
        detectionRate = defaultDetectionRate;
        confirmationThreshold = defaultConfirmationThreshold;
        terminationThreshold = defaultTerminationThreshold;
        meanTrackLength = defaultMeanTrackLength;
        isSingleMotion = defaultIsSingleMotion;
        displacementXY = defaultDisplacementXY;
        displacementZ = defaultDisplacementZ;
        isUpdateMotion = defaultIsUpdateMotion;
        isDirectedMotion = defaultIsDirectedMotion;
        displacementXY2 = defaultDisplacementXY2;
        displacementZ2 = defaultDisplacementZ2;
        isUpdateMotion2 = defaultIsUpdateMotion2;
        isDirectedMotion2 = defaultIsDirectedMotion2;
        useMostLikelyModel = defaultUseMostLikelyModel;
        immInertia = defaultIMMInertia;
        gateFactor = defaultGateFactor;
        numberInitialObjects = defaultNumberInitialObjects;
        mhtDepth = defaultMHTDepth;
        numberNewObjects = defaultNumberNewObjects;
        trackGroupName = defaultTrackGroupName;
    }

    public static @NotNull MHTparameterSet loadFromXML(final Node node) {
        final MHTparameterSet parameterSet = new MHTparameterSet();
        final Element nodeDetection = XMLUtil.getElement(node, ROOT_DETECTION_INPUT);
        if (nodeDetection != null) {
            parameterSet.numberOfFalseDetections = XMLUtil.getAttributeIntValue(nodeDetection, FALSE_DETECTION, defaultNumberOfFalseDetections);
            parameterSet.detectionRate = XMLUtil.getAttributeDoubleValue(nodeDetection, DETECTION_RATE, defaultDetectionRate);
        }
        final Element nodeExistence = XMLUtil.getElement(node, ROOT_TARGET_EXISTENCE);
        if (nodeExistence != null) {
            parameterSet.meanTrackLength = XMLUtil.getAttributeDoubleValue(nodeExistence, TRACK_LENGTH, defaultMeanTrackLength);
            parameterSet.confirmationThreshold = XMLUtil.getAttributeDoubleValue(nodeExistence, CONFIRMATION_THRESHOLD, defaultConfirmationThreshold);
            parameterSet.terminationThreshold = XMLUtil.getAttributeDoubleValue(nodeExistence, TERMINATION_THRESHOLD, defaultTerminationThreshold);
        }
        final Element nodeMotion = XMLUtil.getElement(node, ROOT_MOTION_MODEL);
        if (nodeMotion != null) {
            parameterSet.isSingleMotion = XMLUtil.getAttributeBooleanValue(nodeMotion, IS_SINGLE_MOTION, defaultIsSingleMotion);
            parameterSet.isDirectedMotion = XMLUtil.getAttributeBooleanValue(nodeMotion, IS_DIRECTED_MOTION_1, defaultIsDirectedMotion);
            parameterSet.displacementXY = XMLUtil.getAttributeDoubleValue(nodeMotion, XYDISPLACEMENT_1, defaultDisplacementXY);
            parameterSet.displacementZ = XMLUtil.getAttributeDoubleValue(nodeMotion, ZDISPLACEMENT_1, defaultDisplacementZ);
            parameterSet.isUpdateMotion = XMLUtil.getAttributeBooleanValue(nodeMotion, IS_UPDATE_MOTION_1, defaultIsUpdateMotion);

            if (!parameterSet.isSingleMotion) {
                parameterSet.isDirectedMotion2 = XMLUtil.getAttributeBooleanValue(nodeMotion, IS_DIRECTED_MOTION_2, defaultIsDirectedMotion2);
                parameterSet.displacementXY2 = XMLUtil.getAttributeDoubleValue(nodeMotion, XYDISPLACEMENT_2, defaultDisplacementXY2);
                parameterSet.displacementZ2 = XMLUtil.getAttributeDoubleValue(nodeMotion, ZDISPLACEMENT_2, defaultDisplacementZ2);
                parameterSet.isUpdateMotion2 = XMLUtil.getAttributeBooleanValue(
                        nodeMotion,
                        IS_UPDATE_MOTION_2,
                        XMLUtil.getAttributeBooleanValue(
                                nodeMotion,
                                IS_UPDATE_COVARIANCE_2,
                                defaultIsUpdateMotion2
                        )
                );
                parameterSet.useMostLikelyModel = XMLUtil.getAttributeBooleanValue(nodeMotion, IS_MOST_LIKELY_MODEL, defaultUseMostLikelyModel);
                parameterSet.immInertia = XMLUtil.getAttributeDoubleValue(nodeMotion, IMM_INERTIA, defaultIMMInertia);
            }
        }
        final Element nodeMHT = XMLUtil.getElement(node, MHTparameterSet.ROOT_MHT);
        if (nodeMHT != null) {
            parameterSet.gateFactor = XMLUtil.getAttributeDoubleValue(nodeMHT, MHTparameterSet.GATE_FACTOR, defaultGateFactor);
            parameterSet.numberNewObjects = XMLUtil.getAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_NEW_OBJECTS, defaultNumberNewObjects);
            parameterSet.numberInitialObjects = XMLUtil.getAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_OBJECTS_FIRST_FRAME, defaultNumberInitialObjects);
            parameterSet.mhtDepth = XMLUtil.getAttributeIntValue(nodeMHT, MHTparameterSet.MHT_DEPTH, defaultMHTDepth);
        }
        final Element nodeOutput = XMLUtil.getElement(node, MHTparameterSet.ROOT_OUTPUT);
        if (nodeOutput != null)
            parameterSet.trackGroupName = XMLUtil.getAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, defaultTrackGroupName);

        return parameterSet;
    }

    public void saveToXML(final Node configurationElement) {
        final Element nodeDetection = XMLUtil.setElement(configurationElement, MHTparameterSet.ROOT_DETECTION_INPUT);
        if (nodeDetection != null) {
            if (detectionResults != null)
                XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, detectionResults.toString());
            else
                XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, MHTparameterSet.NO_SOURCE);
            XMLUtil.setAttributeIntValue(nodeDetection, MHTparameterSet.FALSE_DETECTION, numberOfFalseDetections);
            XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.DETECTION_RATE, detectionRate);
        }
        final Element nodeExistence = XMLUtil.setElement(configurationElement, MHTparameterSet.ROOT_TARGET_EXISTENCE);
        if (nodeExistence != null) {
            XMLUtil.setAttributeDoubleValue(nodeExistence, MHTparameterSet.TRACK_LENGTH, meanTrackLength);
            XMLUtil.setAttributeDoubleValue(nodeExistence, MHTparameterSet.CONFIRMATION_THRESHOLD, confirmationThreshold);
            XMLUtil.setAttributeDoubleValue(nodeExistence, MHTparameterSet.TERMINATION_THRESHOLD, terminationThreshold);
        }
        final Element nodeMotionModel = XMLUtil.setElement(configurationElement, MHTparameterSet.ROOT_MOTION_MODEL);
        if (nodeMotionModel != null) {
            XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_SINGLE_MOTION, isSingleMotion);
            if (isSingleMotion) {
                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_DIRECTED_MOTION_1, isDirectedMotion);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.XYDISPLACEMENT_1, displacementXY);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.ZDISPLACEMENT_1, displacementZ);
                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_UPDATE_MOTION_1, isUpdateMotion);
            }
            else {
                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_DIRECTED_MOTION_1, isDirectedMotion);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.XYDISPLACEMENT_1, displacementXY);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.ZDISPLACEMENT_1, displacementZ);
                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_UPDATE_MOTION_1, isUpdateMotion);

                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_DIRECTED_MOTION_2, isDirectedMotion2);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.XYDISPLACEMENT_2, displacementXY2);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.ZDISPLACEMENT_2, displacementZ2);
                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_UPDATE_MOTION_2, isUpdateMotion2);

                XMLUtil.setAttributeBooleanValue(nodeMotionModel, MHTparameterSet.IS_MOST_LIKELY_MODEL, useMostLikelyModel);
                XMLUtil.setAttributeDoubleValue(nodeMotionModel, MHTparameterSet.IMM_INERTIA, immInertia);
            }
        }
        final Element nodeMHT = XMLUtil.setElement(configurationElement, MHTparameterSet.ROOT_MHT);
        if (nodeMHT != null) {
            XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_NEW_OBJECTS, numberNewObjects);
            XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_OBJECTS_FIRST_FRAME, numberInitialObjects);
            XMLUtil.setAttributeIntValue(nodeMHT, MHTparameterSet.MHT_DEPTH, mhtDepth);
            XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.GATE_FACTOR, gateFactor);
        }
        final Element nodeOutput = XMLUtil.setElement(configurationElement, MHTparameterSet.ROOT_OUTPUT);
        if (nodeOutput != null) {
            XMLUtil.setAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, trackGroupName);
        }
    }

    @Override
    public String toString() {
        String result;

        if (isSingleMotion)
            result = trackGroupName + " - Single motion [" + isDirectedMotion + ";"
                    + isUpdateMotion + ";" + StringUtil.toString(displacementXY, 1) + ";"
                    + StringUtil.toString(displacementZ, 1) + "]\n";
        else
            result = trackGroupName + " - Multi motion [" + isDirectedMotion + ";"
                    + isUpdateMotion + ";" + StringUtil.toString(displacementXY, 1) + ";"
                    + StringUtil.toString(displacementZ, 1) + "]" + "[" + isDirectedMotion2 + ";"
                    + isUpdateMotion2 + ";" + StringUtil.toString(displacementXY2, 1) + ";"
                    + StringUtil.toString(displacementZ2, 1) + "]" + "[" + useMostLikelyModel + ";"
                    + StringUtil.toString(immInertia, 2) + "]\n";

        result += "(" + StringUtil.toString(gateFactor, 1) + ";" + StringUtil.toString(mhtDepth) + ";"
                + StringUtil.toString(numberInitialObjects, 1) + ";" + StringUtil.toString(numberNewObjects, 1) + ";"
                + StringUtil.toString(confirmationThreshold, 1) + ";" + StringUtil.toString(terminationThreshold, 1)
                + ";" + StringUtil.toString(meanTrackLength, 1) + ")";

        return result;
    }
}