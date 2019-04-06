package org.apache.rocketmq.example.simple.wulin.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class LaterExecute {
	
	 /**
     * 定时器。用于延迟执行
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "laterExecuteThread");
            }
        });
    
    public static void main(String[] args) {
    	LaterExecute faterExecute = new LaterExecute();
		for (int i = 0; i < 5; i++) {
			faterExecute.executeRequestLater(""+i, i*1000);
		}
		System.out.println("主线程执行完成");
	}
    
    
    /**
     * 执行延迟拉取消息请求
     *
     * @param laterQequest 延迟执行请求
     * @param timeDelay 延迟时间
     */
    public void executeRequestLater(final String laterQequest, final long timeDelay) {
        this.scheduledExecutorService.schedule(new Runnable() {

            @Override
            public void run() {
            	LaterExecute.this.laterPrintRequest(laterQequest,timeDelay);
            }
        }, timeDelay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 延迟执行的方法
     * @param laterQequest
     * @param timeDelay
     */
    private void laterPrintRequest(String laterQequest,long timeDelay){
    	System.out.println("我是延迟打印的请求: "+laterQequest+",延迟时间: "+timeDelay+" ms");
    }
}
