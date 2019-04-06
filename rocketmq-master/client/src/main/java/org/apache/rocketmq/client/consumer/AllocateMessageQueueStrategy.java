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

import java.util.List;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Strategy Algorithm for message allocating between consumers
 * 
 * <p> 消费者之间消息分配的策略算法
 */
public interface AllocateMessageQueueStrategy {

    /**
     * Allocating by consumer id
     * 
     * <p> 按消费者ID分配
     * <p> 详解: 消息负载算法如果没有特殊的要求,尽量使用AllocateMessageQueueAveragely、AllocateMessageQueueAveragelyByCircle,
     * 因为分配算法比较直观.消息队列分配遵循一个消费者可以分配多个消息队列,但同一个消息队列只会分配给一个消费者,故如果消费者个数大于消息队列数量,
     * 则有些消费者无法消费消息.
     *
     * @param consumerGroup current consumer group - 目前的消费组
     * @param currentCID current consumer id - 当前的消费者ID
     * @param mqAll message queue set in current topic - 当前主题中设置的所有消息队列
     * @param cidAll consumer set in current consumer group - 当前消费者组中的所有消费者
     * @return The allocate result of given strategy - 给定策略的分配结果
     */
    List<MessageQueue> allocate(
        final String consumerGroup,
        final String currentCID,
        final List<MessageQueue> mqAll,
        final List<String> cidAll
    );

    /**
     * Algorithm name - 算法名称
     *
     * @return The strategy name - 算法名称
     */
    String getName();
}
