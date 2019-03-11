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
package org.apache.rocketmq.client.consumer;

import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.exception.MQClientException;

/**
 * Push consumer
 * 
 * <p> 推消费者
 */
public interface MQPushConsumer extends MQConsumer {
    /**
     * Start the consumer
     * 
     * <p> 启动消费者
     */
    void start() throws MQClientException;

    /**
     * Shutdown the consumer
     * 
     * <p> 关掉消费者
     */
    void shutdown();

    /**
     * Register the message listener
     * 
     * <p> 注册消息监听器
     */
    @Deprecated
    void registerMessageListener(MessageListener messageListener);

    void registerMessageListener(final MessageListenerConcurrently messageListener);

    void registerMessageListener(final MessageListenerOrderly messageListener);

    /**
     * Subscribe some topic
     * 
     * <p> 订阅一些主题
     *
     * @param subExpression subscription expression.it only support or operation such as "tag1 || tag2 || tag3" <br> if
     * null or * expression,meaning subscribe
     * all
     * 
     * <p> 订阅表达式。仅支持或操作，如“tag1 || tag2 || tag3” 如果为null或*表达式，则表示订阅全部
     */
    void subscribe(final String topic, final String subExpression) throws MQClientException;

    /**
     * Subscribe some topic
     * 
     * <p> 订阅一些主题
     *
     * @param fullClassName full class name,must extend org.apache.rocketmq.common.filter. MessageFilter
     * 
     * <p> 完整的类名，必须扩展org.apache.rocketmq.common.filter。的MessageFilter
     * 
     * @param filterClassSource class source code,used UTF-8 file encoding,must be responsible for your code safety
     * 
     * <p> 类源代码，使用UTF-8文件编码，必须负责您的代码安全
     */
    void subscribe(final String topic, final String fullClassName,
        final String filterClassSource) throws MQClientException;

    /**
     * Subscribe some topic with selector.
     * 
     * <p> 使用选择器订阅一些主题。
     * 
     * <p>
     * This interface also has the ability of {@link #subscribe(String, String)},
     * and, support other message selection, such as {@link org.apache.rocketmq.common.filter.ExpressionType#SQL92}.
     * </p>
     * 
     * <p> 此接口还具有subscribe（String，String）的能力，并支持其他消息选择，
     * 例如org.apache.rocketmq.common.filter.ExpressionType.SQL92。
     * 
     * <p>
     * Choose Tag: {@link MessageSelector#byTag(java.lang.String)}
     * </p>
     * 
     * <p> 选择Tag：MessageSelector.byTag（java.lang.String）
     * 
     * <p>
     * Choose SQL92: {@link MessageSelector#bySql(java.lang.String)}
     * </p>
     * 
     * <p> 选择SQL92：MessageSelector.bySql（java.lang.String）
     *
     * @param selector message selector({@link MessageSelector}), can be null.
     * 
     * <p> 消息选择器（MessageSelector），可以为null。
     */
    void subscribe(final String topic, final MessageSelector selector) throws MQClientException;

    /**
     * Unsubscribe consumption some topic
     * 
     * <p> 取消订阅消费一些主题
     *
     * @param topic message topic - 消息主题
     */
    void unsubscribe(final String topic);

    /**
     * Update the consumer thread pool size Dynamically
     * 
     * <p> 动态更新使用者线程池大小
     */
    void updateCorePoolSize(int corePoolSize);

    /**
     * Suspend the consumption
     * 
     * <p> 暂停消费
     */
    void suspend();

    /**
     * Resume the consumption
     * 
     * <p> 恢复消费
     */
    void resume();
}
