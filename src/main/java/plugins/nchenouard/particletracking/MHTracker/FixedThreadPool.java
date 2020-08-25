package plugins.nchenouard.particletracking.MHTracker;

import java.util.concurrent.locks.ReentrantLock;

public class FixedThreadPool
{
	Task[] threadPool;
	boolean[] finishedTasks;
	int nextThread = 0;
	ReentrantLock poolLock = new ReentrantLock();

	ReentrantLock poolStopped = new ReentrantLock();
	int numStoppedThreads = 0;
	RunningThread[] runningThreads;
	int numThreads;

	public FixedThreadPool(Task[] threadPool)
	{
		this.threadPool = threadPool;
		this.finishedTasks = new boolean[threadPool.length];
	}

	public void runThreads(int numThreads)
	{
		this.numThreads = numThreads;
		nextThread = 0;
		runningThreads = new RunningThread[numThreads];
		numStoppedThreads = 0;
		for (int i = 0; i < numThreads; i ++)
		{
			RunningThread thr = new RunningThread();
			runningThreads[i] = thr;
		}
		for (Thread thr:runningThreads)
			thr.start();
		waitForTermination();
		try{
			if (numStoppedThreads < numThreads)
				throw new Exception("ERRR "+numStoppedThreads+ " " + numThreads);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	void waitForTermination()
	{
		synchronized(this)
		{
			while (numStoppedThreads < numThreads)
			{
				try {
					wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public interface Task
	{
		public void run();
	}

	class RunningThread extends Thread
	{
		boolean stop = false;

		@Override
		public void run()
		{
			//			Task task = getTask();
			Task task = null;
			synchronized(FixedThreadPool.this)
			{
				if(nextThread < threadPool.length)
				{
					task = threadPool[nextThread];
					nextThread++;
				}
			}
			while (task != null)
			{
				task.run();
				task = null;
				synchronized(FixedThreadPool.this)
				{
					if(nextThread < threadPool.length)
					{
						task = threadPool[nextThread];
						nextThread++;
					}
				}
			}
			synchronized(FixedThreadPool.this)
			{
				numStoppedThreads ++;
				FixedThreadPool.this.notify();
			}
		}
	}
}
