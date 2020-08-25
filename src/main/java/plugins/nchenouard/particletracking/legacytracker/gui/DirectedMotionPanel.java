package plugins.nchenouard.particletracking.legacytracker.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;

import plugins.nchenouard.particletracking.filtering.*;


/** Panel to configure and create a predictor for constant speed motion
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/


public class DirectedMotionPanel extends PredictorPanel
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 464464238238749137L;
	public final static String name = "Directed motion";
	
	private FilterCovariancesPanel covPanel = new FilterCovariancesPanel();

	public DirectedMotionPanel()
	{
		GridBagLayout mainLayout = new GridBagLayout();
		this.setLayout(mainLayout);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridheight = 1;
		constraints.gridwidth = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;

		this.add(new JLabel("Motion covariance"), constraints);
		constraints.gridy++;
		
		this.add(covPanel, constraints);
	}


	public Predictor buildPredictor(int dim)
	{
		return buildPredictor(dim, true);
	}

	@Override
	public Predictor buildPredictor(int dim, boolean covUpdate) {
		Predictor predictor = null;
		if (dim == 2)
		{
			predictor = new KF2dDirected();
			predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY()});	
			((KF2dDirected)predictor).setUpdateCovariances(covUpdate);
		}
		else
		{
			predictor = new KF3dDirected();
			predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY(), covPanel.getTrackingCovZ()});	
			((KF3dDirected)predictor).setUpdateCovariances(covUpdate);
		}	
		return predictor;
	}

	public String getName()
	{
		return name;
	}

	public void setDefaultValues()
	{
		covPanel.setDefaultValues();
	}
}
