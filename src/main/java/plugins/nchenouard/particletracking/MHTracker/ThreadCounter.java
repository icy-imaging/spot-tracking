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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Counter of the number of running threads for the tree-based MHT optimization procedure
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class ThreadCounter {
    public Lock lock = new ReentrantLock();
    final public Condition countAndDeathEquality = lock.newCondition();
    public int cntThreads = 0;

    boolean safeEnded = false;

    public void increaseCount() {
        lock.lock();
        cntThreads++;
        lock.unlock();
    }

    public void increaseDeath() {
        lock.lock();
        cntThreads--;
        if (cntThreads == 0)
            countAndDeathEquality.signalAll();
        lock.unlock();
    }

    public void reset() {
        lock.lock();
        cntThreads = 0;
        lock.unlock();
    }
}