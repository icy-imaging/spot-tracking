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

import icy.system.IcyExceptionHandler;
import icy.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.nchenouard.particletracking.MHTparameterSet;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Panel for general MHT parameters control
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class MHTPanel extends JPanel {
    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Tracking algorithm");

    final protected NumberFormat gateFactorFormat = NumberFormat.getNumberInstance();
    final protected JFormattedTextField gateFactorTF = new JFormattedTextField(gateFactorFormat);

    final protected JSpinner numberNewTrackSpinner = new JSpinner();
    final protected JSpinner numberInitialTrackSpinner = new JSpinner();
    final protected JSpinner spinnerMHTDepth = new JSpinner();

    public MHTPanel() {
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


        numberNewTrackSpinner.setModel(new SpinnerNumberModel(MHTparameterSet.defaultNumberNewObjects, 0d, null, 1d));
        final JPanel spinnerNewTrackPanel = new JPanel();
        spinnerNewTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerNewTrackPanel.add(new JLabel("Expected number of new objects per frame"));
        spinnerNewTrackPanel.add(numberNewTrackSpinner);
        c.gridx = 0;
        c.gridy = 0;
        pane.add(spinnerNewTrackPanel, c);

        numberInitialTrackSpinner.setModel(new SpinnerNumberModel(MHTparameterSet.defaultNumberInitialObjects, 0d, null, 1d));
        final JPanel spinnerInitialTrackPanel = new JPanel();
        spinnerInitialTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerInitialTrackPanel.add(new JLabel("Expected number of objects in the first frame"));
        spinnerInitialTrackPanel.add(numberInitialTrackSpinner);
        c.gridx = 0;
        c.gridy = 1;
        pane.add(spinnerInitialTrackPanel, c);

        spinnerMHTDepth.setModel(new SpinnerNumberModel(MHTparameterSet.defaultMHTDepth, 1, 8, 1));
        final JPanel spinnerDepthPanel = new JPanel();
        spinnerDepthPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerDepthPanel.add(new JLabel("Depth of the track trees"));
        spinnerDepthPanel.add(spinnerMHTDepth);
        c.gridx = 0;
        c.gridy = 2;
        pane.add(spinnerDepthPanel, c);

        gateFactorFormat.setMinimumFractionDigits(0);
        gateFactorFormat.setMaximumFractionDigits(12);
        gateFactorFormat.setMinimumIntegerDigits(0);
        gateFactorFormat.setMaximumIntegerDigits(12);
        gateFactorTF.setValue(MHTparameterSet.defaultGateFactor);
        final JPanel gateFactorPanel = new JPanel();
        gateFactorPanel.setLayout(new GridLayout(2, 1, 0, 0));
        gateFactorPanel.add(new JLabel("Gate factor for association"));
        gateFactorPanel.add(gateFactorTF);
        c.gridx = 0;
        c.gridy = 3;
        pane.add(gateFactorPanel, c);

        this.add(pane, BorderLayout.NORTH);
    }

    public double getGateFactor() throws ParseException {
        return gateFactorFormat.parse(gateFactorTF.getText()).doubleValue();
    }

    public double getNumberInitialTracks() {
        return ((SpinnerNumberModel) numberInitialTrackSpinner.getModel()).getNumber().doubleValue();
    }

    public int getMHTDepth() {
        return ((SpinnerNumberModel) spinnerMHTDepth.getModel()).getNumber().intValue();
    }

    public double getNumbeNewTracks() {
        return ((SpinnerNumberModel) numberNewTrackSpinner.getModel()).getNumber().doubleValue();
    }

    // XML import/export

    public void saveToXML(final Node node) {
        final Element nodeMHT = XMLUtil.setElement(node, MHTparameterSet.ROOT_MHT);
        if (nodeMHT != null) {
            XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_NEW_OBJECTS, getNumbeNewTracks());
            XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.NUMBER_OBJECTS_FIRST_FRAME, getNumberInitialTracks());
            XMLUtil.setAttributeIntValue(nodeMHT, MHTparameterSet.MHT_DEPTH, getMHTDepth());
            try {
                XMLUtil.setAttributeDoubleValue(nodeMHT, MHTparameterSet.GATE_FACTOR, getGateFactor());
            }
            catch (final ParseException e) {
                IcyExceptionHandler.showErrorMessage(e, true);
            }
        }
    }

    public void loadFromFromXML(final Node node) {
        final Element nodeDetection = XMLUtil.getElement(node, MHTparameterSet.ROOT_MHT);
        if (nodeDetection != null) {
            try {
                gateFactorTF.setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.GATE_FACTOR, getGateFactor()));
            }
            catch (final ParseException e) {
                IcyExceptionHandler.showErrorMessage(e, true);
            }
            numberNewTrackSpinner.getModel().setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.NUMBER_NEW_OBJECTS, getNumbeNewTracks()));
            numberInitialTrackSpinner.getModel().setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.NUMBER_OBJECTS_FIRST_FRAME, getNumberInitialTracks()));
            spinnerMHTDepth.getModel().setValue(XMLUtil.getAttributeIntValue(nodeDetection, MHTparameterSet.MHT_DEPTH, getMHTDepth()));
        }
    }

    public void fillParameterSet(final MHTparameterSet parameterSet) {
        parameterSet.numberNewObjects = getNumbeNewTracks();
        parameterSet.numberInitialObjects = getNumberInitialTracks();
        parameterSet.mhtDepth = getMHTDepth();
        try {
            parameterSet.gateFactor = getGateFactor();
        }
        catch (final ParseException e) {
            IcyExceptionHandler.showErrorMessage(e, true);
        }
    }

    public void setParameters(final MHTparameterSet parameterSet) {
        gateFactorTF.setValue(parameterSet.gateFactor);
        numberNewTrackSpinner.getModel().setValue(parameterSet.numberNewObjects);
        numberInitialTrackSpinner.getModel().setValue(parameterSet.numberInitialObjects);
        spinnerMHTDepth.getModel().setValue(parameterSet.mhtDepth);
    }
}
