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
package org.apache.rocketmq.client;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * Base interface for MQ management
 * 
 * <p> MQ管理的基本接口
 */
public interface MQAdmin {
    /**
     * Creates an topic
     * 
     * <p> 创建一个主题
     *
     * @param key accesskey - 访问key
     * @param newTopic topic name - 主题名称
     * @param queueNum topic's queue number - 主题的队列数
     */
    void createTopic(final String key, final String newTopic, final int queueNum)
        throws MQClientException;

    /**
     * Creates an topic
     * 
     * <p> 创建一个主题
     *
     * @param key accesskey - 访问key
     * @param newTopic topic name - 主题名称
     * @param queueNum topic's queue number - 主题的队列数
     * @param topicSysFlag topic system flag - 主题系统标志
     */
    void createTopic(String key, String newTopic, int queueNum, int topicSysFlag)
        throws MQClientException;

    /**
     * Gets the message queue offset according to some time in milliseconds<br>
     * be cautious to call because of more IO overhead
     * 
     * <p> 根据时间戳（以毫秒为单位）获取消息队列偏移量
     * <p> 注意: 因为更多的IO开销请谨慎调用
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @param timestamp from when in milliseconds. - 从毫秒开始。
     * @return offset - 返回偏移量
     */
    long searchOffset(final MessageQueue mq, final long timestamp) throws MQClientException;

    /**
     * Gets the max offset
     * 
     * <p> 获取最大偏移量
     * <p> 或 查找该消息队列中最大的物理偏移量 。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return the max offset - 返回最大偏移量
     */
    long maxOffset(final MessageQueue mq) throws MQClientException;

    /**
     * Gets the minimum offset
     * 
     * <p> 获取最小偏移量
     * <p> 或 查找该消息队列中最小物理偏移量 。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return the minimum offset - 返回最小偏移量
     */
    long minOffset(final MessageQueue mq) throws MQClientException;

    /**
     * Gets the earliest stored message time
     * 
     * <p> 获取最早存储的消息时间
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return the time in microseconds - 以微秒为单位的时间
     */
    long earliestMsgStoreTime(final MessageQueue mq) throws MQClientException;

    /**
     * Query message according to message id
     * 
     * <p> 根据消息ID查询消息
     * <p> 或 根据消息偏移量查找消息 。
     *
     * @param offsetMsgId message id - 消息id
     * @return message - 返回要查找的消息,没有则返回null
     */
    MessageExt viewMessage(final String offsetMsgId) throws RemotingException, MQBrokerException,
        InterruptedException, MQClientException;

    /**
     * Query messages
     * 
     * <p> 根据条件查询消息 。
     *
     * @param topic message topic - 消息主题
     * @param key message key index word - 消息索 引 字段 。
     * @param maxNum max message number - 本次最多取出消息条数 。
     * @param begin from when  - 开始时间 。
     * @param end to when - 结束时间 。
     * @return Instance of QueryResult - QueryResult实例
     */
    QueryResult queryMessage(final String topic, final String key, final int maxNum, final long begin,
        final long end) throws MQClientException, InterruptedException;

    /**
     * @return The {@code MessageExt} of given msgId
     * 
     * <p> 根据主题与消息 ID 查找消息 。
     */
    MessageExt viewMessage(String topic,
        String msgId) throws RemotingException, MQBrokerException, InterruptedException, MQClientException;

}