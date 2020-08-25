package plugins.nchenouard.particletracking.legacytracker.gui;

import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import plugins.nchenouard.particletracking.filtering.Predictor;

/**
 * Panel for the configuration of a single motion predictor
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 */

public class SingleMotionPanel extends JPanel
{

    /**
	 * 
	 */
    private static final long serialVersionUID = 1562007105810413917L;
    private int defaultModel = 0;
    private PredictorPanel[] predictorJPs = {new RandomWalkPanel(), new DirectedMotionPanel()};
    private String[] modelNames;
    private JComboBox trackingFilters;
    private final CardLayout predictorCardLayout = new CardLayout();
    JPanel predictorsPanel = new JPanel(predictorCardLayout);

    public SingleMotionPanel()
    {
        GridBagLayout layout = new GridBagLayout();
        this.setLayout(layout);
        modelNames = new String[predictorJPs.length];
        for (int i = 0; i < predictorJPs.length; i++)
        {
            modelNames[i] = predictorJPs[i].getName();
            predictorsPanel.add(predictorJPs[i], predictorJPs[i].getName());
        }
        trackingFilters = new JComboBox(modelNames);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        this.add(new JLabel("Motion type"), constraints);
        constraints.gridy++;

        this.add(trackingFilters, constraints);
        constraints.gridy++;

        setDefaultValues();
        trackingFilters.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                predictorCardLayout.show(predictorsPanel, modelNames[trackingFilters.getSelectedIndex()]);
            }
        });
        this.add(predictorsPanel, constraints);
        constraints.gridy++;
    }

    private void setDefaultValues()
    {
        trackingFilters.setSelectedIndex(defaultModel);
        predictorCardLayout.show(predictorsPanel, modelNames[defaultModel]);
        for (PredictorPanel pp : predictorJPs)
            pp.setDefaultValues();
    }

    public Predictor buildPredictor(int dim, boolean covUpdate)
    {
        return predictorJPs[trackingFilters.getSelectedIndex()].buildPredictor(dim, covUpdate);
    }
}
