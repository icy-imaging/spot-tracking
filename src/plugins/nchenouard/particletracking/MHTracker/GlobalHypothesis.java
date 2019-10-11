package plugins.nchenouard.particletracking.MHTracker;

import java.util.HashSet;
import java.util.LinkedList;


/** 
 * A Global Hypothesis is essentially a set of Hypothesis objects with related methods
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class GlobalHypothesis {

	HashSet<Association> usedAssociations;
	LinkedList<Association> realAssociations;//devient inutile?
	LinkedList<Hypothesis> hyps; 
	int[] usedRealSpotsNum;
	volatile double score;
	int firstT;
	int lastT;
	HMMMHTracker tracker;
	
	GlobalHypothesis(int firstT, int lastT, HMMMHTracker tracker)
	{
		usedAssociations = new HashSet<Association>();
		realAssociations = new LinkedList<Association>();
		hyps = new LinkedList<Hypothesis>();
		score = 0;
		this.usedRealSpotsNum = new int[lastT - firstT+1];
		this.firstT = firstT;
		this.lastT = lastT;
		this.tracker = tracker;
	}

	GlobalHypothesis(HashSet<Association> usedAssociations, LinkedList<Association> realAssociations, LinkedList<Hypothesis> hyps, double score, int[] usedRSpots, int firstT, int lastT, HMMMHTracker tracker)
	{
		this.usedAssociations = usedAssociations;
		this.realAssociations = realAssociations;
		this.hyps = hyps;
		this.score = score;
		this.usedRealSpotsNum = usedRSpots;
		this.firstT = firstT;
		this.lastT = lastT;
		this.tracker = tracker;
	}

	public void addHypothesis(Hypothesis h)
	{
		hyps.add(h);
		usedAssociations.addAll(h.usedAssociations);
		for (Association a:h.usedAssociations)
			if(!a.isPrediction)
			{
				realAssociations.add(a);
				this.usedRealSpotsNum[a.t-firstT] = this.usedRealSpotsNum[a.t-firstT]+1;
			}
		this.score += h.leaf.score;
	}
	
	public GlobalHypothesis copy()
	{
		int[] rsCopy = new int[this.usedRealSpotsNum.length];
		System.arraycopy(this.usedRealSpotsNum, 0, rsCopy, 0, this.usedRealSpotsNum.length);
		return new GlobalHypothesis(new HashSet<Association>(usedAssociations), new LinkedList<Association>(realAssociations), new LinkedList<Hypothesis>(hyps), score, rsCopy, firstT, lastT, this.tracker);
	}
	
	public void copyInto(GlobalHypothesis ghCopy)
	{
		System.arraycopy(this.usedRealSpotsNum, 0, ghCopy.usedRealSpotsNum, 0, this.usedRealSpotsNum.length);
		ghCopy.usedAssociations.addAll(this.usedAssociations);
		ghCopy.realAssociations.addAll(this.realAssociations);
		ghCopy.hyps.addAll(this.hyps);
		ghCopy.score = this.score;
		ghCopy.firstT = this.firstT;
		ghCopy.lastT = this.lastT;
		ghCopy.tracker = this.tracker;
	}

	public void clear()
	{
		usedAssociations.clear();
		realAssociations.clear();
		this.score = 0;
		hyps.clear();
	}
	
	public int getRealSpotsNumAtT(int t)
	{
		if (t >= firstT && t<=lastT)
		{
			return usedRealSpotsNum[t-firstT];
		}
		else return 0;
	}
	
	public int getNumNewTracks(int t)
	{
		int numNT = 0;
		for (Hypothesis h:hyps)//TODO: store num new track in each hypothesis
		{
			if (h.leaf.family.rootTrack==null && h.leaf.family.rootNode.association.t==t)
				numNT++;
		}
		return numNT;
	}
	
	public void penalizeFD(int t, double pFA, HashSet<Association> originalRealAssociations)
	{
		for (Association a:originalRealAssociations)
		{
			if (a.t == t && !realAssociations.contains(a))
			{
			//	this.score+= (Math.log(pFA) - a.getBestScore());
				this.score+= Math.log(pFA);
			}
		}
		
	}
	public void penalizeAllFD(double pFA, HashSet<Association> originalRealAssociations)
	{
		for (Association a:originalRealAssociations)
		{
			if (!realAssociations.contains(a))
			{
			//	this.score+= (Math.log(pFA) - a.getBestScore());
				this.score+= Math.log(pFA);
			}
		}
		
	}
}
