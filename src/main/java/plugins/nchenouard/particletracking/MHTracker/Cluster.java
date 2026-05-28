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

package plugins.nchenouard.particletracking.MHTracker;

import fr.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;

/**
 * A group of Family objects which share some common detections and related methods to solve the tracking problem for them
 * <p>
 * Part of the Spot Tracking plugin for ICY: <a href="https://icy.bioimageanalysis.org/plugin/Spot_Tracking">https://icy.bioimageanalysis.org/plugin/Spot_Tracking</a>
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class Cluster implements AutoCloseable {
    HashSet<Association> realAssociations = new HashSet<>();
    HashMap<Integer, ArrayList<Family>> concurrentFamilies = new HashMap<>();
    final int firstTime;
    final int lastTime;
    int[] realSpotsNum;
    int K;
    HMMMHTracker tracker;
    boolean parallelComputing = false;
    final LinkedList<Thread> buildGHthreadList = new LinkedList<>();


    static class BestHypSynchronization {
        GlobalHypothesis bestHyp = null;
    }

    final BestHypSynchronization bestHypSafe = new BestHypSynchronization();

    public Cluster(final HMMMHTracker tracker, final int firstT, final int lastT, final int K) {
        this.firstTime = firstT;
        this.lastTime = lastT;
        this.K = K;
        realSpotsNum = new int[lastTime - firstTime + 1];
        this.tracker = tracker;
    }

    public void initRealSpotsNum() {
        for (final Association a : realAssociations) {
            if (a.t >= firstTime && a.t <= lastTime)
                realSpotsNum[a.t - firstTime] = realSpotsNum[a.t - firstTime] + 1;
        }
    }


    @SuppressWarnings("BusyWait")
    public void buildAndApplyBestHypTree(final int t) {
        int firstTtemp = -1;
        final boolean buildAndApplyBestHyp;
        for (final Integer i : concurrentFamilies.keySet()) {
            if (firstTtemp < 0 || i < firstTtemp)
                firstTtemp = i;
        }
        final int firstT = firstTtemp;

        buildGHthreadList.clear();

        buildAndApplyBestHyp = (firstT >= 0 && firstT <= t - K);
        if (buildAndApplyBestHyp) {
            println("-----build best Hyp");
            println("different root time for families " + concurrentFamilies.size());
            //for each family build global hyps and only keep the best one
            println("firstT " + firstT + " first time " + firstT + "  time " + t);
            for (final Entry<Integer, ArrayList<Family>> e : concurrentFamilies.entrySet())
                println("time " + e.getKey() + "numFamily " + e.getValue().size());
            final int currentT = firstT;

            //build  the set of mandatories and optional families with root at time currentT
            final ArrayList<Family> fList = concurrentFamilies.get(currentT);
            final ArrayList<Family> mandatoryFamilies = new ArrayList<>(); //families that have a previous validated tracks are mandatory
            final ArrayList<Family> optionalFamilies = new ArrayList<>(); //families that don't have a previous validated tracks are optional
            for (final Family f : fList) {
                if (f.rootTrack == null)
                    optionalFamilies.add(f);//families that do not have a previous ValidatedTrack are optional
                else {
                    mandatoryFamilies.add(f); //families that already have a previous ValidatedTrack are mandatory
                    println("mandatory family : begin = " + f.rootTrack.associations.getFirst().t + " root time " + f.rootTime);
                }
            }
            println("mFamilies " + mandatoryFamilies.size() + " oFamilies " + optionalFamilies.size());

            if (!mandatoryFamilies.isEmpty()) {
                final int familyIdx = 0;
                //final Family f = mandatoryFamilies.get(familyIdx);
                final Family f = mandatoryFamilies.get(0);
                final boolean lastFamily = (familyIdx == mandatoryFamilies.size() - 1);
                for (final Hypothesis htemp : f.hypotheses) {
                    final Thread thr = new Thread(() -> {
                        final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                        gh.addHypothesis(htemp);

//							continue with this hypothesis only if its score is better than score of best hyp because it will always decrease later
                        final boolean feasible;
                        synchronized (bestHypSafe) {
                            feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                        }
                        if (feasible) {
                            if (lastFamily) {
                                //we can do 3 things:
                                // 1/ use this hypothesis as best hyp
                                // 2/ try to add optional hypothesis at currentTime
                                // 3/ try to add optional hypothesis at later times

                                // 1/ use this hypothesis as best hyp
                                {
                                    final GlobalHypothesis ghCopy = gh.copy();
                                    for (int time = currentT; time <= t; time++) {
                                        penalizeFD(ghCopy, time);
                                        penalizeNT(ghCopy, time);
                                    }
                                    synchronized (bestHypSafe) {
                                        if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                            bestHypSafe.bestHyp = ghCopy;
                                    }
                                }
                                if (gh.realAssociations.size() < realAssociations.size()) //there still remains real Association that have not been used
                                {
//										2/ try to add optional hypothesis at currentTime
                                    final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                                    if (currentTimeFD > 0 && !optionalFamilies.isEmpty()) //we can try to add spots from optional families at time currentT
                                    {
                                        recursBuildBestGlobalHypTree(gh, 0, currentT, t, optionalFamilies);
                                    }
//										3/ try to add optional hypothesis at later times
                                    else if (currentT < t) {
                                        penalizeFD(gh, currentTimeFD, currentT);
                                        penalizeNT(gh, currentT);
                                        final boolean feasible2;
                                        synchronized (bestHypSafe) {
                                            feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                        }
                                        if (feasible2)
                                            recursBuildBestGlobalHypTree(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                                    }
                                }
                            }
                            else
                                recursBuildBestGlobalHypWithMandatoryFamiliesTree(gh, familyIdx + 1, currentT, t, mandatoryFamilies, optionalFamilies);
                        }
                    });
                    synchronized (buildGHthreadList) {
                        buildGHthreadList.add(thr);
                    }
//					thrList.add(thr);
                    thr.start();
                    if (!parallelComputing)
                        try {
                            thr.join();
                        }
                        catch (final InterruptedException e1) {
                            IcyLogger.error(this.getClass(), e1);
                        }
                }
//				for (Thread thr:thrList)
//					try {
//						thr.join();
//					} catch (InterruptedException e1) {
//						e1.printStackTrace();
//					}
            }
            else {
                if (!optionalFamilies.isEmpty()) {
                    final int familyIdx = 0;
                    //final Family f = optionalFamilies.get(familyIdx);
                    final Family f = optionalFamilies.get(0);
                    final boolean lastFamily = (familyIdx == optionalFamilies.size() - 1);

//					ArrayList<Thread> thrList = new ArrayList<Thread>();
//					1/ add hypothesis from this family
                    for (final Hypothesis htemp : f.hypotheses) {
                        final Thread thr = new Thread(() -> {
                            final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                            gh.addHypothesis(htemp);
                            // 1/ try to use this hypothesis as the best hyp
                            {
                                final GlobalHypothesis ghCopy = gh.copy();
                                for (int time = currentT; time <= t; time++) {
                                    penalizeFD(ghCopy, time);
                                    penalizeNT(ghCopy, time);
                                }
                                synchronized (bestHypSafe) {
                                    if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                        bestHypSafe.bestHyp = ghCopy;
                                }
                            }
                            if (gh.realAssociations.size() < realAssociations.size()) {
                                final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                                // 2/try to add an hypothesis from next family
                                if (!lastFamily && currentTimeFD > 0)
                                    recursBuildBestGlobalHypTree(gh, familyIdx + 1, currentT, t, optionalFamilies);
                                    // 3/ try to add an hypothesis from a family of next time
                                else if (currentT < t) {
                                    penalizeFD(gh, currentTimeFD, currentT);
                                    penalizeNT(gh, currentT);
                                    final boolean feasible;
                                    synchronized (bestHypSafe) {
                                        feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                    }
                                    if (feasible)
                                        recursBuildBestGlobalHypTree(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                                }
                            }

                        });
                        synchronized (buildGHthreadList) {
                            buildGHthreadList.add(thr);
                        }
                        thr.start();
                        if (!parallelComputing)
                            try {
                                thr.join();
                            }
                            catch (final InterruptedException e1) {
                                IcyLogger.error(this.getClass(), e1);
                            }
                    }
                    // 2/ do not add hypothesis from this family
                    final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                    if (lastFamily) {
                        //add hypothesis from families at next time
                        if (currentT < t) {
                            penalizeFD(gh, currentT);
                            penalizeNT(gh, currentT);
                            final boolean feasible;
                            synchronized (bestHypSafe) {
                                feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                            }
                            if (feasible)
                                recursBuildBestGlobalHypTree(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                        }
                    }
                    else {
//						add hypothesis from next family at current time
                        recursBuildBestGlobalHypTree(gh, familyIdx + 1, currentT, t, fList);
                    }

                }
            }

        }
        else {
            println("no need to build hyp");
        }

        for (; ; ) {
            boolean running = false;
            synchronized (buildGHthreadList) {
                while (!buildGHthreadList.isEmpty()) {
                    final Thread thr = buildGHthreadList.getFirst();
                    if (thr.isAlive()) {
                        running = true;
                        break;
                    }
                    else {
                        buildGHthreadList.poll();
                    }
                }
            }
            if (!running)
                break;
            try {
                Thread.sleep(250);
            }
            catch (final InterruptedException e) {
                IcyLogger.error(this.getClass(), e);
            }
        }

        applyBestHyp(t);
        for (final Collection<Family> c : concurrentFamilies.values())
            for (final Family f : c)
                f.hypotheses.clear();
    }


    public void buildAndApplyBestHyp(final int t) {
        int firstT = -1;
        final boolean buildAndApplyBestHyp;
        for (final Integer i : concurrentFamilies.keySet()) {
            if (firstT < 0 || i < firstT)
                firstT = i;
        }
        buildAndApplyBestHyp = (firstT >= 0 && firstT <= t - K);
        if (buildAndApplyBestHyp) {
            println("-----build best Hyp");
            println("different root time for families " + concurrentFamilies.size());
            //for each family build global hypotheses and only keep the best one
            println("firstT " + firstT);
            for (final Entry<Integer, ArrayList<Family>> e : concurrentFamilies.entrySet())
                println("time " + e.getKey() + "numFamily " + e.getValue().size());
            final int currentT = firstT;

            //ici on doit construire d'abord les hyptheses au temps currentT avec les tracks qui sont deja validees,
            //ces hyp sont ensuite completees ou non avec les hyp optionelles au meme temps.
            //lorsque l'on souhaite passer au temps suivant il faut modifier le score avec le nombre de spots du current T non utilises et le nombre de ceux utilises
            //au temps suivant on continue les hypotheses courantes et on essaie ou pas d'ajouter d'autres hypotheses
            //je pense que l'on pourrait deja eliminer des familles en comparant le score de leur meilleure branche avec l'hypothese que toutes les detections utilisees soient fausses`;

            //build  the set of mandatories and optional families with root at time currentT
            final ArrayList<Family> fList = concurrentFamilies.get(currentT);
            final ArrayList<Family> mandatoryFamilies = new ArrayList<>(); //families that have a previous validated tracks are mandatory
            final ArrayList<Family> optionalFamilies = new ArrayList<>(); //families that don't have a previous validated tracks are optional
            for (final Family f : fList) {
                if (f.rootTrack == null)
                    optionalFamilies.add(f);//families that do not have a previous ValidatedTrack are optional
                else {
                    mandatoryFamilies.add(f); //families that already have a previous ValidatedTrack are mandatory
                    println("mandatory family : begin = " + f.rootTrack.associations.getFirst().t + " root time " + f.rootTime);
                }
            }
            println("mFamilies " + mandatoryFamilies.size() + " oFamilies " + optionalFamilies.size());

//			bestHyp = getMLHypothesis(firstT, t, tracker, mandatoryFamilies, optionalFamilies);
//			SolveMLAssociation solver = new SolveMLAssociation();
//			solver.solve(firstT, lastT, concurrentFamilies, mandatoryFamilies, optionalFamilies);
            //GlobalHypothesis gTemp = solver.getHypothesis(this, firstT, lastT, tracker);
//			if(gTemp!=null)System.out.println("gtemp score "+gTemp.score);
//			bestHyp = gTemp;
            //if (true)
            {
                //utiliser obligatoirement une hypothese par famille obligatoire
                if (!mandatoryFamilies.isEmpty()) {
                    final int familyIdx = 0;
                    //final Family f = mandatoryFamilies.get(familyIdx);
                    final Family f = mandatoryFamilies.get(0);
                    final boolean lastFamily = (familyIdx == mandatoryFamilies.size() - 1);
                    for (final Hypothesis hyp : f.hypotheses) {
                        final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                        gh.addHypothesis(hyp);
//						continue with this hypothesis only if its score is better than score of best hyp because it will always decrease later
                        final boolean feasible;
                        synchronized (bestHypSafe) {
                            feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                        }
                        if (feasible) {
                            if (lastFamily) {
                                //we can do 3 things:
                                // 1/ use this hypothesis as best hyp
                                // 2/ try to add optional hypothesis at currentTime
                                // 3/ try to add optional hypothesis at later times

                                // 1/ use this hypothesis as best hyp
                                {
                                    final GlobalHypothesis ghCopy = gh.copy();
                                    for (int time = currentT; time <= t; time++) {
                                        penalizeFD(ghCopy, time);
                                        penalizeNT(ghCopy, time);
                                    }
                                    synchronized (bestHypSafe) {
                                        if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                            bestHypSafe.bestHyp = ghCopy;
                                    }
                                }
                                if (gh.realAssociations.size() < realAssociations.size()) //there still remains real Association that have not been used
                                {
//									2/ try to add optional hypothesis at currentTime
                                    final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                                    if (currentTimeFD > 0 && !optionalFamilies.isEmpty()) //we can try to add spots from optional families at time currentT
                                    {
                                        recursBuildBestGlobalHyp(gh, 0, currentT, t, optionalFamilies);
                                    }
//									3/ try to add optional hypothesis at later times
                                    else if (currentT < t) {
                                        penalizeFD(gh, currentTimeFD, currentT);
                                        penalizeNT(gh, currentT);
                                        final boolean feasible2;
                                        synchronized (bestHypSafe) {
                                            feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                        }
                                        if (feasible2)
                                            recursBuildBestGlobalHyp(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                                    }
                                }
                            }
                            else
                                recursBuildBestGlobalHypWithMandatoryFamilies(gh, familyIdx + 1, currentT, t, mandatoryFamilies, optionalFamilies);
                        }
                    }
                }
                else //there is no mandatory families so we create hypothesis with hypothesis from optional and later families
                {
                    if (!optionalFamilies.isEmpty()) {
                        final int familyIdx = 0;
                        //final Family f = optionalFamilies.get(familyIdx);
                        final Family f = optionalFamilies.get(0);
                        final boolean lastFamily = (familyIdx == optionalFamilies.size() - 1);

//						1/ add hypothesis from this family
                        for (final Hypothesis hyp : f.hypotheses) {
                            final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                            gh.addHypothesis(hyp);
                            // 1/ try to use this hypothesis as the best hyp
                            {
                                final GlobalHypothesis ghCopy = gh.copy();
                                for (int time = currentT; time <= t; time++) {
                                    penalizeFD(ghCopy, time);
                                    penalizeNT(ghCopy, time);
                                }
                                synchronized (bestHypSafe) {
                                    if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                        bestHypSafe.bestHyp = ghCopy;
                                }
                            }
                            if (gh.realAssociations.size() < realAssociations.size()) {
                                final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                                // 2/try to add an hypothesis from next family
                                if (!lastFamily && currentTimeFD > 0)
                                    recursBuildBestGlobalHyp(gh, familyIdx + 1, currentT, t, optionalFamilies);
                                    // 3/ try to add an hypothesis from a family of next time
                                else if (currentT < t) {
                                    penalizeFD(gh, currentTimeFD, currentT);
                                    penalizeNT(gh, currentT);
                                    final boolean feasible;
                                    synchronized (bestHypSafe) {
                                        feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                    }
                                    if (feasible)
                                        recursBuildBestGlobalHyp(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                                }
                            }
                        }
                        // 2/ do not add hypothesis from this family
                        final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                        if (lastFamily) {
                            //add hypothesis from families at next time
                            if (currentT < t) {
                                penalizeFD(gh, currentT);
                                penalizeNT(gh, currentT);
                                final boolean feasible;
                                synchronized (bestHypSafe) {
                                    feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                }
                                if (feasible)
                                    recursBuildBestGlobalHyp(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                            }
                        }
                        else {
//							add hypothesis from next family at current time
                            recursBuildBestGlobalHyp(gh, familyIdx + 1, currentT, t, fList);
                        }
                    }
                }
            }
//			System.out.println("score "+bestHyp.score);
            if (bestHypSafe.bestHyp != null) {
                println("num leafs for best hyp = " + bestHypSafe.bestHyp.hyps.size());
                println("num spots for best hyp = " + bestHypSafe.bestHyp.usedAssociations.size());
                println("num real spots for best hyp = " + bestHypSafe.bestHyp.realAssociations.size());
                println("score " + bestHypSafe.bestHyp.score);
//				for (Hypothesis h:bestHyp.hyps)
//				{
//				println("hypothesis -------");
//				for (Association a:h.usedAssociations)
//				{
//				if (a.isPrediction)
//				println("pred");
//				else
//				println("asso "+a.t+" "+a.spot.mass_center.x+" "+a.spot.mass_center.y);
//				}
//				println("------------------");
//				}
            }
            else
                IcyLogger.error(this.getClass(), "Error bestHyp null");
        }
        else {
            println("no need to build hyp");
        }
        applyBestHyp(t);
        for (final Collection<Family> c : concurrentFamilies.values())
            for (final Family f : c)
                f.hypotheses.clear();
    }

    protected int getRealSpotsNum(final int t) {
        if (t >= firstTime && t <= lastTime) {
            return realSpotsNum[t - firstTime];
        }
        else return 0;
    }

    protected void penalizeFD(final @NotNull GlobalHypothesis gh, final int t) {
        gh.penalizeFD(t, tracker.pFA, realAssociations);
//		int numFD = getRealSpotsNum(t)- gh.getRealSpotsNumAtT(t);
        //gh.penalize(tracker.computeFalseDetectionsPoissonPenalization(numFD));
        //	gh.penalize(tracker.computeFalseDetectionsPenalization(numFD));
    }

    protected void penalizeNT(final GlobalHypothesis gh, final int t) {
        //int numNT = gh.getNumNewTracks(t);
        //gh.penalize(tracker.computeNewTrackPenalization(numNT, t));
    }

    protected void penalizeFD(final @NotNull GlobalHypothesis gh, final int numFD, final int t) {
        gh.penalizeFD(t, tracker.pFA, realAssociations);
        //gh.penalize(tracker.computeFalseDetectionsPenalization(numFD));
    }

    protected void recursBuildBestGlobalHypTree(final GlobalHypothesis ghPrev, final int familyIdx, final int currentT, final int t, final ArrayList<Family> fList) {
        if (fList != null && !fList.isEmpty()) {
            final Family f = fList.get(familyIdx);
            final boolean lastFamily = (familyIdx == fList.size() - 1);
            //add hypothesis from this family
            final Vector<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesis(ghPrev);
//			ArrayList<Thread> thrList = new ArrayList<Thread>();
            for (final Hypothesis htemp : compatibleHypothesis) {
                final Thread thr = new Thread(() -> {
                    final GlobalHypothesis gh = ghPrev.copy();
                    gh.addHypothesis(htemp);
                    final boolean feasible;
                    synchronized (bestHypSafe) {
                        feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                    }
                    if (feasible) {
                        // 1/ try to use this hypothesis as the best hyp
                        {
                            final GlobalHypothesis ghCopy = gh.copy();
                            for (int time = currentT; time <= t; time++) {
                                penalizeFD(ghCopy, time);
                                penalizeNT(ghCopy, time);
                            }
                            synchronized (bestHypSafe) {
                                if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                    bestHypSafe.bestHyp = ghCopy;
                            }
                        }
                        if (gh.realAssociations.size() < realAssociations.size()) {
                            final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                            // 2/try to add an hypothesis from next family
                            if (!lastFamily && currentTimeFD > 0)
                                recursBuildBestGlobalHypTree(gh, familyIdx + 1, currentT, t, fList);
                                // 3/ try to add an hypothesis from a family of next time
                            else if (currentT < t) {
                                penalizeFD(gh, currentTimeFD, currentT);
                                penalizeNT(gh, currentT);
                                final boolean feasible2;
                                synchronized (bestHypSafe) {
                                    feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                }
                                if (feasible2)
                                    recursBuildBestGlobalHypTree(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                            }
                        }
                    }
                });
                synchronized (buildGHthreadList) {
                    buildGHthreadList.add(thr);
                }
                thr.start();
                if (!parallelComputing)
                    try {
                        thr.join();
                    }
                    catch (final InterruptedException e) {
                        IcyLogger.error(this.getClass(), e);
                    }
            }
        }
        else if (currentT < t) {
            final GlobalHypothesis ghCopy = ghPrev.copy();
            penalizeFD(ghCopy, currentT);
            penalizeNT(ghCopy, currentT);
            final boolean feasible;
            synchronized (bestHypSafe) {
                feasible = (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score);
            }
            if (feasible)
                recursBuildBestGlobalHypTree(ghCopy, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
        }
    }


    protected void recursBuildBestGlobalHyp(final GlobalHypothesis ghPrev, final int familyIdx, final int currentT, final int t, final ArrayList<Family> fList) {
        if (fList != null && !fList.isEmpty()) {
            final Family f = fList.get(familyIdx);
            final boolean lastFamily = (familyIdx == fList.size() - 1);

            //add hypothesis from this family
            for (final Hypothesis hyp : f.hypotheses)//ERROR CONCURRENT MODIFICATION EXCEPTION
            {
                final boolean compatible = areCompatible(ghPrev, hyp);
//				for (Association s:hyp.usedAssociations)
//					if (ghPrev.usedAssociations.contains(s))
//					{
//						compatible = false;
//						break;
//					}
                if (compatible) {
                    final GlobalHypothesis gh = ghPrev.copy();
                    gh.addHypothesis(hyp);
                    final boolean feasible;
                    synchronized (bestHypSafe) {
                        feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                    }
                    if (feasible) {
                        // 1/ try to use this hypothesis as the best hyp
                        {
                            final GlobalHypothesis ghCopy = gh.copy();
                            for (int time = currentT; time <= t; time++) {
                                penalizeFD(ghCopy, time);
                                penalizeNT(ghCopy, time);
                            }
                            synchronized (bestHypSafe.bestHyp) {
                                if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                    bestHypSafe.bestHyp = ghCopy;
                            }
                        }
                        if (gh.realAssociations.size() < realAssociations.size()) {
                            final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                            // 2/try to add an hypothesis from next family
                            if (!lastFamily && currentTimeFD > 0)
                                recursBuildBestGlobalHyp(gh, familyIdx + 1, currentT, t, fList);
                                // 3/ try to add an hypothesis from a family of next time
                            else if (currentT < t) {
                                penalizeFD(gh, currentTimeFD, currentT);
                                penalizeNT(gh, currentT);
                                final boolean feasible2;
                                synchronized (bestHypSafe) {
                                    feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                }
                                if (feasible2)
                                    recursBuildBestGlobalHyp(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                            }
                        }
                    }
                }
            }
            // do not add hypothesis from this family
            if (lastFamily) {//add hypothesis from families at next time
//				1/ try to use this hypothesis as the best hyp
                {
                    final GlobalHypothesis ghCopy = ghPrev.copy();
                    for (int time = currentT; time <= t; time++) {
                        penalizeFD(ghCopy, time);
                        penalizeNT(ghCopy, time);
                    }
                    synchronized (bestHypSafe) {
                        if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                            bestHypSafe.bestHyp = ghCopy;
                    }
                }
                if (currentT < t) {
                    final GlobalHypothesis ghCopy = ghPrev.copy();
                    penalizeFD(ghCopy, currentT);
                    penalizeNT(ghCopy, currentT);
                    final boolean feasible;
                    synchronized (bestHypSafe) {
                        feasible = (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score);
                    }
                    if (feasible)
                        recursBuildBestGlobalHyp(ghCopy, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                }
            }
            else {
//				add hypothesis from next family at current time
                recursBuildBestGlobalHyp(ghPrev, familyIdx + 1, currentT, t, fList);
            }
        }
        else if (currentT < t) {
            final GlobalHypothesis ghCopy = ghPrev.copy();
            penalizeFD(ghCopy, currentT);
            penalizeNT(ghCopy, currentT);
            final boolean feasible;
            synchronized (bestHypSafe) {
                feasible = (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score);
            }
            if (feasible)
                recursBuildBestGlobalHyp(ghCopy, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
        }
    }

    protected boolean areCompatible(final GlobalHypothesis gh, final @NotNull Hypothesis h) {
        final long t1 = System.currentTimeMillis();
        boolean compatible = true;
        for (final Association s : h.usedAssociations)
            if (gh.usedAssociations.contains(s)) {
                compatible = false;
                break;
            }
        final long t2 = System.currentTimeMillis();
        //HMMMHTracker.compatibleElapseTime += (t2 - t1);
        HMMMHTracker.setCompatibleElapseTime(HMMMHTracker.getCompatibleElapseTime() + (t2 - t1));
        return compatible;
    }

    protected void recursBuildBestGlobalHypWithMandatoryFamiliesTree(
            final GlobalHypothesis ghPrev,
            final int familyIdx,
            final int currentT,
            final int t,
            final @NotNull ArrayList<Family> mFamilies,
            final ArrayList<Family> oFamilies
    ) {
        final Family f = mFamilies.get(familyIdx);
        final boolean lastFamily = (familyIdx == mFamilies.size() - 1);
        final Vector<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesis(ghPrev);
        for (final Hypothesis htemp : compatibleHypothesis) {
            final Thread thr = new Thread(() -> {
                final GlobalHypothesis gh = ghPrev.copy();
                gh.addHypothesis(htemp);
                final boolean feasible;
                synchronized (bestHypSafe) {
                    feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                }
                if (feasible) {
                    if (lastFamily) //if this is the last family we can extend hypothesis with hypothesis from optional families or later families
                    {
                        //we can do 3 things:
                        // 1/ use this hypothesis as best hyp
                        // 2/ try to add optional hypothesis at currentTime
                        // 3/ try to add optional hypothesis at later times

                        // 1/ use this hypothesis as best hyp
                        {
                            final GlobalHypothesis ghCopy = gh.copy();
                            for (int time = currentT; time <= t; time++) {
                                penalizeFD(ghCopy, time);
                                penalizeNT(ghCopy, time);
                            }
                            synchronized (bestHypSafe) {
                                if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                    bestHypSafe.bestHyp = ghCopy;
                            }
                        }
                        if (gh.realAssociations.size() < realAssociations.size()) //there still remains real Association that have not been used
                        {
                            final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
//								2/ try to add optional hypothesis at currentTime
                            if (currentTimeFD > 0 && !oFamilies.isEmpty()) //we can try to add spots from optional families at time currentT
                            {
                                recursBuildBestGlobalHypTree(gh, 0, currentT, t, oFamilies);
                            }
//								3/ try to add optional hypothesis at later times
                            else if (currentT < t) {
                                penalizeFD(gh, currentTimeFD, currentT);
                                penalizeNT(gh, currentT);
                                final boolean feasible2;
                                synchronized (bestHypSafe) {
                                    feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                }
                                if (feasible2)
                                    recursBuildBestGlobalHypTree(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                            }
                        }
                    }
                    else
                        recursBuildBestGlobalHypWithMandatoryFamiliesTree(gh, familyIdx + 1, currentT, t, mFamilies, oFamilies);
                }
            });
            synchronized (buildGHthreadList) {
                buildGHthreadList.add(thr);
            }
            thr.start();
            if (!parallelComputing)
                try {
                    thr.join();
                }
                catch (final InterruptedException e) {
                    IcyLogger.error(this.getClass(), e);
                }
        }
    }

    protected void recursBuildBestGlobalHypWithMandatoryFamilies(
            final GlobalHypothesis ghPrev,
            final int familyIdx,
            final int currentT,
            final int t,
            final @NotNull ArrayList<Family> mFamilies,
            final ArrayList<Family> oFamilies
    ) {
        final Family f = mFamilies.get(familyIdx);
        final boolean lastFamily = (familyIdx == mFamilies.size() - 1);
        for (final Hypothesis hyp : f.hypotheses) {
            final boolean compatible = areCompatible(ghPrev, hyp);
            if (compatible) {
                final GlobalHypothesis gh = ghPrev.copy();
                gh.addHypothesis(hyp);
                final boolean feasible;
                synchronized (bestHypSafe) {
                    feasible = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                }
                if (feasible) {
                    if (lastFamily) //if this is the last family we can extend hypothesis with hypothesis from optional families or later families
                    {
                        //we can do 3 things:
                        // 1/ use this hypothesis as best hyp
                        // 2/ try to add optional hypothesis at currentTime
                        // 3/ try to add optional hypothesis at later times

                        // 1/ use this hypothesis as best hyp
                        {
                            final GlobalHypothesis ghCopy = gh.copy();
                            for (int time = currentT; time <= t; time++) {
                                penalizeFD(ghCopy, time);
                                penalizeNT(ghCopy, time);
                            }
                            synchronized (bestHypSafe) {
                                if (bestHypSafe.bestHyp == null || ghCopy.score > bestHypSafe.bestHyp.score)
                                    bestHypSafe.bestHyp = ghCopy;
                            }
                        }
                        if (gh.realAssociations.size() < realAssociations.size()) //there still remains real Association that have not been used
                        {
                            final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
//							2/ try to add optional hypothesis at currentTime
                            if (currentTimeFD > 0 && !oFamilies.isEmpty()) //we can try to add spots from optional families at time currentT
                            {
                                recursBuildBestGlobalHyp(gh, 0, currentT, t, oFamilies);
                            }
//							3/ try to add optional hypothesis at later times
                            else if (currentT < t) {
                                penalizeFD(gh, currentTimeFD, currentT);
                                penalizeNT(gh, currentT);
                                final boolean feasible2;
                                synchronized (bestHypSafe) {
                                    feasible2 = (bestHypSafe.bestHyp == null || gh.score > bestHypSafe.bestHyp.score);
                                }
                                if (feasible2)
                                    recursBuildBestGlobalHyp(gh, 0, currentT + 1, t, concurrentFamilies.get(currentT + 1));
                            }
                        }
                    }
                    else
                        recursBuildBestGlobalHypWithMandatoryFamilies(gh, familyIdx + 1, currentT, t, mFamilies, oFamilies);
                }
            }
        }
    }

    public void recursPrintPerceiv(final @NotNull MHTrack2s trk) {
//		PluginConsole.println(" "+trk.perceiv);
        if (trk.precedingSegment != null) {
            recursPrintPerceiv(trk.precedingSegment);
        }
    }

    public void applyBestHyp(final int t) {
        final long l1 = System.currentTimeMillis();
        if (bestHypSafe.bestHyp != null) {
            println("**************************************************************");
            println("applyBesthyp at t = " + t);
            final int newRootTime = t - K + 1;

            final LinkedList<Family> validatedFamilies = new LinkedList<>();
            for (final Hypothesis h : bestHypSafe.bestHyp.hyps) {
                final Family family = Objects.requireNonNull(h.leaf).family;
                family.lastSelectedLeaf = h.leaf;
                final MHTrack2s oldRoot = family.rootNode;
                println("leaf time " + t + " new root time" + newRootTime + " old root time " + oldRoot.association.t);
                if (oldRoot.association.t < newRootTime)//apply hyp and create a new family with delayed root
                {
                    //find the new root
                    boolean stop;
                    MHTrack2s temp = h.leaf;
                    println("hleaf time " + h.leaf.association.t);
                    stop = (temp == null) || (temp.association.t == newRootTime);
                    while (!stop) {
                        temp = temp.precedingSegment;
                        if (temp != null)
                            println("temp time " + temp.association.t);
                        stop = (temp == null) || (temp.association.t == newRootTime);
                    }
                    MHTrack2s newRoot = null;
                    if (temp != null && temp.association.t == newRootTime)
                        newRoot = temp;
                    else
                        println("new root null");
                    //create or continue a validated track
                    final ValidatedTrack vtrack;
                    if (family.rootTrack == null)//create new track
                    {
                        vtrack = new ValidatedTrack(family);
                        tracker.addValidatedTrack(vtrack);
                    }
                    else//continue previous track
                    {
                        vtrack = family.rootTrack;
                    }
                    //update family if its non null and it is K deep
                    oldRoot.followingSegments.clear();
                    if (newRoot != null) {
                        oldRoot.followingSegments.add(newRoot);
                        family.updateFamily(newRoot, vtrack, t);
                        println("new family: " + family.rootTime + " " + (family.rootTrack == null) + " " + family.leafs.size());
                        vtrack.update(family);
                        tracker.addFamily(family);
                    }
                    else
                        println("no new root");
                }
                final long l2 = System.currentTimeMillis();
                //HMMMHTracker.applyHypTime += (l2 - l1);
                HMMMHTracker.setApplyHypTime(HMMMHTracker.getApplyHypTime() + (l2 - l1));
            }
            //first add families which are less deep than K
            for (final Entry<Integer, ArrayList<Family>> e : concurrentFamilies.entrySet()) {
                final int rootTime = e.getKey();
                if (rootTime > newRootTime)
                    tracker.addFamilies(e.getValue());
                else if (rootTime == newRootTime) // we must check if the family is still feasible compared to selected hypothesis
                {
                    for (final Family f : e.getValue()) {
                        if (!f.rootNode.association.isPrediction) {
                            boolean feasible = true;
                            for (final Family vf : validatedFamilies)
                                if ((!vf.rootNode.association.isPrediction) && (vf.rootNode.association.spot == f.rootNode.association.spot)) {
                                    feasible = false;
                                    break;
                                }
                            if (feasible)
                                tracker.addFamily(f);
                        }
                    }
                }
            }
            println("**************************************************************");
        }
        else
            for (final Collection<Family> c : concurrentFamilies.values())
                tracker.addFamilies(c);
    }

    boolean verbose = false;

    protected void println(final String text) {
        if (verbose)
            IcyLogger.trace(this.getClass(), text);
    }

    /*@Override
    protected void finalize() throws Throwable {
        realAssociations = null;
        concurrentFamilies = null;
        bestHypSafe.bestHyp = null;
        realSpotsNum = null;
        tracker = null;
        super.finalize();
    }*/

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        realAssociations = null;
        concurrentFamilies = null;
        bestHypSafe.bestHyp = null;
        realSpotsNum = null;
        tracker = null;
    }

    //cette version fonctionne mais donne un resultat errone car quand les branches sont longues, c'est la branche d'arret de la track qui a le meilleur score!
    public GlobalHypothesis getMLHypothesis(
            final int firstT,
            final int lastT,
            final HMMMHTracker tracker,
            final @NotNull ArrayList<Family> mandatoryFamilies,
            final ArrayList<Family> optionalFamilies
    ) {
        final GlobalHypothesis gh = new GlobalHypothesis(firstT, lastT, tracker);
        if (!mandatoryFamilies.isEmpty()) {
            final ArrayList<Family> lFamilies = new ArrayList<>(mandatoryFamilies);
            final HashMap<Family, LinkedList<Hypothesis>> feasibleHypothesis = new HashMap<>();
            for (final Family f : lFamilies)
                feasibleHypothesis.put(f, new LinkedList<>(f.hypotheses));
            while (!lFamilies.isEmpty()) {
                //select the best hypothesis in the feasible set
                Family selectedFamily = null;
                Hypothesis bestH = null;
                for (final Family f : lFamilies) {
                    final LinkedList<Hypothesis> lh = feasibleHypothesis.get(f);
                    final LinkedList<Hypothesis> updatelh = new LinkedList<>();
                    for (final Hypothesis h : lh) {
//						update now the sets of feasible hypothesis for next time
                        final boolean compatible = areCompatible(gh, h);
//						for (Association s:h.usedAssociations)
//							if (gh.usedAssociations.contains(s))
//							{
//								compatible = false;
//								break;
//							}
                        if (compatible) {
                            updatelh.add(h);
                            if (h.leaf != null && (bestH == null || h.leaf.score > bestH.leaf.score)) {
                                selectedFamily = f;
                                bestH = h;
                            }
                        }
                    }
                    feasibleHypothesis.put(f, updatelh);
                }
                //apply
                if (bestH == null)
                    break;
                else {
                    gh.addHypothesis(bestH);
                    lFamilies.remove(selectedFamily);
                    feasibleHypothesis.remove(selectedFamily);
                }
            }
        }
        //then for optional families of this time and other times
        int t = firstT;
        while (t <= lastT) {
            ArrayList<Family> lFamilies;
            if (t == firstT)
                lFamilies = optionalFamilies;
            else
                lFamilies = concurrentFamilies.get(t);
            if (lFamilies != null && !lFamilies.isEmpty()) {
                lFamilies = new ArrayList<>(lFamilies);
                final HashMap<Family, LinkedList<Hypothesis>> feasibleHypothesis = new HashMap<>();
                for (final Family f : lFamilies)
                    feasibleHypothesis.put(f, new LinkedList<>(f.hypotheses));
                while (!lFamilies.isEmpty()) {
                    //select the best hypothesis in the feasible set
                    Family selectedFamily = null;
                    Hypothesis bestH = null;
                    for (final Family f : lFamilies) {
                        final LinkedList<Hypothesis> lh = feasibleHypothesis.get(f);
                        final LinkedList<Hypothesis> updatelh = new LinkedList<>();
                        for (final Hypothesis h : lh) {
                            //update now the sets of feasible hypothesis for next time
                            final boolean compatible = areCompatible(gh, h);
//							for (Association s:h.usedAssociations)
//								if (gh.usedAssociations.contains(s))
//								{
//									compatible = false;
//									break;
//								}
                            if (compatible) {
                                updatelh.add(h);
                                if (h.leaf != null && (bestH == null || h.leaf.score > bestH.leaf.score)) {
                                    selectedFamily = f;
                                    bestH = h;
                                }
                            }
                        }
                        feasibleHypothesis.put(f, updatelh);
                    }
                    //apply
                    if (bestH != null) {
                        gh.addHypothesis(bestH);
                        lFamilies.remove(selectedFamily);
                        feasibleHypothesis.remove(selectedFamily);
                    }
                    else
                        break;
                }
            }
            penalizeFD(gh, t);
            penalizeNT(gh, t);
            t++;
        }

        return gh;
    }
}
