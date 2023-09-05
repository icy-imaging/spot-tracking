/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

import plugins.nchenouard.particletracking.filtering.*;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;

/**
 * Panel to configure and create an Interacting Multiple Model (IMM) predictor
 * which allows managing a mixture of predictors
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class IMMPanel extends PredictorPanel {
    private final FilterCovariancesPanel[] covPanelTab1 = new FilterCovariancesPanel[2];
    private final FilterCovariancesPanel[] covPanelTab2 = new FilterCovariancesPanel[2];

    private final JTextField immInertia = new JTextField(6);
    private final JCheckBox maxLBox = new JCheckBox("Use only most likely model");
    private final DecimalFormat f1 = new DecimalFormat("000");

    private final JComboBox<String> immTypeBox = new JComboBox<>(new String[]{
            "Brownian and directed motion models",
            "Heterogeneous Brownian motion models"
    });

    public IMMPanel() {
        final GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        final GridBagConstraints constraints = new GridBagConstraints();
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

        final JPanel brownianDirectedPanel = new JPanel(new GridLayout(2, 1));
        covPanelTab1[0] = new FilterCovariancesPanel();
        covPanelTab1[1] = new FilterCovariancesPanel();
        final Dimension covPanelSize = covPanelTab1[0].getPreferredSize();

        JPanel panel1 = new JPanel(new BorderLayout());
        panel1.add(covPanelTab1[0], BorderLayout.CENTER);
        panel1.add(new JLabel("Brownian motion covariance"), BorderLayout.NORTH);
        brownianDirectedPanel.add(panel1);

        JPanel panel2 = new JPanel(new BorderLayout());
        panel2.add(covPanelTab1[1], BorderLayout.CENTER);
        panel2.add(new JLabel("Directed motion covariance"), BorderLayout.NORTH);
        brownianDirectedPanel.add(panel2);

        final JScrollPane scrollPane1 = new JScrollPane(brownianDirectedPanel);
        scrollPane1.setPreferredSize(new Dimension((int) (1.2 * covPanelSize.width), (int) (covPanelSize.height * 1.5)));
        immTypePanel.add(scrollPane1, immTypeBox.getItemAt(0));

        final JPanel directedDirectedPanel = new JPanel(new GridLayout(2, 1));
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

        final JScrollPane scrollPane2 = new JScrollPane(directedDirectedPanel);
        scrollPane2.setPreferredSize(new Dimension((int) (1.2 * covPanelSize.width), (int) (covPanelSize.height * 1.5)));
        immTypePanel.add(scrollPane2, immTypeBox.getItemAt(1));

        immTypeBox.addActionListener(e -> immTypeLayout.show(immTypePanel, (String) immTypeBox.getSelectedItem()));
        immTypeLayout.show(immTypePanel, (String) immTypeBox.getSelectedItem());
    }

    @Override
    public Predictor buildPredictor(final int dim) {
        return buildPredictor(dim, false);
    }

    @Override
    public Predictor buildPredictor(final int dim, final boolean covUpdate) {
        if (dim == 3) {
            final ArrayList<Predictor3D> predictors = new ArrayList<>();
            final int selectedIdx = immTypeBox.getSelectedIndex();
            switch (selectedIdx) {
                case 0: {
                    {
                        final KF3dRandomWalk predictor = new KF3dRandomWalk();
                        final double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[0].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        final KF3dDirected predictor = new KF3dDirected();
                        final double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[1].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    break;
                }
                case 1: {
                    {
                        final KF3dRandomWalk predictor = new KF3dRandomWalk();
                        final double[] trackingCovariances = new double[3];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        trackingCovariances[2] = covPanelTab1[0].getTrackingCovZ();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        final KF3dRandomWalk predictor = new KF3dRandomWalk();
                        final double[] trackingCovariances = new double[3];
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
            final double inertia = Double.parseDouble(immInertia.getText());
            return new IMM3D(predictorType, inertia, predictors);
        }
        else {
            final ArrayList<Predictor2D> predictors = new ArrayList<>();
            final int selectedIdx = immTypeBox.getSelectedIndex();
            switch (selectedIdx) {
                case 0: {
                    {
                        final KF2dRandomWalk predictor = new KF2dRandomWalk();
                        final double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        final KF2dDirected predictor = new KF2dDirected();
                        final double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[1].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[1].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    break;
                }
                case 1: {
                    {
                        final KF2dRandomWalk predictor = new KF2dRandomWalk();
                        final double[] trackingCovariances = new double[2];
                        trackingCovariances[0] = covPanelTab1[0].getTrackingCovX();
                        trackingCovariances[1] = covPanelTab1[0].getTrackingCovY();
                        predictor.setTrackingCovariances(trackingCovariances);
                        predictor.setUpdateCovariances(covUpdate);
                        predictors.add(predictor);
                    }
                    {
                        final KF2dRandomWalk predictor = new KF2dRandomWalk();
                        final double[] trackingCovariances = new double[2];
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
            final double inertia = Double.parseDouble(immInertia.getText());
            return new IMM2D(predictorType, inertia, predictors);
        }
    }

    public double getIMMInertia() {
        try {
            return f1.parse(immInertia.getText()).doubleValue();
        }
        catch (final ParseException e) {
            return .8d;
        }
    }

    @Override
    public String getName() {
        return "Interacting Multiple Models";
    }

    @Override
    public void setDefaultValues() {
        for (final FilterCovariancesPanel covPanel : covPanelTab1)
            covPanel.setDefaultValues();
        for (final FilterCovariancesPanel covPanel : covPanelTab2)
            covPanel.setDefaultValues();
        immInertia.setText("0.8");
        maxLBox.setSelected(false);
    }
}