/*
 * Copyright (c) 2010-2026. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.nchenouard.particletracking.MHTracker.LinearProgramming;

import org.jetbrains.annotations.Contract;

import java.util.LinkedList;

/**
 * Linear Programming problem for which some variables have a fixed value
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class IncompleteLProblem {
    public double scoreCorrection = 0; // a score correction term st the final score to consider is cx + scoreCorrection
    public double previousBestScore = 0;
    public int[] fixedValues;

    public SimplexProblem sp;

    @Contract(pure = true)
    public IncompleteLProblem(final int[] fixedValues) {
        this.fixedValues = fixedValues;
    }

    public double[] getInitialSizeSolution(final double[] x) {
        final double[] solution = new double[fixedValues.length];
        int cnt = 0;
        for (int i = 0; i < fixedValues.length; i++) {
            if (fixedValues[i] == -1) {
                solution[i] = x[cnt];
                cnt++;
            }
            else
                solution[i] = fixedValues[i];
        }
        return solution;
    }

    public double getCompleteScore(final double[] x) {
        double v = scoreCorrection;
        for (int i = 0; i < sp.numVariables; i++)
            v += sp.c[i] * x[i];
        return v;
    }

//	public void fixVariable(int idx, int val, SimplexProblem extendSP)
//	{
//		// refresh the table of fixed values
//		int cnt = 0;
//		for (int i = 0; i < fixedValues.length; i++)
//		{
//			if(fixedValues[i] == -1)
//			{
//				if (cnt == idx)
//				{
//					fixedValues[i] = val;
//					break;
//				}
//				else
//					cnt++;
//			}
//		}
//		SimplexProblem problem = new SimplexProblem();
//		this.sp = problem;
//		// we have to modify constraints and scores to reflect the new value 
//		
//		// remove the fixed value from the score variable
//		problem.numVariables = extendSP.numVariables - 1;
//		problem.c = new double[problem.numVariables];
//		for (int i = 0; i < idx; i++)
//			problem.c[i] = extendSP.c[i];
//		for (int i = idx + 1; i < extendSP.numVariables; i++)
//			problem.c[i - 1] = extendSP.c[i];
//		
//		// modify the correction factor
//		this.scoreCorrection += val*extendSP.c[idx];
//		
//		// modify the constraints
//		problem.totalNumConstraints = extendSP.totalNumConstraints;
//		LinkedList<Integer> nonEmptyConstraints = new LinkedList<Integer>();
//		double[][] modifiedConstraints = new double[problem.totalNumConstraints][];
//		double[] modifiedConstraintValue = new double[problem.totalNumConstraints];
//		for (int j = 0; j < extendSP.totalNumConstraints; j++)
//		{
//			double[] modifiedC = new double[problem.numVariables];
//			for (int i = 0; i < idx; i++)
//				modifiedC[i] = extendSP.A[j][i];
//			for (int i = idx + 1; i < problem.numVariables; i++)
//				modifiedC[i - 1] = extendSP.A[j][i];
//			modifiedConstraints[j] = modifiedC;
//			modifiedConstraintValue[j] = extendSP.b[j] - val * extendSP.A[j][idx]; // correct the constraint value
//			// check if there is a non-zero value in the constraint
//			for (int i = 0; i < modifiedC.length; i++)
//				if (modifiedC[i] > 0 || modifiedC[i] < 0)
//				{
//					nonEmptyConstraints.add(new Integer(j));// there is a non-zero value in the constraint expression
//					break;
//				}
//		}
//		if (nonEmptyConstraints.size() < problem.totalNumConstraints)
//		{
//			problem.A = new double[nonEmptyConstraints.size()][];
//			problem.b = new double[nonEmptyConstraints.size()];
//			int i = 0;
//			for (Integer idxN:nonEmptyConstraints)
//			{
//				problem.A[i] = modifiedConstraints[idxN];
//				problem.b[i] = modifiedConstraintValue[idxN];
//				i++;
//			}
//			problem.totalNumConstraints = problem.A.length;
//		}
//		else
//		{
//			problem.A = modifiedConstraints;
//			problem.b = modifiedConstraintValue;
//		}
//		
//	}

    public void fixVariable(final int idx, final int val, final SimplexProblem extendSP) {
        // refresh the table of fixed values
        int cnt = 0;
        for (int i = 0; i < fixedValues.length; i++) {
            if (fixedValues[i] == -1) {
                if (cnt == idx) {
                    fixedValues[i] = val;
                    break;
                }
                else
                    cnt++;
            }
        }
        final SimplexProblem problem = new SimplexProblem();
        this.sp = problem;
        if (val == 0) {

        }
        else {
            // we need to check for constraint values that may go < 0 for this fixed variable
            //since constraint is now a[i != j]'A[k][i != j] <= b[k] - a[j]A[k][j]
            // 0 is no longer a feasible solution here


        }

        // we have to modify constraints and scores to reflect the new value


        // remove the fixed value from the score variable
        problem.numVariables = extendSP.numVariables - 1;
        problem.c = new double[problem.numVariables];
        if (idx >= 0)
            System.arraycopy(extendSP.c, 0, problem.c, 0, idx);
        if (extendSP.numVariables - (idx + 1) >= 0)
            System.arraycopy(extendSP.c, idx + 1, problem.c, idx + 1 - 1, extendSP.numVariables - (idx + 1));

        // modify the correction factor
        this.scoreCorrection += val * extendSP.c[idx];

        // modify the constraints
        problem.totalNumConstraints = extendSP.totalNumConstraints;
        final LinkedList<Integer> nonEmptyConstraints = new LinkedList<>();
        final double[][] modifiedConstraints = new double[problem.totalNumConstraints][];
        final double[] modifiedConstraintValue = new double[problem.totalNumConstraints];
        for (int j = 0; j < extendSP.totalNumConstraints; j++) {
            final double[] modifiedC = new double[problem.numVariables];
            System.arraycopy(extendSP.A[j], 0, modifiedC, 0, idx);
            if (problem.numVariables - (idx + 1) >= 0)
                System.arraycopy(extendSP.A[j], idx + 1, modifiedC, idx + 1 - 1, problem.numVariables - (idx + 1));
            modifiedConstraints[j] = modifiedC;
            modifiedConstraintValue[j] = extendSP.b[j] - val * extendSP.A[j][idx]; // correct the constraint value
            // check if there is a non-zero value in the constraint
            for (final double v : modifiedC)
                if (v > 0 || v < 0) {
                    nonEmptyConstraints.add(j);// there is a non-zero value in the constraint expression
                    break;
                }
        }
        if (nonEmptyConstraints.size() < problem.totalNumConstraints) {
            problem.A = new double[nonEmptyConstraints.size()][];
            problem.b = new double[nonEmptyConstraints.size()];
            int i = 0;
            for (final Integer idxN : nonEmptyConstraints) {
                problem.A[i] = modifiedConstraints[idxN];
                problem.b[i] = modifiedConstraintValue[idxN];
                i++;
            }
            problem.totalNumConstraints = problem.A.length;
        }
        else {
            problem.A = modifiedConstraints;
            problem.b = modifiedConstraintValue;
        }
    }
}
