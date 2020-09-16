package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import java.util.ArrayList;

import plugins.nchenouard.spot.Spot;

/**
 * Object dedicated the assignment problem tracks vs. detections
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class AssignProblem
{
	AssignProblemSolver solver;
	ArrayList<Track> tracks;
	ArrayList<Track> tracksToBeProlongated;
	ArrayList<Spot> tracksToBeCreated;
	ArrayList<Spot> detections;
	double[][] likelihoods;
	double[][] innovations;
	int t;
	
	public AssignProblem()
	{
		tracks = new ArrayList<Track>();
		detections = new ArrayList<Spot>();
		tracksToBeCreated = new ArrayList<Spot>();
		tracksToBeProlongated = new ArrayList<Track>();
	}
}
