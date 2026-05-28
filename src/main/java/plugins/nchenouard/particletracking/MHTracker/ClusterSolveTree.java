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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Specialized Cluster object which contains some Linear Programming (LP) methods to solve the tracking problem using a tree exploration implementation (slow)
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class ClusterSolveTree extends Cluster {
    public class WaitingThreadCounter {
        public Lock cntLock = new ReentrantLock();
        final public Condition countWainting = cntLock.newCondition();
        int cntThreads = 0;

        public void increaseCount() {
            cntLock.lock();
            cntThreads++;
            if (cntThreads == maxNumThread && waitingTasks.isEmpty)
                countWainting.signalAll();
            cntLock.unlock();
        }

        public void decreaseCount() {
            cntLock.lock();
            cntThreads--;
            cntLock.unlock();
        }

        public void reset() {
            cntLock.lock();
            cntThreads = 0;
            cntLock.unlock();
        }

        public boolean someRunning() {
            final boolean someRunning;
            cntLock.lock();
            someRunning = (cntThreads != maxNumThread);
            cntLock.unlock();
            return someRunning;
        }
    }

    WaitingThreadCounter wtc = new WaitingThreadCounter();

    class ExecutorThread extends Thread {
        public boolean run = true;

        public ExecutorThread() {
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            Task tsk = getTask();
            while (run) {
                final TaskList taskList = Objects.requireNonNull(tsk).run();
                if (taskList == null || taskList.isEmpty) {
                    tsk = getTask();
                }
                else {
                    tsk = taskList.popTask();
                    waitingTasks.insertAtStart(taskList);
                }
            }
        }

        private @Nullable Task getTask() {
            waitingTasks.lock.lock();
            if (waitingTasks.isEmpty) {
                wtc.increaseCount();
                while (waitingTasks.isEmpty && run) {
                    try {
                        waitingTasks.emptyCondition.await();
                    }
                    catch (final InterruptedException e) {
                        IcyLogger.error(this.getClass(), e);
                    }
                }
                wtc.decreaseCount();
                if (!run) {
                    waitingTasks.lock.unlock();
                    return null;
                }
            }
            final Task tsk = waitingTasks.popTask();
            waitingTasks.lock.unlock();
            return tsk;
        }
    }


    public interface Task {
        TaskList run();
    }

    public static class TLinked {
        Task t;
        TLinked nextT = null;

        @Contract(pure = true)
        TLinked(final Task t) {
            this.t = t;
        }
    }

    public static class TaskList {
        boolean isEmpty = true;
        TLinked firstT = null;
        TLinked lastT = null;

        @Contract(pure = true)
        public TaskList() {
        }

        public TaskList(final TLinked t) {
            if (t != null && t.t != null) {
                isEmpty = false;
                firstT = t;
                lastT = t;
                t.nextT = null;
            }
        }

        public void addLast(final TLinked t) {
            if (t != null && t.t != null) {
                if (isEmpty) {
                    firstT = t;
                    lastT = t;
                    isEmpty = false;
                }
                else {
                    lastT.nextT = t;
                    lastT = t;

                }
                t.nextT = null;
            }
        }

        public void addLast(final Task task) {
            if (task != null) {
                final TLinked t = new TLinked(task);
                addLast(t);
            }
        }


        public void addFirst(final TLinked t) {
            if (t != null && t.t != null) {
                if (isEmpty) {
                    firstT = t;
                    lastT = t;
                    t.nextT = null;
                    isEmpty = false;
                }
                else {
                    t.nextT = firstT;
                    firstT = t;
                }
            }
        }

        public void addFirst(final Task task) {
            if (task != null) {
                final TLinked t = new TLinked(task);
                addFirst(t);
            }
        }

        public void insertAtStart(final @NotNull TaskList tList) {
            if (!tList.isEmpty) {
//				TLinked tl = tList.firstT;
//				while (tl!=null)
//				{
//					TLinked tlNext = tl.nextT;
//					addFirst(tl);
//					tl = tlNext;
//				}

                if (isEmpty) {
                    firstT = tList.firstT;
                    lastT = tList.lastT;
                    isEmpty = false;
                }
                else {
                    tList.lastT.nextT = firstT;
                    firstT = tList.firstT;
                }
            }
        }

        public Task popTask() {
            final Task t = firstT.t;
            firstT = firstT.nextT;
            if (firstT == null) {
                if (lastT.t != t) {
                    if (lastT == null)
                        IcyLogger.error(this.getClass(), "ERROR lastT null");
                    else
                        IcyLogger.error(this.getClass(), "ERROR ");
                }
                lastT = null;
                isEmpty = true;
            }
            return t;
        }
    }

    class RecursHypTreeTask implements Task {
        final GlobalHypothesis gh;
        final int currentT;
        final boolean lastFamily;
        final int familyIdx;

        public RecursHypTreeTask(final @NotNull GlobalHypothesis ghPrev, final Hypothesis hyp, final int currentT, final int familyIdx, final boolean lastFamily) {
            gh = ghPrev.copy();
            gh.addHypothesis(hyp);
            this.currentT = currentT;
            this.lastFamily = lastFamily;
            this.familyIdx = familyIdx;
        }

        @Override
        public TaskList run() {
            TaskList tskList = null;
            // 1/ try to use this hypothesis as the best hyp
            {
                final GlobalHypothesis ghCopy = gh.copy();
                for (int time = currentT; time <= t; time++) {
                    penalizeFD(ghCopy, time);
                    penalizeNT(ghCopy, time);
                }
                synchronized (BH) {
                    if (ghCopy.score > BH.score)
                        BH = ghCopy;
                }
            }
            if (gh.realAssociations.size() < realAssociations.size()) {
                final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
                // 2/try to add an hypothesis from next family
                if (!lastFamily && currentTimeFD > 0)
                    tskList = recursBuildBestGlobalHypTree(gh, familyIdx + 1, currentT);
                    // 3/ try to add an hypothesis from a family of next time
                else if (currentT < t) {
                    penalizeFD(gh, currentTimeFD, currentT);
                    penalizeNT(gh, currentT);
                    if (gh.score > BH.score)
                        tskList = recursBuildBestGlobalHypTree(gh, 0, currentT + 1);
                }
            }
            return tskList;
        }
    }


    class RecursHypTreeMandatoryTask implements Task {
        final boolean lastFamily;
        final GlobalHypothesis gh;
        final int currentT;
        final int familyIdx;

        public RecursHypTreeMandatoryTask(
                final @NotNull GlobalHypothesis ghPrev,
                final Hypothesis hyp,
                final int currentT,
                final int familyIdx,
                final boolean lastFamily
        ) {
            gh = ghPrev.copy();
            gh.addHypothesis(hyp);
            this.lastFamily = lastFamily;
            this.currentT = currentT;
            this.familyIdx = familyIdx;
        }

        @Override
        public TaskList run() {
            TaskList tskList = null;
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
                    synchronized (BH) {
                        if (ghCopy.score > BH.score)
                            BH = ghCopy;
                    }
                }
                if (gh.realAssociations.size() < realAssociations.size()) //there still remains real Association that have not been used
                {
                    final int currentTimeFD = getRealSpotsNum(currentT) - gh.getRealSpotsNumAtT(currentT);
//					2/ try to add optional hypothesis at currentTime
                    if (currentTimeFD > 0 && !optionalFamilies.isEmpty()) //we can try to add spots from optional families at time currentT
                    {
                        tskList = recursBuildBestGlobalHypTree(gh, 0, currentT);
                    }
//					3/ try to add optional hypothesis at later times
                    else if (currentT < t) {
                        penalizeFD(gh, currentTimeFD, currentT);
                        penalizeNT(gh, currentT);
                        if (gh.score > BH.score)
                            tskList = recursBuildBestGlobalHypTree(gh, 0, currentT + 1);
                    }
                }
            }
            else
                tskList = recursBuildBestGlobalHypWithMandatoryFamiliesTree(gh, familyIdx + 1, currentT);
            return tskList;
        }
    }

    static class WaitingList extends TaskList {
        public Lock lock = new ReentrantLock();
        final public Condition emptyCondition = lock.newCondition();

        @Override
        public void insertAtStart(final @NotNull TaskList tList) {
            if (/*tList != null &&*/ !tList.isEmpty) {
                lock.lock();
                super.insertAtStart(tList);
                emptyCondition.signalAll();
                lock.unlock();
            }
        }

        @Override
        public void addLast(final TLinked t) {
            lock.lock();
            super.addLast(t);
            emptyCondition.signalAll();
            lock.unlock();
        }

        @Override
        public void addLast(final Task t) {
            lock.lock();
            super.addLast(t);
            emptyCondition.signalAll();
            lock.unlock();
        }

        @Override
        public void addFirst(final TLinked t) {
            lock.lock();
            super.addFirst(t);
            emptyCondition.signalAll();
            lock.unlock();
        }

        @Override
        public void addFirst(final Task t) {
            lock.lock();
            super.addFirst(t);
            emptyCondition.signalAll();
            lock.unlock();
        }

        @Override
        public Task popTask() {
            lock.lock();
            final Task tsk = super.popTask();
            lock.unlock();
            return tsk;
        }
    }

    WaitingList waitingTasks;

    ArrayList<ExecutorThread> executorList = new ArrayList<>();

    int maxNumThread = 4;
    volatile GlobalHypothesis BH;
    int t;
    final ArrayList<Family> mandatoryFamilies = new ArrayList<>(); //families that have a previous validated tracks are mandatory
    final ArrayList<Family> optionalFamilies = new ArrayList<>(); //families that don't have a previous validated tracks are optional

    public ClusterSolveTree(final HMMMHTracker tracker, final int firstT, final int lastT, final int K) {
        super(tracker, firstT, lastT, K);
    }

    @Override
    public void buildAndApplyBestHypTree(final int t2) {
        {
            t = t2;
            int firstTtemp = -1;
            final boolean buildAndApplyBestHyp;
            for (final Integer i : concurrentFamilies.keySet()) {
                if (firstTtemp < 0 || i < firstTtemp)
                    firstTtemp = i;
            }
            buildAndApplyBestHyp = (firstTtemp >= 0 && firstTtemp <= t - K);
            GlobalHypothesis test = null;
            final int firstT = firstTtemp;

            if (buildAndApplyBestHyp) {
                println("-----build best Hyp");
                println("different root time for families " + concurrentFamilies.size());
                //for each family build global hyps and only keep the best one
                println("firstT " + firstT + " first time " + firstT + "  time " + t);
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
                mandatoryFamilies.clear(); //families that have a previous validated tracks are mandatory
                optionalFamilies.clear(); //families that don't have a previous validated tracks are optional
                for (final Family f : fList) {
                    if (f.rootTrack == null)
                        optionalFamilies.add(f);//families that do not have a previous ValidatedTrack are optional
                    else {
                        mandatoryFamilies.add(f); //families that already have a previous ValidatedTrack are mandatory
                        println("mandatory family : begin = " + f.rootTrack.associations.getFirst().t + " root time " + f.rootTime);
                    }
                }
                println("mFamilies " + mandatoryFamilies.size() + " oFamilies " + optionalFamilies.size());

                final SolveMLAssociation mlSolver = new SolveMLAssociation();
                final long ml1 = System.currentTimeMillis();
                mlSolver.solve(firstT, t, concurrentFamilies, mandatoryFamilies, optionalFamilies);
                BH = mlSolver.getHypothesis(this, firstT, t, tracker);
                test = BH;
                if (BH == null)
                    IcyLogger.error(this.getClass(), "BH NULL, ML failed");
                // BEST GLOBAL SOLUTION EXTRACTION ////////////////////////////////////////////////////////////////////////////////////////////////////
                //create a set of executor threads
                waitingTasks = new WaitingList();

                final long ml2 = System.currentTimeMillis();
                //HMMMHTracker.MLFormationTime += (ml2 - ml1);
                HMMMHTracker.setMLFormationTime(HMMMHTracker.getMLFormationTime() + (ml2 - ml1));

                if (!mandatoryFamilies.isEmpty()) {
                    final int familyIdx = 0;
                    //final Family f = mandatoryFamilies.get(familyIdx);
                    final Family f = mandatoryFamilies.get(0);
                    final boolean lastFamily = (familyIdx == mandatoryFamilies.size() - 1);
                    final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
                    final TaskList tskList = new TaskList();
                    for (final Hypothesis htemp : f.hypotheses)
                        tskList.addLast(new RecursHypTreeMandatoryTask(gh, htemp, currentT, familyIdx, lastFamily));
                    waitingTasks.insertAtStart(tskList);
                }
                else {
                    if (!optionalFamilies.isEmpty()) {
                        final int familyIdx = 0;
                        //final Family f = optionalFamilies.get(familyIdx);
                        final Family f = optionalFamilies.get(0);
                        final boolean lastFamily = (familyIdx == optionalFamilies.size() - 1);
                        final GlobalHypothesis gh = new GlobalHypothesis(firstT, t, tracker);
//						1/ add hypothesis from this family
                        final TaskList tskList = new TaskList();
                        for (final Hypothesis htemp : f.hypotheses)
                            tskList.addLast(new RecursHypTreeTask(gh, htemp, currentT, familyIdx, lastFamily));
                        waitingTasks.insertAtStart(tskList);
                        // 2/ do not add hypothesis from this family
                        if (lastFamily) {
                            //add hypothesis from families at next time
                            if (currentT < t) {
                                penalizeFD(gh, currentT);
                                penalizeNT(gh, currentT);
                                if (gh.score > BH.score) {
                                    final TaskList tskList2 = recursBuildBestGlobalHypTree(gh, 0, currentT + 1);
//									if (tskList2!=null){waitingTasks.lock.lock(); waitingTasks.insertAtStart(tskList2); waitingTasks.lock.unlock();}
                                    if (tskList2 != null) {
                                        waitingTasks.insertAtStart(tskList2);
                                    }
                                }
                            }
                        }
                        else {
//							add hypothesis from next family at current time
                            final TaskList tskList2 = recursBuildBestGlobalHypTree(gh, familyIdx + 1, currentT);
//							if (tskList2!=null){waitingTasks.lock.lock(); waitingTasks.insertAtStart(tskList2); waitingTasks.lock.unlock();}
                            if (tskList2 != null) {
                                waitingTasks.insertAtStart(tskList2);
                            }
                        }
                    }
                }
                for (int i = 0; i < maxNumThread; i++)
                    executorList.add(new ExecutorThread());
                for (final ExecutorThread exe : executorList)
                    exe.start();
                wtc.cntLock.lock();
                try {
                    while (wtc.someRunning())
                        wtc.countWainting.await();
                }
                catch (final InterruptedException e) {
                    IcyLogger.error(this.getClass(), e);
                }
                wtc.cntLock.unlock();
                for (final ExecutorThread e : executorList) {
                    e.run = false;
                }
                waitingTasks.lock.lock();
                waitingTasks.emptyCondition.signalAll();
                waitingTasks.lock.unlock();


                for (final ExecutorThread e : executorList)
                    try {
                        e.join();
                    }
                    catch (final InterruptedException e1) {
                        IcyLogger.error(this.getClass(), e1);
                    }
                executorList.clear();
            }
            else {
                println("no need to build hyp");
            }
            if (test != null) {
                if (test.score == BH.score)
                    ;//System.out.println("same");
                else if (test.score < BH.score)
                    ;//System.out.println("improvement");
                else {
                    IcyLogger.trace(this.getClass(), "decrease!", test.score + " | " + BH.score);
                }
            }
        }
        bestHypSafe.bestHyp = BH;
        applyBestHyp(t);
        for (final Collection<Family> c : concurrentFamilies.values())
            for (final Family f : c)
                f.hypotheses.clear();
    }

    protected TaskList recursBuildBestGlobalHypTree(final GlobalHypothesis ghPrev, final int familyIdx, final int currentT) {
        final ArrayList<Family> fList;
        if (currentT == firstTime)
            fList = optionalFamilies;
        else
            fList = concurrentFamilies.get(currentT);
        if (fList != null && !fList.isEmpty()) {
            final Family f = fList.get(familyIdx);
            final boolean lastFamily = (familyIdx == fList.size() - 1);
            final double bestScore = BH.score;
//			Vector<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesis(ghPrev, bestScore);
            final ArrayList<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesisSingleThread(ghPrev, bestScore);

            final TaskList tasks = new TaskList();
            for (final Hypothesis htemp : compatibleHypothesis)
                tasks.addLast(new RecursHypTreeTask(ghPrev, htemp, currentT, familyIdx, lastFamily));
            return tasks;
        }
        else if (currentT < t) {
            final GlobalHypothesis ghCopy = ghPrev.copy();
            penalizeFD(ghCopy, currentT);
            penalizeNT(ghCopy, currentT);
            return recursBuildBestGlobalHypTree(ghCopy, 0, currentT + 1);
        }
        return null;
    }

    protected TaskList recursBuildBestGlobalHypWithMandatoryFamiliesTree(final GlobalHypothesis ghPrev, final int familyIdx, final int currentT) {
        final Family f = mandatoryFamilies.get(familyIdx);
        final boolean lastFamily = (familyIdx == mandatoryFamilies.size() - 1);
        final double bestScore = BH.score;
//		Vector<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesis(ghPrev, bestScore);	
        final ArrayList<Hypothesis> compatibleHypothesis = f.getCompatibleHypothesisSingleThread(ghPrev, bestScore);

        final TaskList tskList = new TaskList();
        for (final Hypothesis htemp : compatibleHypothesis)
            tskList.addLast(new RecursHypTreeMandatoryTask(ghPrev, htemp, currentT, familyIdx, lastFamily));
        return tskList;
    }
}