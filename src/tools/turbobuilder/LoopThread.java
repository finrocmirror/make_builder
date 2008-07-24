/**
 * You received this file as part of Jmcagui - a universal
 * (Web-)GUI editor for Robotic Systems.
 *
 * Copyright (C) 2007 Max Reichardt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package tools.turbobuilder;

/**
 * @author max
 * 
 * A Thread that calls a callback function with a specified rate
 */
public abstract class LoopThread extends Thread {

	/** Signals for state change */
	private volatile boolean stopSignal = false, pauseSignal = false;
	
	/** Cycle time with which callback function is called */
	private long cycleTime;
	
	/** Display warning, if cycle time is exceeded? */
	private final boolean warnOnCycleTimeExceed;
	
	/** Display warnings on console? */
	private static final boolean DISPLAYWARNINGS = false;

	/** Maximum time to spend waiting before waitCallback() is called */
	private int maxWaitTime = Integer.MAX_VALUE;
	
	/** Is thread currently waiting ? */
	private volatile boolean waiting;
	
	/**
	 * @param defaultCycleTime Cycle time with which callback function is called
	 * @param warnOnCycleTimeExceed Display warning, if cycle time is exceeded?
	 */
	public LoopThread(long defaultCycleTime, boolean warnOnCycleTimeExceed) {
		this(defaultCycleTime, warnOnCycleTimeExceed, false);
	}

	/**
	 * @param defaultCycleTime Cycle time with which callback function is called
	 * @param warnOnCycleTimeExceed Display warning, if cycle time is exceeded?
	 * @param pauseOnStartup Pause Signal set at startup of this thread?
	 */
	public LoopThread(long defaultCycleTime, boolean warnOnCycleTimeExceed, boolean pauseOnStartup) {
		pauseSignal = pauseOnStartup;
		cycleTime = defaultCycleTime;
		this.warnOnCycleTimeExceed = warnOnCycleTimeExceed;
		setName(getClass().getSimpleName() + " MainLoop");
	}
	
	public void run() {
		try {
						
			stopSignal = false;
			
			// Start main loop
			mainLoop();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * The main loop
	 */
	private void mainLoop() throws Exception {

		while(!stopSignal) {
			
			if (pauseSignal) {
				waitUntilNotification();
			}
			
			// remember start time
			long startTimeMs = System.currentTimeMillis();

			mainLoopCallback();
			
			// wait 
			long waitFor = cycleTime - (System.currentTimeMillis() - startTimeMs);
			if (waitFor < 0 && warnOnCycleTimeExceed && DISPLAYWARNINGS) {
				System.err.println("warning: Couldn't keep up cycle time (" + (-waitFor) + " ms too long)");
			} else if (waitFor > 0){
				waitFor(waitFor);
			}
		}
	}
	
	public void waitFor(long waitFor) {
		waiting = true;
		try {
			Thread.sleep(waitFor);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		waiting = false;
	}
	
	public void waitUntilNotification() {
		synchronized(this) {
			waiting = true;
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			waiting = false;
		}		
	}
	

	/**
	 * callback function that is called with the specified rate
	 */
	public abstract void mainLoopCallback() throws Exception;

	/**
	 * @return Current Cycle time with which callback function is called
	 */
	public long getCycleTime() {
		return cycleTime;
	}

	/**
	 * @param cycleTime New Cycle time with which callback function is called
	 */
	public void setCycleTime(long cycleTime) {
		this.cycleTime = cycleTime;
	}
	
	/**
	 * @return Is thread currently running?
	 */
	public boolean isRunning() {
		return isAlive();
	}
	
	/**
	 * Stop Loop. Cannot be restarted.
	 */
	public void stopThread() {
		waiting = true;
		stopSignal = true;
	}
	
	/**
	 * Stop Loop. Cannot be restarted (same as StopThread)
	 */
	public void stopLoop() {
		stopThread();
	}

	
	/**
	 * Pause Thread.
	 */
	public void pauseThread() {
		pauseSignal = true;
	}
	
	/**
	 * Pause thread (same as pauseThread())
	 */
	public void pauseLoop() {
		pauseThread();
	}
	
	/**
	 * Resume Thread;
	 */
	public void continueThread() {
		pauseSignal = false;
		synchronized(this) {
			notify();
		}
	}
	
	/**
	 * Resume Thread (same as continueThread)
	 */
	public void continueLoop() {
		continueThread();
	}

	
	/**
	 * @return Is Thread currently paused?
	 */
	public boolean isPausing() {
		return pauseSignal;
	}
	
	/**
	 * @return Is the stop signal set in order to stop the thread?
	 */
	public boolean isStopSignalSet() {
		return stopSignal;
	}


	/**
	 * @return the maxWaitTime
	 */
	public int getMaxWaitTime() {
		return maxWaitTime;
	}


	/**
	 * @param maxWaitTime the maxWaitTime to set
	 */
	public void setMaxWaitTime(int maxWaitTime) {
		this.maxWaitTime = maxWaitTime;
	}


	/**
	 * @return the waiting
	 */
	public boolean isWaiting() {
		return waiting;
	}
}
