package plugins.nchenouard.particletracking;

import plugins.nchenouard.spot.Spot;

/**
 * virtual spot created by a tracking algorithm to cope for a missing spot
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class VirtualSpot extends Spot
{
	public VirtualSpot(double x, double y, double z)
	{
		super(x,y,z);
	}
}
