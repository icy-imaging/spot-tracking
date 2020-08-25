package plugins.nchenouard.particletracking.legacytracker.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import plugins.nchenouard.particletracking.filtering.IMM2D;
import plugins.nchenouard.particletracking.filtering.IMM3D;
import plugins.nchenouard.particletracking.filtering.KF2dDirected;
import plugins.nchenouard.particletracking.filtering.KF2dRandomWalk;
import plugins.nchenouard.particletracking.filtering.KF3dDirected;
import plugins.nchenouard.particletracking.filtering.KF3dRandomWalk;
import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.filtering.Predictor2D;
import plugins.nchenouard.particletracking.filtering.Predictor3D;

/**
 * Panel to configure and create an Interacting Multiple Model (IMM) predictor
 * which allows managing a mixture of predictors
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 */

public class IMMPanel extends PredictorPanel
{

    private static final long serialVersionUID = -6006993674059346298L;
    private FilterCovariancesPanel[] covPanelTab1 = new FilterCovariancesPanel[2];
    private FilterCovariancesPanel[] covPanelTab2 = new FilterCovariancesPanel[2];

    private String name = "Interacting Multiple Models";
    private JTextField immInertia = new JTextField(6);
    private JCheckBox maxLBox = new JCheckBox("Use only most likely model");
    private DecimalFormat f1 = new DecimalFormat("000");

    private JComboBox immTypeBox = new JComboBox(new String[] {"Brownian and directed motion models",
            "Heterogeneous Brownian motion models"});

    public IMMPanel()
    {
        GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        this.add(new JLabel("Inertia:"), constraints);
        constraints.gridy++;

        this.add(immInertia, constraints);
        constraints.gridy++;

        this.add(maxLBox, constraints);
        constraints.gridy++;
        maxLBox.setSelected(false);

        this.add(immTypeBox, constraints);
        constraints.gridy++;

        final CardLayout immTypeLayout = new CardLayout();
        final JPanel immTypePanel = new JPanel(immTypeLayout);
        this.add(immTypePanel, constraints);
        constraints.gridy++;

        JPanel brownianDirectedPanel = new JPanel(new GridLayout(2, 1));
        covPanelTab1[0] = new FilterCovariancesPanel();
        covPanelTab1[1] = new FilterCovariancesPanel();
        Dimension covPanelSize = covPanelTab1[0].getPreferredSize();

        JPanel panel1 = new JPanel(new BorderLayout());
        panel1.add(covPanelTab1[0], BorderLayout.CENTER);
        panel1.add(new JLabel("Brownian motion covariance"), BorderLayout.NORTH);
        brownianDirectedPanel.add(panel1);

        JPanel panel2 = new JPanel(new BorderLayout());
        panel2.add(covPanelTab1[1], BorderLayout.CENTER);
        panel2.add(new JLabel("Directed motion covariance"), BorderLayout.NORTH);
        brownianDirectedPanel.add(panel2);

        JScrollPane scrollPane1 = new JScrollPane(brownianDirectedPanel);
        scrollPane1
                .setPreferredSize(new Dimension((int) (1.2 * covPanelSize.width), (int) (covPanelSize.height * 1.5)));
        immTypePanel.add(scrollPane1, immTypeBox.getItemAt(0).toString());

        JPanel directedDirectedPanel = new JPanel(new GridLayout(2, 1));
        covPanelTab2[0] = new FilterCovariancesPanel();
        covPanelTab2[1] = new FilterCovariancesPanel();

        panel1 = new JPanel(new BorderLayout());
        panel1.add(covPanelTab2[0], BorderLayout.CENTER);
        panel1.add(new JLabel("Brownian motion 1 covariance"), BorderLayout.NORTH);
        directedDirectedPanel.add(panel1);

        panel2 = new JPanel(new BorderLayout());
        panel2.add(covPanelTab2[1], BorderLayout.CENTER);
        panel2.add(new JLabel("Brownian motion 2 covariance"), BorderLayout.NORTH);
        directedDirectedPanel.add(panel2);

        JScrollPane scrollPane2 = new JScrollPane(directedDirectedPanel);
        scrollPane2
                .setPreferredSize(new Dimension((int) (1.2 * covPanelSize.width), (int) (covPanelSize.height * 1.5)));
        immTypePanel.add(scrollPane2, immTypeBox.getItemAt(1).toString());

        immTypeBox.addActionListener(new ActionListener()
        {

            @Override
            public void actionPerformed(ActionEvent e)
            {
                immTypeLayout.show(immTypePanel, immTypeBox.getSelectedItem().toString());
            }
        });
        immTypeLayout.show(immTypePanel, immTypeBox.getSelectedItem().toString());
    }

    public Predictor buildPredictor(int dim)
    {
        return buildPredictor(dim, false);
    }

    @Override
    public Predictor buildPredictor(int dim, boolean covUpdate)
    {
        if (dim == 3)
        {
            ArrayList<Predictor3D> predictors = new ArrayList<Predictor3D>();
            int selectedIdx = immTypeBox.getSelectedIndex();
            switch (selectedIdx)
            {
                case 0:
                {
                    {
                        KF3dRandomWalk predictor = new KF3dRandomWalk();
                        double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[0].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        KF3dDirected predictor = new KF3dDirected();
                        double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[1].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    break;
                }
                case 1:
                {
                    {
                        KF3dRandomWalk predictor = new KF3dRandomWalk();
                        double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[0].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        KF3dRandomWalk predictor = new KF3dRandomWalk();
                        double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[1].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                }
            }
            IMM3D.LikelihoodTypes predictorType = IMM3D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
            if (maxLBox.isSelected())
                predictorType = IMM3D.LikelihoodTypes.IMM_MAX_LIKELIHOOD;
            double inertia = Double.parseDouble(immInertia.getText());
            IMM3D immFilter = new IMM3D(predictorType, inertia, predictors);
            return immFilter;
        }
        else
        {
            ArrayList<Predictor2D> predictors = new ArrayList<Predictor2D>();
            int selectedIdx = immTypeBox.getSelectedIndex();
            switch (selectedIdx)
            {
                case 0:
                {
                    {
                        KF2dRandomWalk predictor = new KF2dRandomWalk();
                        double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        KF2dDirected predictor = new KF2dDirected();
                        double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    break;
                }
                case 1:
                {
                    {
                        KF2dRandomWalk predictor = new KF2dRandomWalk();
                        double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        KF2dRandomWalk predictor = new KF2dRandomWalk();
                        double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                }
            }
            IMM2D.LikelihoodTypes predictorType = IMM2D.LikelihoodTypes.IMM_WEIGHTED_LIKELIHOOD;
            if (maxLBox.isSelected())
                predictorType = IMM2D.LikelihoodTypes.IMM_MAX_LIKELIHOOD;
            double inertia = Double.parseDouble(immInertia.getText());
            IMM2D immFilter = new IMM2D(predictorType, inertia, predictors);
            return immFilter;
        }
    }

    public double getIMMInertia()
    {
        try
        {
            return f1.parse(immInertia.getText()).doubleValue();
        }
        catch (ParseException e)
        {
            return 0.8;
        }
    }

    public String getName()
    {
        return name;
    }

    public void setDefaultValues()
    {
        for (FilterCovariancesPanel covPanel : covPanelTab1)
            covPanel.setDefaultValues();
        for (FilterCovariancesPanel covPanel : covPanelTab2)
            covPanel.setDefaultValues();
        immInertia.setText("0.8");
        maxLBox.setSelected(false);
    }
}