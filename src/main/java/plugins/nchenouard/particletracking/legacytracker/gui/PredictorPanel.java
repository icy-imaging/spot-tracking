package plugins.nchenouard.particletracking.legacytracker.gui;

import javax.swing.JPanel;

import plugins.nchenouard.particletracking.filtering.Predictor;

/**
 * Standard panel for a motion predictor
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public abstract class PredictorPanel extends JPanel {
    private static final long serialVersionUID = -3409174505640300869L;

    /**
     * Build a predictor with the dimension given by the parameter dim
     * @param dim int
     * @return Predictor
     */
    public abstract Predictor buildPredictor(int dim);

    /**
     * Build a predictor with the dimension given by the parameter dim
     * @param dim int
     * @param covUpdate bool
     * @return Predictor
     */
    public abstract Predictor buildPredictor(int dim, boolean covUpdate);

    /**
     * Reset values of the parameters of the predictors to their default value
     */
    public abstract void setDefaultValues();

    /**
     * Name of the predictor
     * @return String
     */
    public abstract String getName();
}
