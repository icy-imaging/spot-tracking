package plugins.nchenouard.particletracking.MHTracker;


import plugins.nchenouard.particletracking.VirtualSpot;
import plugins.nchenouard.particletracking.filtering.Predictor;

public class MHTrack3s extends MHTrack2s
{

	/** 
	 * Track with three perceivability states: 
	 * 	- state 0: do not exist
	 * - state 1: exists
	 * - state 2: exists but has temporarily disappeared
	 * 
	 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
	 * 
	 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
	 * @version 3.1
	 * @date 2013-11-13
	 * @license gpl v3.0
	 * */
	
	public double p20 = 0.45;
	public double p21 = 0.45;
	public double p22 = 0.1;
	public double p12 = 0.05;
	public double p02 = 0;
	public double p1;
	public double p2;
	public double p1Pred;
	public double p2Pred;
	
	MHTrack3s(HMMMHTracker tracker, Predictor predictor, Association association, Family family) {
		super(0.5, tracker, predictor, association, family);
		p00 = 1;
		p01 = 0.0;
		p10 = 0.05;
		p11 = 0.9;
		
		this.p1 = pObs;
		this.p2 = 0;
		this.p1Pred = p11*p1 + p21*p2 + p01*(1-pObs);
		this.p2Pred = p22*p2 + p12*p1 + p02*(1-pObs);
		this.pPredObs = p1Pred + p2Pred;
	}
	
	private MHTrack3s(){}
	
	@Override
	public MHTrack2s copy()
	{
		MHTrack3s mtrack = new MHTrack3s();
		mtrack.predictor = this.predictor.copy();
		mtrack.likelihood = this.likelihood;
		mtrack.associationLikelihood = this.associationLikelihood;
		mtrack.score = this.score;
		mtrack.family = this.family;
		mtrack.association = this.association;
//		mtrack.followingSegments = (LinkedList<MHTrack2s>) this.followingSegments.clone();
		mtrack.precedingSegment = this.precedingSegment;
		mtrack.tracker = this.tracker;
		mtrack.hypothesis = this.hypothesis;
		mtrack.confirmed = this.confirmed;
		mtrack.terminated = this.terminated;
		mtrack.pObs = this.pObs;
		mtrack.pPredObs = this.pPredObs;
		mtrack.p1 = this.p1;
		mtrack.p1Pred = this.p1Pred;
		mtrack.p2 = this.p2;
		mtrack.p2Pred = this.p2Pred;
		return mtrack;
	}
	
	@Override
	public void associate(Association a, int t)
	{
		association = a;
		double kL = tracker.totalGateLikelihood*kineticLikelihood(a.spot, true, tracker.gateFactor);
		double faL = falseAlarmLikelihood();
		
		associationLikelihood = kL*p1Pred + faL*(1-pPredObs + p2Pred);

		likelihood *=associationLikelihood;
		score += Math.log(associationLikelihood);	
		
		p1 = kL*p1Pred/associationLikelihood;
		p2 = faL*p2Pred/associationLikelihood;
		pObs = p1+p2;
		p1Pred =  p11*p1 + p21*p2 + p01*(1-pObs);
		p2Pred = p22*p2 + p12*p1 + p02*(1-pObs);
		pPredObs = p1Pred + p2Pred;
		if (pObs > pc)
			this.confirmed = true;
		else if(pObs < pt)
			this.terminated = true;
	}
	
	@Override
	public void prolongate(int t)
	{
		VirtualSpot pred = this.predictor.getCurrentPredictedStateAsSpot();
		association = new Association(null, pred, true, t, 0);
		
		double kL = 1-tracker.totalGateLikelihood;
		double faL = 1;
		
		associationLikelihood = kL*p1Pred + faL*(1-pPredObs + p2Pred);
		likelihood *= associationLikelihood;
		score += Math.log(associationLikelihood);	
		
		p1 = kL*p1Pred/associationLikelihood;
		p2 = faL*p2Pred/associationLikelihood;
		pObs = p1+p2;
		p1Pred =  p11*p1 + p21*p2 + p01*(1-pObs);
		p2Pred = p22*p2 + p12*p1 + p02*(1-pObs);
		pPredObs = p1Pred + p2Pred;
	
		if (pObs > pc)
			this.confirmed = true;
		else if(pObs < pt)
			this.terminated = true;
	}
}
