package plugins.nchenouard.particletracking.legacytracker;


/**
 * Interface for a tracker that can be externally stopped while it is computing
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public interface StoppableTracker {
	public void stopComputing();
	public void stopCurrentIteration();
}
