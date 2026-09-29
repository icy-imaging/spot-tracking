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

import fr.icy.io.xml.XMLUtil;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import plugins.nchenouard.particletracking.MHTparameterSet;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for configuring the model of target existence (perceivability)
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class TargetExistencePanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(TargetExistencePanel.class.getName());

    final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Target existence");

    final protected JSpinner spinnerMeanTrackLength = new JSpinner();

    NumberFormat probaFormat = NumberFormat.getNumberInstance();
    final protected JFormattedTextField confirmationTF = new JFormattedTextField(probaFormat);
    final protected JFormattedTextField terminationTF = new JFormattedTextField(probaFormat);

    public TargetExistencePanel() {
        probaFormat.setMinimumFractionDigits(1);
        probaFormat.setMaximumFractionDigits(12);
        probaFormat.setMaximumIntegerDigits(1);
        probaFormat.setMinimumIntegerDigits(1);

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

        spinnerMeanTrackLength.setModel(new SpinnerNumberModel(MHTparameterSet.defaultMeanTrackLength, 1d, null, 1d));
        final JPanel spinnerPanel = new JPanel();
        spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
        spinnerPanel.add(new JLabel("Expected track length"));
        spinnerPanel.add(spinnerMeanTrackLength);
        c.gridx = 0;
        c.gridy = 0;
        pane.add(spinnerPanel, c);

        confirmationTF.setValue(MHTparameterSet.defaultConfirmationThreshold);
        final JPanel confirmationPanel = new JPanel();
        confirmationPanel.setLayout(new GridLayout(2, 1, 0, 0));
        confirmationPanel.add(new JLabel("Minimum probability of existence for confirmation"));
        confirmationPanel.add(confirmationTF);
        c.gridx = 0;
        c.gridy = 1;
        pane.add(confirmationPanel, c);

        c.gridx = 0;
        c.gridy = 2;
        terminationTF.setValue(MHTparameterSet.defaultTerminationThreshold);
        final JPanel terminationPanel = new JPanel();
        terminationPanel.setLayout(new GridLayout(2, 1, 0, 0));
        terminationPanel.add(new JLabel("Probability of existence threshold for track termination"));
        terminationPanel.add(terminationTF);
        pane.add(terminationPanel, c);

        this.add(pane, BorderLayout.NORTH);
    }

    public double getConfirmationThreshold() throws ParseException {
        return probaFormat.parse(confirmationTF.getText()).doubleValue();
    }

    public double getTerminationThreshold() throws ParseException {
        return probaFormat.parse(terminationTF.getText()).doubleValue();
    }

    public double getMeanTrackLength() {
        return ((SpinnerNumberModel) spinnerMeanTrackLength.getModel()).getNumber().doubleValue();
    }

    // XML import/export

    public void saveToXML(final Node node) {
        final Element nodeDetection = XMLUtil.setElement(node, MHTparameterSet.ROOT_TARGET_EXISTENCE);
        if (nodeDetection != null) {
            XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.TRACK_LENGTH, getMeanTrackLength());
            try {
                final double c = getConfirmationThreshold();
                XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.CONFIRMATION_THRESHOLD, c);
            }
            catch (final Exception e) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Unable to save confirmation threshold to XML.", e);
            }
            try {
                final double c = getTerminationThreshold();
                XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.TERMINATION_THRESHOLD, c);
            }
            catch (final Exception e) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Unable to save termination threshold to XML.", e);
            }
        }
    }

    public void loadFromFromXML(final Node node) {
        final Element nodeDetection = XMLUtil.getElement(node, MHTparameterSet.ROOT_TARGET_EXISTENCE);
        if (nodeDetection != null) {
            spinnerMeanTrackLength.getModel().setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.TRACK_LENGTH, getMeanTrackLength()));
            try {
                confirmationTF.setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.CONFIRMATION_THRESHOLD, getConfirmationThreshold()));
            }
            catch (final ParseException e) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Unable to load confirmation threshold from XML.", e);
            }
            try {
                terminationTF.setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.TERMINATION_THRESHOLD, getTerminationThreshold()));
            }
            catch (final ParseException e) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Unable to load termination threshold from XML.", e);
            }
        }
    }

    public void fillParameterSet(final @NotNull MHTparameterSet parameterSet) {
        try {
            parameterSet.terminationThreshold = getTerminationThreshold();
        }
        catch (final ParseException e) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, "Unable to get termination threshold.", e);
        }
        try {
            parameterSet.confirmationThreshold = getConfirmationThreshold();
        }
        catch (final ParseException e) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, "Unable to get confirmation threshold.", e);
        }
        parameterSet.meanTrackLength = getMeanTrackLength();
    }

    public void setParameters(final @NotNull MHTparameterSet parameterSet) {
        spinnerMeanTrackLength.getModel().setValue(parameterSet.meanTrackLength);
        confirmationTF.setValue(parameterSet.confirmationThreshold);
        terminationTF.setValue(parameterSet.terminationThreshold);
    }
}
