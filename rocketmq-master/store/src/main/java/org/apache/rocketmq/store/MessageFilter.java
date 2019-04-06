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
package org.apache.rocketmq.store;

import java.nio.ByteBuffer;
import java.util.Map;

public interface MessageFilter {
    /**
     * match by tags code or filter bit map which is calculated when message received
     * and stored in consume queue ext.
     * 
     * <p> 通过标签代码匹配或过滤位图，该位图是在消息接收并存储在消耗队列ext中时计算的。
     * <p> 详解: 根据 ConsumeQueue 判断消息是否匹配 。
     *
     * @param tagsCode tagsCode 消息 tag 的 hashcode 。
     * @param cqExtUnit consumequeue 条目扩展属性 。
     * 
     * <p> extend unit of consume queue
     * 
     * <p> 扩展消耗队列单元
     */
    boolean isMatchedByConsumeQueue(final Long tagsCode,
        final ConsumeQueueExt.CqExtUnit cqExtUnit);

    /**
     * 根据存储在 commitlog 文件中的内容判断消息是否匹配 。ByteBuffer msgBuffer ： 消息内容，如果为空， 该方法返回 true 。
     * Map<String,String> properties ： 消息属性，主要用于表达式 SQL92 过滤模式 。
     * 
     * <p> match by message content which are stored in commit log.
     * <br>{@code msgBuffer} and {@code properties} are not all null.If invoked in store,
     * {@code properties} is null;If invoked in {@code PullRequestHoldService}, {@code msgBuffer} is null.
     * 
     * <p> 匹配存储在提交日志中的消息内容。msgBuffer和属性都不是null。如果在store中调用，则属性为null;
     * 如果在PullRequestHoldService中调用，则msgBuffer为null。
     *
     * @param msgBuffer message buffer in commit log, may be null if not invoked in store.
     * 
     * <p> 提交日志中的消息缓冲区，如果未在存储中调用，则可以为null。
     * 
     * @param properties message properties, should decode from buffer if null by yourself.
     * 
     * <p> 消息属性，如果自己为null，则应从缓冲区解码。
     */
    boolean isMatchedByCommitLog(final ByteBuffer msgBuffer,
        final Map<String, String> properties);
}
