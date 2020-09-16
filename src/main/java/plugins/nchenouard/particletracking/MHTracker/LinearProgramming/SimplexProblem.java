package plugins.nchenouard.particletracking.MHTracker.LinearProgramming;


/** 
 *  Tracking problem under the form of a linear program
 *  
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 * */

public class SimplexProblem
{
	public int numVariables;
	public int totalNumConstraints;
	
	public double[] c; // score vector s.t. problem is argmax_x cx
	public double[][] A; // constraint matrix st Ax <= b
	public double[] b; // the constraint value vector
}
