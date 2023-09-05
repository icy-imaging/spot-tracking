/*
 * Copyright (c) 2010-2023. Institut Pasteur.
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

import icy.system.IcyExceptionHandler;

import java.util.concurrent.locks.ReentrantLock;

public class FixedThreadPool {
    Task[] threadPool;
    boolean[] finishedTasks;
    int nextThread = 0;
    ReentrantLock poolLock = new ReentrantLock();

    ReentrantLock poolStopped = new ReentrantLock();
    int numStoppedThreads = 0;
    RunningThread[] runningThreads;
    int numThreads;

    public FixedThreadPool(final Task[] threadPool) {
        this.threadPool = threadPool;
        this.finishedTasks = new boolean[threadPool.length];
    }

    public void runThreads(final int numThreads) {
        this.numThreads = numThreads;
        nextThread = 0;
        runningThreads = new RunningThread[numThreads];
        numStoppedThreads = 0;
        for (int i = 0; i < numThreads; i++) {
            final RunningThread thr = new RunningThread();
            runningThreads[i] = thr;
        }
        for (final Thread thr : runningThreads)
            thr.start();
        waitForTermination();
        try {
            if (numStoppedThreads < numThreads)
                throw new Exception("ERRR " + numStoppedThreads + " " + numThreads);
        }
        catch (final Exception e) {
            IcyExceptionHandler.showErrorMessage(e, true);
        }
    }

    void waitForTermination() {
        synchronized (this) {
            while (numStoppedThreads < numThreads) {
                try {
                    wait();
                }
                catch (final InterruptedException e) {
                    IcyExceptionHandler.showErrorMessage(e, true);
                }
            }
        }
    }

    public interface Task {
        void run();
    }

    class RunningThread extends Thread {
        boolean stop = false;

        @Override
        public void run() {
            //			Task task = getTask();
            Task task = null;
            synchronized (FixedThreadPool.this) {
                if (nextThread < threadPool.length) {
                    task = threadPool[nextThread];
                    nextThread++;
                }
            }
            while (task != null) {
                task.run();
                task = null;
                synchronized (FixedThreadPool.this) {
                    if (nextThread < threadPool.length) {
                        task = threadPool[nextThread];
                        nextThread++;
                    }
                }
            }
            synchronized (FixedThreadPool.this) {
                numStoppedThreads++;
                FixedThreadPool.this.notify();
            }
        }
    }
}
