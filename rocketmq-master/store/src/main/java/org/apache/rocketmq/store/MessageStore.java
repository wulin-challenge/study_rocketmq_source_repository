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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageExtBatch;

/**
 * This class defines contracting interfaces to implement, allowing third-party vendor to use customized message store.
 * 
 * <p> 此类定义要实现的签约接口，允许第三方供应商使用自定义消息存储。
 */
public interface MessageStore {

    /**
     * Load previously stored messages.
     * 
     * <p> 加载以前存储的消息。
     *
     * @return true if success; false otherwise. - 如果成功，则为真 否则是假的。
     */
    boolean load();

    /**
     * Launch this message store.
     * 
     * <p> 启动此消息存储。
     *
     * @throws Exception if there is any error. - 如果有任何错误。
     */
    void start() throws Exception;

    /**
     * Shutdown this message store.
     * 
     * <p> 关闭此邮件存储。
     */
    void shutdown();

    /**
     * Destroy this message store. Generally, all persistent files should be removed after invocation.
     * 
     * <p> 销毁此消息存储库。 通常，应在调用后删除所有持久性文件。
     */
    void destroy();

    /**
     * Store a message into store.
     * 
     * <p> 将消息存储到store中。
     *
     * @param msg Message instance to store - 要存储的消息实例
     * @return result of store operation. - 存储操作的结果。
     */
    PutMessageResult putMessage(final MessageExtBrokerInner msg);

    /**
     * Store a batch of messages.
     * 
     * <p> 存储一批消息
     *
     * @param messageExtBatch Message batch. - 批量消息
     * @return result of storing batch messages. - 存储批量消息的结果。
     */
    PutMessageResult putMessages(final MessageExtBatch messageExtBatch);

    /**
     * Query at most <code>maxMsgNums</code> messages belonging to <code>topic</code> at <code>queueId</code> starting
     * from given <code>offset</code>. Resulting messages will further be screened using provided message filter.
     * 
     * <p> 从给定的偏移量开始，最多查询属于queueId主题的maxMsgNums消息。 将使用提供的消息过滤器进一步筛选生成的消息。
     *
     * @param group Consumer group that launches this query.
     * 
     * <p> 启动此查询的使用者组。
     * 
     * @param topic Topic to query. - 要查询的主题。
     * 
     * @param queueId Queue ID to query. - 要查询的队列ID。
     * 
     * @param offset Logical offset to start from. - 从逻辑偏移开始。
     * 
     * @param maxMsgNums Maximum count of messages to query. - 要查询的消息的最大数量。
     * 
     * @param messageFilter Message filter used to screen desired messages. - 消息过滤器用于筛选所需的消息。
     * 
     * @return Matched messages. - 匹配的消息
     */
    GetMessageResult getMessage(final String group, final String topic, final int queueId,
        final long offset, final int maxMsgNums, final MessageFilter messageFilter);

    /**
     * Get maximum offset of the topic queue. - 获取主题队列的最大偏移量。
     *
     * @param topic Topic name. - 主题名称
     * @param queueId Queue ID. - 队列id
     * @return Maximum offset at present. - 目前最大偏移量。
     */
    long getMaxOffsetInQueue(final String topic, final int queueId);

    /**
     * Get the minimum offset of the topic queue.
     * 
     * <p> 获取主题队列的最小偏移量。
     *
     * @param topic Topic name. - 主题名称
     * @param queueId Queue ID. - 队列id
     * @return Minimum offset at present. - 目前最小偏移量。
     */
    long getMinOffsetInQueue(final String topic, final int queueId);

    /**
     * Get the offset of the message in the commit log, which is also known as physical offset.
     * 
     * <p> 获取提交日志中消息的偏移量，也称为物理偏移量。
     *
     * @param topic Topic of the message to lookup.
     * 
     * <p> 要查找的消息主题。
     * 
     * @param queueId Queue ID. - 队列id
     * 
     * @param consumeQueueOffset offset of consume queue.
     * 
     * <p> 消耗队列的偏移量。
     * 
     * @return physical offset. - 物理偏移量.
     */
    long getCommitLogOffsetInQueue(final String topic, final int queueId, final long consumeQueueOffset);

    /**
     * Look up the physical offset of the message whose store timestamp is as specified.
     * 
     * <p> 查找存储时间戳为指定的消息的物理偏移量。
     *
     * @param topic Topic of the message. - 消息主题。
     * @param queueId Queue ID. - 队列id
     * @param timestamp Timestamp to look up. - 要查找的时间戳。
     * @return physical offset which matches. - 物理偏移匹配。
     */
    long getOffsetInQueueByTime(final String topic, final int queueId, final long timestamp);

    /**
     * Look up the message by given commit log offset.
     * 
     * <p> 通过给定的提交日志偏移量查找消息。
     *
     * @param commitLogOffset physical offset. - 物理偏移量。
     * @return Message whose physical offset is as specified.
     * 
     * <p> 物理偏移量与指定的消息。
     */
    MessageExt lookMessageByOffset(final long commitLogOffset);

    /**
     * Get one message from the specified commit log offset.
     * 
     * <p> 从指定的提交日志偏移量中获取一条消息。
     *
     * @param commitLogOffset commit log offset. - 提交日志偏移量。
     * @return wrapped result of the message. - 包装结果的消息。
     */
    SelectMappedBufferResult selectOneMessageByOffset(final long commitLogOffset);

    /**
     * Get one message from the specified commit log offset.
     * 
     * <p> 从指定的提交日志偏移量中获取一条消息。
     *
     * @param commitLogOffset commit log offset. - 提交日志偏移量。
     * @param msgSize message size. - 消息大小
     * @return wrapped result of the message. - 包装结果的消息。
     */
    SelectMappedBufferResult selectOneMessageByOffset(final long commitLogOffset, final int msgSize);

    /**
     * Get the running information of this store.
     * 
     * <p> 获取此存储的运行信息。
     *
     * @return message store running info. - 消息存储运行信息。
     */
    String getRunningDataInfo();

    /**
     * Message store runtime information, which should generally contains various statistical information.
     * 
     * <p> 消息存储库运行时信息，通常应包含各种统计信息。
     *
     * @return runtime information of the message store in format of key-value pairs.
     * 
     * <p> 密钥值对格式的消息存储库的运行时信息。
     */
    HashMap<String, String> getRuntimeInfo();

    /**
     * Get the maximum commit log offset.
     * 
     * <p> 获取最大提交日志偏移量。
     *
     * @return maximum commit log offset. - 最大提交日志偏移量。
     */
    long getMaxPhyOffset();

    /**
     * 最小物理偏移量
     * <p> et the minimum commit log offset. - 获取最小提交日志偏移量。
     *
     * @return minimum commit log offset. - 最小提交日志偏移量。
     */
    long getMinPhyOffset();

    /**
     * Get the store time of the earliest message in the given queue.
     * 
     * <p> 获取给定队列中最早消息的存储时间。
     *
     * @param topic Topic of the messages to query. - 要查询的消息主题。
     * @param queueId Queue ID to find. - 要查找的队列ID。
     * @return store time of the earliest message. - 存储最早消息的时间。
     */
    long getEarliestMessageTime(final String topic, final int queueId);

    /**
     * Get the store time of the earliest message in this store.
     * 
     * <p> 获取此存储中最早消息的存储时间。
     *
     * @return timestamp of the earliest message in this store.
     * 
     * <p> 此存储中最早消息的时间戳。
     */
    long getEarliestMessageTime();

    /**
     * Get the store time of the message specified.
     * 
     * <p> 获取指定消息的存储时间。
     *
     * @param topic message topic. - 消息主题
     * @param queueId queue ID. - 队列id
     * @param consumeQueueOffset consume queue offset. - 消费队列偏移量。
     * @return store timestamp of the message. - 存储消息的时间戳。
     */
    long getMessageStoreTimeStamp(final String topic, final int queueId, final long consumeQueueOffset);

    /**
     * Get the total number of the messages in the specified queue.
     * 
     * <p> 获取指定队列中的消息总数。
     *
     * @param topic Topic - 主题
     * @param queueId Queue ID. - 队里id
     * @return total number. - 总数。
     */
    long getMessageTotalInQueue(final String topic, final int queueId);

    /**
     * Get the raw commit log data starting from the given offset, which should used for replication purpose.
     * 
     * <p> 从给定偏移量开始获取原始提交日志数据，该偏移量应用于复制目的。
     *
     * @param offset starting offset. - 起始偏移量.
     * @return commit log data. - 提交日志数据。
     */
    SelectMappedBufferResult getCommitLogData(final long offset);

    /**
     * Append data to commit log.
     * 
     * <p> 将数据附加到提交日志。
     *
     * @param startOffset starting offset. - 起始偏移量.
     * @param data data to append. - 要追加的数据。
     * @return true if success; false otherwise. - 如果成功，则为真 否则是假的。
     */
    boolean appendToCommitLog(final long startOffset, final byte[] data);

    /**
     * Execute file deletion manually.
     * 
     * <p> 手动执行文件删除。
     */
    void executeDeleteFilesManually();

    /**
     * Query messages by given key.
     * 
     * <p> 按给定key查询消息。
     *
     * @param topic topic of the message. - 消息的主题。
     * @param key message key. - 消息key
     * @param maxNum maximum number of the messages possible. - 可能的最大消息数。
     * @param begin begin timestamp. - 开始时间戳。
     * @param end end timestamp. - 结束时间戳。
     */
    QueryMessageResult queryMessage(final String topic, final String key, final int maxNum, final long begin,
        final long end);

    /**
     * Update HA master address. - 更新HA主站地址。
     *
     * @param newAddr new address. - 新地址
     */
    void updateHaMasterAddress(final String newAddr);

    /**
     * Return how much the slave falls behind. - 返回slave落后的程度。
     *
     * @return number of bytes that slave falls behind. - slave落后的字节数。
     */
    long slaveFallBehindMuch();

    /**
     * Return the current timestamp of the store.
     * 
     * <p> 返回存储的当前时间戳。
     *
     * @return current time in milliseconds since 1970-01-01. - 自1970-01-01以来的当前时间（以毫秒为单位）。
     */
    long now();

    /**
     * Clean unused topics.
     * 
     * <p> 清理未使用的主题
     *
     * @param topics all valid topics.
     * 
     * <p> 主题所有有效主题。
     * 
     * @return number of the topics deleted.
     * 
     * <p> 删除的主题数量。
     */
    int cleanUnusedTopic(final Set<String> topics);

    /**
     * Clean expired consume queues. - 清除过期的消费队列。
     */
    void cleanExpiredConsumerQueue();

    /**
     * Check if the given message has been swapped out of the memory.
     * 
     * <p> 检查给定的消息是否已从内存中换出。
     *
     * @param topic topic. - 主题
     * @param queueId queue ID. - 队列id
     * @param consumeOffset consume queue offset. - 消费队列偏移量
     * @return true if the message is no longer in memory; false otherwise.
     * 
     * <p> 如果消息不再在内存中，则为true; 否则是假的。
     * 
     */
    boolean checkInDiskByConsumeOffset(final String topic, final int queueId, long consumeOffset);

    /**
     * Get number of the bytes that have been stored in commit log and not yet dispatched to consume queue.
     * 
     * <p> 获取已存储在提交日志中但尚未调度到使用队列的字节数。
     *
     * @return number of the bytes to dispatch. - 要调度的字节数。
     */
    long dispatchBehindBytes();

    /**
     * Flush the message store to persist all data.
     * 
     * <p> 刷新消息存储库以保留所有数据。
     *
     * @return maximum offset flushed to persistent storage device.
     * 
     * <p> 刷新到持久存储设备的最大偏移量。
     */
    long flush();

    /**
     * Reset written offset. - 重置写偏移量。
     *
     * @param phyOffset new offset. - 新的偏移量
     * @return true if success; false otherwise. - 如果成功，则为真 否则是假的。
     */
    boolean resetWriteOffset(long phyOffset);

    /**
     * Get confirm offset. - 获取确认偏移量
     *
     * @return confirm offset. - 确认偏移量
     */
    long getConfirmOffset();

    /**
     * Set confirm offset. - 设置确认偏移量。
     *
     * @param phyOffset confirm offset to set. - 确认要设置的偏移量。
     */
    void setConfirmOffset(long phyOffset);

    /**
     * Check if the operation system page cache is busy or not.
     * 
     * <p> 检查操作系统页面缓存是否繁忙。
     *
     * @return true if the OS page cache is busy; false otherwise.
     * 
     * <p> 如果操作系统页面缓存繁忙，则为true; 否则是假的。
     */
    boolean isOSPageCacheBusy();

    /**
     * Get lock time in milliseconds of the store by far.
     * 
     * <p> 到目前为止，以存储的毫秒为单位获取锁定时间。
     *
     * @return lock time in milliseconds. - 锁定时间，以毫秒为单位。
     */
    long lockTimeMills();

    /**
     * Check if the transient store pool is deficient.
     * 
     * <p> 检查瞬态存储池是否存在缺陷。
     *
     * @return true if the transient store pool is running out; false otherwise.
     * 
     * <p> 如果临时存储池已用完，则为true;否则为false。 否则是假的。
     */
    boolean isTransientStorePoolDeficient();

    /**
     * Get the dispatcher list.
     * 
     * <p> 获取调度程序列表。
     *
     * @return list of the dispatcher.
     * 
     * <p> 调度员名单。
     */
    LinkedList<CommitLogDispatcher> getDispatcherList();

    /**
     * Get consume queue of the topic/queue.
     * 
     * <p> 获取主题/队列的消耗队列。
     *
     * @param topic Topic. - 主题
     * @param queueId Queue ID. - 队列Id
     * @return Consume queue. - 消费队列
     */
    ConsumeQueue getConsumeQueue(String topic, int queueId);
}
