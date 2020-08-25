package plugins.nchenouard.particletracking.legacytracker.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JLabel;

import plugins.nchenouard.particletracking.filtering.*;


/**
 * Panel for defining the configuration of the random walk predictor
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class RandomWalkPanel extends PredictorPanel
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 2396842098905570570L;
	/**Name of the predictor*/
	public final static String name = "Brownian motion";
	/**Panel for tuning the filters co-variances*/
	private FilterCovariancesPanel covPanel = new FilterCovariancesPanel();

	/**Standard constructor*/
	public RandomWalkPanel()
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
	/**Build the predictor as defined in the GUI*/
	public Predictor buildPredictor(int dim)
	{
		return buildPredictor(dim, true);
	}

	/**Build the predictor as defined in the GUI when specifying if the co-variances should be auto-updated later on*/
	@Override
	public Predictor buildPredictor(int dim, boolean covUpdate) {
		Predictor predictor = null;
		if (dim == 2)
		{
			predictor = new KF2dRandomWalk();
			predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY()});	
			((KF2dRandomWalk)predictor).setUpdateCovariances(covUpdate);
		}
		else
		{
			predictor = new KF3dRandomWalk();
			predictor.setTrackingCovariances(new double[]{covPanel.getTrackingCovX(), covPanel.getTrackingCovY(), covPanel.getTrackingCovZ()});	
			((KF3dRandomWalk)predictor).setUpdateCovariances(covUpdate);
		}
		return predictor;
	}

	/** get the name of the predictor*/
	public String getName()
	{
		return name;
	}
	/** reset the GUI to match the default configuration of the predictor*/
	public void setDefaultValues()
	{
		covPanel.setDefaultValues();
	}
}
