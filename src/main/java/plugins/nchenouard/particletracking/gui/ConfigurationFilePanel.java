package plugins.nchenouard.particletracking.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Panel for parameter file import and export
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class ConfigurationFilePanel extends JPanel
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -1624230514111837892L;
	final DefaultMutableTreeNode node = new DefaultMutableTreeNode("Configuration file");

	public JButton exportConfigurationFileButton = new JButton("Save the current configuration");
	public JButton loadConfigurationFileButton = new JButton("Load a configuration file");
//	public JButton runMultipleConfigurationFileButton = new JButton("Run tracking for multiple configuration files");

	public ConfigurationFilePanel()
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
		pane.add(exportConfigurationFileButton, c);
		
		c.gridx = 0;
		c.gridy = 1;
		pane.add(loadConfigurationFileButton, c);
		
//		c.gridx = 0;
//		c.gridy = 2;
//		pane.add(runMultipleConfigurationFileButton, c);
		
		this.add(pane, BorderLayout.NORTH);
	}
}
