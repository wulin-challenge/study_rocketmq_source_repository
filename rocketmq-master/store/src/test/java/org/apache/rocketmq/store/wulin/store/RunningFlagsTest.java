package org.apache.rocketmq.store.wulin.store;

import org.apache.rocketmq.store.RunningFlags;
import org.junit.Test;

public class RunningFlagsTest {

	@Test
	public void runningFlagsTest(){
		RunningFlags runningFlags = new RunningFlags();
		boolean andMakeReadable2 = runningFlags.getAndMakeNotReadable();
		boolean andMakeReadable = runningFlags.getAndMakeReadable();
		
		System.out.println(andMakeReadable+","+andMakeReadable2);
	}
	
	@Test
	public void szTest(){
		int a = 1;
		int b = 1;
		
		int c = ~a;
		System.out.println(c);
	}
}
