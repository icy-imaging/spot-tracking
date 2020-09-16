package plugins.nchenouard.particletracking.gui;

import icy.util.XMLUtil;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.tree.DefaultMutableTreeNode;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import plugins.nchenouard.particletracking.MHTparameterSet;


/**
 * Panel for setting the output of the Spot Tracking Plugin to the Track Manager plugin
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class TracksOutputPanel extends JPanel
{
	private static final long serialVersionUID = -7161218710138752173L;
	final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Output");

	final protected JTextField trackGroupTF = new JTextField();

	public TracksOutputPanel()
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
		JPanel trackGroupPanel = new JPanel();
		trackGroupPanel.setLayout(new GridLayout(2, 1, 0, 0));
		trackGroupPanel.add(new JLabel("Name of the group of tracks that is generated:"));
		trackGroupTF.setText("mhtTracks-Run1");
		trackGroupPanel.add(trackGroupTF);
		pane.add(trackGroupPanel, c);

		this.add(pane, BorderLayout.NORTH);
	}

	// XML import/export

	public void saveToXML(Node node)
	{
		final Element nodeOutput = XMLUtil.setElement(node, MHTparameterSet.ROOT_OUTPUT);
		if (nodeOutput != null)
		{
			XMLUtil.setAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, trackGroupTF.getText());
		}
	}

	public void loadFromFromXML(Node node)
	{
		final Element nodeOutput = XMLUtil.getElement(node, MHTparameterSet.ROOT_OUTPUT);
		if (nodeOutput != null)
		{
			trackGroupTF.setText(XMLUtil.getAttributeValue(nodeOutput, MHTparameterSet.TRACK_GROUP_NAME, MHTparameterSet.defaultTrackGroupName));
		}
	}

	public void fillParameterSet(MHTparameterSet parameterSet) {
		parameterSet.trackGroupName = trackGroupTF.getText();
	}

	public void setParameters(MHTparameterSet parameterSet) {
		trackGroupTF.setText(parameterSet.trackGroupName);
	}
}
