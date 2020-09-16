package plugins.nchenouard.particletracking.MHTracker;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** 
 *  Counter of the number of running threads for the tree-based MHT optimization procedure
 *  
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 * */

public class ThreadCounter
{
	public Lock lock = new ReentrantLock();
	final public Condition countAndDeathEquality = lock.newCondition();
	public int cntThreads = 0;
	
	boolean safeEnded = false;
	
	public void increaseCount()
	{
		lock.lock();
		cntThreads++;
		lock.unlock();
	}
	
	public void increaseDeath()
	{
		lock.lock();
		cntThreads--;
		if (cntThreads == 0)
			countAndDeathEquality.signalAll();
		lock.unlock();
	}
	
	public void reset()
	{
		lock.lock();
		cntThreads = 0;
		lock.unlock();
	}
}