package plugins.nchenouard.particletracking.MHTracker.LinearProgramming;

//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
import java.util.ArrayList;

import plugins.nchenouard.linearprogrammingfullsimplex.CanonicalProgramParameters;
import plugins.nchenouard.linearprogrammingfullsimplex.CanonicalSimplexProgram;
//import plugins.nchenouard.linearprogrammingfullsimplex.LPSolveComparison;
import plugins.nchenouard.linearprogrammingfullsimplex.SimplexLEXICO;

public class HierarchicalLP
{
	static double tolerance = 1e-4;
	public static boolean wroteFile = false;

	public static HierarchicalProcedureResult hierarchicalBinarySimplex(CanonicalSimplexProgram program, boolean display)
	{
		if (program == null)
			return null;
//		System.out.println("Num vari "+program.getNumVariables()+" num cons "+program.getNumConstraints());
		// try to solve a binary integer problem by relaxing variables to the interval [0 1]
		// if we find solutions s.t. it has some values different from 0 or 1 then we fix those variables to either 1 or 0, and rerun the optimization
		CanonicalProgramParameters p = program.getParameters();
		double[][] AClone = new double[p.A.length][];
		for (int i = 0; i < AClone.length; i++)
			AClone[i] = p.A[i].clone();
//		CanonicalSimplexProgram pClone2 = new SimplexLEXICO(AClone, p.b.clone(), p.c.clone(), true, p.equalityConstraints.clone());
//		CanonicalProgramParameters p2 = pClone2.getParameters();
		
//		double lpSolution = LPSolveComparison.solveWithLPSolve(program);
		if (program.solvePrimalSimplex())
		{
//			if (Math.abs(program.getScore() - lpSolution) > 0.01)
//			{
//
//				System.out.println("!!!s2!!! : "+program.getScore() + " | " + lpSolution);
//				if (!wroteFile)
//				{
//					// re-run the optimization in verbose mode					
//					wroteFile = true;
//			        try {
//			            BufferedWriter out = new BufferedWriter(new FileWriter("A.txt"));
//						for (int i = 0; i < p2.A.length; i++)
//						{
//							for (int j = 0; j < p2.A[i].length; j++)
//								out.write(p2.A[i][j]+", ");
//							out.newLine();
//						}
//			            out.close();
//			            out = new BufferedWriter(new FileWriter("b.txt"));
//						for (int i = 0; i < p2.b.length; i++)
//							out.write(p2.b[i]+", ");
//						out.close();
//						out = new BufferedWriter(new FileWriter("c.txt"));
//						for (int i = 0; i < p2.c.length; i++)
//							out.write(p2.c[i]+", ");
//						out.close();
//						out = new BufferedWriter(new FileWriter("eq.txt"));
//						for (int i = 0; i < p2.equalityConstraints.length; i++)
//							if (p2.equalityConstraints[i])
//								out.write(1+", ");
//							else
//								out.write(0+", ");
//						out.close();
//						out = new BufferedWriter(new FileWriter("solCustom.txt"));						
//						LPSolveComparison.solveWithLPSolve(pClone2);
//			        	double[] s = pClone2.getSolution();
//			        	for (int i = 0; i < s.length; i++)
//			        	{
//							out.write(s[i]+", ");
//			        	}
//			        	out.close();
//			            } catch (IOException e) {}
//			        	
//				}
//			}
			double[] solution = program.getSolution();
			if (solution == null)
				return null;
			int idxNonBool = -1;
			for (int i = 0; i < program.getNumVariables(); i++)
			{
				if (Math.abs(solution[i]) > tolerance && Math.abs(1 - solution[i]) > tolerance)
				{
					idxNonBool = i;
					break;
				}
			}
			if (idxNonBool < 0)
			{
//				if (display)
//					System.out.println("Non Binary");
				HierarchicalProcedureResult result = new HierarchicalProcedureResult();
				result.solution = solution;
				result.score = program.getScore();
				return result;
			}
			else
			{
//				if (display)
					System.out.println("Non binary");
				// fix first non boolean variable to 0 and 1 and solve again in each case
				CanonicalSimplexProgram p0 = createSubFixedProblem(program, idxNonBool, 0);
				HierarchicalProcedureResult result0 = null;
				double score0 = 0;
				if (p0 != null)
				{
					result0 = hierarchicalBinarySimplex(p0, display);
					if (result0 != null)
						score0 = result0.score;
				}
				else
					System.out.println("Non feasible sub problem");
				CanonicalSimplexProgram p1 = createSubFixedProblem(program, idxNonBool, 1);
				HierarchicalProcedureResult result1 = null;
				double score1 = 0;
				if (p1 != null)
				{
					result1 = hierarchicalBinarySimplex(p1, display);
					if (result1 != null)
						score1 = result1.score + program.getParameters().c[idxNonBool];
				}
				else
					System.out.println("Non feasible sub problem");
				if (result0 == null && result1 == null)
				{
					System.out.println("Both sub problem non feasible");
					return null;
				}
				if (result0 != null)
				{
					if (result1 == null || score0 >= score1)
					{
						HierarchicalProcedureResult result = new HierarchicalProcedureResult();
						result.solution = fillIncompleteSolution(result0.solution, idxNonBool, 0);
						result.score = score0;
						result.hadNonBinary = true;
						return result;
					}
					else
					{
						HierarchicalProcedureResult result = new HierarchicalProcedureResult();
						result.solution = fillIncompleteSolution(result1.solution, idxNonBool, 1);
						result.score = score1;
						result.hadNonBinary = true;
						return result;
					}
				}
				else
				{
					HierarchicalProcedureResult result = new HierarchicalProcedureResult();
					result.solution = fillIncompleteSolution(result1.solution, idxNonBool, 1);
					result.score = score1;
					result.hadNonBinary = true;
					return result;
				}
			}
		}
		else
		{
			System.out.println("SOLVE FAILED");
			return null;
		}
	}
	
	public static HierarchicalProcedureResult hierarchicalBinarySimplex(CanonicalSimplexProgram program)
	{
		if (program == null)
			return null;
		// try to solve a binary integer problem by relaxing variables to the interval [0 1]
		// if we find solutions s.t. it has some values different from 0 or 1 then we fix those variables to either 1 or 0, and rerun the optimization
		if (program.solvePrimalSimplex())
		{
			double[] solution = program.getSolution();
			if (solution == null)
				return null;
			int idxNonBool = -1;
			for (int i = 0; i < program.getNumVariables(); i++)
			{
				if (Math.abs(solution[i]) > tolerance && Math.abs(1 - solution[i]) > tolerance)
				{
					idxNonBool = i;
					break;
				}
			}
			if (idxNonBool < 0)
			{
				HierarchicalProcedureResult result = new HierarchicalProcedureResult();
				result.solution = solution;
				result.score = program.getScore();
				return result;
			}
			else
			{
//				System.out.println("Non binary");
				// fix first non boolean variable to 0 and 1 and solve again in each case
				CanonicalSimplexProgram p0 = createSubFixedProblem(program, idxNonBool, 0);
				HierarchicalProcedureResult result0 = null;
				double score0 = 0;
				if (p0 != null)
				{
					result0 = hierarchicalBinarySimplex(p0);
					if (result0 != null)
						score0 = result0.score;
				}
				else
					System.out.println("Non feasible sub problem");
				CanonicalSimplexProgram p1 = createSubFixedProblem(program, idxNonBool, 1);
				HierarchicalProcedureResult result1 = null;
				double score1 = 0;
				if (p1 != null)
				{
					result1 = hierarchicalBinarySimplex(p1);
					if (result1 != null)
						score1 = result1.score + program.getParameters().c[idxNonBool];
				}
				else
					System.out.println("Non feasible sub problem");
				if (result0 == null && result1 == null)
				{
					System.out.println("Both sub problem non feasible");
					return null;
				}
				if (result0 != null)
				{
					if (result1 == null || score0 >= score1)
					{
						HierarchicalProcedureResult result = new HierarchicalProcedureResult();
						result.solution = fillIncompleteSolution(result0.solution, idxNonBool, 0);
						result.score = score0;
						result.hadNonBinary = true;
						return result;
					}
					else
					{
						HierarchicalProcedureResult result = new HierarchicalProcedureResult();
						result.solution = fillIncompleteSolution(result1.solution, idxNonBool, 1);
						result.score = score1;
						result.hadNonBinary = true;
						return result;
					}
				}
				else
				{
					HierarchicalProcedureResult result = new HierarchicalProcedureResult();
					result.solution = fillIncompleteSolution(result1.solution, idxNonBool, 1);
					result.score = score1;
					result.hadNonBinary = true;
					return result;
				}
			}
		}
		else
		{
			System.out.println("SOLVE FAILED");
			return null;
		}
	}
	
	private static double[] fillIncompleteSolution(double[] solution, int idxNonBool, double val) {
		if (solution == null)
			return null;
		double[] completeSolution = new double[solution.length + 1];
		if (idxNonBool > 0)
			System.arraycopy(solution, 0, completeSolution, 0, idxNonBool);
		if (idxNonBool < solution.length)
			System.arraycopy(solution, idxNonBool, completeSolution, idxNonBool + 1, solution.length - idxNonBool);
		completeSolution[idxNonBool] = val;
		return completeSolution;
	}

	private static CanonicalSimplexProgram createSubFixedProblem(CanonicalSimplexProgram program, int idxNonBool, int val)
	{
		CanonicalProgramParameters p = program.getParameters();
		int numVariables = p.c.length;
		int numConstraints = p.b.length;
		ArrayList<double[]> constraints = new ArrayList<double[]>(numConstraints);
		ArrayList<Double> bVals = new ArrayList<Double>(numConstraints);
		ArrayList<Boolean> eqVals = new ArrayList<Boolean>(numConstraints);

		for (int i = 0; i < numConstraints; i++)
		{
			double[] vector = new double[numVariables - 1];
			if (idxNonBool > 0)
				System.arraycopy(p.A[i], 0, vector, 0, idxNonBool);
			if (idxNonBool < numVariables - 1)
				System.arraycopy(p.A[i], idxNonBool + 1, vector, idxNonBool, numVariables - 1 - idxNonBool);
			// check that the vector has at least a non 0 value
			boolean isZero = true;
			for (int j = 0; j < vector.length; j++)
				if (vector[j] != 0)
				{
					isZero = false;
					break;
				}
			if (!isZero)
			{
				double modifiedB = p.b[i] - val*p.A[i][idxNonBool];
				if (modifiedB < 0)
				{
					if (modifiedB + tolerance < 0)
						return null; // not feasible here to have a negative bound with 0/1 constraints and variables
					else
						modifiedB = 0; // adjust to 0 in case it is inacurately < 0
				}
//				// check that vector does not already exist in our list of constraints
				// this is in case we can have multiples of the same line. Not the case here since everything is 0/1
//				boolean alreadyIn = false;
//				int vectorIdx = -1;
//				double m = 0;
//				for (int k = 0; k < constraints.size(); k++)
//				{
//					boolean sameVector = true;
//					double[] v1 = constraints.get(k);
//					if (v1[0] != 0 || vector[0] == 0)
//					{
//						m = 0;
//						if (v1[0] == 0)
//							m = 1;
//						else
//							m = vector[0]/v1[0];
//						for (int kk = 1; kk < vector.length; kk++)
//						{
//							if (vector[kk] != m*v1[kk])
//							{
//								sameVector = false;
//								break;
//							}
//						}
//					}
//					else
//						sameVector = false;
				// check that vector does not already exist in our list of constraints
				boolean alreadyIn = false;
				int vectorIdx = -1;
				for (int k = 0; k < constraints.size(); k++)
				{
					boolean sameVector = true;
					double[] v1 = constraints.get(k);
					for (int kk = 0; kk < vector.length; kk++)
					{
						if (vector[kk] != v1[kk])
						{
							sameVector = false;
							break;
						}
					}
					alreadyIn = sameVector;
					if (alreadyIn)
					{
						vectorIdx = k;
						break;
					}
				}
				if (alreadyIn)
				{
					// keep only one of the two constraints
					// equality constraint prevails
					if (p.equalityConstraints[i])
					{
						if (eqVals.get(vectorIdx))
						{
							if (Math.abs(modifiedB - bVals.get(vectorIdx)) > tolerance)
								return null; // it is not feasible to have to equality constraints with different values
							// else: no need to add the constraint, it is already exactly present
						}
						else
						{
							// check that equality contraint is lower that non-equality bound
							if (modifiedB <= bVals.get(vectorIdx))
							{
								bVals.set(vectorIdx, modifiedB);
								eqVals.set(vectorIdx, true); // set as an equality constraint
							}
							else
								return null; // not feasible to have equality constraint > upper bound
						}
					}
					else
					{
						if (eqVals.get(vectorIdx))
						{
							if (modifiedB < bVals.get(vectorIdx))
								return null; // not feasible. Upper bound value should be greater or equal to equality constraint
						}
						else
						{
							// keep only the smallest bound
							if (modifiedB < bVals.get(vectorIdx))								
								bVals.set(vectorIdx, modifiedB);
							// else: no need to increase the bound
						}
					}
				}
				else
				{
					constraints.add(vector);
					eqVals.add(p.equalityConstraints[i]);
					// correct the constraint value for the fixed variable
					bVals.add(modifiedB);
				}
			}
			// else: do not add empty constraint
		}
		if (!constraints.isEmpty())
		{
			double[][] A = new double[constraints.size()][];
			double[] b = new double[A.length];
			boolean[] eq = new boolean[A.length];
			for (int i = 0; i < constraints.size(); i++)
			{
				A[i] = constraints.get(i);
				b[i] = bVals.get(i);
				eq[i] = eqVals.get(i);
			}
			double[] c = new double[numVariables - 1];
			if (idxNonBool > 0)
				System.arraycopy(p.c, 0, c, 0, idxNonBool);
			if (idxNonBool < numVariables - 1)
				System.arraycopy(p.c, idxNonBool + 1, c, idxNonBool, numVariables - 1 - idxNonBool);
			return new SimplexLEXICO(A, b, c, p.maximization, eq);
		}
		return null;
	}

}
