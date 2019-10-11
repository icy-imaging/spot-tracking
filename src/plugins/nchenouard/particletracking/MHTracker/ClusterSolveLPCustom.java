package plugins.nchenouard.particletracking.MHTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;
import plugins.nchenouard.linearprogrammingfullsimplex.CanonicalSimplexProgram;
import plugins.nchenouard.linearprogrammingfullsimplex.SimplexLEXICO;
import plugins.nchenouard.particletracking.MHTracker.LinearProgramming.HierarchicalLP;
import plugins.nchenouard.particletracking.MHTracker.LinearProgramming.HierarchicalProcedureResult;

/** 
 * Specialized Cluster object which contains some Linear Programming (LP) methods to solve the tracking problem using a custom implementation of the optimizer
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 */

public class ClusterSolveLPCustom extends Cluster
{
	double pFA;

	public ClusterSolveLPCustom(HMMMHTracker tracker, int firstT, int lastT, int K, double pFA) {
		super(tracker, firstT, lastT, K);
		this.pFA = pFA;
	}
	@Override
	public void buildAndApplyBestHypTree(final int t2)
	{	
		int t = t2;
		int firstTtemp = -1;
		boolean buildAndApplyBestHyp = false;
		for (Integer i:concurrentFamilies.keySet())
		{
			if(firstTtemp<0 || i.intValue() < firstTtemp)
				firstTtemp =  i.intValue();
		}
		buildAndApplyBestHyp = (firstTtemp >=0 && firstTtemp<=t-K);
		final int firstT = firstTtemp;
		if (buildAndApplyBestHyp)
		{
			println("-----build best Hyp");
			println("different root time for families "+concurrentFamilies.size());
			//for each family build global hyps and only keep the best one
			println("firstT "+firstT + " first time "+firstT+"  time "+t);
			for(Entry<Integer, ArrayList<Family>> e:concurrentFamilies.entrySet())
				println("time "+((Integer)e.getKey()).intValue() + "numFamily " + ((ArrayList<Family>)e.getValue()).size() );
			final int currentT = firstT;
			//build  the set of mandatories and optional families with root at time currentT
			final ArrayList<Family> fList = concurrentFamilies.get(new Integer(currentT));
			ArrayList<Family> mandatoryFamilies = new ArrayList<Family>(); //families that have a previous validated tracks are mandatory 
			ArrayList<Family> optionalFamilies = new ArrayList<Family>(); //families that don't have a previous validated tracks are optional
			for (Family f:fList)
			{
				if (f.rootTrack == null)
					optionalFamilies.add(f);//families that do not have a previous ValidatedTrack are optional
				else
				{
					mandatoryFamilies.add(f); //families that already have a previous ValidatedTrack are mandatory
					println("mandatory family : begin = "+f.rootTrack.associations.getFirst().t + " root time "+f.rootTime);
				} 
			}
			//			long tOpt = System.currentTimeMillis();			
			bestHypSafe.bestHyp  = solvePbWithLPCustomHierarchical3(firstT, t);			
//			GlobalHypothesis refHyp = solvePbWithLpLibrary(firstT, t);
			if (bestHypSafe.bestHyp == null)
			{
				SolveMLAssociation mlSolver = new SolveMLAssociation();
				mlSolver.solve(firstT, t, concurrentFamilies, mandatoryFamilies, optionalFamilies);
				bestHypSafe.bestHyp  = mlSolver.getHypothesis(this , firstT, t, tracker);
			}
//			else
//			{
//				if (bestHypSafe.bestHyp.score != refHyp.score)
//				{
//					System.out.println("Diff score: "+bestHypSafe.bestHyp.score+" | "+refHyp.score);
//					bestHypSafe.bestHyp  = solvePbWithLPCustomHierarchical3(firstT, t);	
//				}
////				else
////					System.out.println("Same score: "+bestHypSafe.bestHyp.score+" | "+refHyp.score);		
//			}
		}
		applyBestHyp(t);
		for (Collection<Family>c:concurrentFamilies.values())
			for (Family f:c)
				f.hypotheses.clear();
	}
	
	public GlobalHypothesis solvePbWithLPCustomHierarchical3(int firstT, int t)
	{
		GlobalHypothesis gh = null;
		//first build a list of detections
		ArrayList<Association> associations = new ArrayList<Association>();
		ArrayList<Association> mandatoryAssociations = new ArrayList<Association>();
		for (ArrayList<Family> flist:this.concurrentFamilies.values())
		{
			for (Family f:flist)
			{
				// first we add mandatory families
				if(f.rootTrack != null)//add the previous validated association
				{
					Association rootAssociation = f.rootNode.association;
					if (rootAssociation != null && !associations.contains(rootAssociation)  && ! mandatoryAssociations.contains(rootAssociation))
					{
						mandatoryAssociations.add(rootAssociation);
					}
					else
						System.out.println("Error: rootrack without association!");
				}
			}
			for (Family f:flist)
			{	
				for (Association a:f.usedAssociations)
				{
					//add all the associations of the family
					if(!associations.contains(a) && ! mandatoryAssociations.contains(a))
						associations.add(a);
				}
			}
		}
		//then build the table of tuples, along with the list of scores
		ArrayList<boolean[]> tuples = new ArrayList<boolean[]>();
		ArrayList<Double> scores = new ArrayList<Double>();
		ArrayList<Object> hypList = new ArrayList<Object>(); 
		ArrayList<Boolean> isPredList = new ArrayList<Boolean>(); 
		double logPFA = Math.log(pFA);
		for (ArrayList<Family> flist:this.concurrentFamilies.values())
		{	
			for (Family f:flist)
			{
				// check that the family is feasible: its root is not used by a mandatory track
				if (f.rootTrack != null || ! mandatoryAssociations.contains(f.rootNode.association))
					for (Hypothesis h:f.hypotheses)
					{
						// build a table indicating which association the hypothesis is using
						boolean[] detectionTab = new boolean[associations.size() + mandatoryAssociations.size()];
						double s = h.leaf.score;
						// and correct leaf score for detection it is using						
						int cnt = 0;
						for (Association a:associations)
						{
							if (h.usedAssociations.contains(a))
							{
								detectionTab[cnt] = true;
								if (!a.isPrediction)
									s -= logPFA;
							}
							cnt++;
						}
						for (Association a:mandatoryAssociations)
						{
							Association rootAssociation = f.rootNode.association;
							if (rootAssociation != null && rootAssociation == a)
							{
								detectionTab[cnt] = true;
								if (!a.isPrediction)
									s -= logPFA;
							}
							else
								detectionTab[cnt] = false;
							cnt++;
						}
						tuples.add(detectionTab);
						scores.add(s);
						//						scores.add(new Double(h.leaf.score));
						hypList.add(h);
						isPredList.add(new Boolean(false));
					}
			}
		}
	
		// now create the corresponding lp problem
		int numVariables = tuples.size(); // the number of variables is the number of tuples
		int numConstraintsDetections = tuples.get(0).length; // the number of constraints is the number of detections (each is used once)
	
		//		Simplex solving:
		//		 *  N-length vector c, solve the  LP { max cx : Ax <= b, x >= 0 }.
		//		 *  Assumes that b >= 0 so that x = 0 is a basic feasible solution.
		//		since we want here x to be boolean, we relax it to be in the interval [0 1]
		//		this adds one constraint per variable : x <= 1
		//		for each tuple we also have constraints of the form Ax <= 1 if the detection is not mandatory
		//		otherwise the constraint is on the form AX = 1
		ArrayList<double[]> Alist = new ArrayList<double[]>(numConstraintsDetections + numVariables);
		ArrayList<Boolean> isEqList = new ArrayList<Boolean>(numConstraintsDetections + numVariables);
		// add a constraint per variable (tuple indicator) as each variable is <= 1
		for (int n = 0; n < numVariables; n++)
		{
			double[] constraint = new double[numVariables];
			constraint[n] = 1d;
			Alist.add(constraint);
			isEqList.add(false);
		}
		// now add one constraint per detection, checking that it is not already in 
		for (int n = 0; n < numConstraintsDetections; n++)
		{	
			boolean isEquality = (n >= associations.size());
			double[] constraint = new double[numVariables];
			int num1 = 0;
			for (int i = 0; i < numVariables; i++)
				if (tuples.get(i)[n])
				{
					constraint[i] = 1 ;
					num1++;
				}
			if (num1 > 0)
			{
				// check that the contraint does not already exist
				boolean alreadyExists = false;
				int idx = -1;
				for (int kk = 0; kk < Alist.size(); kk++)
				{
					double[] c = Alist.get(kk);
					boolean e = true;
					for (int k = 0; k < numVariables; k++)
						if (c[k] != constraint[k])
						{
							e = false;
							break;
						}
					if (e)
					{
						alreadyExists = true;
						idx = kk;
						break;
					}
				}
				if (!alreadyExists)
				{
					Alist.add(constraint); // add the constraint
					isEqList.add(isEquality);
				}
				else
				{
					if (isEquality)
						isEqList.set(idx, true);// set the previously existing constraint as an equality
				}
			}
			// else no need to add the constraint, it is either only 0s
		}
		int totalNumConstraints = Alist.size();
		double[] c = new double[numVariables]; // score vector s.t. problem is argmax_x cx
		double[] b = new double[totalNumConstraints];
		double[][] A = new double[totalNumConstraints][numVariables]; // constraint matrix st Ax <= b
		boolean[] equalityConstraints = new boolean[totalNumConstraints];
		// set the constraints
		for (int i = 0; i < totalNumConstraints; i++)
		{
			A[i] = Alist.get(i);
			b[i] = 1d;
			equalityConstraints[i] = isEqList.get(i);
		}
//		set objective function
		for (int v = 0; v < numVariables; v++)
			c[v] = scores.get(v);
//		
//		// set objective function
//		for (int v = 0; v < numVariables; v++)
//			c[v] = scores.get(v);
//		// add constraints: each detection is used at max once
//		for (int n = 0; n < numConstraintsDetections; n++)
//		{
//			// each constraint is a:
//			// * double table defining a linear combination of variables
//			// * constraint type: equality, greater/lesser than
//			// * right hand side of the equation (double)
//			for (int i = 0; i < numVariables; i++)
//				if (tuples.get(i)[n])
//					A[n][i] = 1 ;
//			if(n >= associations.size()) // mandatory associations yield equality constraints
//				equalityConstraints[n] = true;
//		}
//		// each variable is in the interval [0 1]
//		for (int n = 0; n < numVariables; n++)
//			A[n + numConstraintsDetections][n] = 1;
//		double[] b = new double[totalNumConstraints];
//		for (int i = 0; i < b.length; i++)
//			b[i] = 1;
		
		
		
		// solve the problem by a hierarchy of problems
		CanonicalSimplexProgram program = new  SimplexLEXICO(A, b, c, true, equalityConstraints);
		
		long tCustom1 = System.currentTimeMillis();
		HierarchicalProcedureResult  res = HierarchicalLP.hierarchicalBinarySimplex(program, true);
		long tCustom2 = System.currentTimeMillis();
		if (res != null)
		{
			double[] x = res.solution;
			// build the global Hypothesis corresponding to the lp solution
			gh = new GlobalHypothesis(firstT, t, tracker);
			for (int cnt = 0; cnt < numVariables; cnt++)
			{
				if (x[cnt] > 0.5)
				{
					// the corresponding object is used
					if (hypList.get(cnt) instanceof Hypothesis)
						gh.addHypothesis((Hypothesis) hypList.get(cnt));
				}
			}
			// penalize false alarms
			gh.penalizeAllFD(pFA, super.realAssociations);
			
			/////////////////////////////////////////////////////////////////
			boolean compareWithLPsolve = false;
			if (compareWithLPsolve)
			{
				long tLP1 = System.currentTimeMillis();

				/////////////////////////////////////////////////////////////
				// now compare with what we would get with the LP library //
				////////////////////////////////////////////////////////////
				LpSolve solver = null;
				try {
					solver = LpSolve.makeLp(0, numVariables);
				} catch (LpSolveException e1) {
					e1.printStackTrace();
					return null;
				}
				double[] objFn = new double[numVariables + 1];
				for (int v = 0; v < numVariables; v++)
					objFn[v + 1] = c[v];
				try {
					solver.setObjFn(objFn);
					//solver.printLp();
				} catch (LpSolveException e) {
					e.printStackTrace();
					return null;
				}
				for (int i = 0; i < A.length; i++)
				{
					int constrType = LpSolve.LE;
					if (equalityConstraints[i])
						constrType = LpSolve.EQ;
					double[] constr = new double[numVariables + 1];
					System.arraycopy(A[i], 0, constr, 1, numVariables);
					try {
						solver.addConstraint(constr, constrType, b[i]);
					} catch (LpSolveException e) {
						e.printStackTrace();
						return null;
					}
				}
				// force variables to be binary
				for (int v = 0; v < numVariables; v++)
				{
					try {
						solver.setBinary(v + 1, true);
					} catch (LpSolveException e) {
						e.printStackTrace();
						return null;
					}
				}
				// maximization problem
				solver.setMaxim();
				solver.setVerbose(LpSolve.CRITICAL);
				// solve the problem
				try {
					//			solver.printLp();
					solver.solve();
				} catch (LpSolveException e) {
					e.printStackTrace();
					return null;
				}
				// build the global Hypothesis corresponding to the lp solution
				GlobalHypothesis gh2 = new GlobalHypothesis(firstT, t, tracker);
				double[] sol = null;
				try {
					sol = solver.getPtrVariables();
	
					if (solver.getStatus() != LpSolve.OPTIMAL)
						System.out.println("NON OPTIMAL");
					for (int cnt = 0; cnt < sol.length; cnt++)
					{
						int idx = cnt;
						if (sol[idx] > 0.5)
						{
							// the corresponding object is used
							if (hypList.get(idx) instanceof Hypothesis)
								gh2.addHypothesis((Hypothesis) hypList.get(idx));
						}
					}
				} catch (LpSolveException e) {
					e.printStackTrace();
					return null;
				}
				gh2.penalizeAllFD(pFA, super.realAssociations);
				// delete the problem and free memory
				solver.deleteLp();
				long tLP2 = System.currentTimeMillis();
				if((tCustom2 - tCustom1) > 10 || (tLP2 - tLP1) > 10)
					System.out.println("Time diff "+(tCustom2 - tCustom1)+" "+(tLP2 - tLP1));
				if (gh2.score != gh.score)
				{
					System.out.println("DIFF SCORE " + gh.score+" | " + gh2.score + " "+ res.hadNonBinary);
					CanonicalSimplexProgram program2 = new  SimplexLEXICO(A, b, c, true, equalityConstraints);
					HierarchicalProcedureResult  res2 = HierarchicalLP.hierarchicalBinarySimplex(program2, true);
					if (res2 == null)
						System.out.println("RES2 null");
				}
//				else
//					System.out.println("SAME");
			}
		}
		else // optimization failed for some reason (should not happen) then use the simple instantaneoux max likelihood solution
		{
			// get the instantaneous max likelihood hypothesis
			ArrayList<Family> mandatoryFamilies = new ArrayList<Family>(); //families that have a previous validated tracks are mandatory 
			ArrayList<Family> optionalFamilies = new ArrayList<Family>(); //families that don't have a previous validated tracks are optional
			final ArrayList<Family> fList = concurrentFamilies.get(new Integer(firstT));
			for (Family f:fList)
			{
				if (f.rootTrack == null)
					optionalFamilies.add(f);//families that do not have a previous ValidatedTrack are optional
				else
				{
					mandatoryFamilies.add(f); //families that already have a previous ValidatedTrack are mandatory
					println("mandatory family : begin = " + f.rootTrack.associations.getFirst().t + " root time " + f.rootTime);
				} 
			}
			SolveMLAssociation mlSolver = new SolveMLAssociation();
			mlSolver.solve(firstT, t, concurrentFamilies, mandatoryFamilies, optionalFamilies);
			bestHypSafe.bestHyp  = mlSolver.getHypothesis(this , firstT, t, tracker);
		}
		return gh;
	}
	
//	public GlobalHypothesis solvePbWithLpLibrary(int firstT, int t)
//	{
//		//first build a list of detections
//		ArrayList<Association> associations = new ArrayList<Association>();
//		ArrayList<Association> mandatoryAssociations = new ArrayList<Association>();
//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
//		{
//			for (Family f:flist)
//			{
//				// first we add mandatory families
//				if(f.rootTrack != null)//add the previous validated association
//				{
//					Association rootAssociation = f.rootNode.association;
//					if (rootAssociation != null && !associations.contains(rootAssociation)  && ! mandatoryAssociations.contains(rootAssociation))
//					{
//						mandatoryAssociations.add(rootAssociation);
//					}
//					else
//						System.out.println("Error: rootrack without association!");
//				}
//			}
//			for (Family f:flist)
//			{	
//				for (Association a:f.usedAssociations)
//				{
//					//add all the associations of the family
//					if(!associations.contains(a) && ! mandatoryAssociations.contains(a))
//						associations.add(a);
//				}
//			}
//		}
//		//then build the table of tuples, along with the list of scores
//		ArrayList<boolean[]> tuples = new ArrayList<boolean[]>();
//		ArrayList<Double> scores = new ArrayList<Double>();
//		ArrayList<Object> hypList = new ArrayList<Object>(); 
//		ArrayList<Boolean> isPredList = new ArrayList<Boolean>(); 
//
//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
//		{	
//			for (Family f:flist)
//			{
//				// check that the family is feasible: its root is not used by a mandatory track
//				if (f.rootTrack!=null || ! mandatoryAssociations.contains(f.rootNode.association))
//					for (Hypothesis h:f.hypotheses)
//					{
//						boolean[] detectionTab = new boolean[associations.size()+mandatoryAssociations.size()];
//						int cnt = 0;
//						for (Association a:associations)
//						{
//							detectionTab[cnt] = h.usedAssociations.contains(a);
//							cnt++;
//						}
//						for (Association a:mandatoryAssociations)
//						{
//							Association rootAssociation = f.rootNode.association;
//							if (rootAssociation!=null && rootAssociation==a)
//							{
//								detectionTab[cnt] = true;						
//							}
//							else
//								detectionTab[cnt] = false;
//							cnt++;
//						}
//						tuples.add(detectionTab);
//						scores.add(new Double(h.leaf.score));
//						hypList.add(h);
//						isPredList.add(new Boolean(false));
//					}
//			}
//		}
//		//then add dummy tracks for virtual spots and for false detections
//		for (int cnt = 0; cnt < associations.size(); cnt++)
//		{
//			boolean[] detectionTab = new boolean[associations.size()+mandatoryAssociations.size()];
//			detectionTab[cnt] = true;
//			tuples.add(detectionTab);
//			if (associations.get(cnt).isPrediction)
//			{
//				scores.add(new Double(0));//no penalty for using a virtual measurement with a dummy track
//				isPredList.add(new Boolean(true));
//			}
//			else
//			{
//				scores.add(new Double(Math.log(pFA)));//association is false
//				isPredList.add(new Boolean(false));
//			}
//			hypList.add(associations.get(cnt));
//		}
//
//		// now create the corresponding lp problem
//		int numVariables = tuples.size(); // the number of variables is the number of tuples
//		int numConstraints = tuples.get(0).length; // the number of constraints is the number of detections (each is used once)
//		LpSolve solver;
//		try {
//			//solver = LpSolve.makeLp(numConstraints, numVariables);
//			solver = LpSolve.makeLp(0, numVariables);
//		} catch (LpSolveException e1) {
//			e1.printStackTrace();
//			return null;
//		}
//		// set objective function
//		// this is a linear combination of variables to maximize
//		// be careful: there is an issue with the library, first index is 1
//		// so we add a dummy score
//		double[] objFn = new double[numVariables + 1];
//		for (int v = 0; v < numVariables; v++)
//			objFn[v + 1] = scores.get(v);
//		try {
//			solver.setObjFn(objFn);
//			//solver.printLp();
//		} catch (LpSolveException e) {
//			e.printStackTrace();
//			return null;
//		}
//		// add constraints: each detection is used once
//		for (int n = 0; n < numConstraints; n++)
//		{
//			// each constraint is a:
//			// * double table defining a linear combination of variables
//			// * constraint type: equality, greater/lesser than
//			// * right hand side of the equation (double)
//			// there is a probleme with the library: we have to start at index 1
//			double[] eq = new double[numVariables + 1];
//			for (int i = 0; i < numVariables; i++)
//			{
//				boolean b = tuples.get(i)[n];
//				if (b)
//					eq[i + 1] = 1;
//				else
//					eq[i + 1] = 0;
//			}
//			int constrType = LpSolve.EQ;// equality
//			double rh = 1;
//			try {
//				solver.addConstraint(eq, constrType, rh);
//			} catch (LpSolveException e) {
//				e.printStackTrace();
//				return null;
//			}
//		}
//		// force variables to be binary
//		for (int v = 0; v < numVariables; v++)
//		{
//			try {
//				solver.setBinary(v+1, true);
//			} catch (LpSolveException e) {
//				e.printStackTrace();
//				return null;
//			}
//		}
//		// maximization problem
//		solver.setMaxim();
//		// set level of display
//		//possible values:
//		// NEUTRAL
//		// CRITICAL
//		// SEVERE
//		// IMPORTANT
//		// NORMAL
//		// DETAILED
//		// FULL
//		solver.setVerbose(LpSolve.CRITICAL);
//		// solve the problem
//		try {
//			//			solver.printLp();
//			solver.solve();
//		} catch (LpSolveException e) {
//			e.printStackTrace();
//			return null;
//		}
//		// build the global Hypothesis corresponding to the lp solution
//		GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
//		try {
//			double[] sol = solver.getPtrVariables();
//
//			if (solver.getStatus() != LpSolve.OPTIMAL)
//			{
//				System.out.println("NON OPTIMAL");
//			}
//			for (int cnt = 0; cnt < sol.length; cnt++)
//			{
//				int idx = cnt;
//				//int idx = solver.getOrigIndex(cnt);
//				//tmpScore+=(sol[idx]*objFn[idx+1]);//objective function is shifted in objFn because of the error of first idx
//				if (sol[idx] > 0.5)
//				{
//					// the corresponding object is used
//					if (hypList.get(idx) instanceof Hypothesis)
//					{
//						gh.addHypothesis((Hypothesis) hypList.get(idx));
//						//	tmpScore+=((Hypothesis) hypList.get(cnt)).leaf.score;
//					}
//					//else
//					//{
//					//	if (!isPredList.get(cnt))
//					//		tmpScore+=Math.log(pFA);
//					//}
//				}
//			}
//		} catch (LpSolveException e) {
//			e.printStackTrace();
//			return null;
//		}
//		// penalize false alarms
//		//for (int ttmp = firstT; ttmp<=t; ttmp++)
//		//	penalizeFD(gh, ttmp);
//		gh.penalizeAllFD(pFA, super.realAssociations);
//		// delete the problem and free memory
//		solver.deleteLp();
//		//System.out.println("GHlp score = "+gh.score);
//		//System.out.println("tmp score = "+tmpScore);
//		return gh;
//	}

	//	public GlobalHypothesis solvePbWithLPCustomHierarchical(int firstT, int t, GlobalHypothesis initHypothesis)
	//	{
	//		//first build a list of detections
	//		ArrayList<Association> associations = new ArrayList<Association>();
	//		ArrayList<Association> mandatoryAssociations = new ArrayList<Association>();
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{
	//			for (Family f:flist)
	//			{
	//				// first we add mandatory families
	//				if(f.rootTrack!=null)//add the previous validated association
	//				{
	//					Association rootAssociation = f.rootNode.association;
	//					if (rootAssociation!=null && !associations.contains(rootAssociation)  && ! mandatoryAssociations.contains(rootAssociation))
	//					{
	//						mandatoryAssociations.add(rootAssociation);
	//					}
	//					else
	//						System.out.println("Error: rootrack without association!");
	//				}
	//			}
	//			for (Family f:flist)
	//			{	
	//				for (Association a:f.usedAssociations)
	//				{
	//					//add all the associations of the family
	//					if(!associations.contains(a) && ! mandatoryAssociations.contains(a))
	//						associations.add(a);
	//				}
	//			}
	//		}
	//		//then build the table of tuples, along with the list of scores
	//		ArrayList<boolean[]> tuples = new ArrayList<boolean[]>();
	//		ArrayList<Double> scores = new ArrayList<Double>();
	//		ArrayList<Object> hypList = new ArrayList<Object>(); 
	//		ArrayList<Boolean> isPredList = new ArrayList<Boolean>(); 
	//		double logPFA = Math.log(pFA);
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{	
	//			for (Family f:flist)
	//			{
	//				// check that the family is feasible: its root is not used by a mandatory track
	//				if (f.rootTrack!=null || ! mandatoryAssociations.contains(f.rootNode.association))
	//					for (Hypothesis h:f.hypotheses)
	//					{
	//						// build a table indicating which association the hypothesis is using
	//						boolean[] detectionTab = new boolean[associations.size() + mandatoryAssociations.size()];
	//						double s = h.leaf.score;
	//						// and correct leaf score for detection it is using						
	//						int cnt = 0;
	//						for (Association a:associations)
	//						{
	//							if (h.usedAssociations.contains(a))
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							cnt++;
	//						}
	//						for (Association a:mandatoryAssociations)
	//						{
	//							Association rootAssociation = f.rootNode.association;
	//							if (rootAssociation != null && rootAssociation == a)
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							else
	//								detectionTab[cnt] = false;
	//							cnt++;
	//						}
	//						tuples.add(detectionTab);
	//						scores.add(s);
	//						//						scores.add(new Double(h.leaf.score));
	//						hypList.add(h);
	//						isPredList.add(new Boolean(false));
	//					}
	//			}
	//		}
	//
	//		// now create the corresponding lp problem
	//		int numVariables = tuples.size(); // the number of variables is the number of tuples
	//		int numConstraints = tuples.get(0).length; // the number of constraints is the number of detections (each is used once)
	//
	//		//		Simplex solving:
	//		//		 *  N-length vector c, solve the  LP { max cx : Ax <= b, x >= 0 }.
	//		//		 *  Assumes that b >= 0 so that x = 0 is a basic feasible solution.
	//		//		since we want here x to be boolean, we relax it to be in the interval [0 1]
	//		//		this adds one constraint per variable : x <= 1
	//		//		for each tuple we also have constraints of the form Ax <= 1 if the detection is not mandatory
	//		//		otherwise the constraint is on the form AX = 1
	//		int totalNumConstraints = numConstraints + numVariables;
	//
	//		double[] c = new double[numVariables]; // score vector s.t. problem is argmax_x cx
	//		double[][] A = new double[totalNumConstraints][numVariables]; // constraint matrix st Ax <= b
	//		boolean[] equalityConstraints = new boolean[totalNumConstraints];
	//		// set objective function
	//		for (int v = 0; v < numVariables; v++)
	//			c[v] = scores.get(v);
	//
	//		// add constraints: each detection is used at max once
	//		for (int n = 0; n < numConstraints; n++)
	//		{
	//			// each constraint is a:
	//			// * double table defining a linear combination of variables
	//			// * constraint type: equality, greater/lesser than
	//			// * right hand side of the equation (double)
	//			for (int i = 0; i < numVariables; i++)
	//			{
	//				if (tuples.get(i)[n])
	//					A[n][i] = 1 ;
	//			}
	//			if(n >= associations.size()) // mandatory associations yield equality constraints
	//				equalityConstraints[n] = true;
	//			//			b[n] = 1;
	//		}
	//		// each variable is in the interval [0 1]
	//		for (int n = 0; n < numVariables; n++)
	//			A[n + numConstraints][n] = 1;
	//
	//		// modify the constraints and the scores to shift the problem on the initial solution
	//
	//		// create the initial solution
	//		boolean[] initSolutionArray = new boolean[numVariables];
	//		for (Hypothesis h:initHypothesis.hyps)
	//		{
	//			int idx = hypList.indexOf(h);
	//			initSolutionArray[idx] = true;
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		//		System.out.print("x0 = [");
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//			if (initSolutionArray[i])
	//		//				System.out.print("1, ");
	//		//			else
	//		//				System.out.print("0, ");
	//		//		System.out.println("];");
	//		//		
	//		double v = 0;
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//			if (initSolutionArray[i])
	//		//				v += c[i];
	//		//		System.out.println("value = "+ v);
	//		//		
	//		double[] constraintValues = new double[totalNumConstraints];
	//		for (int j = 0; j < totalNumConstraints; j++)
	//		{
	//			v = 0;
	//			for (int i = 0; i < initSolutionArray.length; i++)
	//				if (initSolutionArray[i])
	//					v += A[j][i];
	//			constraintValues[j] = v;
	//			//			if (equalityConstraints[j])
	//			//				System.out.println("EQ "+v);
	//			//			else
	//			//				System.out.println("LEQ "+v);
	//		}
	//
	//		//		double[] cSave = c.clone();
	//		double[][] ASave = new double[A.length][];
	//		for (int i = 0; i < A.length; i++)
	//			ASave[i] = A[i].clone();
	//		for (int i = 0; i < numVariables; i++)
	//		{
	//			if (initSolutionArray[i])
	//			{
	//				c[i] = - c[i];
	//				for (int j = 0; j < A.length; j++)
	//					A[j][i] = -A[j][i];
	//			}
	//		}
	//		double[] b = new double[2*totalNumConstraints]; // constraint vector, twice the number of constraints since they are equality constraits: <= and >=
	//		double[][] B = new double[2*totalNumConstraints][];
	//		for (int i = 0; i < totalNumConstraints; i++)
	//		{
	//			// compute the constraint value for the initial solution
	//			B[2 * i] = A[i]; // A
	//			B[2 * i + 1] = new double[numVariables]; // -A
	//			for (int j = 0; j < numVariables; j++)
	//				B[2 * i + 1][j] = - A[i][j];
	//			if (equalityConstraints[i])
	//			{
	//				b[2 * i] = 0; // Ay <= 0
	//				b[2 * i + 1] = 0; // -Ay <=0		
	//			}
	//			else
	//			{
	//				if (constraintValues[i] > 0)
	//				{
	//					b[2 * i] = 0; // Ay <= 0
	//					b[2 * i + 1] = 1; // -Ay <= 1
	//				}
	//				else
	//				{
	//					b[2 * i] = 1; // Ay <= 1
	//					b[2 * i + 1] = 0; // -Ay <=0		
	//				}
	//			}
	//		}
	//
	//		// solve the problem by a hierarchy of problems
	//		SimplexProblem problem = new SimplexProblem();
	//		problem.numVariables = numVariables;
	//		problem.totalNumConstraints = totalNumConstraints;
	//		problem.A = B;
	//		problem.b  = b;
	//		problem.c = c;
	//
	//		double[] x = hierarchicalSimplex(problem);
	//
	//		// translate back x
	//		for (int i = 0; i < x.length; i++)
	//		{
	//			if (initSolutionArray[i])
	//				x[i] = 1 - x[i];
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		//		System.out.print("xf = [");
	//		//		for (int i = 0; i < x.length; i++)
	//		//				System.out.print(x[i]+", ");
	//		//		System.out.println("];");
	//		//
	//		//		v = 0;
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//				v += cSave[i]*x[i];
	//		//		System.out.println("value = "+ v);
	//		//
	//		//		for (int j = 0; j < totalNumConstraints; j++)
	//		//		{
	//		//			v = 0;
	//		//			for (int i = 0; i < initSolutionArray.length; i++)
	//		//					v += x[i]*ASave[j][i];
	//		//			if (equalityConstraints[j])
	//		//				System.out.println("EQ "+v);
	//		//			else
	//		//				System.out.println("LEQ "+v);
	//		//		}
	//
	//
	//		//		Simplex.test(B, b, c);
	//
	//		// build the global Hypothesis corresponding to the lp solution
	//		GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
	//		for (int cnt = 0; cnt < numVariables; cnt++)
	//		{
	//			int idx = cnt;
	//			if (x[idx] > 0.5)
	//			{
	//				// the corresponding object is used
	//				if (hypList.get(idx) instanceof Hypothesis)
	//					gh.addHypothesis((Hypothesis) hypList.get(idx));
	//			}
	//		}
	//		// penalize false alarms
	//		gh.penalizeAllFD(pFA, super.realAssociations);
	//		return gh;
	//	}
	//	private double[] hierarchicalSimplex(SimplexProblem problem)
	//	{
	//		// try to solve a binary integer problem by relaxing variables to the interval [0 1]
	//		// if we find solutions s.t. it has some values different from 0 or 1 then we fix those variables to either 1 or 0, and rerun the optimization
	//
	//		double tolerance = 1e-2;
	//		int numVariables = problem.numVariables;
	//
	//		// we have designed the problem s.t. the all-0 solution is feasible
	//		double[] bestSolution = new double[numVariables];
	//		double bestScore = 0;
	//
	//		LinkedList<IncompleteLProblem> problems = new LinkedList<IncompleteLProblem>();		
	//
	//		int[] fixedValues = new int[numVariables];
	//		for (int i = 0; i < fixedValues.length; i++)
	//			fixedValues[i] = -1;
	//		IncompleteLProblem iProblem = new IncompleteLProblem(fixedValues);
	//		iProblem.sp = problem;		
	//		problems.add(iProblem);
	//		boolean firstProblem = true;
	//
	//		try{
	//			while(!problems.isEmpty())
	//			{
	//				IncompleteLProblem ip = problems.pollLast();
	//				if (ip.previousBestScore > bestScore || firstProblem) // no use continuing to optimize along its path if the parent problem didn't give a good enough score
	//				{
	//					firstProblem = false;
	//					SimplexProblem p = ip.sp;
	//					Simplex s = new Simplex(p.A, p.b, p.c, 1000);
	//					if (s.check(p.A, p.b, p.c))
	//					{
	//						double[] x = s.primal();
	//						// check the score of the solution against the best score achieved until then
	//						double v = ip.getCompleteScore(x);
	//						if (v > bestScore) // continue only if the solution improves the current one since its score is an upperbound on the score of the feasible solutions based on it
	//						{
	//							// check whether the solution is binary
	//							boolean binary = true;
	//							int i = -1;
	//							while(binary && i < x.length - 1)
	//							{
	//								i ++;
	//								binary  = (x[i] -  tolerance <= 0)| (x[i] + tolerance >= 1);
	//							}
	//							if (binary)
	//							{
	//								bestScore = v;
	//								bestSolution = ip.getInitialSizeSolution(x);
	//								// no need to continue the optimization process, the solution is already binary
	//							}
	//							else
	//							{
	//								// the solution is not binary, hence create a set of subproblems from it where the first non-binary variable is fixed to either 0 or 1
	//								IncompleteLProblem nIp = new IncompleteLProblem(ip.fixedValues.clone());
	//								nIp.fixVariable(i, 0, p);
	//								nIp.previousBestScore = v;
	//								problems.add(nIp);
	//								nIp = new IncompleteLProblem(ip.fixedValues.clone());
	//								nIp.fixVariable(i, 1, p);
	//								nIp.previousBestScore = v;
	//								problems.add(nIp);
	//							}
	//						}
	//					}
	//					else
	//					{
	//						System.out.println("Unfeasible problem");
	//					}
	//				}
	//			}
	//		}
	//		catch (Exception e)
	//		{
	//			e.printStackTrace();
	//			System.out.println("Could not found a feasible solution in time. Using default solution instead");
	//			bestSolution = new double[numVariables];
	//		}
	//		return bestSolution;
	//	}



	//	public GlobalHypothesis solvePbWithLPCustom(int firstT, int t, GlobalHypothesis initHypothesis)
	//	{
	//		//first build a list of detections
	//		ArrayList<Association> associations = new ArrayList<Association>();
	//		ArrayList<Association> mandatoryAssociations = new ArrayList<Association>();
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{
	//			for (Family f:flist)
	//			{
	//				// first we add mandatory families
	//				if(f.rootTrack!=null)//add the previous validated association
	//				{
	//					Association rootAssociation = f.rootNode.association;
	//					if (rootAssociation!=null && !associations.contains(rootAssociation)  && ! mandatoryAssociations.contains(rootAssociation))
	//					{
	//						mandatoryAssociations.add(rootAssociation);
	//					}
	//					else
	//						System.out.println("Error: rootrack without association!");
	//				}
	//			}
	//			for (Family f:flist)
	//			{	
	//				for (Association a:f.usedAssociations)
	//				{
	//					//add all the associations of the family
	//					if(!associations.contains(a) && ! mandatoryAssociations.contains(a))
	//						associations.add(a);
	//				}
	//			}
	//		}
	//		//then build the table of tuples, along with the list of scores
	//		ArrayList<boolean[]> tuples = new ArrayList<boolean[]>();
	//		ArrayList<Double> scores = new ArrayList<Double>();
	//		ArrayList<Object> hypList = new ArrayList<Object>(); 
	//		ArrayList<Boolean> isPredList = new ArrayList<Boolean>(); 
	//		double logPFA = Math.log(pFA);
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{	
	//			for (Family f:flist)
	//			{
	//				// check that the family is feasible: its root is not used by a mandatory track
	//				if (f.rootTrack!=null || ! mandatoryAssociations.contains(f.rootNode.association))
	//					for (Hypothesis h:f.hypotheses)
	//					{
	//						// build a table indicating which association the hypothesis is using
	//						boolean[] detectionTab = new boolean[associations.size() + mandatoryAssociations.size()];
	//						double s = h.leaf.score;
	//						// and correct leaf score for detection it is using						
	//						int cnt = 0;
	//						for (Association a:associations)
	//						{
	//							if (h.usedAssociations.contains(a))
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							cnt++;
	//						}
	//						for (Association a:mandatoryAssociations)
	//						{
	//							Association rootAssociation = f.rootNode.association;
	//							if (rootAssociation != null && rootAssociation == a)
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							else
	//								detectionTab[cnt] = false;
	//							cnt++;
	//						}
	//						tuples.add(detectionTab);
	//						scores.add(s);
	//						//						scores.add(new Double(h.leaf.score));
	//						hypList.add(h);
	//						isPredList.add(new Boolean(false));
	//					}
	//			}
	//		}
	//		//		// for now there is one tuple per hypothesis
	//		//		// no need to correct for false detections here
	//		//		// then add dummy tracks for virtual spots and for false detections // for virtual spots only
	//		//		for (int cnt = 0; cnt < associations.size(); cnt++)
	//		//		{
	//		//			boolean[] detectionTab = new boolean[associations.size() + mandatoryAssociations.size()];
	//		//			detectionTab[cnt] = true;
	//		//			tuples.add(detectionTab);
	//		//			if (associations.get(cnt).isPrediction)
	//		//			{
	//		//				scores.add(new Double(0)); //no penalty for using a virtual measurement with a dummy track
	//		//				isPredList.add(new Boolean(true));
	//		//			}
	//		//			else
	//		//			{
	//		//				scores.add(new Double(Math.log(pFA))); //association is false
	//		//				isPredList.add(new Boolean(false));
	//		//			}
	//		//			hypList.add(associations.get(cnt));
	//		//		}
	//
	//		// now create the corresponding lp problem
	//		int numVariables = tuples.size(); // the number of variables is the number of tuples
	//		int numConstraints = tuples.get(0).length; // the number of constraints is the number of detections (each is used once)
	//
	//		//		Simplex solving:
	//		//		 *  N-length vector c, solve the  LP { max cx : Ax <= b, x >= 0 }.
	//		//		 *  Assumes that b >= 0 so that x = 0 is a basic feasible solution.
	//		//		since we want here x to be boolean, we relax it to be in the interval [0 1]
	//		//		this adds one constraint per variable : x <= 1
	//		//		for each tuple we also have constraints of the form Ax <= 1 if the detection is not mandatory
	//		//		otherwise the constraint is on the form AX = 1
	//		int totalNumConstraints = numConstraints + numVariables;
	//
	//		double[] c = new double[numVariables]; // score vector s.t. problem is argmax_x cx
	//		double[][] A = new double[totalNumConstraints][numVariables]; // constraint matrix st Ax <= b
	//		boolean[] equalityConstraints = new boolean[totalNumConstraints];
	//		// set objective function
	//		for (int v = 0; v < numVariables; v++)
	//			c[v] = scores.get(v);
	//
	//		// add constraints: each detection is used at max once
	//		for (int n = 0; n < numConstraints; n++)
	//		{
	//			// each constraint is a:
	//			// * double table defining a linear combination of variables
	//			// * constraint type: equality, greater/lesser than
	//			// * right hand side of the equation (double)
	//			for (int i = 0; i < numVariables; i++)
	//			{
	//				if (tuples.get(i)[n])
	//					A[n][i] = 1 ;
	//			}
	//			if(n >= associations.size()) // mandatory associations yield equality constraints
	//				equalityConstraints[n] = true;
	//			//			b[n] = 1;
	//		}
	//		// each variable is in the interval [0 1]
	//		for (int n = 0; n < numVariables; n++)
	//		{
	//			A[n + numConstraints][n] = 1;
	//			//			b[n + numConstraints] = 1;
	//		}
	//
	//		// create the initial solution
	//		boolean[] initSolutionArray = new boolean[numVariables];
	//		for (Hypothesis h:initHypothesis.hyps)
	//		{
	//			int idx = hypList.indexOf(h);
	//			initSolutionArray[idx] = true;
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		System.out.print("x0 = [");
	//		for (int i = 0; i < initSolutionArray.length; i++)
	//			if (initSolutionArray[i])
	//				System.out.print("1, ");
	//			else
	//				System.out.print("0, ");
	//		System.out.println("];");
	//
	//		double v = 0;
	//		for (int i = 0; i < initSolutionArray.length; i++)
	//			if (initSolutionArray[i])
	//				v += c[i];
	//		System.out.println("value = "+ v);
	//
	//		double[] constraintValues = new double[totalNumConstraints];
	//		for (int j = 0; j < totalNumConstraints; j++)
	//		{
	//			v = 0;
	//			for (int i = 0; i < initSolutionArray.length; i++)
	//				if (initSolutionArray[i])
	//					v += A[j][i];
	//			constraintValues[j] = v;
	//			if (equalityConstraints[j])
	//				System.out.println("EQ "+v);
	//			else
	//				System.out.println("LEQ "+v);
	//		}
	//
	//
	//		// modify the constraints and the scores to shift the problem on the initial solution
	//		double[] cSave = c.clone();
	//		double[][] ASave = new double[A.length][];
	//		for (int i = 0; i < A.length; i++)
	//			ASave[i] = A[i].clone();
	//		for (int i = 0; i < numVariables; i++)
	//		{
	//			if (initSolutionArray[i])
	//			{
	//				c[i] = -c[i];
	//				for (int j = 0; j < A.length; j++)
	//					A[j][i] = -A[j][i];
	//			}
	//		}
	//		double[] b = new double[2*totalNumConstraints]; // constraint vector, twice the number of constraints since they are equality constraits: <= and >=
	//		double[][] B = new double[2*totalNumConstraints][];
	//		for (int i = 0; i < totalNumConstraints; i++)
	//		{
	//			// compute the constraint value for the initial solution
	//			B[2 * i] = A[i]; // A
	//			B[2 * i + 1] = new double[numVariables]; // -A
	//			for (int j = 0; j < numVariables; j++)
	//				B[2 * i + 1][j] = - A[i][j];
	//			if (equalityConstraints[i])
	//			{
	//				b[2 * i] = 0; // Ay <= 0
	//				b[2 * i + 1] = 0; // -Ay <=0		
	//			}
	//			else
	//			{
	//				if (constraintValues[i] > 0)
	//				{
	//					b[2 * i] = 0; // Ay <= 0
	//					b[2 * i + 1] = 1; // -Ay <= 1
	//				}
	//				else
	//				{
	//					b[2 * i] = 1; // Ay <= 1
	//					b[2 * i + 1] = 0; // -Ay <=0		
	//				}
	//			}
	//		}
	//
	//		// solve the problem by a hierarchy of problems
	//		Simplex s = new Simplex(B, b, c);
	//		double[] x = s.primal();
	//		// translate back x
	//		for (int i = 0; i < x.length; i++)
	//		{
	//			if (initSolutionArray[i])
	//				x[i] = 1 - x[i];
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		System.out.print("xf = [");
	//		for (int i = 0; i < x.length; i++)
	//			System.out.print(x[i]+", ");
	//		System.out.println("];");
	//
	//		v = 0;
	//		for (int i = 0; i < initSolutionArray.length; i++)
	//			v += cSave[i]*x[i];
	//		System.out.println("value = "+ v);
	//
	//		for (int j = 0; j < totalNumConstraints; j++)
	//		{
	//			v = 0;
	//			for (int i = 0; i < initSolutionArray.length; i++)
	//				v += x[i]*ASave[j][i];
	//			if (equalityConstraints[j])
	//				System.out.println("EQ "+v);
	//			else
	//				System.out.println("LEQ "+v);
	//		}
	//
	//
	//		Simplex.test(B, b, c);
	//
	//		// build the global Hypothesis corresponding to the lp solution
	//		GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
	//		for (int cnt = 0; cnt < numVariables; cnt++)
	//		{
	//			int idx = cnt;
	//			if (x[idx] > 0.5)
	//			{
	//				// the corresponding object is used
	//				if (hypList.get(idx) instanceof Hypothesis)
	//					gh.addHypothesis((Hypothesis) hypList.get(idx));
	//			}
	//		}
	//		// penalize false alarms
	//		gh.penalizeAllFD(pFA, super.realAssociations);
	//		return gh;
	//	}
	//	public GlobalHypothesis solvePbWithLPCustomHierarchical2(int firstT, int t, GlobalHypothesis initHypothesis)
	//	{
	//		//first build a list of detections
	//		ArrayList<Association> associations = new ArrayList<Association>();
	//		ArrayList<Association> mandatoryAssociations = new ArrayList<Association>();
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{
	//			for (Family f:flist)
	//			{
	//				// first we add mandatory families
	//				if(f.rootTrack != null)//add the previous validated association
	//				{
	//					Association rootAssociation = f.rootNode.association;
	//					if (rootAssociation != null && !associations.contains(rootAssociation)  && ! mandatoryAssociations.contains(rootAssociation))
	//					{
	//						mandatoryAssociations.add(rootAssociation);
	//					}
	//					else
	//						System.out.println("Error: rootrack without association!");
	//				}
	//			}
	//			for (Family f:flist)
	//			{	
	//				for (Association a:f.usedAssociations)
	//				{
	//					//add all the associations of the family
	//					if(!associations.contains(a) && ! mandatoryAssociations.contains(a))
	//						associations.add(a);
	//				}
	//			}
	//		}
	//		//then build the table of tuples, along with the list of scores
	//		ArrayList<boolean[]> tuples = new ArrayList<boolean[]>();
	//		ArrayList<Double> scores = new ArrayList<Double>();
	//		ArrayList<Object> hypList = new ArrayList<Object>(); 
	//		ArrayList<Boolean> isPredList = new ArrayList<Boolean>(); 
	//		double logPFA = Math.log(pFA);
	//		for (ArrayList<Family> flist:this.concurrentFamilies.values())
	//		{	
	//			for (Family f:flist)
	//			{
	//				// check that the family is feasible: its root is not used by a mandatory track
	//				if (f.rootTrack!=null || ! mandatoryAssociations.contains(f.rootNode.association))
	//					for (Hypothesis h:f.hypotheses)
	//					{
	//						// build a table indicating which association the hypothesis is using
	//						boolean[] detectionTab = new boolean[associations.size() + mandatoryAssociations.size()];
	//						double s = h.leaf.score;
	//						// and correct leaf score for detection it is using						
	//						int cnt = 0;
	//						for (Association a:associations)
	//						{
	//							if (h.usedAssociations.contains(a))
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							cnt++;
	//						}
	//						for (Association a:mandatoryAssociations)
	//						{
	//							Association rootAssociation = f.rootNode.association;
	//							if (rootAssociation != null && rootAssociation == a)
	//							{
	//								detectionTab[cnt] = true;
	//								if (!a.isPrediction)
	//									s -= logPFA;
	//							}
	//							else
	//								detectionTab[cnt] = false;
	//							cnt++;
	//						}
	//						tuples.add(detectionTab);
	//						scores.add(s);
	//						//						scores.add(new Double(h.leaf.score));
	//						hypList.add(h);
	//						isPredList.add(new Boolean(false));
	//					}
	//			}
	//		}
	//
	//		// now create the corresponding lp problem
	//		int numVariables = tuples.size(); // the number of variables is the number of tuples
	//		int numConstraints = tuples.get(0).length; // the number of constraints is the number of detections (each is used once)
	//
	//		//		Simplex solving:
	//		//		 *  N-length vector c, solve the  LP { max cx : Ax <= b, x >= 0 }.
	//		//		 *  Assumes that b >= 0 so that x = 0 is a basic feasible solution.
	//		//		since we want here x to be boolean, we relax it to be in the interval [0 1]
	//		//		this adds one constraint per variable : x <= 1
	//		//		for each tuple we also have constraints of the form Ax <= 1 if the detection is not mandatory
	//		//		otherwise the constraint is on the form AX = 1
	//		int totalNumConstraints = numConstraints + numVariables;
	//
	//		double[] c = new double[numVariables]; // score vector s.t. problem is argmax_x cx
	//		double[][] A = new double[totalNumConstraints][numVariables]; // constraint matrix st Ax <= b
	//		boolean[] equalityConstraints = new boolean[totalNumConstraints];
	//		// set objective function
	//		for (int v = 0; v < numVariables; v++)
	//			c[v] = scores.get(v);
	//
	//		// add constraints: each detection is used at max once
	//		for (int n = 0; n < numConstraints; n++)
	//		{
	//			// each constraint is a:
	//			// * double table defining a linear combination of variables
	//			// * constraint type: equality, greater/lesser than
	//			// * right hand side of the equation (double)
	//			for (int i = 0; i < numVariables; i++)
	//			{
	//				if (tuples.get(i)[n])
	//					A[n][i] = 1 ;
	//			}
	//			if(n >= associations.size()) // mandatory associations yield equality constraints
	//				equalityConstraints[n] = true;
	//			//			b[n] = 1;
	//		}
	//		// each variable is in the interval [0 1]
	//		for (int n = 0; n < numVariables; n++)
	//			A[n + numConstraints][n] = 1;
	//
	//		// modify the constraints and the scores to shift the problem on the initial solution
	//
	//		// create the initial solution
	//		boolean[] initSolutionArray = new boolean[numVariables];
	//		for (Hypothesis h:initHypothesis.hyps)
	//		{
	//			int idx = hypList.indexOf(h);
	//			initSolutionArray[idx] = true;
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		//		System.out.print("x0 = [");
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//			if (initSolutionArray[i])
	//		//				System.out.print("1, ");
	//		//			else
	//		//				System.out.print("0, ");
	//		//		System.out.println("];");
	//		//		
	//		double v = 0;
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//			if (initSolutionArray[i])
	//		//				v += c[i];
	//		//		System.out.println("value = "+ v);
	//		//		
	//		double[] constraintValues = new double[totalNumConstraints];
	//		for (int j = 0; j < totalNumConstraints; j++)
	//		{
	//			v = 0;
	//			for (int i = 0; i < initSolutionArray.length; i++)
	//				if (initSolutionArray[i])
	//					v += A[j][i];
	//			constraintValues[j] = v;
	//			//			if (equalityConstraints[j])
	//			//				System.out.println("EQ "+v);
	//			//			else
	//			//				System.out.println("LEQ "+v);
	//		}
	//
	//		//		double[] cSave = c.clone();
	//		double[][] ASave = new double[A.length][];
	//		for (int i = 0; i < A.length; i++)
	//			ASave[i] = A[i].clone();
	//		for (int i = 0; i < numVariables; i++)
	//		{
	//			if (initSolutionArray[i])
	//			{
	//				c[i] = - c[i];
	//				for (int j = 0; j < A.length; j++)
	//					A[j][i] = -A[j][i];
	//			}
	//		}
	//		int numEqualityConstraints = 0;
	//		for (int i = 0; i < equalityConstraints.length; i++)
	//			if (equalityConstraints[i])
	//				numEqualityConstraints++;
	//		double[] b = new double[totalNumConstraints + numEqualityConstraints]; // constraint vector
	//		int k = 0;
	//		for (int i = 0; i < totalNumConstraints; i++)
	//		{
	//			if (equalityConstraints[i])
	//			{
	//				b[k] = 0; // we know for sure that the value of the constraint is 1 for the init solution
	//				k++;
	//				b[k] = 0;
	//				k++;
	//			}
	//			else
	//			{
	//				b[k] = 1 - constraintValues[k];
	//				k++;
	//			}
	//		}
	//
	//		// solve the problem by a hierarchy of problems
	//		SimplexProblem problem = new SimplexProblem();
	//		problem.numVariables = numVariables;
	//		problem.totalNumConstraints = totalNumConstraints;
	//		problem.A = A;
	//		problem.b  = b;
	//		problem.c = c;
	//
	//		double[] x = hierarchicalSimplex(problem);
	//
	//		// translate back x
	//		for (int i = 0; i < x.length; i++)
	//		{
	//			if (initSolutionArray[i])
	//				x[i] = 1 - x[i];
	//		}
	//
	//		// compute the initial solution score and check that it verifies the constraints
	//		//		System.out.print("xf = [");
	//		//		for (int i = 0; i < x.length; i++)
	//		//				System.out.print(x[i]+", ");
	//		//		System.out.println("];");
	//		//
	//		//		v = 0;
	//		//		for (int i = 0; i < initSolutionArray.length; i++)
	//		//				v += cSave[i]*x[i];
	//		//		System.out.println("value = "+ v);
	//		//
	//		//		for (int j = 0; j < totalNumConstraints; j++)
	//		//		{
	//		//			v = 0;
	//		//			for (int i = 0; i < initSolutionArray.length; i++)
	//		//					v += x[i]*ASave[j][i];
	//		//			if (equalityConstraints[j])
	//		//				System.out.println("EQ "+v);
	//		//			else
	//		//				System.out.println("LEQ "+v);
	//		//		}
	//
	//
	//		//		Simplex.test(B, b, c);
	//
	//		// build the global Hypothesis corresponding to the lp solution
	//		GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
	//		for (int cnt = 0; cnt < numVariables; cnt++)
	//		{
	//			int idx = cnt;
	//			if (x[idx] > 0.5)
	//			{
	//				// the corresponding object is used
	//				if (hypList.get(idx) instanceof Hypothesis)
	//					gh.addHypothesis((Hypothesis) hypList.get(idx));
	//			}
	//		}
	//		// penalize false alarms
	//		gh.penalizeAllFD(pFA, super.realAssociations);
	//		return gh;
	//	}
}