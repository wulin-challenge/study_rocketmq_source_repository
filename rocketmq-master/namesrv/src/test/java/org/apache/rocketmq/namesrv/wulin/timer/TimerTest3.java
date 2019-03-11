package org.apache.rocketmq.namesrv.wulin.timer;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.common.ThreadFactoryImpl;

public class TimerTest3 {
	 private static TimerTest3 timerTest = new TimerTest3();
		
		/**
	     * 安排指定的任务在指定的时间开始进行重复的固定速率执行。以近似固定的时间间隔（由指定的周期分隔）进行后续执行。 
	     * <p> 在固定速率执行中，相对于已安排的初始执行时间来安排每次执行。如果由于任何原因（如垃圾回收或其他后台活动）而延迟了某次执行，则将快速连续地出现两次或更多次执行，从而使后续执行能够赶上来。从长远来看，执行的频率将正好是指定周期的倒数（假定 Object.wait(long) 所依靠的系统时钟是准确的）。 
	     * 
	     * <p> 疑问?经测试发现: 与 Executors.newScheduledThreadPool(1) 和 Executors.newSingleThreadScheduledExecutor 效果一致,不知道他们有什么却别
	     */
	    private final Timer timer = new Timer("ServerHouseKeepingService", true);
		 
		 public static void main(String[] args) throws IOException {
			 
			 for (int i = 0; i < 5; i++) {
				 timerTest.scheduledExecutorServiceTest();
				 System.out.println(i);
			}
			
			 System.in.read();
		 }
		 
		 private void scheduledExecutorServiceTest(){
			 
			 timer.scheduleAtFixedRate(new TimerTask(){
				@Override
				public void run() {
					System.out.println("我是第一个定时器");
				}
			 }, 1000*1, 1000*2);
			 
//			 timer.scheduleAtFixedRate(new TimerTask(){
//					@Override
//					public void run() {
//						System.out.println("我是第二个定时器");
//					}
//				 }, 1000*3, 1000*3);
			 
		 }
}
