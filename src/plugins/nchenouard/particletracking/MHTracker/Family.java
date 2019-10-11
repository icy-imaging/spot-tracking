package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 
 * A Family object represents a tree of track segments with a shared root node (association)
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class Family
{
	MHTrack2s rootNode;
	ValidatedTrack rootTrack;
	double trackScore = 0;
	double rootScore = 0;
	HashSet<Association> usedAssociations = new HashSet<Association>();
	LinkedList<MHTrack2s> leafs = new LinkedList<MHTrack2s>();
	LinkedList<Hypothesis> hypotheses = new LinkedList<Hypothesis>();
	int rootTime;
	MHTrack2s lastSelectedLeaf = null;
	IdCluster cluster=null;
	
	protected void recursUpdate(MHTrack2s trk)
	{
		//since it is validated confirm the track
		trk.confirmed = true;
		//update used associations
		usedAssociations.add(trk.association);
		//update followingSegments
		if (trk.followingSegments.isEmpty()) 
			leafs.add(trk);
		else
			for (MHTrack2s trk2:trk.followingSegments)
				recursUpdate(trk2);
	}
	public void updateFamily(MHTrack2s track, ValidatedTrack validatedTrack, int leafTime)
	{
		this.rootNode = track;
		this.rootTrack = validatedTrack;
		this.rootTime = rootNode.association.t;
		this.trackScore =rootScore;
		this.rootScore =rootNode.score;
		//build leafs and used spots
		for (MHTrack2s trk:leafs)
			trk.hypothesis = null;
		leafs.clear();
		usedAssociations.clear();
		recursUpdate(track);
	}
	
	Vector<Hypothesis> getCompatibleHypothesis(GlobalHypothesis gh)
	{
		Vector<Hypothesis> hypList = new Vector<Hypothesis>();
		if (!gh.usedAssociations.contains(this.rootNode.association))
		{
			if (rootNode.followingSegments.size()>0)
				for(MHTrack2s trk:rootNode.followingSegments)
					recursCompatibleHypotheses(gh, trk, hypList);
			else if (rootNode.hypothesis!=null)
				hypList.add(rootNode.hypothesis);
		}
		return hypList;
	}
	
	void recursCompatibleHypotheses(final GlobalHypothesis gh, MHTrack2s node, Vector<Hypothesis> hypList)
	{
		if (!gh.usedAssociations.contains(node.association))
		{
			if (node.followingSegments.size()>0)
				for(MHTrack2s trk:node.followingSegments)
				{
					recursCompatibleHypotheses(gh, trk, hypList);
				}
			else if (node.hypothesis!=null)
				hypList.add(node.hypothesis);
		}
	}
	
	private boolean belowBound(GlobalHypothesis gh, double bestScore, MHTrack2s node)
	{
			if (gh.score + node.bestScore <  bestScore)
				return true;
		return false;
	}
	
	Vector<Hypothesis> getCompatibleHypothesis(GlobalHypothesis gh, double bestScore)
	{
//		long ml1 = System.currentTimeMillis();
//		if (bestHyp==null)
//			return getCompatibleHypothesis(gh);
		final ThreadCounter tc = new ThreadCounter();
//		ExecutorService executor = Executors.newFixedThreadPool(4);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		
		tc.reset();
		Vector<Hypothesis> hypList = new Vector<Hypothesis>();
		if (!belowBound(gh, bestScore, this.rootNode))
		{
			if (!gh.usedAssociations.contains(this.rootNode.association))
			{
				if (rootNode.followingSegments.size()>0)
					for(MHTrack2s trk:rootNode.followingSegments)
//						recursCompatibleHypotheses(gh, bestScore, trk, hypList);
					{		
						tc.increaseCount();
						executor.execute(new RecursCompatibleThr(gh, bestScore, trk, hypList, tc, executor));
					}
				else if (rootNode.hypothesis!=null)
					hypList.add(rootNode.hypothesis);
			}
		}
		tc.lock.lock();
		try {
			while (tc.cntThreads!=0)
				tc.countAndDeathEquality.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		finally{tc.lock.unlock();}
		executor.shutdown();
//		long ml2 = System.currentTimeMillis();
//		HMMMHTracker.compatibleHypTime+=(ml2-ml1);
		return hypList;
	}
	
	ArrayList<Hypothesis> getCompatibleHypothesisSingleThread(GlobalHypothesis gh, double bestScore)
	{
		ArrayList<Hypothesis> hypList = new ArrayList<Hypothesis>();
		Stack<MHTrack2s> waitingTracks = new Stack<MHTrack2s>();
		if (!belowBound(gh, bestScore, this.rootNode))
		{
			if (!gh.usedAssociations.contains(this.rootNode.association))
			{
				if (rootNode.followingSegments.size()>0)
					waitingTracks.addAll(rootNode.followingSegments);
				else if (rootNode.hypothesis!=null)
					hypList.add(rootNode.hypothesis);
			}
		}
		while(!waitingTracks.isEmpty())
		{
			recursCompatibleHypotheses(gh,bestScore, waitingTracks.pop(), hypList, waitingTracks);
		}
		Collections.sort(hypList, new HypothesisComparator());
		return hypList;
	}
	
	void getCompatibleHypothesisSingleThread(GlobalHypothesis gh, double bestScore, ArrayList<Hypothesis> hypList, Stack<MHTrack2s> waitingTracks)
	{
		if (!belowBound(gh, bestScore, this.rootNode))
		{
			if (!gh.usedAssociations.contains(this.rootNode.association))
			{
				if (rootNode.followingSegments.size()>0)
					waitingTracks.addAll(rootNode.followingSegments);
				else if (rootNode.hypothesis!=null)
					hypList.add(rootNode.hypothesis);
			}
		}
		while(!waitingTracks.isEmpty())
		{
			recursCompatibleHypotheses(gh,bestScore, waitingTracks.pop(), hypList, waitingTracks);
		}
		Collections.sort(hypList, new HypothesisComparator());
	}
	
	
	void recursCompatibleHypotheses(GlobalHypothesis gh, double bestScore, MHTrack2s node, Vector<Hypothesis> hypList, ThreadCounter tc, ExecutorService executor)
	{
		if (!belowBound(gh, bestScore, this.rootNode))
		{
			if (!gh.usedAssociations.contains(node.association))
			{
				if (node.followingSegments.size()>0)
					for(MHTrack2s trk:node.followingSegments)
//						recursCompatibleHypotheses(gh, bestScore, trk, hypList);
				{		
					tc.increaseCount();
					executor.execute(new RecursCompatibleThr(gh, bestScore, trk, hypList, tc, executor));
				}
				else if (node.hypothesis!=null)
					hypList.add(node.hypothesis);
			}
		}
	}
	
	void recursCompatibleHypotheses(GlobalHypothesis gh, double bestScore, MHTrack2s node, ArrayList<Hypothesis> hypList, Stack<MHTrack2s> taskList)
	{
		if (!belowBound(gh, bestScore, this.rootNode))
		{
			if (!gh.usedAssociations.contains(node.association))
			{
				if (node.followingSegments.size()>0)
					for(MHTrack2s trk:node.followingSegments)
					{		
						taskList.add(trk);
					}
				else if (node.hypothesis!=null)
					hypList.add(node.hypothesis);
			}
		}
	}
	
	
	
	public void buildHypothesesAndResetBestScores()
	{		
		ArrayList<Association> measurementsList =  new ArrayList<Association>();
		measurementsList.add(rootNode.association);
		rootNode.resetBestScore();
	
		Hypothesis h  = new Hypothesis(measurementsList, rootNode);
		if (rootNode.followingSegments.isEmpty())
		{
			if (rootNode.confirmed)
			{	
				hypotheses.add(h);
				rootNode.hypothesis = h;
			}
		}
		else
			recursBuildHyp(h, hypotheses);
		
		usedAssociations.clear();
		for (Hypothesis hyp:hypotheses)
			usedAssociations.addAll(hyp.usedAssociations);
		//find best scores for each node
		correctHypothesesScore();
		putScoresOnNodes();
	}
	
	private void correctHypothesesScore()
	{
	//	System.out.println("DIFF "+rootScore+" "+trackScore+" "+Math.log(rootNode.associationLikelihood));
		for (Hypothesis hyp:hypotheses)
		{
			double bnf = 0;//best non feasible score
			for (Association a:hyp.usedAssociations)
				bnf+=a.getBestScore();
			hyp.leaf.bestScore = hyp.leaf.score - bnf - rootScore;
		}
	}
	
	private void putScoresOnNodes() {
		for (Hypothesis h:hypotheses)
		{
			//h.leaf.bestScore = h.leaf.score;
			recursPutScoresOnNodes(h.leaf);
		}
	}
	private void recursPutScoresOnNodes(MHTrack2s node)
	{
		if (node.precedingSegment!=null)
		{
			if (node.precedingSegment.bestScore<node.bestScore)
			{
				node.precedingSegment.bestScore=node.bestScore;
				recursPutScoresOnNodes(node.precedingSegment);
			}
		}
	}
	protected void recursBuildHyp(Hypothesis h, LinkedList<Hypothesis> hList)
	{
		for (MHTrack2s nextTrk:h.leaf.followingSegments)
		{
			nextTrk.resetBestScore();
			ArrayList<Association> associationList = new ArrayList<Association>(h.usedAssociations);
			associationList.add(nextTrk.association);
			Hypothesis nextH  = new Hypothesis(associationList, nextTrk);
			if (nextTrk.followingSegments.isEmpty())
			{
					if (nextTrk.confirmed)
					{
						hList.add(nextH);
						nextTrk.hypothesis = nextH;
					}
			}
			else
				recursBuildHyp(nextH, hList);
		}
	}
	
	protected void printHypotheses()
	{
		/*PluginConsole.println("Family hypothesis ");
		for (Hypothesis h:hypothesis)
		{
			PluginConsole.print("H: ");
			for (Association a:h.usedAssociations)
				PluginConsole.print("["+a.id +" "+a.isPrediction+" "+a.t+"]");
			PluginConsole.println("score "+h.leaf.score);
		}*/
	}
	
	class RecursCompatibleThr implements Runnable
	{
		ThreadCounter tc;
		ExecutorService executor;
		GlobalHypothesis gh;
		MHTrack2s trk;
		Vector<Hypothesis> hypList;
		double bestScore;
		
		public RecursCompatibleThr(GlobalHypothesis gh, double bestScore, MHTrack2s trk, Vector<Hypothesis> hypList, ThreadCounter tc, ExecutorService executor)
		{
			this.tc = tc;
			this.gh = gh;
			this.trk = trk;
			this.hypList = hypList;
			this.bestScore = bestScore;
			this.executor = executor;
		}
		
		public void run()
		{				
			recursCompatibleHypotheses(gh, bestScore, trk, hypList, tc, executor);
			tc.increaseDeath();
		}
	}
	
	class  HypothesisComparator implements Comparator <Hypothesis>
	{

		public int compare(Hypothesis o1, Hypothesis o2) {
			if (o1.leaf.score > o2.leaf.score)
				return 1;
			else if (o1.leaf.score< o2.leaf.score)
				return -1;
			return 0;
		}
		
	}
}
