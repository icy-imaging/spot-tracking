package plugins.nchenouard.particletracking.filtering;

/**
 * A standard set of predictors for target motion in 2D and 3D
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public enum PredictorTypes {KF_RANDOM_WALK, KF_FIRST_ORDER_LINEAR_EXTRAPOLATION,  KF_SECOND_ORDER_LINEAR_EXTRAPOLATION, IMM_MAX_LIKELIHOOD, IMM_WEIGHTED_LIKELIHOOD, KF2D_DIRECTED, CUSTOM
}
