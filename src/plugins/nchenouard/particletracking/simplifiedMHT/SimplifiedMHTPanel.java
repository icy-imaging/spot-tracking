package plugins.nchenouard.particletracking.simplifiedMHT;

import icy.gui.frame.progress.AnnounceFrame;
import icy.main.Icy;
import icy.plugin.PluginDescriptor;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPoolEvent;
import icy.swimmingPool.SwimmingPoolEventType;
import icy.swimmingPool.SwimmingPoolListener;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import plugins.fab.spotDetector.SpotDetector;
import plugins.fab.trackmanager.TrackGroup;
import plugins.nchenouard.particletracking.DetectionChooser;
import plugins.nchenouard.particletracking.MHTparameterSet;
import plugins.nchenouard.particletracking.DetectionChooser.DetectionResultChooserListener;
import plugins.nchenouard.spot.DetectionResult;


/** 
 * Simplified Graphical interface for configuring the MHT tracking algorithm.
 *  
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 * */

public class SimplifiedMHTPanel extends JPanel
{
	private static final long serialVersionUID = 7516111529309828176L;

	public DetectionChooser detectionChooser;
	JEditorPane detectionDescriptionPanel;
	JLabel detectionIcon;
	
	JEditorPane parametersDescriptionPanel;
	public JButton estimateParametersButton;
	public JButton loadParametersButton;
	JLabel parametersIcon;
	AnnounceFrame defaultParametersAnnouce = null;
	boolean usingDefaultParameters = true;
	
	JEditorPane outputDescriptionPanel;
	JLabel outputIcon;
	public JTextField trackGroupNameTF;
	AnnounceFrame outputAnnouce = null;
	
	JLabel runIncon;
	public JButton runTrackingButton;
	JEditorPane runDescriptionPanel;
		
	boolean areDetectionSettingsOk = false;
	boolean areTrackingSettingsOk = false;
	boolean areOutputSettingsOk = false;
	
	public MHTparameterSet mhtParameterSet = null;
	
	public SimplifiedMHTPanel()
	{
		this(null, null, null, null);
	}
	
	SpotDetector s;
	
	public SimplifiedMHTPanel(ImageIcon detectionIconImage, ImageIcon parametersIconImage, ImageIcon outputIconImage, ImageIcon runIconImage)
	{
		this.setLayout(new GridBagLayout());
		int h = 0;
		if (detectionIconImage != null)
			h += detectionIconImage.getIconHeight();
		if (parametersIconImage != null)
			h += parametersIconImage.getIconHeight();
		if (outputIconImage != null)
			h += outputIconImage.getIconHeight();
		if (runIconImage != null)
			h += runIconImage.getIconHeight();
		if (h > 0)
			this.setPreferredSize(new Dimension(400, h + 70));
		else
			this.setPreferredSize(new Dimension(400, 450));

		GridBagConstraints gcMain = new GridBagConstraints();
		gcMain.fill = GridBagConstraints.BOTH;
		gcMain.gridwidth = 1;
		gcMain.gridx = 0;
		gcMain.gridy = 0;
				
		// Detection panel
		
		JPanel westDetectionPanel = new JPanel(new BorderLayout());
		gcMain.gridx = 0;
		gcMain.weightx = 0.2;
		this.add(westDetectionPanel, gcMain);
		detectionIcon = new JLabel("", detectionIconImage, JLabel.CENTER);
		westDetectionPanel.add(detectionIcon, BorderLayout.CENTER);
		if (detectionIconImage != null)
			westDetectionPanel.setPreferredSize(new Dimension(detectionIconImage.getIconWidth(), detectionIconImage.getIconWidth()));
		
		JPanel eastDetectionPanel = new JPanel(new BorderLayout());
		gcMain.gridx = 1;
		gcMain.weightx = 0.8;
		this.add(eastDetectionPanel, gcMain);
		detectionDescriptionPanel = new JEditorPane();
		detectionDescriptionPanel.setContentType("text/html");
		detectionDescriptionPanel.setEditable(false);
		detectionDescriptionPanel.setText("The particle tracking method links through time a set of spatial locations (detections). First <strong>create a detection set</strong> using the <a href=http://icy.bioimageanalysis.org/plugin/Spot_Detector>Spot Detector plugin</a> and then select results in the box below.");
		detectionDescriptionPanel.setEditable(false);
		//mainDescriptionPanel.setPreferredSize(new Dimension(400, 80));
		eastDetectionPanel.add(detectionDescriptionPanel, BorderLayout.CENTER);
		
		JPanel eastSouthDetectionPanel = new JPanel(new GridLayout(2, 1));
		eastDetectionPanel.add(eastSouthDetectionPanel, BorderLayout.SOUTH);

		JButton launchDetectorButton = new JButton("Run the Spot Detector plugin");
		launchDetectorButton.setToolTipText("Runs the Spot Detector plugin in order to create a set of locations for spot targets over the whole image sequence.");
		launchDetectorButton.addActionListener(new ActionListener() {			
			@Override
			public void actionPerformed(ActionEvent arg0) {
				PluginDescriptor plugin = PluginLoader.getPlugin("plugins.fab.spotDetector.SpotDetector");
				if (plugin != null) PluginLauncher.start(plugin);
				else
					JOptionPane.showMessageDialog(detectionDescriptionPanel,
						    "The Spot Detector plugin is missing.\n Please install it first through the ICY interface for online plugin browsing.",
						    "Missing plugin error",
						    JOptionPane.ERROR_MESSAGE);
//			PluginLauncher.start("plugins.fab.spotDetector.SpotDetector");
			}
		});
		eastSouthDetectionPanel.add(launchDetectorButton);

		detectionChooser = new DetectionChooser();
		detectionChooser.setToolTipText("<html>The tracking algorithm requires a set of detections (locations of targets) through time to proceed.<br> Use the plugin 'Spot Detector' and then select the results in this box.</html>");
		detectionChooser.addListener(new DetectionResultChooserListener() {			
			@Override
			public void DetectionResultChanged(DetectionResult detectionResult) {
				refreshPanelStatus();
			}
		});
		detectionChooser.swimmingPoolChangeEvent(new SwimmingPoolEvent(SwimmingPoolEventType.ELEMENT_REMOVED, new SwimmingObject(null)));
		eastSouthDetectionPanel.add(detectionChooser);
		
		
		// parameters panel
		
		gcMain.weightx = 1;
		gcMain.gridx = 0;
		gcMain.gridwidth = 2;
		gcMain.gridy++;
		this.add(new JSeparator(JSeparator.HORIZONTAL), gcMain);
		gcMain.gridwidth = 1;

		
		GridBagConstraints gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.gridwidth = 1;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;		

		JPanel westParameterPanel = new JPanel(new BorderLayout());
		parametersIcon = new JLabel("", parametersIconImage, JLabel.CENTER);
		westParameterPanel.add(parametersIcon, BorderLayout.CENTER);
		if (parametersIconImage != null)
			westParameterPanel.setPreferredSize(new Dimension(parametersIconImage.getIconWidth(), parametersIconImage.getIconWidth()));
	
		
		gcMain.gridx = 0;
		gcMain.gridy ++;
		gcMain.weightx = 0.2;
		this.add(westParameterPanel, gcMain);
		
		JPanel eastParameterPanel = new JPanel(new GridBagLayout());
		gcMain.gridx = 1;
		gcMain.weightx = 0.8;
		this.add(eastParameterPanel, gcMain);
		gc.gridy = 0;
		gc.gridwidth = 2;
		parametersDescriptionPanel = new JEditorPane();
		parametersDescriptionPanel.setContentType("text/html");
		parametersDescriptionPanel.setEditable(false);
//		parametersDescriptionPanel.setText("The Multiple Hypothesis Tracking (MHT) method link detections through time to form tracks. For its parameters, either:" +
//				"<ul><li>run the automatic parameter estimation procedure</li><li>load an existing parameter configuration file</li></ul><");
		parametersDescriptionPanel.setText("The Multiple Hypothesis Tracking (MHT) method link detections through time to form tracks. It requires a number of parameters to proceed. Please, either:");
		parametersDescriptionPanel.setEditable(false);
//		parametersPanel.setPreferredSize(new Dimension(400, 100));
		eastParameterPanel.add(parametersDescriptionPanel, gc);
		gc.gridy++;
		
		gc.weightx = 0.5;
		gc.gridwidth = 1;
		estimateParametersButton = new JButton("Estimate parameters");
		estimateParametersButton.setToolTipText("<html>Automatically get an estimation of the parameters for the tracking algorithm.<br>The estimation is rough, hence performance may not be optimal.<br>Switch to the advanced inteface for complete control of the parameterss.</html>");
		eastParameterPanel.add(estimateParametersButton, gc);
		
		gc.gridx = 1;

		loadParametersButton = new JButton("Load existing parameters");
		loadParametersButton.setToolTipText("Load a set of parameters saved from a previous session in a XML file.");
		eastParameterPanel.add(loadParametersButton, gc);
		gc.weightx = 0;
		
		// output panel
		
		gcMain.weightx = 1;
		gcMain.gridx = 0;
		gcMain.gridwidth = 2;
		gcMain.gridy++;
		this.add(new JSeparator(JSeparator.HORIZONTAL), gcMain);
		gcMain.gridwidth = 1;
		
		JPanel westOutPutPanel = new JPanel(new BorderLayout());
		outputIcon = new JLabel("", outputIconImage, JLabel.CENTER);
		westOutPutPanel.add(outputIcon, BorderLayout.CENTER);
		if (outputIconImage != null)
			westOutPutPanel.setPreferredSize(new Dimension(outputIconImage.getIconWidth(), outputIconImage.getIconWidth()));
	
		gcMain.gridx = 0;
		gcMain.gridy++;
		gcMain.weightx = 0.2;
		this.add(westOutPutPanel, gcMain);
		
		JPanel eastOutputPanel = new JPanel(new BorderLayout());
		gcMain.gridx = 1;
		gcMain.weightx = 0.8;
		this.add(eastOutputPanel, gcMain);
		
		outputDescriptionPanel = new JEditorPane();
		outputDescriptionPanel.setContentType("text/html");
		outputDescriptionPanel.setEditable(false);
		outputDescriptionPanel.setText("Tracking results will be automatically exported to the  <a href=http://icy.bioimageanalysis.org/plugin/Spot_Detector>TrackManager</a> plugin from where they can be analyzed and saved.</p><p>Choose a name for the results:");
		outputDescriptionPanel.setEditable(false);
//		outputPanel.setPreferredSize(new Dimension(400, 90));
		eastOutputPanel.add(outputDescriptionPanel, BorderLayout.CENTER);
		
		trackGroupNameTF = new JTextField("MHT-tracks-1");
		trackGroupNameTF.setToolTipText("<html>Name of the tracking results that will be exported to the 'SwimmingPool' of ICY<br> and will be accessible through the 'TrackManager' plugin for analysis and export.<br>The name needs to be different from that of the already existing results.</html>");
		trackGroupNameTF.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent arg0) {
			}
			
			@Override
			public void keyReleased(KeyEvent arg0) {
				refreshPanelStatus();
			}
			
			@Override
			public void keyPressed(KeyEvent arg0) {
			}
		});
		eastOutputPanel.add(trackGroupNameTF, BorderLayout.SOUTH);
		Icy.getMainInterface().getSwimmingPool().addListener(new SwimmingPoolListener() {			
			@Override
			public void swimmingPoolChangeEvent(SwimmingPoolEvent event) {
				refreshPanelStatus();
			}
		});
		
		// run
		gcMain.weightx = 1;
		gcMain.gridx = 0;
		gcMain.gridwidth = 2;
		gcMain.gridy++;
		this.add(new JSeparator(JSeparator.HORIZONTAL), gcMain);
		gcMain.gridwidth = 1;
		
		JPanel westRunPanel = new JPanel(new BorderLayout());
		runIncon = new JLabel("", runIconImage, JLabel.CENTER);
		westRunPanel.add(runIncon, BorderLayout.CENTER);
		if (runIconImage != null)
			westRunPanel.setPreferredSize(new Dimension(runIconImage.getIconWidth(), runIconImage.getIconWidth()));
		gcMain.gridy++;
		gcMain.gridx = 0;
		gcMain.weightx = 0.2;
		this.add(westRunPanel, gcMain);
		
		JPanel eastRunPanel = new JPanel(new BorderLayout());
		gcMain.gridx = 1;
		gcMain.weightx = 0.8;
		this.add(eastRunPanel, gcMain);
		
		runDescriptionPanel = new JEditorPane();
		runDescriptionPanel.setContentType("text/html");
		runDescriptionPanel.setEditable(false);
		runDescriptionPanel.setText("<p>Press 'Run tracking' to proceed with the <strong>track extraction</strong> process (this can take from a few seconds to several minutes).");
		runDescriptionPanel.setEditable(false);
		//runPanel.setPreferredSize(new Dimension(400, 90));
		eastRunPanel.add(runDescriptionPanel, BorderLayout.CENTER);
		
		runTrackingButton = new JButton("Run tracking");
		runTrackingButton.setToolTipText("<html>Compute the set of optimal track with respect to the set of detections and parameters.<br>This may take from few seconds to several minutes depending of the size of the data and the parameters.</html>");
		eastRunPanel.add(runTrackingButton, BorderLayout.SOUTH);
		
		refreshPanelStatus();
		

	}

	public void refreshPanelStatus()
	{
		DetectionResult dr = detectionChooser.getSelectedDetectionResult();
		if (dr  == null)
			areDetectionSettingsOk = false;
		else
			areDetectionSettingsOk = true;
		if (mhtParameterSet == null)	
			areTrackingSettingsOk = false;
		else
		{
			areTrackingSettingsOk = true;
			if (areDetectionSettingsOk && usingDefaultParameters && this.defaultParametersAnnouce == null)
			{
				defaultParametersAnnouce = new AnnounceFrame("Using default tracking parameters for tracking, which is NOT optimal for performance.", 20);
				// do not reset to null so that the frame is shown only once per run of the plugin
			}
		}
		if (testOutputName())
			areOutputSettingsOk = true;
		else
			areOutputSettingsOk = false;
		refreshPanels();
	}
	
	public void refreshPanels()
	{
		// enable detection settings panel
		detectionChooser.setEnabled(true);
		detectionDescriptionPanel.setEnabled(true);
		detectionIcon.setEnabled(true);
		if (areDetectionSettingsOk)
		{
			// enable tracking parameters panel
			estimateParametersButton.setEnabled(true);
			loadParametersButton.setEnabled(true);
			parametersIcon.setEnabled(true);
			parametersDescriptionPanel.setEnabled(true);
			
			if (areTrackingSettingsOk)
			{
				outputDescriptionPanel.setEnabled(true);
				outputIcon.setEnabled(true);
				trackGroupNameTF.setEnabled(true);
				if (areOutputSettingsOk)
				{
					runTrackingButton.setEnabled(true);
					runIncon.setEnabled(true);
					runDescriptionPanel.setEnabled(true);
					outputAnnouce = null;
				}
				else
				{
					runTrackingButton.setEnabled(false);
					runIncon.setEnabled(false);
					runDescriptionPanel.setEnabled(false);
				}
			}
			else
			{
				outputDescriptionPanel.setEnabled(false);
				outputIcon.setEnabled(false);
				trackGroupNameTF.setEnabled(false);
				
				runTrackingButton.setEnabled(false);
				runIncon.setEnabled(false);
				runDescriptionPanel.setEnabled(false);
			}
		}
		else
		{
			// disable all other panels
			estimateParametersButton.setEnabled(false);
			loadParametersButton.setEnabled(false);
			parametersIcon.setEnabled(false);
			parametersDescriptionPanel.setEnabled(false);
			
			outputDescriptionPanel.setEnabled(false);
			outputIcon.setEnabled(false);
			trackGroupNameTF.setEnabled(false);
		
			runTrackingButton.setEnabled(false);
			runIncon.setEnabled(false);
			runDescriptionPanel.setEnabled(false);
		}
	}
	
	public boolean testOutputName()
	{
		String outputName = trackGroupNameTF.getText();
		for (SwimmingObject s:new ArrayList<SwimmingObject>(Icy.getMainInterface().getSwimmingPool().getObjects()))
		{
			if (s.getObject() instanceof TrackGroup)
			{
				if (((TrackGroup)s.getObject()).getDescription().trim().equals(outputName.trim()))
				{
					if (outputAnnouce == null)
						outputAnnouce = new AnnounceFrame("Track group with similar name already existing. Please change output name.");
					return false;
				}
			}
		}
		if (outputAnnouce != null)
		{
			outputAnnouce.close();
			outputAnnouce = null;
		}
		return true;
	}
	
	public void changeTrackingState(boolean isRunning) {
		runTrackingButton.setEnabled(!isRunning);
		loadParametersButton.setEnabled(!isRunning);
		detectionChooser.setEnabled(!isRunning);
		estimateParametersButton.setEnabled(!isRunning);
		trackGroupNameTF.setEnabled(!isRunning);
		trackGroupNameTF.setEditable(!isRunning);
		refreshPanelStatus();
	}

	public void setSelectedDetectionResults(
			DetectionResult dr) {
		if (this.detectionChooser.getSelectedDetectionResult() == dr)
			return;
		int cnt = this.detectionChooser.getItemCount();
		for (int i = 0; i < cnt; i++)
		{
			Object o = this.detectionChooser.getItemAt(i);
			if (o instanceof DetectionResult)
			{
				if (o == dr)
				{
					this.detectionChooser.setSelectedIndex(i);
					break;
				}
			}
		}
		refreshPanelStatus();
	}

	public void setParameter(MHTparameterSet mhtParameterSet2)
	{
		mhtParameterSet = mhtParameterSet2;
		this.detectionChooser.setSelectedItem(mhtParameterSet.detectionResults);
		this.trackGroupNameTF.setText(mhtParameterSet.trackGroupName);
		refreshPanelStatus();
	}
	
	public void setUsingDefaultParameters(boolean useDefault)
	{
		usingDefaultParameters = useDefault;
	}
}
