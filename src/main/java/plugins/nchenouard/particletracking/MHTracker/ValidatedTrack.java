package plugins.nchenouard.particletracking.MHTracker;

import java.util.LinkedList;

/** 
 *  Track that has been validated by the MHT procedure, and thus cannot be changed
 *  
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 * */


public class ValidatedTrack {
	LinkedList<Association> associations = new LinkedList<Association>();
	Family family;

	ValidatedTrack(Family family)
	{
		this.family = family;
		if ( family.rootNode != null )
			associations.add( family.rootNode.association );
	}

	void update(Family f)
	{
		family = f;
		if (family.rootNode != null)
		{
			associations.add( family.rootNode.association );
		}
	}
}
