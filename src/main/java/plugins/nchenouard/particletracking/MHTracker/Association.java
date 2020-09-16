package plugins.nchenouard.particletracking.MHTracker;

import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

/** 
 * Describes the association between a Spot and a putative track
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class Association
{
	Matrix state;
	Spot spot;
	boolean isPrediction;
	int t;
	
	private double bestScore;
	
	public Association(Matrix state, Spot spot, boolean isPrediction, int t, double initScore)
	{
		this.state = state;
		this.spot = spot;
		this.isPrediction = isPrediction;
		this.t  = t;
		this.bestScore = initScore;
	}
	
	public synchronized  void setNewScore(double score)
	{
		if (score>bestScore)
			bestScore = score;
	}
	
	public double getBestScore()
	{
		return bestScore;
	}
}
