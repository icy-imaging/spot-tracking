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

package plugins.nchenouard.particletracking.legacytracker.gui;

import org.bioimageanalysis.icy.gui.component.combo.SwimmingObjectChooser;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingObject;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingPoolEvent;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingPoolEventType;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.Tracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.AssignProblemSolver;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.InstantaneousTracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.SolveMLAssociation;
import plugins.nchenouard.spot.DetectionResult;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Panel to configure the legacy tracker of Genovesio et al., TIP 2006.
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class PanelTracking extends JPanel {
    SwimmingObjectChooser detectionChooser;

    SingleMotionPanel singleMotionPanel;
    IMMPanel immPanel;
    final String singleMotionString = "Single motion model";
    final String multipleMotionString = "Multiple motion models";
    final JComboBox<String> filtersBox = new JComboBox<>(new String[]{singleMotionString, multipleMotionString});
    JCheckBox covUpdateBox;

    private final SpinnerNumberModel gateFactorModel = new SpinnerNumberModel(3, 0.5, 100, 0.5);
    private final SpinnerNumberModel maxConsPredModel = new SpinnerNumberModel(2, 0, 100, 1);
    private final JTextField trackGroupNameTF;

    public JButton trackingStartButton;

    public enum AssociationMethods {
        ML, OPTIMAL, GREEDY
    }

    String[] associationNames = new String[]{
            "Maximum Likelihood",
            "Optimal association (slower)",
            "Greedy association (faster)"
    };

    // private JComboBox<String> trackingAssociationBox;

    public PanelTracking() {
        final GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        // detection
        this.add(new JLabel("Detection source:"), constraints);
        constraints.gridy++;
        detectionChooser = new SwimmingObjectChooser(DetectionResult.class);
        detectionChooser.swimmingPoolChangeEvent(new SwimmingPoolEvent(SwimmingPoolEventType.ELEMENT_REMOVED, new SwimmingObject(null)));
        this.add(detectionChooser, constraints);
        constraints.gridy++;

        // motion filtering
        this.add(new JLabel("Motion model:"), constraints);
        constraints.gridy++;

        final CardLayout motionCardLayout = new CardLayout();
        final JPanel motionModelPanel = new JPanel(motionCardLayout);
        singleMotionPanel = new SingleMotionPanel();
        immPanel = new IMMPanel();
        immPanel.setDefaultValues();
        motionModelPanel.add(singleMotionPanel, singleMotionString);
        motionModelPanel.add(immPanel, multipleMotionString);
        motionModelPanel.setBorder(new TitledBorder("Motion setting"));

        filtersBox.addActionListener(e -> motionCardLayout.show(motionModelPanel, (String) filtersBox.getSelectedItem()));
        this.add(filtersBox, constraints);
        constraints.gridy++;
        this.add(motionModelPanel, constraints);
        constraints.gridy++;
        covUpdateBox = new JCheckBox("Use adaptive covariance filter");
        this.add(covUpdateBox, constraints);
        constraints.gridy++;

        // // association
        // this.add(new JLabel("Tracking association method:"), constraints);
        // constraints.gridy++;
        // trackingAssociationBox = new JComboBox<String>(associationNames);
        // this.add(trackingAssociationBox, constraints);
        // constraints.gridy++;

        this.add(new JLabel("Max. number of consecutive missed detections:"), constraints);
        constraints.gridy++;
        final JSpinner maxConsPredSpinner = new JSpinner(maxConsPredModel);
        this.add(maxConsPredSpinner, constraints);
        constraints.gridy++;

        this.add(new JLabel("Gate factor:"), constraints);
        constraints.gridy++;
        final JSpinner gateFactorSpinner = new JSpinner(gateFactorModel);
        this.add(gateFactorSpinner, constraints);
        constraints.gridy++;

        final JLabel trackGroupLabel = new JLabel("Name of the output track group");
        this.add(trackGroupLabel, constraints);
        constraints.gridy++;
        trackGroupNameTF = new JTextField("newTracks-1");
        this.add(trackGroupNameTF, constraints);
        constraints.gridy++;

        trackingStartButton = new JButton();
        changeTrackingState(false);
        this.add(trackingStartButton, constraints);
    }

    public void changeTrackingState(final boolean isRunning) {
        final String runningString = "Stop tracking";
        final String nonRunningString = "Start tracking";
        if (isRunning)
            trackingStartButton.setText(runningString);
        else
            trackingStartButton.setText(nonRunningString);
        trackingStartButton.updateUI();
    }

    public DetectionResult getDetectionResults() {
        if (detectionChooser.getSelectedObject() != null)
            return (DetectionResult) detectionChooser.getSelectedObject();
        else
            return null;
    }

    public boolean IsErasePreExistingTracks() {
        return false;
    }

    public Predictor buildPredictor(final int dim) {
        final String item = (String) filtersBox.getSelectedItem();
        if (item != null && item.equals(singleMotionString))
            return singleMotionPanel.buildPredictor(dim, covUpdateBox.isSelected());
        else
            return immPanel.buildPredictor(dim, covUpdateBox.isSelected());
    }

    public AssociationMethods getAssociationMethod() {
        return AssociationMethods.ML;
        // int associationMethod = trackingAssociationBox.getSelectedIndex();
        // switch(associationMethod)
        // {
        // case 0:
        // return AssociationMethods.ML;
        // case 1:
        // return AssociationMethods.OPTIMAL;
        // case 2:
        // return AssociationMethods.GREEDY;
        // default:
        // throw new IllegalArgumentException("Association method not available");
        // }
    }

    public double getGateFactor() {
        return gateFactorModel.getNumber().doubleValue();
    }

    public int getMaxConsPred() {
        return maxConsPredModel.getNumber().intValue();
    }

    public Tracker buildTracker(final int dim) {
        final Predictor predictor = buildPredictor(dim);
        final AssignProblemSolver assignementSolver;
        // AssociationMethods associationMethod = getAssociationMethod();
        assignementSolver = new SolveMLAssociation(true);
        // switch(associationMethod)
        // {
        // case GREEDY:
        // {
        // assignementSolver = new SolveGreedyNNAssociation(true);
        // break;
        // }
        // case OPTIMAL:
        // assignementSolver = new SolveOBAssociation(true);
        // break;
        // default:
        // assignementSolver = new SolveMLAssociation(true);
        // }
        // Create Tracker
        return new InstantaneousTracker(assignementSolver, predictor, true, getGateFactor(), getMaxConsPred() + 1);
    }

    public String getTrackGroupName() {
        return trackGroupNameTF.getText();
    }
}
