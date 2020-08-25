package plugins.nchenouard.particletracking.legacytracker;

import java.util.Vector;

import plugins.nchenouard.spot.Spot;

/**
 * A generic tracker interface (links spots through frames)
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public interface Tracker
{
	/**Extend tracks at a given time with a set of spots*/
	public void track(int t, Vector<Spot> spotsVector);
}
