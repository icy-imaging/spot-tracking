package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;

/** 
 * Hypothesis
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 * */

public class Hypothesis
{
	final ArrayList<Association> usedAssociations;
	
	final MHTrack2s leaf;
	Hypothesis()
	{
		usedAssociations = new ArrayList<Association>();
		leaf = null;
	}

	Hypothesis(ArrayList<Association> usedAssociations, MHTrack2s leaf)
	{
		this.usedAssociations = usedAssociations;
		this.leaf = leaf;
	}
}
