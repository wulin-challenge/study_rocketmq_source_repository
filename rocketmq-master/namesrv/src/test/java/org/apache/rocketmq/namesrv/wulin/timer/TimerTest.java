package org.apache.rocketmq.namesrv.wulin.timer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.common.ThreadFactoryImpl;

public class TimerTest {
	private static TimerTest timerTest = new TimerTest();
	
	/**
	 * 应用于多个定时器,但多个定时器直接共用一个线程,因此多个定时器不可能同时执行
	 */
	 private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryImpl( "NSScheduledThread"));
	 
	 // 经测试发现, newSingleThreadScheduledExecutor 与 newScheduledThreadPool(1) 等效
//	 private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1,new ThreadFactoryImpl( "NSScheduledThread"));
	 
	 public static void main(String[] args) {
		 
		 timerTest.scheduledExecutorServiceTest();
	 }
	 
	 private void scheduledExecutorServiceTest(){
		 
		 scheduledExecutorService.scheduleAtFixedRate(new Runnable(){
				@Override
				public void run() {
					System.out.println("我是第一个定时器");
				}
			 }, 1, 2, TimeUnit.SECONDS);
		 
		 scheduledExecutorService.scheduleAtFixedRate(new Runnable(){
				@Override
				public void run() {
					System.out.println("我是第二个定时器");
				}
		 }, 3, 3, TimeUnit.SECONDS);
	 }
}
