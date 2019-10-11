package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;



/** 
 * Very simple (and inaccurate) solver for track-to-detection problems
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 * */

public class SolveMLAssociation
{	
	class Track
	{
		Family f;
		LinkedList<MHTrack2s> association = new LinkedList<MHTrack2s>();
		LinkedList<MHTrack2s> followingTracks = new LinkedList<MHTrack2s>();
	}
	
	ArrayList<Track> trackList = new ArrayList<Track>();
	ArrayList<Track> currentTrackList = new ArrayList<Track>();
	
	public GlobalHypothesis getHypothesis(Cluster cl,int firstT, int lastT, HMMMHTracker tracker)
	{
		GlobalHypothesis gh = new GlobalHypothesis(firstT, lastT, tracker);
		for (Track trk:trackList)
		{
			Family f = trk.f;
			Hypothesis hSelected = null;
			for (Hypothesis h:f.hypotheses)
			{
				if(h.leaf==trk.association.getLast())
					hSelected = h;
			}
			if(hSelected!=null)
				gh.addHypothesis(hSelected);
			else
			{
				System.out.println(trk.association.getLast().followingSegments.isEmpty()+" "+lastT+" "+trk.association.getLast().association.t+trk.association.getLast().confirmed);
				System.out.println("probleme in SolveMLAssociation, no selected hypothesis ");
				return null;
			}
		}
		for (int t =firstT; t<=lastT;t++)
		{
			cl.penalizeFD(gh, t);
			cl.penalizeNT(gh, t);
		}
		return gh;
	}
	
	public void solve(int firstT, int lastT, HashMap<Integer, ArrayList<Family>> concurrentFamilies, ArrayList<Family> mandatoryFamilies, ArrayList<Family> optionalFamilies)
	{
		solve2(firstT, lastT, mandatoryFamilies);
	}
	
	
	
	
	// only mandatory tracks are extended
	// the remaining detections are considered false
	public void solve2(int firstT, int lastT, ArrayList<Family> mandatoryFamilies)
	{
//		first create tracks from mandatory families
		ArrayList<Association> usedAssociations = new ArrayList<Association>();
		for (Family f:mandatoryFamilies)
		{
			Track trk = new Track();
			trk.association.add(f.rootNode);
			trk.followingTracks = new LinkedList<MHTrack2s>(f.rootNode.followingSegments);
			trk.f = f;
			Collections.sort(trk.followingTracks, new LowerThan());
			trackList.add(trk);
			if (!trk.followingTracks.isEmpty())
				currentTrackList.add(trk);
			else
				if (!f.rootNode.terminated)
					System.out.println("error in solveML: following tracks is empty");
		}
		
		for (int t = firstT+1; t<=lastT; t++)
		{
			usedAssociations.clear();
			//try to extend current tracks with observed detections
			ArrayList<Track> temp = new ArrayList<Track>(currentTrackList);
			// until all tracks are extended
			while(!temp.isEmpty())
			{
				Track bestTrack = null;
				double bestLikelihood = 0;
				for (Track trk:temp)
				{
					if (bestTrack==null || trk.followingTracks.getFirst().associationLikelihood > bestLikelihood)
					{
						bestTrack = trk;
						bestLikelihood = trk.followingTracks.getFirst().associationLikelihood;
					}
				}
				if (bestTrack!=null)
				{
					//extend best track
					MHTrack2s bestAssociation = bestTrack.followingTracks.getFirst();
					usedAssociations.add(bestAssociation.association);
					bestTrack.association.add(bestAssociation);
					bestTrack.followingTracks = new LinkedList<MHTrack2s>(bestAssociation.followingSegments);
					Collections.sort(bestTrack.followingTracks, new LowerThan());
					
					if (bestTrack.followingTracks.isEmpty())
						currentTrackList.remove(bestTrack);
					temp.remove(bestTrack);
					
					//update other track following segments;
					ArrayList<Track> toRemove = new ArrayList<Track>();
					for (Track trk:temp)
					{
						LinkedList<MHTrack2s> copy = new LinkedList<MHTrack2s>(trk.followingTracks);
						for (MHTrack2s mhtrk:copy)
						{
							if(mhtrk.association==bestAssociation.association)
								trk.followingTracks.remove(mhtrk);
						}
						if (trk.followingTracks.isEmpty())
							toRemove.add(trk);
					}
					for (Track trk:toRemove)
					{
						currentTrackList.remove(trk);
						temp.remove(trk);
					}
				}
				else
				{
					System.out.println("error in SolveMLAssociation: no best track");
				}
			}
		}
	}
	
	
	//ne fonctionne pas toujours car on peut creer une track qui au final ne peut pas etre continuee car les associations suivantes sont deja prises
	//et les predictions ne sont pas possibles car la track n'aurait pas ete confirmee avec
	public void solve1(int firstT, int lastT, HashMap<Integer, ArrayList<Family>> concurrentFamilies, ArrayList<Family> mandatoryFamilies, ArrayList<Family> optionalFamilies)
	{
		//first create tracks with mandatory families
		ArrayList<Association> usedAssociations = new ArrayList<Association>();
		for (Family f:mandatoryFamilies)
		{
			Track trk = new Track();
			trk.association.add(f.rootNode);
			trk.followingTracks = new LinkedList<MHTrack2s>(f.rootNode.followingSegments);
			trk.f = f;
			Collections.sort(trk.followingTracks, new LowerThan());
			usedAssociations.add(f.rootNode.association);
			trackList.add(trk);
			if (!trk.followingTracks.isEmpty())
				currentTrackList.add(trk);
		}
		//then create tracks with optional families
		for (Family f:optionalFamilies)
		{
				if ((!usedAssociations.contains(f.rootNode.association)) && (!f.rootNode.association.isPrediction))
				{
					usedAssociations.add(f.rootNode.association);
					Track trk = new Track();
					trk.association.add(f.rootNode);
					trk.followingTracks = new LinkedList<MHTrack2s>(f.rootNode.followingSegments);
					trk.f = f;
					Collections.sort(trk.followingTracks, new LowerThan());
					trackList.add(trk);
					if (!trk.followingTracks.isEmpty())
						currentTrackList.add(trk);	
				}
		}
		for (int t = firstT+1; t<=lastT; t++)
		{
			usedAssociations.clear();
			//try to prolongate current tracks with observed detections
			ArrayList<Track> temp = new ArrayList<Track>(currentTrackList);
			//tant que toutes les tracks n'ont pas ete prolongees
			while(!temp.isEmpty())
			{
				Track bestTrack = null;
				double bestLikelihood = 0;
				for (Track trk:temp)
				{
					if (bestTrack==null || trk.followingTracks.getFirst().associationLikelihood > bestLikelihood)
					{
						bestTrack = trk;
						bestLikelihood = trk.followingTracks.getFirst().associationLikelihood;
					}
				}
				if (bestTrack!=null)
				{
					//prolongate best track
					MHTrack2s bestAssociation = bestTrack.followingTracks.getFirst();
					usedAssociations.add(bestAssociation.association);
					bestTrack.association.add(bestAssociation);
					bestTrack.followingTracks = new LinkedList<MHTrack2s>(bestAssociation.followingSegments);
					Collections.sort(bestTrack.followingTracks, new LowerThan());
					
					if (bestTrack.followingTracks.isEmpty())
						currentTrackList.remove(bestTrack);
					temp.remove(bestTrack);
					
					//update other track following segments;
					ArrayList<Track> toRemove = new ArrayList<Track>();
					for (Track trk:temp)
					{
						LinkedList<MHTrack2s> copy = new LinkedList<MHTrack2s>(trk.followingTracks);
						for (MHTrack2s mhtrk:copy)
						{
							if(mhtrk.association==bestAssociation.association)
								trk.followingTracks.remove(mhtrk);
						}
						if (trk.followingTracks.isEmpty())
							toRemove.add(trk);
					}
					for (Track trk:toRemove)
					{
						currentTrackList.remove(trk);
						temp.remove(trk);
					}
				}
				else
				{
					System.out.println("error in SolveMLAssociation: no best track");
				}
			}
			//now try to create new tracks with real association that are missing
			
			ArrayList<Family> fList = concurrentFamilies.get(new Integer(t));
			if (fList!=null)
				for (Family f:fList)
				{
					if (!usedAssociations.contains(f.rootNode.association) && (!f.rootNode.association.isPrediction))
					{
						usedAssociations.add(f.rootNode.association);
						Track trk = new Track();
						trk.association.add(f.rootNode);
						trk.followingTracks = new LinkedList<MHTrack2s>(f.rootNode.followingSegments);
						trk.f = f;
						Collections.sort(trk.followingTracks, new LowerThan());
						trackList.add(trk);
						if (!trk.followingTracks.isEmpty())
							currentTrackList.add(trk);
					}
				}
		}
	}
	
	class LowerThan implements Comparator<MHTrack2s>
	{
		public int compare(MHTrack2s t1, MHTrack2s t2)
		{	
			if (t1.association.isPrediction)
			{
				if (t2.association.isPrediction)
				{
					if (t2.associationLikelihood > t1.associationLikelihood)
						return 1;
					else if (t1.associationLikelihood > t2.associationLikelihood)
						return -1;
					else return 0;
				}
				else
					return 1;
			}
			else if (t2.association.isPrediction)
				return -1;
			else
			{
				if (t2.associationLikelihood > t1.associationLikelihood)
					return 1;
				else if (t1.associationLikelihood > t2.associationLikelihood)
					return -1;
				else return 0;
			}
		}
	}
}
