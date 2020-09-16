package plugins.nchenouard.particletracking.legacytracker.gui;

import icy.gui.component.pool.SwimmingObjectChooser;
import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPoolEvent;
import icy.swimmingPool.SwimmingPoolEventType;

import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;

import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.Tracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.AssignProblemSolver;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.InstantaneousTracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.SolveMLAssociation;
import plugins.nchenouard.spot.DetectionResult;

/**
 * Panel to configure the legacy tracker of Genovesio et al., TIP 2006.
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class PanelTracking extends JPanel
{
    private static final long serialVersionUID = -1080899131238029873L;
    SwimmingObjectChooser detectionChooser;

    SingleMotionPanel singleMotionPanel;
    IMMPanel immPanel;
    final String singleMotionString = "Single motion model";
    final String multipleMotionString = "Multiple motion models";
    final JComboBox filtersBox = new JComboBox(new String[] {singleMotionString, multipleMotionString});
    JCheckBox covUpdateBox;

    private SpinnerNumberModel gateFactorModel = new SpinnerNumberModel(3, 0.5, 100, 0.5);
    private JSpinner gateFactorSpinner = new JSpinner(gateFactorModel);
    private SpinnerNumberModel maxConsPredModel = new SpinnerNumberModel(2, 0, 100, 1);
    private JSpinner maxConsPredSpinner = new JSpinner(maxConsPredModel);;
    private JTextField trackGroupNameTF;

    private String runningString = "Stop tracking";
    private String nonRunningString = "Start tracking";
    public JButton trackingStartButton;

    public enum AssociationMethods
    {
        ML, OPTIMAL, GREEDY
    };

    String[] associationNames = new String[] {"Maximum Likelihood", "Optimal association (slower)",
            "Greedy association (faster)"};

    // private JComboBox<String> trackingAssociationBox;

    public PanelTracking()
    {
        GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        // detection
        this.add(new JLabel("Detection source:"), constraints);
        constraints.gridy++;
        detectionChooser = new SwimmingObjectChooser(DetectionResult.class);
        detectionChooser.swimmingPoolChangeEvent(new SwimmingPoolEvent(SwimmingPoolEventType.ELEMENT_REMOVED,
                new SwimmingObject(null)));
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

        filtersBox.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                motionCardLayout.show(motionModelPanel, filtersBox.getSelectedItem().toString());
            }
        });
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
        this.add(maxConsPredSpinner, constraints);
        constraints.gridy++;

        this.add(new JLabel("Gate factor:"), constraints);
        constraints.gridy++;
        this.add(gateFactorSpinner, constraints);
        constraints.gridy++;

        JLabel trackGroupLabel = new JLabel("Name of the output track group");
        this.add(trackGroupLabel, constraints);
        constraints.gridy++;
        trackGroupNameTF = new JTextField("newTracks-1");
        this.add(trackGroupNameTF, constraints);
        constraints.gridy++;

        trackingStartButton = new JButton();
        changeTrackingState(false);
        this.add(trackingStartButton, constraints);
    }

    public void changeTrackingState(boolean isRunning)
    {
        if (isRunning)
            trackingStartButton.setText(runningString);
        else
            trackingStartButton.setText(nonRunningString);
        trackingStartButton.updateUI();
    }

    public DetectionResult getDetectionResults()
    {
        if (detectionChooser.getSelectedObject() != null)
            return (DetectionResult) detectionChooser.getSelectedObject();
        else
            return null;
    }

    public boolean IsErasePreExistingTracks()
    {
        return false;
    }

    public Predictor buildPredictor(int dim)
    {
        if (filtersBox.getSelectedItem().equals(singleMotionString))
            return singleMotionPanel.buildPredictor(dim, covUpdateBox.isSelected());
        else
            return immPanel.buildPredictor(dim, covUpdateBox.isSelected());
    }

    public AssociationMethods getAssociationMethod()
    {
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

    public double getGateFactor()
    {
        return gateFactorModel.getNumber().doubleValue();
    }

    public int getMaxConsPred()
    {
        return maxConsPredModel.getNumber().intValue();
    }

    public Tracker buildTracker(int dim)
    {
        Predictor predictor = buildPredictor(dim);
        AssignProblemSolver assignementSolver = null;
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
        InstantaneousTracker tracker = new InstantaneousTracker(assignementSolver, predictor, true, getGateFactor(),
                getMaxConsPred() + 1);
        return tracker;
    }

    public String getTrackGroupName()
    {
        return trackGroupNameTF.getText();
    }
}
