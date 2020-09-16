package plugins.nchenouard.particletracking.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Panel describing the tracking method
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class TrackingDescriptionPanel extends JPanel
{	
	private static final long serialVersionUID = -5923713245322492089L;
	final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Tracking");
	
	JTextArea methodDescription = new JTextArea("This plugin implements the Multiple Hypothesis Tracking algorithm described in:\n Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin, Multiple Hypothesis Tracking for Cluttered Biological Image Sequences, IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013.\n Pubmed link: : http://www.ncbi.nlm.nih.gov/pubmed/23689865. \nPlease reference and cite properly.");
	final protected JSpinner numberNewTrackSpinner = new JSpinner();
	final protected JSpinner numberInitialTrackSpinner = new JSpinner();
	final protected JSpinner spinnerMeanTrackLength = new JSpinner();
	JSpinner spinnerXYDisplacement = new JSpinner();

	public TrackingDescriptionPanel()
	{
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

		c.gridx = 0;
		c.gridy = 0;
		JPanel textPanel = new JPanel();
		textPanel.setLayout(new GridLayout(1, 1, 0, 0));
		methodDescription.setLineWrap(true);
		textPanel.add(methodDescription);
		methodDescription.setEditable(false);
		textPanel.setBorder(new TitledBorder("Reference"));
		pane.add(textPanel, c);

		
		JPanel quickSettingsPanel = new JPanel();
		quickSettingsPanel.setLayout(new GridLayout(4, 1, 0, 0));
		
		numberNewTrackSpinner.setModel(new SpinnerNumberModel(new Double(20), new Double(0), null, new Double(1)));
		JPanel spinnerNewTrackPanel = new JPanel();
		spinnerNewTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
		spinnerNewTrackPanel.add(new JLabel("Expected number of new tracks per frame"));
		spinnerNewTrackPanel.add(numberNewTrackSpinner);
		quickSettingsPanel.add(spinnerNewTrackPanel);
		
		numberInitialTrackSpinner.setModel(new SpinnerNumberModel(new Double(20), new Double(0), null, new Double(1)));
		JPanel spinnerInitialTrackPanel = new JPanel();
		spinnerInitialTrackPanel.setLayout(new GridLayout(2, 1, 0, 0));
		spinnerInitialTrackPanel.add(new JLabel("Expected number of objects in the first frame"));
		spinnerInitialTrackPanel.add(numberInitialTrackSpinner);
		quickSettingsPanel.add(spinnerInitialTrackPanel);
		
		spinnerMeanTrackLength.setModel(new SpinnerNumberModel(new Double(20), new Double(1), null, new Double(1)));
		JPanel spinnerPanel = new JPanel();
		spinnerPanel.setLayout(new GridLayout(2, 1, 0, 0));
		spinnerPanel.add(new JLabel("Expected track length"));
		spinnerPanel.add(spinnerMeanTrackLength);
		quickSettingsPanel.add(spinnerPanel);
		
		spinnerXYDisplacement.setModel(new SpinnerNumberModel(new Double(4), new Double(0.5), null, new Double(0.5)));
		JPanel displacementPanel = new JPanel();
		displacementPanel.setLayout(new GridLayout(2, 1, 0, 0));
		displacementPanel.add(new JLabel("Average length of displacement between two frames"));
		displacementPanel.add(spinnerXYDisplacement);
		quickSettingsPanel.add(displacementPanel);
		
		quickSettingsPanel.setBorder(new TitledBorder("Quick settings"));
		
		c.gridx = 0;
		c.gridy = 1;
		pane.add(quickSettingsPanel, c);

		this.add(pane, BorderLayout.NORTH);
	}
}
