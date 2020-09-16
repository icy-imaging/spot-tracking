package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import java.util.ArrayList;

import plugins.nchenouard.particletracking.filtering.*;
import plugins.nchenouard.spot.Spot;
import Jama.Matrix;

/**
 * Track object
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com). Based on Auguste Genovesio original code.
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
*/

public class Track
{
	class Association
	{
		Matrix state;
		Spot spot;
		boolean isPrediction;
		
		Association(Matrix state, Spot spot, boolean isPrediction)
		{
			this.state = state;
			this.spot = spot;
			this.isPrediction = isPrediction;
		}
	}
		
	protected int id = -1;
	protected ArrayList<Association> associations;
	protected int lastConsecutivePredictions = 0; // nb of consecutive virtual detections
	protected int realValues = 0; // ? may be a boolean telling if the detection is a true one.

	protected Predictor predictor    = null; // Kalman filter or something like that ( imm (...) )
	protected double gateFactor;
	
	protected int firstIndex = -1; // first index in the sequence
	protected int lastIndex  = -1; // last  index in the sequence
	
	public Track(){
		this.associations = new ArrayList<Association>();
		this.firstIndex   = 0;
		this.lastIndex    = 0; 
		gateFactor = 3;
	}

	public Track(int id, Predictor predictor, int firstIndex, double gateFactor){
		this.id           = id;
		this.associations = new ArrayList<Association>();
		this.predictor    = predictor;
		this.firstIndex   = firstIndex;
		this.lastIndex    = firstIndex;
		this.gateFactor = gateFactor;
	}
	
	public void add(Spot spot, boolean isPrediction)
	{
		Matrix measurement = predictor.buildMeasurementMatrix(spot);
		add(measurement, spot, isPrediction);
	}
	
	public void add(Matrix state, Spot spot, boolean isPrediction)
	{
		if(state.getColumnDimension() != 1)
			throw new IllegalArgumentException("state must be a vector");
		if(isPrediction)
			lastConsecutivePredictions++;
		else{
			realValues++;
			lastConsecutivePredictions = 0;
		}
		associations.add(new Association(state, spot, isPrediction));
		lastIndex++;
	}
	
	public void associate(Spot spot, int t)
	{
		predictor.update(spot, t);
		add(getEstimatedState(), spot, false);
	}
	
	public Matrix getEstimatedState(){return predictor.getCurrentEstimatedState();}

	public int getFirstIndex(){return firstIndex;}
	
	public int getId(){ return id;}
	
	public int getLastConsecutivePredictions(){return lastConsecutivePredictions;}
	
	public int getLastIndex(){return lastIndex;}
	
	public Matrix getPredictedState(){return predictor.getCurrentPredictedState();}
	
	public Matrix getStateAtFrame(int frameIndex)
	{
		if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex-firstIndex) || associations.get(frameIndex-firstIndex) == null)
			return null;
		return associations.get(frameIndex-firstIndex).state;
	}
	
	public Spot getSpotAtFrame(int frameIndex)
	{
		if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex-firstIndex) || associations.get(frameIndex-firstIndex) == null)
			return null;
		return associations.get(frameIndex - firstIndex).spot;
	}
	
	//return the estimated state at a given time as a spot
	public Spot getStateAsSpot(Matrix state)
	{
		return predictor.buildSpotFromState(state);
	}

	public double getTotalGateLikelihood()
	{
		return predictor.getTotalGateLikelihood(true, gateFactor);
	}
	
	public boolean isPredictionAtFrame(int frameIndex)
	{
		if (frameIndex < firstIndex || frameIndex > lastIndex || associations.size() <= (frameIndex-firstIndex) || associations.get(frameIndex-firstIndex) == null)
			return true;
		return associations.get(frameIndex - firstIndex).isPrediction;
	}
	
	public void initTrack(Spot firstSpot, int t)
	{
		predictor.initWithFirstElement(firstSpot, t);
		add(firstSpot, false);
	}
	
	public boolean isStillActive(int maxPred) {
		if (lastConsecutivePredictions < maxPred)
			return true;
		else return false;
	}
	
	public double likelihood(Spot s, boolean gated, double gateFactor){return predictor.likelihood(s,gated, gateFactor);}
	
	public double loglikelihood(Spot s){return predictor.loglikelihood(s);}
	
	public double minLikelihoodInGate()
	{
		return predictor.getCurrentMinLikelihoodInGate(gateFactor);
	}
	
	public double normalizedInnovation(Spot s){return predictor.normalizedInnovation(s);}
	
	public void prolongate(int t)
	{
		predictor.update((Spot)null, t);
		add(getPredictedState(), predictor.getCurrentPredictedStateAsSpot(), true);
	}
	
	public LikelihoodMap getLikelihoodMap(double gateFactor, int t)
	{
		if (predictor instanceof IMM2D)
			return ((IMM2D)predictor).getLikelihoodMap(gateFactor, t);
		if (predictor instanceof IMM3D)
			return ((IMM3D)predictor).getLikelihoodMap(gateFactor, t);
		if (predictor instanceof KalmanFilter)
			return ((KalmanFilter)predictor).getLikelihoodMap(gateFactor, t);
		else return null;
	}
	
	public LikelihoodMap[] getLikelihoodMapsIMM(double gateFactor, int t)
	{
		if (predictor instanceof IMM2D)
			return ((IMM2D)predictor).getLikelihoodMapsIMM(gateFactor, t);
		if (predictor instanceof IMM3D)
			return ((IMM3D)predictor).getLikelihoodMapsIMM(gateFactor, t);
		else return null;
	}

	public double[] getIMMWeights() {
		if (predictor instanceof IMM2D)
			return ((IMM2D)predictor).getWeights();
		if (predictor instanceof IMM3D)
			return ((IMM3D)predictor).getWeights();
		return null;
	}
}
