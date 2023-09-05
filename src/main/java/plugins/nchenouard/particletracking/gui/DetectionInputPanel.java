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

package plugins.nchenouard.particletracking.gui;

import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPoolEvent;
import icy.swimmingPool.SwimmingPoolEventType;
import icy.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.nchenouard.particletracking.DetectionChooser;
import plugins.nchenouard.particletracking.MHTparameterSet;
import plugins.nchenouard.spot.DetectionResult;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Panel for detection results selection
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class DetectionInputPanel extends JPanel {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Detection source");

    final public DetectionChooser detectionChooser = new DetectionChooser();
    final public JSpinner numberFalseDetectionsSpinner = new JSpinner();
    final public JSpinner rateDetectionSpinner = new JSpinner();

    public DetectionInputPanel() {

        this.setLayout(new BorderLayout());

        final JPanel pane = new JPanel();
        final GridBagLayout gbl = new GridBagLayout();
        pane.setLayout(gbl);
        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.gridwidth = 1;
        c.insets = new Insets(2, 2, 2, 2);

        final JPanel detectionChooserPanel = new JPanel();
        detectionChooserPanel.setLayout(new GridLayout(2, 1, 0, 0));
        detectionChooserPanel.add(new JLabel("Select the input for detections in the Swimming Pool"));
        detectionChooserPanel.add(detectionChooser);
        c.gridx = 0;
        c.gridy = 0;
        pane.add(detectionChooserPanel, c);

        numberFalseDetectionsSpinner.setModel(new SpinnerNumberModel(MHTparameterSet.defaultNumberOfFalseDetections, 0, null, 1));
        final JPanel spinnerPanel = new JPanel();
        //spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerPanel.setLayout(new GridLayout(2, 1));
        spinnerPanel.add(new JLabel("Expected number of false detections per frame"));
        spinnerPanel.add(numberFalseDetectionsSpinner);
        c.gridx = 0;
        c.gridy = 1;
        pane.add(spinnerPanel, c);


        rateDetectionSpinner.setModel(new SpinnerNumberModel(MHTparameterSet.defaultDetectionRate, .01d, 1d, .01d));
        final JPanel spinnerDetectionPanel = new JPanel();
        //spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerDetectionPanel.setLayout(new GridLayout(2, 1));
        spinnerDetectionPanel.add(new JLabel("Probability of detection for each particle"));
        spinnerDetectionPanel.add(rateDetectionSpinner);
        c.gridx = 0;
        c.gridy = 2;
        pane.add(spinnerDetectionPanel, c);

        detectionChooser.swimmingPoolChangeEvent(new SwimmingPoolEvent(SwimmingPoolEventType.ELEMENT_REMOVED, new SwimmingObject(null)));

        this.add(pane, BorderLayout.NORTH);
    }

    public int getNumberOfFalseDetections() {
        return ((SpinnerNumberModel) numberFalseDetectionsSpinner.getModel()).getNumber().intValue();
    }

    public double getDetectionRate() {
        return ((SpinnerNumberModel) rateDetectionSpinner.getModel()).getNumber().doubleValue();
    }

    // XML import/export

    public void saveToXML(final Node node) {
        final Element nodeDetection = XMLUtil.setElement(node, MHTparameterSet.ROOT_DETECTION_INPUT);
        if (nodeDetection != null) {
            final DetectionResult dr = detectionChooser.getSelectedDetectionResult();
            if (dr != null)
                XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, dr.toString());
            else
                XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, MHTparameterSet.NO_SOURCE);
            XMLUtil.setAttributeIntValue(nodeDetection, MHTparameterSet.FALSE_DETECTION, getNumberOfFalseDetections());
            XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.DETECTION_RATE, getDetectionRate());
        }
    }

    public void loadFromFromXML(final Node node) {
        final Element nodeDetection = XMLUtil.getElement(node, MHTparameterSet.ROOT_DETECTION_INPUT);
        if (nodeDetection != null) {
            numberFalseDetectionsSpinner.getModel().setValue(XMLUtil.getAttributeIntValue(nodeDetection, MHTparameterSet.FALSE_DETECTION, getNumberOfFalseDetections()));
            rateDetectionSpinner.getModel().setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.DETECTION_RATE, getDetectionRate()));
        }
    }

    public void fillParameterSet(final MHTparameterSet parameterSet) {
        parameterSet.numberOfFalseDetections = getNumberOfFalseDetections();
        parameterSet.detectionRate = getDetectionRate();
        parameterSet.detectionResults = detectionChooser.getSelectedDetectionResult();
    }

    public void setParameters(final MHTparameterSet parameterSet) {
        detectionChooser.setSelectedItem(parameterSet.detectionResults);
        numberFalseDetectionsSpinner.getModel().setValue(parameterSet.numberOfFalseDetections);
        rateDetectionSpinner.getModel().setValue(parameterSet.detectionRate);
    }
}
