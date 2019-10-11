package plugins.nchenouard.particletracking.gui;

import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPoolEvent;
import icy.swimmingPool.SwimmingPoolEventType;
import icy.util.XMLUtil;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.tree.DefaultMutableTreeNode;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import plugins.nchenouard.particletracking.DetectionChooser;
import plugins.nchenouard.particletracking.MHTparameterSet;
import plugins.nchenouard.spot.DetectionResult;

/**
 * Panel for detection results selection
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/
public class DetectionInputPanel extends JPanel
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 2186083246350778294L;
	
	final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Detection source");
	
	final public DetectionChooser detectionChooser = new DetectionChooser();
	final public JSpinner numberFalseDetectionsSpinner = new JSpinner();
	final public JSpinner rateDetectionSpinner = new JSpinner();

	public DetectionInputPanel()
	{
		
		this.setLayout( new BorderLayout() );
		
		JPanel pane = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		pane.setLayout(gbl);
		GridBagConstraints c = new GridBagConstraints();				
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.weighty = 0.0;
		c.gridwidth = 1;
		c.insets = new Insets(2, 2, 2, 2);

		JPanel detectionChooserPanel = new JPanel();
		detectionChooserPanel.setLayout(new GridLayout(2, 1, 0, 0));
		detectionChooserPanel.add(new JLabel("Select the input for detections in the Swimming Pool"));
		detectionChooserPanel.add(detectionChooser);
		c.gridx = 0;
		c.gridy = 0;
		pane.add(detectionChooserPanel, c);

		numberFalseDetectionsSpinner.setModel(new SpinnerNumberModel(new Integer(MHTparameterSet.defaultNumberOfFalseDetections), new Integer(0), null, new Integer(1)));
		JPanel spinnerPanel = new JPanel();
		//spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
		spinnerPanel.setLayout(new GridLayout(2, 1));
		spinnerPanel.add(new JLabel("Expected number of false detections per frame"));
		spinnerPanel.add(numberFalseDetectionsSpinner);
		c.gridx = 0;
		c.gridy = 1;
		pane.add(spinnerPanel, c);

		
		rateDetectionSpinner.setModel(new SpinnerNumberModel(new Double(MHTparameterSet.defaultDetectionRate), new Double(0.01), new Double(1), new Double(0.01)));
		JPanel spinnerDetectionPanel = new JPanel();
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

	public int getNumberOfFalseDetections()
	{
		return ((SpinnerNumberModel)numberFalseDetectionsSpinner.getModel()).getNumber().intValue();
	}
	
	public double getDetectionRate()
	{
		return ((SpinnerNumberModel)rateDetectionSpinner.getModel()).getNumber().doubleValue();
	}

	// XML import/export

	public void saveToXML(Node node)
	{
		final Element nodeDetection = XMLUtil.setElement(node, MHTparameterSet.ROOT_DETECTION_INPUT);
		if (nodeDetection != null)
		{
				Object dr = detectionChooser.getSelectedDetectionResult();
				if ( dr  != null )
					XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, ((DetectionResult) dr).toString());
				else
					XMLUtil.setAttributeValue(nodeDetection, MHTparameterSet.DETECTION_SOURCE, MHTparameterSet.NO_SOURCE);
				XMLUtil.setAttributeIntValue(nodeDetection, MHTparameterSet.FALSE_DETECTION, getNumberOfFalseDetections());
				XMLUtil.setAttributeDoubleValue(nodeDetection,MHTparameterSet.DETECTION_RATE, getDetectionRate());				
		}
	}
	
	public void loadFromFromXML(Node node)
	{
		final Element nodeDetection = XMLUtil.getElement(node, MHTparameterSet.ROOT_DETECTION_INPUT);
		if (nodeDetection != null)
		{
			((SpinnerNumberModel)numberFalseDetectionsSpinner.getModel()).setValue(XMLUtil.getAttributeIntValue(nodeDetection, MHTparameterSet.FALSE_DETECTION, getNumberOfFalseDetections()));
			((SpinnerNumberModel)rateDetectionSpinner.getModel()).setValue(XMLUtil.getAttributeDoubleValue(nodeDetection, MHTparameterSet.DETECTION_RATE, getDetectionRate()));
		}
	}

	public void fillParameterSet(MHTparameterSet parameterSet)
	{
		parameterSet.numberOfFalseDetections = getNumberOfFalseDetections();
		parameterSet.detectionRate = getDetectionRate();
		parameterSet.detectionResults = detectionChooser.getSelectedDetectionResult();
	}

	public void setParameters(MHTparameterSet parameterSet)
	{
		detectionChooser.setSelectedItem(parameterSet.detectionResults);
		((SpinnerNumberModel)numberFalseDetectionsSpinner.getModel()).setValue(parameterSet.numberOfFalseDetections);
		((SpinnerNumberModel)rateDetectionSpinner.getModel()).setValue(parameterSet.detectionRate);	
	}
}
