package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import icy.system.thread.Processor;
import icy.system.thread.ThreadUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import plugins.nchenouard.particletracking.filtering.Predictor;
import plugins.nchenouard.particletracking.legacytracker.Tracker;
import plugins.nchenouard.spot.Spot;

/**
 * Implementation of the tracking algorithm described in:
 * Genovesio A, Liedl T, Emiliani V, Parak WJ, Coppey-Moisan M, Olivo-Marin JC.
 * Multiple particle tracking in 3-D+t microscopy: method and application to the
 * tracking of endocytosed quantum dots. IEEE Trans Image Process. 2006
 * May;15(5):1062-70. PubMed PMID: 16671288.
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com). Based on Auguste Genovesio original code.
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/


public class InstantaneousTracker implements Tracker
{

	class PostProcessThr extends Thread
	{
		final AssignProblem p;
		final int t;

		PostProcessThr(AssignProblem p, int t)
		{
			this.p = p;
			this.t = t;
		}

		public void run()
		{
			postProcess(p, t);
		}
	}
	class SolveProblemThr extends Thread
	{
		final AssignProblem p;

		public SolveProblemThr(AssignProblem p)
		{
			this.p = p;
		}
		public void run()
		{
			assignementSolver.solve(p);
		}
	}
	
	ArrayList<Track> tracks;
	final Predictor predictor;
	boolean gateLikelihood = true;
	
	double gateFactor = 4;
	double totalGateLikelihood = 0.99;
	final AssignProblemSolver assignementSolver ;
	public final static String[] associationMethodsNames = { "Max Likelihood", "Optimal Bayesian", "Greedy Nearest Neighbor"};

	int trackCount = 0;
	int maxConsecutivePred;

	public InstantaneousTracker (AssignProblemSolver assignementSolver, Predictor predictor, boolean gateLikelihood, double gateFactor, int maxConsecutivePred)
	{
		this.assignementSolver = assignementSolver;
		this.predictor = predictor;
		this.gateLikelihood = gateLikelihood;
		this.gateFactor = gateFactor;
		this.totalGateLikelihood = predictor.getTotalGateLikelihood(gateLikelihood, gateFactor);
		tracks = new ArrayList<Track>();
		this.maxConsecutivePred = maxConsecutivePred;
	}

	protected double[][] computeLikelihoodTable(int t, ArrayList<Track>  activeTracks,ArrayList<Spot> spots)
	{
		int numTracks = activeTracks.size();
		int numDetect = spots.size();
		if (numTracks == 0 || numDetect == 0)
			return null;
//		computing likelihood table
		double[][] likelihoods = new double[numDetect][numTracks];
		for (int i = 0; i < numTracks; i++) 
		{
			Track track = (Track) activeTracks.get(i);
			for (int j = 0; j < numDetect; j++)
				likelihoods[j][i] = track.likelihood(spots.get(j),gateLikelihood, gateFactor);
		}
//		for(int i=0; i < likelihoods.length; i++)
//		{
//		for (int j=0; j < likelihoods[i].length; j++)
//		{
//		PluginConsole.print(" "+likelihoods[i][j]);
//		}
//		PluginConsole.println();
//		}
		return likelihoods;
	}
	
	protected double[][] computeLikelihoodTable(int t, ArrayList<Track>  activeTracks,ArrayList<Spot> spots, double gateFactor)
	{
		int numTracks = activeTracks.size();
		int numDetect = spots.size();
		if (numTracks == 0 || numDetect == 0)
			return null;
//		computing likelihood table
		double[][] likelihoods = new double[numDetect][numTracks];
		for (int i = 0; i < numTracks; i++) 
		{
			Track track = (Track) activeTracks.get(i);
			for (int j = 0; j < numDetect; j++) 
				likelihoods[j][i] = track.likelihood(spots.get(j),gateLikelihood, gateFactor);
		}
//		for(int i=0; i < likelihoods.length; i++)
//		{
//		for (int j=0; j < likelihoods[i].length; j++)
//		{
//		PluginConsole.print(" "+likelihoods[i][j]);
//		}
//		PluginConsole.println();
//		}
		return likelihoods;
	}
	
	protected double[][] computeInnovationTable(int t, ArrayList<Track>  activeTracks,ArrayList<Spot> spots)
	{
		int numTracks = activeTracks.size();
		int numDetect = spots.size();
		
		if (numTracks == 0 || numDetect == 0)
			return null;
		
		double[][] innovations = new double[numDetect][numTracks];
		for (int i = 0; i < numTracks; i++) 
		{
			Track track = (Track) activeTracks.get(i);
			for (int j = 0; j < numDetect; j++) 
				innovations[j][i] = track.normalizedInnovation(spots.get(j));
		}
		return innovations;
	}

	protected ArrayList<AssignProblem> createIndependentProblems(int t, double[][] likelihoods, double[][] innovations, ArrayList<Track> activeTracks, ArrayList<Spot> detections)
	{
		// TODO: check that likelihood is not null ie detections.size() > 0
		int n_pred = activeTracks.size();
		int n_meas = detections.size();
		
		if (n_meas == 0)
		{
			ArrayList<AssignProblem> pbList = new ArrayList<AssignProblem>();
			for (Track trk:activeTracks)
			{
				AssignProblem pb = new AssignProblem();
				pb.tracks.add(trk);
				pb.t = t;
			}
			return pbList;
		}
		
		ArrayList<HashSet<Integer>[]> accumulator = new ArrayList<HashSet<Integer>[]>(n_pred);		

		boolean[] trackIsBusy = new boolean[n_pred];
		boolean[] measIsBusy = new boolean[n_meas];

//		initialize tracks and measurements to not busy
		for(int j = 0; j < n_pred; j++)
			trackIsBusy[j] = false;		
		for(int i = 0; i<n_meas; i++)
			measIsBusy[i] = false;		
//		recursive construction of connected tracks/measurements (in validation gates)
		for(int j = 0; j < n_pred; j++)
		{ 
			if(!trackIsBusy[j]){
				HashSet[] hs = new HashSet[2];
				hs[0] = new HashSet<Integer>(5); // prediction
				hs[1] = new HashSet<Integer>(5); // measurement
				recursAddTrack(j, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
				accumulator.add(hs);
			}
		}
//		for all measurements (should stay only lonely measurements from now)
		for(int i = 0; i < n_meas; i++)
		{
			if(!measIsBusy[i]){
				HashSet hs[] = new HashSet[2];
				hs[0] = new HashSet(5); // prediction
				hs[1] = new HashSet(5); // measurement
				recursAddMeasurement(i, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
				accumulator.add(hs);
			}
		}
//		AssignProblem' creation
		ArrayList<AssignProblem> problems = new ArrayList<AssignProblem> ();
		for(Iterator it = accumulator.iterator(); it.hasNext();)
		{
			HashSet[] hs = (HashSet[]) it.next();
			AssignProblem pb = new AssignProblem();
			Object[] trackTable = hs[0].toArray();
			for(int i = 0; i < trackTable.length;i++)
				pb.tracks.add(activeTracks.get(((Integer)trackTable[i]).intValue()));
			Object[] detectTable = hs[1].toArray();
			for(int j = 0; j < detectTable.length; j++)
				pb.detections.add(detections.get(((Integer) detectTable[j]).intValue()));
			double[][] compactLikelihoods = new double[detectTable.length][trackTable.length];
			double[][] compactInnovations = new double[detectTable.length][trackTable.length];
			for(int j = 0; j < detectTable.length; j++)
			{
				for(int i = 0; i < trackTable.length; i++)
				{
					compactLikelihoods[j][i] = likelihoods[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
					compactInnovations[j][i] = innovations[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
				}
			}
			pb.likelihoods = compactLikelihoods;
			pb.innovations = compactInnovations;
			pb.t = t;
			if (assignementSolver.numMaxMeasurements <= 0 && assignementSolver.numMaxTracks <= 0)
				problems.add( pb);
			else smallAssignProblemCreation(assignementSolver.numMaxMeasurements, assignementSolver.numMaxTracks, pb, problems, gateFactor);
		}
		return problems;
	}
	
	protected void smallAssignProblemCreation(int maxNumMeasurements, int maxNumTracks, AssignProblem pb, ArrayList<AssignProblem> problems, double gFactor)
	{
		boolean valid = (maxNumMeasurements <= 0 || pb.detections.size() < maxNumMeasurements);
		valid = valid && (maxNumTracks<= 0 || pb.tracks.size() < maxNumTracks);
		if (valid)
		{
			problems.add(pb);
		}
		else
		{
			//try to reduce gateFactor to reduce problem's size
			double gFactor2 = gFactor*0.75;
			int n_pred = pb.tracks.size();
			int n_meas = pb.detections.size();
			double[][] likelihoods = computeLikelihoodTable(pb.t, pb.tracks, pb.detections, gFactor2);
			double[][] innovations = computeInnovationTable(pb.t, pb.tracks, pb.detections);
			
			ArrayList<HashSet<Integer>[]> accumulator = new ArrayList<HashSet<Integer>[]>(n_pred);		
			boolean[] trackIsBusy = new boolean[n_pred];
			boolean[] measIsBusy = new boolean[n_meas];

//			initialize tracks and measurements to not busy
			for(int j=0; j<n_pred; j++)
				trackIsBusy[j] = false;		
			for(int i=0; i<n_meas; i++)
				measIsBusy[i] = false;		
//			recursive construction of connected tracks/measurements (in validation gates)
			for(int j=0; j<n_pred; j++)
			{ 
				if(!trackIsBusy[j]){
					HashSet[] hs = new HashSet[2];
					hs[0] = new HashSet<Integer>(5); // prediction
					hs[1] = new HashSet<Integer>(5); // measurement
					recursAddTrack(j, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
					accumulator.add(hs);
				}
			}
//			for all measurements (should stay only lonely measurements from now)
			for(int i=0; i<n_meas; i++)
			{
				if(!measIsBusy[i]){
					HashSet hs[] = new HashSet[2];
					hs[0] = new HashSet(5); // prediction
					hs[1] = new HashSet(5); // measurement
					recursAddMeasurement(i, likelihoods, hs[0], hs[1], trackIsBusy, measIsBusy);
					accumulator.add(hs);
				}
			}
//			AssignProblem' creation
			for(Iterator it = accumulator.iterator(); it.hasNext();)
			{
				HashSet[] hs = (HashSet[]) it.next();
				AssignProblem pb2 = new AssignProblem();
				Object[] trackTable = hs[0].toArray();
				for(int i=0; i<trackTable.length;i++){
					pb2.tracks.add(pb.tracks.get(((Integer)trackTable[i]).intValue()));
				}
				Object[] detectTable = hs[1].toArray();
				for(int j=0; j<detectTable.length;j++){
					pb2.detections.add(pb.detections.get(((Integer) detectTable[j]).intValue()));
				}	
				double[][] compactLikelihoods = new double[detectTable.length][trackTable.length];
				double[][] compactInnovations = new double[detectTable.length][trackTable.length];
				for(int j=0; j<detectTable.length;j++)
				{
					for(int i=0; i<trackTable.length;i++)
					{
						compactLikelihoods[j][i] = likelihoods[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
						compactInnovations[j][i] = innovations[((Integer) detectTable[j]).intValue()][((Integer) trackTable[i]).intValue()];
					}
				}
				pb2.likelihoods = compactLikelihoods;
				pb2.innovations = compactInnovations;
				pb2.t = pb.t;
				smallAssignProblemCreation(assignementSolver.numMaxMeasurements, assignementSolver.numMaxTracks, pb2, problems, gFactor2);
			}
		}
	}

	protected Track createTrack(Spot spot, int t)
	{
		Predictor new_predictor = predictor.copyInit();
		trackCount++;
		Track track =  new Track(trackCount, new_predictor, t, gateFactor);
		track.initTrack(spot, t);
		return track;
	}

	protected ArrayList<Track> filterTracks(ArrayList<Track> tracks, int t)
	{
		synchronized(tracks)
		{
			ArrayList<Track> activeTracks = new ArrayList<Track>();
			for (Track track:tracks)
				if (track.isStillActive(maxConsecutivePred))
					activeTracks.add(track);
			return activeTracks;
		}
	}

	public ArrayList<Track> getTracks() {return tracks;}


	protected void initTracks(ArrayList<Spot> spots, int t)
	{
		for (Spot spot:spots)
		{
			Track track = createTrack(spot, t);
			if (track != null)
				synchronized(tracks)
				{tracks.add(track);}
		}
	}
	
	protected void postProcess(AssignProblem pb, int t)
	{
//		Create new tracks
		initTracks(pb.tracksToBeCreated, t);
		// Prolongate lonely tracks
		prolongateTracks(pb.tracksToBeProlongated, t);
	}

	protected void postProcessProblems(ArrayList<AssignProblem> problems, final int t, boolean parallel)
	{
		parallel = false;
		if (parallel)
		{
			LinkedList<Thread> listThreads = new LinkedList<Thread>();
			for (final AssignProblem p: problems)
			{
				Thread b = new PostProcessThr(p, t);
				b.start();
				listThreads.add(b);
			}
			for (Thread thr:listThreads)
				try {
					thr.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			listThreads.clear();
		}
		else
			for (final AssignProblem p: problems)
			{
				postProcess(p, t);
			}
	}


	protected void preProcess(ArrayList<AssignProblem> problems, AssignProblem pb, int t)
	{
		
	}

	protected void preProcessProblems(ArrayList<AssignProblem> problems, final int t, boolean parallel)
	{
		for (final AssignProblem p: problems)
		{
			preProcess(problems, p, t);
		}
	}

	protected void prolongateTracks(ArrayList<Track> tracks, int t)
	{
		for (Track track:tracks)
			track.prolongate(t);
	}

	protected void recursAddMeasurement(int measIndex, double[][] likelihood, HashSet<Integer> pred, HashSet<Integer> meas, boolean[] trackIsBusy, boolean[] measIsBusy){
		if(!measIsBusy[measIndex]){
			measIsBusy[measIndex] = true;
			meas.add(new Integer(measIndex));
			for(int j=0; j<likelihood[measIndex].length; j++){
				if(likelihood[measIndex][j] > 0){
					recursAddTrack(j, likelihood, pred, meas, trackIsBusy, measIsBusy);
				}
			}
		}
	}

	protected void recursAddTrack(int trackIndex, double[][] likelihood, HashSet<Integer> pred, HashSet<Integer> meas, boolean[] trackIsBusy, boolean[] measIsBusy){
		if(!trackIsBusy[trackIndex]){
			trackIsBusy[trackIndex] = true;
			pred.add(new Integer(trackIndex));
			for(int i=0; i<likelihood.length; i++){
				if(likelihood[i][trackIndex] > 0){
					recursAddMeasurement(i, likelihood, pred, meas, trackIsBusy, measIsBusy);
				}
			}
		}
	}
	
	Processor processor = new Processor( 1000000 , 8 );
	
	protected void solveProblems(ArrayList<AssignProblem>  problems, boolean parallel)
	{
		parallel = false;
		if (parallel)
		{			
			//List<Thread> listThreads = new LinkedList<Thread>();

			//System.out.println("solveProblems : size : " + problems.size() );
			
			for (AssignProblem p:problems)
			{
				Thread b = new SolveProblemThr(p);
//				b.start();
				processor.submit( b , false );
//				listThreads.add(b);
			}
			
			while ( processor.isProcessing() )
			{
				ThreadUtil.sleep( 1000 );
				//Thread.sleep( 10 );
			}
			
			
//			for (Thread thr:listThreads)
//				try {
//					thr.join();
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			listThreads.clear();
		}
		else
			for (AssignProblem p:problems)
				assignementSolver.solve(p);
	}

	public void track(int t, Vector<Spot> spotsVector)
	{	
		ArrayList<Spot>  spots =new ArrayList<Spot> (spotsVector);
//		filter active tracks
		ArrayList<Track> activeTracks = filterTracks(tracks, t);
//		PluginConsole.println("active tracks "+activeTracks.size());
		if (activeTracks.size() > 0)
		{
			//compute likelihoodTable
			double[][]  likelihoods = computeLikelihoodTable(t, activeTracks, spots);
			//compute innovations //useful for hypothesis tests pruning
			double[][] innovations = computeInnovationTable(t, activeTracks, spots);
			//create independent problems
			ArrayList<AssignProblem> problems = createIndependentProblems(t, likelihoods, innovations, activeTracks, spots);
			boolean parallel = true;
			preProcessProblems(problems, t, parallel);
			solveProblems(problems, parallel);
			postProcessProblems(problems, t, parallel);
		}
		else
			initTracks(spots, t);
	}
}
