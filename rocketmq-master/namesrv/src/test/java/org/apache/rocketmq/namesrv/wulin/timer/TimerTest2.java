package org.apache.rocketmq.namesrv.wulin.timer;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.common.ThreadFactoryImpl;
public class TimerTest2 {
	private static TimerTest2 timerTest = new TimerTest2();
	
	/**
	 * 应用于多个定时器,当第一个定时器正在执行的过程中,第二个定时器触发了,会使用第二个线程执行第二个定时器,若有第三个定时器,而第一个定时器与第二个定时器都还在
	 * 执行时第三个定时器触发,则会等到第一个定时器或者第二个定时器执行关闭,释放线程后才会从线程池中获取线程执行
	 */
	 private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2,new ThreadFactoryImpl( "NSScheduledThread"));
	 
	 
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
