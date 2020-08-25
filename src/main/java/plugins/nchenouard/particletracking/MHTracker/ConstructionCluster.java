package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;
import java.util.HashSet;

/** 
 * Temporary object used to build Cluster objects
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class ConstructionCluster
{
	ArrayList<Family> families = new ArrayList<Family>();
	HashSet<Association> associations = new HashSet<Association>();
}
