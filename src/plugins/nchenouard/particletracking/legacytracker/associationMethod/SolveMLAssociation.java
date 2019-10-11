package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import java.util.ArrayList;

import plugins.nchenouard.spot.Spot;


/**
 * Solve the assignment problem by local ML maximization, as described in
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

public class SolveMLAssociation extends AssignProblemSolver
{
	public final static int methoId = 0;
	protected final boolean addNewObjects;
	
	public SolveMLAssociation(boolean addNewObjects)
	{
		this.addNewObjects = addNewObjects;
	}
	
	protected void solve1(AssignProblem pb)
	{
		Track  track       = (Track)  pb.tracks.get(0);
		Spot spot = (Spot) pb.detections.get(0);
		track.associate(spot, pb.t);
	}
	
	protected void solve2(AssignProblem pb)
	{
		if (addNewObjects)
			pb.tracksToBeCreated.add(pb.detections.get(0));
	}
	
	protected void solve3(AssignProblem pb)
	{
		pb.tracksToBeProlongated.add(pb.tracks.get(0));
	}
	
	protected void solve4(AssignProblem pb, int num)
	{
		asignOpt(pb, num, num);
	}
	
	protected void solve5(AssignProblem pb, int numTracks, int numDetect)
	{
		asignOpt(pb, numTracks, numDetect);
	}
	
	protected void solve6(AssignProblem pb, int numTracks, int numDetect)
	{
		asignOpt(pb, numTracks, numDetect);
	}
	
	public void asignOpt(AssignProblem pb, int numTracks, int numDetect)
	{
		int[] tm =assignOptMax(pb.likelihoods);
		ArrayList<Integer> usedDetection = new ArrayList<Integer>();
		for(int j=0; j<numTracks; j++)
		{
			Track  track = (Track) pb.tracks.get(j);
			if(tm[j] >= 0)
			{
				track.associate(pb.detections.get(tm[j]), pb.t);
				usedDetection.add(new Integer(tm[j]));
			}
			else
				pb.tracksToBeProlongated.add(track);
		}
		for (int i = 0; i< numDetect; i++)
			if(!usedDetection.contains(new Integer(i)))
				pb.tracksToBeCreated.add(pb.detections.get(i));
	}
	
	public void solve(AssignProblem pb)
	{	
		int numTracks = pb.tracks.size();
		int numDetect = pb.detections.size();

		pb.tracksToBeCreated.clear();
		pb.tracksToBeProlongated.clear();
		
		// case 1 : 1 prediction faces 1 measurement
		// no conflict, the single track selects the single measurement 
		if(numTracks == 1 && numDetect == 1)
		{ 
			solve1(pb);
			return;
		}

		// case 2 : 0 prediction faces 1 measurement
		// a track is created from the measurement.
		// (we can try with default profile to determine the number potential fused detections)

		if(numTracks == 0 && numDetect == 1)
		{ 
			solve2(pb);
			return;
		}
		// case 3 : 1 prediction faces 0 measurement
		// the track is prolongated with prediction
		// we can try to re-detect with profiles detection method

		if(numTracks == 1 && numDetect  == 0)
		{
			solve3(pb);
			return;
		}

		//case 4 : n predictions for n measurements
		//simplification : there is no fused spots nor new spot, there is a one-to-one association between measurements and tracks
		if (numTracks == numDetect)
		{
			solve4(pb, numTracks);
			return;
		}
		//General case : n predictions != m measurements
		else
		{
		//case 5 : n predictions < m measurements
			if(numTracks < numDetect)
			{
				solve5(pb, numTracks, numDetect);
			}
			else
		//case 6 :	n predictions > m mesurements
		//we must find if some tracks has disappeared or if some has fused
			{
				solve6(pb, numTracks, numDetect);
				return;
			}
		}
	}
	
	
	protected int[] assignOptMax(double[][] d)
	{
		if(d.length == 0 || d[0].length == 0)
			return null;
		int trackNumber = d[0].length;
		int measuNumber = d.length;
		int[] tm = new int[trackNumber]; // track-measurement associations
		int[] t = new int[trackNumber]; // tracks indices
		int[] m = new int[measuNumber]; // measurement indices
		for(int i=0; i<trackNumber; i++)
		{
			tm[i] = -2;
			t[i] = i;
		}
		for(int i=0; i<measuNumber; i++)
			m[i] = i;
		int iter = trackNumber;
		while(iter-- > 0)
		{
			int tIndex = -1;
			int mIndex = -1;
			//double opt = Double.MIN_VALUE;
			double opt = 0;
			for(int i=0; i<trackNumber; i++)
			{
				if(t[i] >= 0 )
				{
					for(int j=0; j<measuNumber; j++)
					{
						if(m[j] >= 0)
						{
							if(opt < d[j][i])// && d[j][i] >= 0)
							{
								tIndex = i;
								mIndex = j;
								opt    = d[j][i];
							}
						}
					}
				}
			}
			if((tIndex != -1)&&(mIndex != -1)){
				if(opt >= 0){
					tm[tIndex] = mIndex;
				}else
				tm[tIndex] = -1;
				t[tIndex]  = -1;
				m[mIndex]  = -1;
			}
		}
		return tm;
	} 

	protected int[] assignOptMin(double[][] d)
	{
		if(d.length == 0 || d[0].length == 0)
			return null;
		int trackNumber = d[0].length;
		int measuNumber = d.length;
		int iter = trackNumber;
		int[] tm = new int[trackNumber]; // track-measurement associations
		int[] t = new int[trackNumber]; // tracks indices
		int[] m = new int[measuNumber]; // measurement indices
		for(int i=0; i<trackNumber; i++)
			tm[i] = -2;
		for(int i=0; i<trackNumber; i++)
			t[i] = i;
		for(int i=0; i<measuNumber; i++)
			m[i] = i;
		while(iter-- > 0){
			int tIndex = -1;
			int mIndex = -1;
			double opt= Double.MAX_VALUE;
				for(int i=0; i<trackNumber; i++)
				{
					if(t[i] != -1)
					{
						for(int j=0; j<measuNumber; j++)
						{
							if(m[j] != -1)
							{
								if(opt > d[j][i] && d[j][i] >= 0)
								{
									tIndex = i;
									mIndex = j;
									opt    = d[j][i];
								}			
							}
						}
					}
				}
			if((tIndex != -1)&&(mIndex != -1)){
				if(opt >= 0){
					tm[tIndex] = mIndex;
				}else
					tm[tIndex] = -1;
				t[tIndex]  = -1;
				m[mIndex]  = -1;
			}
		}
		return tm;
	}
}
