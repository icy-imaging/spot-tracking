package plugins.nchenouard.particletracking.gui;

import icy.util.XMLUtil;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.text.ParseException;

import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import plugins.nchenouard.particletracking.MHTparameterSet;

/**
 * Panel for configuring the model of target existence (perceivability)
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class TargetExistencePanel extends JPanel
{
	private static final long serialVersionUID = 3528013023762686155L;

	final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Target existence");

	final protected JSpinner spinnerMeanTrackLength = new JSpinner();

	NumberFormat probaFormat = NumberFormat.getNumberInstance();
	final protected JFormattedTextField confirmationTF = new JFormattedTextField(probaFormat);
	final protected JFormattedTextField terminationTF = new JFormattedTextField(probaFormat);


	public TargetExistencePanel()
	{
		probaFormat.setMinimumFractionDigits(1);
		probaFormat.setMaximumFractionDigits(12);
		probaFormat.setMaximumIntegerDigits(1);
		probaFormat.setMinimumIntegerDigits(1);

		this.setLayout(new BorderLayout());

		JPanel pane = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		pane.setLayout(gbl);
		GridBagConstraints c = new GridBagConstraints();				
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.gridwidth = 1;
		c.insets = new Insets(2, 2, 2, 2);

		spinnerMeanTrackLength.setModel(new SpinnerNumberModel(MHTparameterSet.defaultMeanTrackLength, new Double(1), null, new Double(1)));
		JPanel spinnerPanel = new JPanel();
		spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
		spinnerPanel.add(new JLabel("Expected track length"));
		spinnerPanel.add(spinnerMeanTrackLength);
		c.gridx = 0;
		c.gridy = 0;
		pane.add(spinnerPanel, c);

		confirmationTF.setValue(MHTparameterSet.defaultConfirmationThreshold);
		JPanel confirmationPanel = new JPanel();
		confirmationPanel.setLayout(new GridLayout(2, 1, 0, 0));
		confirmationPanel.add(new JLabel("Minimum probability of existence for confirmation"));
		confirmationPanel.add(confirmationTF);
		c.gridx = 0;
		c.gridy = 1;
		pane.add(confirmationPanel, c);

		c.gridx = 0;
		c.gridy = 2;
		terminationTF.setValue(MHTparameterSet.defaultTerminationThreshold);
		JPanel terminationPanel = new JPanel();
		terminationPanel.setLayout(new GridLayout(2, 1, 0, 0));
		terminationPanel.add(new JLabel("Probability of existence threshold for track termination"));
		terminationPanel.add(terminationTF);
		pane.add(terminationPanel, c);

		this.add(pane, BorderLayout.NORTH);
	}

	public double getConfirmationThreshold() throws ParseException
	{
		return probaFormat.parse(confirmationTF.getText()).doubleValue();

	}

	public double getTerminationThreshold() throws ParseException
	{
		return probaFormat.parse(terminationTF.getText()).doubleValue();	
	}

	public double getMeanTrackLength()
	{
		return ((SpinnerNumberModel)spinnerMeanTrackLength.getModel()).getNumber().doubleValue();
	}

	// XML import/export

	public void saveToXML(Node node)
	{
		final Element nodeDetection = XMLUtil.setElement(node, MHTparameterSet.ROOT_TARGET_EXISTENCE);
		if (nodeDetection != null)
		{
			XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.TRACK_LENGTH, getMeanTrackLength());
			try{
				double c = getConfirmationThreshold();
				XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.CONFIRMATION_THRESHOLD, c);
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			try{
				double c = getTerminationThreshold();
				XMLUtil.setAttributeDoubleValue(nodeDetection, MHTparameterSet.TERMINATION_THRESHOLD, c);				
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void loadFromFromXML(Node node)
	{
		final Element nodeDetection = XMLUtil.getElement(node, MHTparameterSet.ROOT_TARGET_EXISTENCE);
		if (nodeDetection != null)
		{
			((SpinnerNumberModel)spinnerMeanTrackLength.getModel()).setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.TRACK_LENGTH, getMeanTrackLength()));
			try {
				confirmationTF.setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.CONFIRMATION_THRESHOLD, getConfirmationThreshold()));
			} catch (ParseException e) {
				e.printStackTrace();
			}
			try {
				terminationTF.setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.TERMINATION_THRESHOLD, getTerminationThreshold()));
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
	}

	public void fillParameterSet(MHTparameterSet parameterSet) {
		try {
			parameterSet.terminationThreshold = getTerminationThreshold();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		try {
			parameterSet.confirmationThreshold = getConfirmationThreshold();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		parameterSet.meanTrackLength = getMeanTrackLength();
	}

	public void setParameters(MHTparameterSet parameterSet) {
		((SpinnerNumberModel)spinnerMeanTrackLength.getModel()).setValue(parameterSet.meanTrackLength);
		confirmationTF.setValue(parameterSet.confirmationThreshold);
		terminationTF.setValue(parameterSet.terminationThreshold);
	}
}
