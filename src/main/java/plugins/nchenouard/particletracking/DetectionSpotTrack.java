package plugins.nchenouard.particletracking;

import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.Spot;

/**
 * A specialized Detection object that embeds a DetectionSpot object
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class DetectionSpotTrack extends Detection
{

	public Spot spot;
	
	public DetectionSpotTrack( Spot detection, int t )
	{
		super(detection.mass_center.x, detection.mass_center.y, detection.mass_center.z, t);
		this.spot = detection;
	}

	public DetectionSpotTrack( )
	{
		super();
	}
	
	public void setDetection(Spot detection, int t)
	{
		this.spot = detection;
		this.t = t;
	}
}
