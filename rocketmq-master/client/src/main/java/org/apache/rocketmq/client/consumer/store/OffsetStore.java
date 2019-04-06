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
package org.apache.rocketmq.client.consumer.store;

import java.util.Map;
import java.util.Set;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * Offset store interface
 * 
 * <p> 偏移量存储接口
 * <p> 客户端消费消息进度的接口
 */
public interface OffsetStore {
    /**
     * 从消息进度存储文件加载消息进度到内存 。
     */
    void load() throws MQClientException;

    /**
     *  Update the offset,store it in memory
     * 
     * <p> 更新偏移量，将其存储在内存中
     * 
     * <p> 更新内存中的消息消费进度 。
     * 
     * @param mq 消息消费队列 。
     * @param offset 消息消费偏移量 。
     * @param increaseOnly true 表示 offset 必须大于 内存 中 当前的消费偏移量才更新 。
     */
    void updateOffset(final MessageQueue mq, final long offset, final boolean increaseOnly);

    /**
     * Get offset from local storage - 从本地存储中获取偏移量
     *
     * <p> 读取消息消费进度 。
     * 
     * @param mq 消息消费队列 。
     * @param type 读取方式,可选值READ_FROM_MEMORY:从内存中；READFROMSTORE:从磁盘中；MEMORYFIRSTTHENSTORE:先从内存读取,再从磁盘.
     * @return The fetched offset - 被抓取的偏移量
     */
    long readOffset(final MessageQueue mq, final ReadOffsetType type);

    /**
     * Persist all offsets,may be in local storage or remote name server
     * 
     * <p> 保留所有偏移量，可以在本地存储或远程名称服务器中
     * 
     * <p> 持久化指定消息队列进度到磁盘。
     * 
     * @param mqs 消息队列集合 。
     */
    void persistAll(final Set<MessageQueue> mqs);

    /**
     * Persist the offset,may be in local storage or remote name server
     * 
     * <p> 保留偏移量，可以在本地存储或远程名称服务器中
     * 
     * <p> 持久化指定消息队列进度到磁盘。
     * 
     * @param mq 消息队列
     */
    void persist(final MessageQueue mq);

    /**
     * Remove offset - 删除偏移量
     * 
     * <p> 将消息队列的消息消费进度从内存中移除 。
     */
    void removeOffset(MessageQueue mq);

    /**
     * 
     * <p> 克隆该主题下所有消息队列的消息消费进度 。
     * 
     * @return The cloned offset table of given topic
     * 
     * <p> 给定主题的克隆偏移表
     */
    Map<MessageQueue, Long> cloneOffsetTable(String topic);

    /**
     * 更新存储在 Brokder 端的消息消费进度，使用集群模式 。
     * @param mq
     * @param offset
     * @param isOneway 是否为单向请求
     */
    void updateConsumeOffsetToBroker(MessageQueue mq, long offset, boolean isOneway) throws RemotingException,
        MQBrokerException, InterruptedException, MQClientException;
}
