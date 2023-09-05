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

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * Panel to configure the spatial covariance of predictors
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class FilterCovariancesPanel extends JPanel {
    private final DecimalFormat f1 = new DecimalFormat("000");
    private final double[] trackCov = {3, 3, 1, 150, 150};
    private final JSlider xSens = new JSlider(JSlider.HORIZONTAL, 0, 60, (int) Math.round(trackCov[0]));
    private final JSlider ySens = new JSlider(JSlider.HORIZONTAL, 0, 60, (int) Math.round(trackCov[1]));
    private final JSlider zSens = new JSlider(JSlider.HORIZONTAL, 0, 60, (int) Math.round(trackCov[2]));
    private final JLabel xLab = new JLabel();
    private final JLabel yLab = new JLabel();
    private final JLabel zLab = new JLabel();
    private final JLabel aLab = new JLabel();
    private final JLabel iLab = new JLabel();
    private final JCheckBox icheckB = new JCheckBox("Intensity");
    private final JCheckBox acheckB = new JCheckBox("Area");

    public void setDefaultValues() {
        xSens.setValue((int) Math.round(trackCov[0]));
        ySens.setValue((int) Math.round(trackCov[1]));
        zSens.setValue((int) Math.round(trackCov[2]));
        xLab.setText(f1.format(trackCov[0]));
        yLab.setText(f1.format(trackCov[1]));
        zLab.setText(f1.format(trackCov[2]));
        aLab.setText(f1.format(trackCov[3]));
        iLab.setText(f1.format(trackCov[4]));
        acheckB.setSelected(false);
        icheckB.setSelected(false);
    }

    public FilterCovariancesPanel() {
        final GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        xSens.addChangeListener(arg0 -> xLab.setText(f1.format(xSens.getValue())));
        ySens.addChangeListener(arg0 -> yLab.setText(f1.format(ySens.getValue())));
        zSens.addChangeListener(arg0 -> zLab.setText(f1.format(zSens.getValue())));

        final JPanel jpx = new JPanel();
        jpx.setLayout(new BoxLayout(jpx, BoxLayout.X_AXIS));
        jpx.add(new JLabel("x"));
        jpx.add(xSens);
        jpx.add(xLab);

        final JPanel jpy = new JPanel();
        jpy.setLayout(new BoxLayout(jpy, BoxLayout.X_AXIS));
        jpy.add(new JLabel("y"));
        jpy.add(ySens);
        jpy.add(yLab);

        final JPanel jpz = new JPanel();
        jpz.setLayout(new BoxLayout(jpz, BoxLayout.X_AXIS));
        jpz.add(new JLabel("z"));
        jpz.add(zSens);
        jpz.add(zLab);

        this.add(jpx, constraints);
        constraints.gridy++;
        this.add(jpy, constraints);
        constraints.gridy++;
        this.add(jpz, constraints);
        constraints.gridy++;
    }

    public boolean isAreaSelected() {
        return acheckB.isSelected();
    }

    public boolean isIntensitySelected() {
        return icheckB.isSelected();
    }

    public double getTrackingCovX() {
        return xSens.getValue();
    }

    public double getTrackingCovY() {
        return ySens.getValue();
    }

    public double getTrackingCovZ() {
        return zSens.getValue();
    }
}
