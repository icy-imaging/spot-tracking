package plugins.nchenouard.particletracking.legacytracker.gui;

import javax.swing.JPanel;

import plugins.nchenouard.particletracking.filtering.Predictor;

/**
 * Standard panel for a motion predictor
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public abstract class PredictorPanel extends JPanel{
	/**
	 * 
	 */
	private static final long serialVersionUID = -3409174505640300869L;
	/**build a predictor with the dimension given by the parameter dim*/
	public abstract Predictor buildPredictor(int dim);
	/**build a predictor with the dimension given by the parameter dim*/
	public abstract Predictor buildPredictor(int dim, boolean covUpdate);
	/**reset values of the parameters of the predictors to their default value*/
	public abstract void setDefaultValues();
	/**name of the predictor*/
	public abstract String getName();
}
