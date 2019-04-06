/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.impl.consumer;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.utils.ThreadUtils;
import org.slf4j.Logger;

/**
 * 消息拉取服务线程， run 方法是其核心逻辑 。
 *
 */
public class PullMessageService extends ServiceThread {
    private final Logger log = ClientLogger.getLog();
    
    /**
     * 拉取消息请求队列
     */
    private final LinkedBlockingQueue<PullRequest> pullRequestQueue = new LinkedBlockingQueue<PullRequest>();
    
    /**
     * MQClient对象
     */
    private final MQClientInstance mQClientFactory;
    
    /**
     * 定时器。用于延迟提交拉取请求
     */
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PullMessageServiceScheduledThread");
            }
        });

    public PullMessageService(MQClientInstance mQClientFactory) {
        this.mQClientFactory = mQClientFactory;
    }

    /**
     * 执行延迟拉取消息请求
     *
     * @param pullRequest 拉取消息请求
     * @param timeDelay 延迟时长
     */
    public void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        this.scheduledExecutorService.schedule(new Runnable() {

            @Override
            public void run() {
                PullMessageService.this.executePullRequestImmediately(pullRequest);
            }
        }, timeDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行立即拉取消息请求
     *
     * @param pullRequest 拉取消息请求
     */
    public void executePullRequestImmediately(final PullRequest pullRequest) {
        try {
            this.pullRequestQueue.put(pullRequest);
        } catch (InterruptedException e) {
            log.error("executePullRequestImmediately pullRequestQueue.put", e);
        }
    }

    /**
     * 执行延迟任务
     *
     * @param r 任务
     * @param timeDelay 延迟时长
     */
    public void executeTaskLater(final Runnable r, final long timeDelay) {
        this.scheduledExecutorService.schedule(r, timeDelay, TimeUnit.MILLISECONDS);
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    /**
     * 拉取消息
     *
     * @param pullRequest 拉取消息请求
     */
    private void pullMessage(final PullRequest pullRequest) {
    	
    	/*
    	 * 根据消费组名从MQClientInstance中获取消费者内部实现类MQConsumerInner,
    	 * 令人意外的是这里将consumer强制转换为DefaultMQPushConsumerImpl,也就是PullMessageService,该线程只为PUSH模式服务,
    	 * 那拉模式如何拉取消息呢？其实细想也不难理解,PULL模式,RocketMQ只需要提供拉取消息API即可,具体由应用程序显示调用拉取API.
    	 */
        final MQConsumerInner consumer = this.mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
        if (consumer != null) {
            DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
            impl.pullMessage(pullRequest);
        } else {
            log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
        }
    }

    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            try {
            	/*
            	 * 从pullRequestQueue中获取一个PullRequest消息拉取任务,如果pullRequestQueue为空,则线程将阻塞,直到有拉取任务被放入.
            	 */
                PullRequest pullRequest = this.pullRequestQueue.take();
                if (pullRequest != null) {
                	//调用pullMessage方法进行消息拉取.
                    this.pullMessage(pullRequest);
                }
            } catch (InterruptedException e) {
            } catch (Exception e) {
                log.error("Pull Message Service Run Method exception", e);
            }
        }

        log.info(this.getServiceName() + " service end");
    }

    @Override
    public void shutdown(boolean interrupt) {
        super.shutdown(interrupt);
        ThreadUtils.shutdownGracefully(this.scheduledExecutorService, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServiceName() {
        return PullMessageService.class.getSimpleName();
    }

}
