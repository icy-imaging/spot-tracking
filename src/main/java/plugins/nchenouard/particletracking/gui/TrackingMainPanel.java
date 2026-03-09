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

package plugins.nchenouard.particletracking.gui;

import org.bioimageanalysis.icy.model.swimmingPool.SwimmingObject;
import plugins.nchenouard.particletracking.MHTparameterSet;
import plugins.nchenouard.spot.DetectionResult;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

/**
 * Main panel for the advanced interface of the Spot Tracking plugin
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class TrackingMainPanel extends JPanel implements TreeSelectionListener {
    JPanel centerPane = new JPanel(new CardLayout());

    JTree tree;
    public DetectionInputPanel detectionInputPanel = new DetectionInputPanel();
    public TrackingDescriptionPanel trackingPanel = new TrackingDescriptionPanel();
    public TargetExistencePanel targetExistencePanel = new TargetExistencePanel();
    public MotionModelPanel motionModelPanel = new MotionModelPanel();
    public MHTPanel mhtPanel = new MHTPanel();
    public TracksOutputPanel tracksOutputPanel = new TracksOutputPanel();
    public ConfigurationFilePanel configFilePanel = new ConfigurationFilePanel();
//	public BenchmarkPanel benchmarkPanel = new BenchmarkPanel();

    public JButton startTrackingButton = new JButton("Start");

    public TrackingMainPanel() {
        /*Create the panel with the tree of actions and settings*/
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode("");
        root.add(detectionInputPanel.node);

        final DefaultMutableTreeNode rootTracking = trackingPanel.node;
        root.add(rootTracking);

        rootTracking.add(targetExistencePanel.node);
        rootTracking.add(motionModelPanel.node);
        rootTracking.add(mhtPanel.node);

        root.add(tracksOutputPanel.node);
        root.add(configFilePanel.node);
//		root.add( benchmarkPanel.node );

        tree = new JTree(root);

        tree.setRootVisible(false);

        for (int row = 0; row < tree.getRowCount(); row++) // Expand tree
        {
            tree.expandRow(row);
        }
        tree.addTreeSelectionListener(this);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setOpaque(true);
        final JScrollPane treeScrollPane = new JScrollPane(tree);

        /*Create the center panel*/
        centerPane.add(detectionInputPanel, detectionInputPanel.node.toString());
        centerPane.add(trackingPanel, trackingPanel.node.toString());
        centerPane.add(targetExistencePanel, targetExistencePanel.node.toString());
        centerPane.add(motionModelPanel, motionModelPanel.node.toString());
        centerPane.add(mhtPanel, mhtPanel.node.toString());
        centerPane.add(tracksOutputPanel, tracksOutputPanel.node.toString());
        centerPane.add(configFilePanel, configFilePanel.node.toString());
//		centerPane.add(benchmarkPanel, benchmarkPanel.node.toString());

        /*Create the action panel*/
        final JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(startTrackingButton);

        this.setLayout(new BorderLayout());
        this.add(treeScrollPane, BorderLayout.WEST);
        this.add(centerPane, BorderLayout.CENTER);
        this.add(actionPanel, BorderLayout.SOUTH);

        /*Set up listener*/
        // mean track length
        trackingPanel.spinnerMeanTrackLength.addChangeListener(arg0 -> targetExistencePanel.spinnerMeanTrackLength.setValue(trackingPanel.spinnerMeanTrackLength.getValue()));
        targetExistencePanel.spinnerMeanTrackLength.addChangeListener(arg0 -> trackingPanel.spinnerMeanTrackLength.setValue(targetExistencePanel.spinnerMeanTrackLength.getValue()));
        // expected number of new tracks
        trackingPanel.numberNewTrackSpinner.addChangeListener(arg0 -> mhtPanel.numberNewTrackSpinner.setValue(trackingPanel.numberNewTrackSpinner.getValue()));
        mhtPanel.numberNewTrackSpinner.addChangeListener(arg0 -> trackingPanel.numberNewTrackSpinner.setValue(mhtPanel.numberNewTrackSpinner.getValue()));
        // expected number of initial tracks
        trackingPanel.numberInitialTrackSpinner.addChangeListener(arg0 -> mhtPanel.numberInitialTrackSpinner.setValue(trackingPanel.numberInitialTrackSpinner.getValue()));
        mhtPanel.numberInitialTrackSpinner.addChangeListener(arg0 -> trackingPanel.numberInitialTrackSpinner.setValue(mhtPanel.numberInitialTrackSpinner.getValue()));
        // average length for displacement
        trackingPanel.spinnerXYDisplacement.addChangeListener(arg0 -> motionModelPanel.singleMotionPanel.diffusionPanel.spinnerXYDisplacement.setValue(trackingPanel.spinnerXYDisplacement.getValue()));
        // average length for displacement
        motionModelPanel.singleMotionPanel.diffusionPanel.spinnerXYDisplacement.addChangeListener(arg0 -> trackingPanel.spinnerXYDisplacement.setValue(motionModelPanel.singleMotionPanel.diffusionPanel.spinnerXYDisplacement.getValue()));
    }

    @Override
    public void valueChanged(final TreeSelectionEvent arg0) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == detectionInputPanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, detectionInputPanel.node.toString());
        }
        if (node == trackingPanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, trackingPanel.node.toString());
        }
        if (node == targetExistencePanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, targetExistencePanel.node.toString());
        }
        if (node == motionModelPanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, motionModelPanel.node.toString());
        }
        if (node == mhtPanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, mhtPanel.node.toString());
        }
        if (node == tracksOutputPanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, tracksOutputPanel.node.toString());
        }
        if (node == configFilePanel.node) {
            final CardLayout cl = (CardLayout) (centerPane.getLayout());
            cl.show(centerPane, configFilePanel.node.toString());
        }
//		if (node == benchmarkPanel.node)
//		{
//			CardLayout cl = (CardLayout)(centerPane.getLayout());
//			cl.show(centerPane, benchmarkPanel.node.toString());
//		}
    }

    public DetectionResult getSelectedDetectionResult() {
        if (this.detectionInputPanel.detectionChooser.getSelectedDetectionResult() != null)
            return this.detectionInputPanel.detectionChooser.getSelectedDetectionResult();
        else return null;
    }

//	public HMMMHTracker buildTracker(final int dim, final double volume, boolean useLPSolve, boolean multithreaded) throws Exception {
//		double probaDetect = detectionInputPanel.getDetectionRate();
//		if (probaDetect < 0 || probaDetect > 1)
//			throw new IllegalArgumentException("The probability of detection for each particle has to lie between 0 and 1");
//		double densityFalseDetection = detectionInputPanel.getNumberOfFalseDetections()/volume;
//		if (densityFalseDetection < 0)
//			throw new IllegalArgumentException("The expected number of false detections needs to be a positive number");
//
//		double gateFactor = mhtPanel.getGateFactor();
//		if (gateFactor <= 0)
//			throw new IllegalArgumentException("The gate factor needs to be a positive number");
//
//		double densityInitialTrack = mhtPanel.getNumberInitialTracks()/volume;
//		if (densityInitialTrack < 0)
//			throw new IllegalArgumentException("The expected number of initial needs to be a positive number");
//		double densityNewTrack = mhtPanel.getNumbeNewTracks()/volume;
//		if (densityNewTrack < 0)
//			throw new IllegalArgumentException("The expected number of new tracks per frame needs to be a positive number");
//
//		int treeDepth = mhtPanel.getMHTDepth();
//		if (treeDepth < 1)
//			throw new IllegalArgumentException("The tree depth needs to be an integer greater or equal to 1");
//		/* build the predictor*/
//		Predictor pred = null;
//		if (motionModelPanel.isSingleMotion())
//		{
//			if (motionModelPanel.singleMotionPanel.isDirectedMotion())
//			{
//				if (dim == 3)
//				{
//					KF3dDirected predictor = new KF3dDirected();
//					double[]trackingCovariances = new double[3];
//					trackingCovariances[0] = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					trackingCovariances[1] = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					trackingCovariances[2] = motionModelPanel.singleMotionPanel.getDisplacementZ();					
//					if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
//						throw new IllegalArgumentException("The expected particle displacement has to be a positive number.");
//					
//					predictor.setTrackingCovariances(trackingCovariances);
//					predictor.setUpdateCovariances(motionModelPanel.singleMotionPanel.isUpdateMotion());
//					pred = predictor;
//				}
//				else
//				{
//					KF2dDirected predictor = new KF2dDirected();
//					double[]trackingCovariances = new double[2];
//					trackingCovariances[0] = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					trackingCovariances[1] = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
//						throw new IllegalArgumentException("The expected particle displacement has to be a positive number.");
//
//					predictor.setTrackingCovariances(trackingCovariances);
//					predictor.setUpdateCovariances(motionModelPanel.singleMotionPanel.isUpdateMotion());
//					pred = predictor;
//				}
//			}
//			else
//			{
//				if (dim == 3)
//				{
//					
//					KF3dRandomWalk predictor = new KF3dRandomWalk();
//					double[]trackingCovariances = new double[3];
//					double displacementXY = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					trackingCovariances[0] = displacementXY*displacementXY/2;
//					trackingCovariances[1] = displacementXY*displacementXY/2;
//					double displacementZ = motionModelPanel.singleMotionPanel.getDisplacementZ();
//					trackingCovariances[2] = displacementZ*displacementZ;
//					if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
//						throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//					predictor.setTrackingCovariances(trackingCovariances);
//					predictor.setUpdateCovariances(motionModelPanel.singleMotionPanel.isUpdateMotion());
//					pred = predictor;
//				}
//				else
//				{
//					KF2dRandomWalk predictor = new KF2dRandomWalk();
//					double[]trackingCovariances = new double[2];
//					double displacementXY = motionModelPanel.singleMotionPanel.getDisplacementXY();
//					trackingCovariances[0] = displacementXY*displacementXY/2;
//					trackingCovariances[1] = displacementXY*displacementXY/2;
//					if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
//						throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//					predictor.setTrackingCovariances(trackingCovariances);
//					predictor.setUpdateCovariances(motionModelPanel.singleMotionPanel.isUpdateMotion());
//					pred = predictor;
//				}
//			}
//		}
//		else
//		{	
//			if (dim == 3)
//			{
//				double immInertia = motionModelPanel.multipleMotionPanel.getInertia();
//				IMM3D.LikelihoodTypes predictorType = IMM3D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
//				if (motionModelPanel.multipleMotionPanel.isUseMostLikelyModel())
//					predictorType = IMM3D.LikelihoodTypes.IMM_MAX_LIKELIHOOD;	
//
//				ArrayList<Predictor3D> p3DList = new ArrayList<Predictor3D>();
//				for (SingleMotionPanel singlePanel:motionModelPanel.multipleMotionPanel.getSingleMotionPanels())
//				{
//					if (singlePanel.isDirectedMotion())
//					{
//						KF3dDirected predictor = new KF3dDirected();
//						double[]trackingCovariances = new double[3];
//						double displacementXY = singlePanel.getDisplacementXY();
//						trackingCovariances[0] = displacementXY*displacementXY/2;
//						trackingCovariances[1] = displacementXY*displacementXY/2;
//						double displacementZ = singlePanel.getDisplacementZ();
//						trackingCovariances[2] = displacementZ*displacementZ;
//						if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
//							throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//						predictor.setTrackingCovariances(trackingCovariances);
//						predictor.setUpdateCovariances(singlePanel.isUpdateMotion());
//						p3DList.add(predictor);
//					}
//					else
//					{
//						KF3dRandomWalk predictor = new KF3dRandomWalk();
//						double[]trackingCovariances = new double[3];
//						double displacementXY = singlePanel.getDisplacementXY();
//						trackingCovariances[0] = displacementXY*displacementXY/2;
//						trackingCovariances[1] = displacementXY*displacementXY/2;
//						double displacementZ = singlePanel.getDisplacementZ();
//						trackingCovariances[2] = displacementZ*displacementZ;
//						if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0 || trackingCovariances[2] < 0)
//							throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//						predictor.setTrackingCovariances(trackingCovariances);
//						predictor.setUpdateCovariances(singlePanel.isUpdateMotion());
//						p3DList.add(predictor);
//					}
//				}
//				IMM3D immFilter = new IMM3D(predictorType, immInertia, p3DList);
//				pred = immFilter;
//			}
//			else
//			{
//				double immInertia = motionModelPanel.multipleMotionPanel.getInertia();
//				if (immInertia < 0 || immInertia > 1)
//					throw new IllegalArgumentException("The inertia for model switching must be a number lying between 0 and 1.");
//
//				IMM2D.LikelihoodTypes predictorType = IMM2D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
//				if (motionModelPanel.multipleMotionPanel.isUseMostLikelyModel())
//					predictorType = IMM2D.LikelihoodTypes.IMM_MAX_LIKELIHOOD;	
//
//				ArrayList<Predictor2D> p2DList = new ArrayList<Predictor2D>();
//				for (SingleMotionPanel singlePanel:motionModelPanel.multipleMotionPanel.getSingleMotionPanels())
//				{
//					if (singlePanel.isDirectedMotion())
//					{
//							KF2dDirected predictor = new KF2dDirected();
//							double[]trackingCovariances = new double[2];
//							double displacementXY = singlePanel.getDisplacementXY();
//							trackingCovariances[0] = displacementXY*displacementXY/2;
//							trackingCovariances[1] = displacementXY*displacementXY/2;
//							if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
//								throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//							predictor.setTrackingCovariances(trackingCovariances);
//							predictor.setUpdateCovariances(singlePanel.isUpdateMotion());
//							p2DList.add(predictor);
//					}
//					else
//					{
//						KF2dRandomWalk predictor = new KF2dRandomWalk();
//						double[]trackingCovariances = new double[2];
//						double displacementXY = singlePanel.getDisplacementXY();
//						trackingCovariances[0] = displacementXY*displacementXY/2;
//						trackingCovariances[1] = displacementXY*displacementXY/2;
//						if (trackingCovariances[0] < 0 || trackingCovariances[1] < 0)
//							throw new IllegalArgumentException("The expected particle displacement has to be a positive number");
//						predictor.setTrackingCovariances(trackingCovariances);
//						predictor.setUpdateCovariances(singlePanel.isUpdateMotion());
//						p2DList.add(predictor);
//					}
//				}
//				IMM2D immFilter = new IMM2D(predictorType, immInertia, p2DList);
//				pred = immFilter;
//			}
//		}
//			
//		/*build the mht tracker*/
//		ArrayList<Predictor> predictors = new ArrayList<Predictor>();
//		predictors.add(pred);
//		HMMMHTracker tracker = new HMMMHTracker(predictors, volume, gateFactor, probaDetect, densityFalseDetection, densityInitialTrack, densityNewTrack, treeDepth, dim, multithreaded, useLPSolve);
//		/* set the existence parameters*/
//		double meanTrackLength = targetExistencePanel.getMeanTrackLength();
//		if (meanTrackLength <= 0 )
//			throw new IllegalArgumentException("The expected track length must be a positive number.");
//		double p10 = 1/meanTrackLength;
//		double pc;
//		try{
//			pc = targetExistencePanel.getConfirmationThreshold();
//		}
//		catch (Exception e){throw(e);}
//		if (pc < 0 || pc > 1)
//			throw new IllegalArgumentException("The confirmation probability threshold must be a number lying between 0 and 1.");
//		double pt;
//		try{ pt = targetExistencePanel.getTerminationThreshold();}
//		catch(Exception e) {throw(e);}
//		if (pt < 0 || pt > 1)
//			throw new IllegalArgumentException("The termination probability threshold must be a number lying between 0 and 1.");
//		tracker.changeExistenceSettings(p10, pc, pt);
//		return tracker;
//	}

    public String getTrackGroupName() {
        return tracksOutputPanel.trackGroupTF.getText();
    }

    public void setSelectedDetectionResults(final DetectionResult dr) {
        if (this.detectionInputPanel.detectionChooser.getSelectedDetectionResult() == dr)
            return;
        final int cnt = this.detectionInputPanel.detectionChooser.getItemCount();
        for (int i = 0; i < cnt; i++) {
            final Object o = this.detectionInputPanel.detectionChooser.getItemAt(i);
            if (o != null) {
                if (o == dr) {
                    this.detectionInputPanel.detectionChooser.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    public SwimmingObject getSelectedDetectionResults() {
        if (this.detectionInputPanel.detectionChooser.getSelectedItem() != null)
            return (SwimmingObject) this.detectionInputPanel.detectionChooser.getSelectedItem();
        else return null;
    }

    /**
     * Does nothing for now
     */
    public void changeTrackingState(final boolean b) {
        // TODO: 04/09/2023 Does nothing
    }

    public MHTparameterSet getParameterSet() {
        final MHTparameterSet parameterSet = new MHTparameterSet();
        detectionInputPanel.fillParameterSet(parameterSet);
        targetExistencePanel.fillParameterSet(parameterSet);
        motionModelPanel.fillParameterSet(parameterSet);
        mhtPanel.fillParameterSet(parameterSet);
        tracksOutputPanel.fillParameterSet(parameterSet);
        return parameterSet;
    }

    public void setParameterset(final MHTparameterSet parameterSet) {
        detectionInputPanel.setParameters(parameterSet);
        targetExistencePanel.setParameters(parameterSet);
        motionModelPanel.setParameters(parameterSet);
        mhtPanel.setParameters(parameterSet);
        tracksOutputPanel.setParameters(parameterSet);
    }
}
