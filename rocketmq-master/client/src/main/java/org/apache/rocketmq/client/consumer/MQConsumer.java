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

import java.util.Set;
import org.apache.rocketmq.client.MQAdmin;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * Message queue consumer interface
 * 
 * <p> 消息队列消费者接口
 */
public interface MQConsumer extends MQAdmin {
    /**
     * If consuming failure,message will be send back to the brokers,and delay consuming some time
     * 
     * <p> 如果消耗失败，消息将被发送回brokers，并延迟消耗一些时间
     */
    @Deprecated
    void sendMessageBack(final MessageExt msg, final int delayLevel) throws RemotingException,
        MQBrokerException, InterruptedException, MQClientException;

    /**
     * If consuming failure,message will be send back to the broker,and delay consuming some time
     * 
     * <p> 如果消耗失败，消息将被发送回broker，并延迟消耗一些时间
     * 
     * @param msg 消息
     * @param delayLevel 消息延迟级别 。
     * @param brokerName 消息服务器名称 。
     */
    void sendMessageBack(final MessageExt msg, final int delayLevel, final String brokerName)
        throws RemotingException, MQBrokerException, InterruptedException, MQClientException;

    /**
     * Fetch message queues from consumer cache according to the topic
     * 
     * <p> 根据主题从消费者缓存中获取消息队列 或者 获取消费者对主题 topic 分配了哪些消息队列 。
     *
     * @param topic message topic - 消息主题
     * @return queue set - 消息队列集合
     */
    Set<MessageQueue> fetchSubscribeMessageQueues(final String topic) throws MQClientException;
}
