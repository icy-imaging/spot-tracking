package plugins.nchenouard.particletracking.legacytracker.associationMethod;

/**
 * Interface for algorithms that can solve assignment problems (AssignProblem object)
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public abstract class AssignProblemSolver
{
	int numMaxMeasurements = -1;
	int numMaxTracks = -1;
	abstract void solve(AssignProblem pb);
}
