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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.consumer.store.OffsetStore;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * In most scenarios, this is the mostly recommended class to consume messages.
 * </p>
 *
 * 在大多数情况下，这是消耗消息的最常推荐的类。</p>
 * 
 * Technically speaking, this push client is virtually a wrapper of the underlying pull service. Specifically, on
 * arrival of messages pulled from brokers, it roughly invokes the registered callback handler to feed the messages.
 * </p>
 * 
 * 从技术上讲，这个推送客户端实际上是底层拉取服务的包装。 具体来说，在从代理中提取的消息到达时，它会粗略地调用已注册的回调处理程序来提供消息。</p>
 *
 * See quickstart/Consumer in the example module for a typical usage.
 * </p>
 *
 * <p> 有关典型用法，请参阅示例模块中的quickstart / Consumer。
 * 
 * <p>
 * <strong>Thread Safety:</strong> After initialization, the instance can be regarded as thread-safe.
 * </p>
 * 
 * <p> 线程安全：初始化后，实例可视为线程安全。
 */
public class DefaultMQPushConsumer extends ClientConfig implements MQPushConsumer {

    /**
     * Internal implementation. Most of the functions herein are delegated to it.
     * 
     * <p> 内部实施。 此处的大多数功能都委托给它。
     */
    protected final transient DefaultMQPushConsumerImpl defaultMQPushConsumerImpl;

    /**
     * Consumers of the same role is required to have exactly same subscriptions and consumerGroup to correctly achieve
     * load balance. It's required and needs to be globally unique.
     * </p>
     * 
     * <p> 具有相同角色的消费者需要具有完全相同的订阅和consumerGroup才能正确实现负载平衡。 这是必需的，需要全球独一无二。
     *
     * See <a href="http://rocketmq.apache.org/docs/core-concept/">here</a> for further discussion.
     * 
     * <p> 请参阅此处以进一步讨论
     */
    private String consumerGroup;

    /**
     * Message model defines the way how messages are delivered to each consumer clients.
     * </p>
     * 
     * <p> 消息模型定义了如何将消息传递给每个使用者客户端的方式。
     *
     * RocketMQ supports two message models: clustering and broadcasting. If clustering is set, consumer clients with
     * the same {@link #consumerGroup} would only consume shards of the messages subscribed, which achieves load
     * balances; Conversely, if the broadcasting is set, each consumer client will consume all subscribed messages
     * separately.
     * </p>
     * 
     * <p> RocketMQ支持两种消息模型：聚类和广播。 如果设置了群集，则具有相同consumerGroup的使用者客户端将仅消耗所订阅消息的分片，从而实现负载平衡; 相反，如果设置了广播，则每个消费者客户端将单独消费所有订阅的消息。
     *
     * This field defaults to clustering.
     * 
     * <p> 此字段默认为群集。
     */
    private MessageModel messageModel = MessageModel.CLUSTERING;

    /**
     * Consuming point on consumer booting.
     * </p>
     * 
     * <p> 消费者引导消费点。
     *
     * <p> There are three consuming points:
     * 
     * <p> 有三个消费点：
     * 
     * <ul>
     * <li>
     * <code>CONSUME_FROM_LAST_OFFSET</code>: consumer clients pick up where it stopped previously.
     * If it were a newly booting up consumer client, according aging of the consumer group, there are two
     * cases:
     * 
     * <li> CONSUME_FROM_LAST_OFFSET：消费者客户端在之前停止的地方获取。 如果它是一个新启动的消费者客户端，根据消费者群体的老化，有两种情况：
     * 
     * <ol>
     * <li>
     * if the consumer group is created so recently that the earliest message being subscribed has yet
     * expired, which means the consumer group represents a lately launched business, consuming will
     * start from the very beginning;
     * </li>
     * 
     * <li>如果消费者群体最近被创建以使订阅的最早消息尚未到期，这意味着消费者群体最近表示 推出业务，消费将从一开始就开始; </li>
     * 
     * <li>
     * if the earliest message being subscribed has expired, consuming will start from the latest
     * messages, meaning messages born prior to the booting timestamp would be ignored.
     * </li>
     * 
     * <li> 如果订阅的最早消息已过期，则将从最新消息开始消费，这意味着将忽略在引导时间戳之前生成的消息。</li>
     * 
     * </ol>
     * </li>
     * <li>
     * <code>CONSUME_FROM_FIRST_OFFSET</code>: Consumer client will start from earliest messages available.
     * </li>
     * 
     * <li> CONSUME_FROM_FIRST_OFFSET：消费者客户端将从最早的可用消息开始。
     * 
     * <li>
     * <code>CONSUME_FROM_TIMESTAMP</code>: Consumer client will start from specified timestamp, which means
     * messages born prior to {@link #consumeTimestamp} will be ignored
     * </li>
     * 
     * <li> CONSUME_FROM_TIMESTAMP：消费者客户端将从指定的时间戳开始，这意味着将忽略在consumeTimestamp之前生成的消息
     * 
     * </ul>
     */
    private ConsumeFromWhere consumeFromWhere = ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET;

    /**
     * Backtracking consumption time with second precision. Time format is
     * 20131223171201
     * 
     * <p> 以第二精度回溯消耗时间。 时间格式为20131223171201<br>
     * 
     * <p> Implying Seventeen twelve and 01 seconds on December 23, 2013 year
     * 
     * <p> 暗示2013年12月23日的十七二十一和十一秒
     * 
     * <p> Default backtracking consumption time Half an hour ago.
     * 
     * <p> 默认回溯消耗时间半小时前。
     */
    private String consumeTimestamp = UtilAll.timeMillisToHumanString3(System.currentTimeMillis() - (1000 * 60 * 30));

    /**
     * Queue allocation algorithm specifying how message queues are allocated to each consumer clients.
     * 
     * <p> 消费者之间消息分配的策略算法
     */
    private AllocateMessageQueueStrategy allocateMessageQueueStrategy;

    /**
     * Subscription relationship
     * 
     * <p> 订阅关系
     */
    private Map<String /* topic */, String /* sub expression */> subscription = new HashMap<String, String>();

    /**
     * Message listener
     * 
     * <p> 消息监听器
     */
    private MessageListener messageListener;

    /**
     * Offset Storage
     * 
     * <p> 偏移量存储
     */
    private OffsetStore offsetStore;

    /**
     * Minimum consumer thread number
     * 
     * <p> 最小消费者线程数
     */
    private int consumeThreadMin = 20;

    /**
     * Max consumer thread number
     * 
     * <p> 最大消费者线程数
     */
    private int consumeThreadMax = 64;

    /**
     * Threshold for dynamic adjustment of the number of thread pool
     * 
     * <p> 用于动态调整线程池数量的阈值
     */
    private long adjustThreadPoolNumsThreshold = 100000;

    /**
     * Concurrently max span offset.it has no effect on sequential consumption
     * 
     * <p> 同时max span offset.it对顺序消耗没有影响
     */
    private int consumeConcurrentlyMaxSpan = 2000;

    /**
     * Flow control threshold on queue level, each message queue will cache at most 1000 messages by default,
     * Consider the {@code pullBatchSize}, the instantaneous value may exceed the limit
     * 
     * <p> 队列级别的流量控制阈值，默认情况下每个消息队列最多会缓存1000条消息，考虑到pullBatchSize，瞬时值可能超过限制
     */
    private int pullThresholdForQueue = 1000;

    /**
     * Limit the cached message size on queue level, each message queue will cache at most 100 MiB messages by default,
     * Consider the {@code pullBatchSize}, the instantaneous value may exceed the limit
     *<p>
     *
     * <p> 限制队列级别的缓存消息大小，默认情况下每个消息队列最多会缓存100条MiB消息，考虑pullBatchSize，瞬时值可能超过限制
     *
     * <p> The size of a message only measured by message body, so it's not accurate
     * 
     * <p> 邮件大小仅由邮件正文测量，因此不准确
     */
    private int pullThresholdSizeForQueue = 100;

    /**
     * Flow control threshold on topic level, default value is -1(Unlimited)
     * 
     * <p> 主题级别的流量控制阈值，默认值为-1（无限制）
     * 
     * <p> The value of {@code pullThresholdForQueue} will be overwrote and calculated based on
     * {@code pullThresholdForTopic} if it is't unlimited
     * 
     * <p> 如果不是无限制的话，pullThresholdForQueue的值将被覆盖并基于pullThresholdForTopic计算
     * 
     * <p>
     * For example, if the value of pullThresholdForTopic is 1000 and 10 message queues are assigned to this consumer,
     * then pullThresholdForQueue will be set to 100
     * 
     * <p> 例如，如果pullThresholdForTopic的值为1000并且为此使用者分配了10个消息队列，则pullThresholdForQueue将设置为100
     * 
     */
    private int pullThresholdForTopic = -1;

    /**
     * Limit the cached message size on topic level, default value is -1 MiB(Unlimited)
     * 
     * <p> 限制主题级别的缓存邮件大小，默认值为-1 MiB（无限制）
     * 
     * <p>
     * The value of {@code pullThresholdSizeForQueue} will be overwrote and calculated based on
     * {@code pullThresholdSizeForTopic} if it is't unlimited
     * 
     * <p> 如果不是无限制的话，pullThresholdSizeForQueue的值将被覆盖并基于pullThresholdSizeForTopic计算
     * 
     * <p>
     * For example, if the value of pullThresholdSizeForTopic is 1000 MiB and 10 message queues are
     * assigned to this consumer, then pullThresholdSizeForQueue will be set to 100 MiB
     * 
     * <p> 例如，如果pullThresholdSizeForTopic的值为1000 MiB并且为此使用者分配了10个消息队列，
     * 则pullThresholdSizeForQueue将设置为100 MiB
     * 
     */
    private int pullThresholdSizeForTopic = -1;

    /**
     * Message pull Interval
     * 
     * <p> 消息拉间隔
     */
    private long pullInterval = 0;

    /**
     * Batch consumption size
     * 
     * <p> 批量消耗大小
     */
    private int consumeMessageBatchMaxSize = 1;

    /**
     * Batch pull size
     * 
     * <p> 批量拉大小
     */
    private int pullBatchSize = 32;

    /**
     * Whether update subscription relationship when every pull
     * 
     * <p> 每次拉消息时是否更新订阅关系
     */
    private boolean postSubscriptionWhenPull = false;

    /**
     * Whether the unit of subscription group
     * 
     * <p> 是否为订阅组的单元
     */
    private boolean unitMode = false;

    /**
     * Max re-consume times. -1 means 16 times.
     * </p>
     * 
     * <p> 最大消耗时间。 -1表示16次。
     *
     * If messages are re-consumed more than {@link #maxReconsumeTimes} before success, it's be directed to a deletion
     * queue waiting.
     * 
     * <p> 如果消息在成功之前被重新消耗超过maxReconsumeTimes，则它将被定向到等待的删除队列。
     */
    private int maxReconsumeTimes = -1;

    /**
     * Suspending pulling time for cases requiring slow pulling like flow-control scenario.
     * 
     * <p> 暂停需要缓慢拉动的情况的拉动时间，如流量控制方案。
     */
    private long suspendCurrentQueueTimeMillis = 1000;

    /**
     * Maximum amount of time in minutes a message may block the consuming thread.
     * 
     * <p> 消息可能阻止使用线程的最长时间（以分钟为单位）。
     */
    private long consumeTimeout = 15;

    /**
     * Default constructor.
     * 
     * <p> 默认构造函数。
     */
    public DefaultMQPushConsumer() {
        this(MixAll.DEFAULT_CONSUMER_GROUP, null, new AllocateMessageQueueAveragely());
    }

    /**
     * Constructor specifying consumer group, RPC hook and message queue allocating algorithm.
     * 
     * <p> 构造函数指定使用者组，RPC挂钩和消息队列分配算法。
     *
     * @param consumerGroup Consume queue. - 消费者队列组
     * @param rpcHook RPC hook to execute before each remoting command.
     * 
     * <p> RPC钩子在每个远程处理命令之前执行。
     * 
     * @param allocateMessageQueueStrategy message queue allocating algorithm.
     * 
     * <p> 消息队列分配算法。
     */
    public DefaultMQPushConsumer(final String consumerGroup, RPCHook rpcHook,
        AllocateMessageQueueStrategy allocateMessageQueueStrategy) {
        this.consumerGroup = consumerGroup;
        this.allocateMessageQueueStrategy = allocateMessageQueueStrategy;
        defaultMQPushConsumerImpl = new DefaultMQPushConsumerImpl(this, rpcHook);
    }

    /**
     * Constructor specifying RPC hook.
     * 
     * <p> 指定RPC钩子的构造函数。
     *
     * @param rpcHook RPC hook to execute before each remoting command.
     * 
     * <p> RPC钩子在每个远程处理命令之前执行。
     */
    public DefaultMQPushConsumer(RPCHook rpcHook) {
        this(MixAll.DEFAULT_CONSUMER_GROUP, rpcHook, new AllocateMessageQueueAveragely());
    }

    /**
     * Constructor specifying consumer group.
     * 
     * <p> 指定使用者组的构造函数。
     *
     * @param consumerGroup Consumer group. - 消费组
     */
    public DefaultMQPushConsumer(final String consumerGroup) {
        this(consumerGroup, null, new AllocateMessageQueueAveragely());
    }

    @Override
    public void createTopic(String key, String newTopic, int queueNum) throws MQClientException {
        createTopic(key, newTopic, queueNum, 0);
    }

    @Override
    public void createTopic(String key, String newTopic, int queueNum, int topicSysFlag) throws MQClientException {
        this.defaultMQPushConsumerImpl.createTopic(key, newTopic, queueNum, topicSysFlag);
    }

    @Override
    public long searchOffset(MessageQueue mq, long timestamp) throws MQClientException {
        return this.defaultMQPushConsumerImpl.searchOffset(mq, timestamp);
    }

    @Override
    public long maxOffset(MessageQueue mq) throws MQClientException {
        return this.defaultMQPushConsumerImpl.maxOffset(mq);
    }

    @Override
    public long minOffset(MessageQueue mq) throws MQClientException {
        return this.defaultMQPushConsumerImpl.minOffset(mq);
    }

    @Override
    public long earliestMsgStoreTime(MessageQueue mq) throws MQClientException {
        return this.defaultMQPushConsumerImpl.earliestMsgStoreTime(mq);
    }

    @Override
    public MessageExt viewMessage(
        String offsetMsgId) throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        return this.defaultMQPushConsumerImpl.viewMessage(offsetMsgId);
    }

    @Override
    public QueryResult queryMessage(String topic, String key, int maxNum, long begin, long end)
        throws MQClientException, InterruptedException {
        return this.defaultMQPushConsumerImpl.queryMessage(topic, key, maxNum, begin, end);
    }

    @Override
    public MessageExt viewMessage(String topic,
        String msgId) throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        try {
            MessageDecoder.decodeMessageId(msgId);
            return this.viewMessage(msgId);
        } catch (Exception e) {
            // Ignore
        }
        return this.defaultMQPushConsumerImpl.queryMessageByUniqKey(topic, msgId);
    }

    public AllocateMessageQueueStrategy getAllocateMessageQueueStrategy() {
        return allocateMessageQueueStrategy;
    }

    public void setAllocateMessageQueueStrategy(AllocateMessageQueueStrategy allocateMessageQueueStrategy) {
        this.allocateMessageQueueStrategy = allocateMessageQueueStrategy;
    }

    public int getConsumeConcurrentlyMaxSpan() {
        return consumeConcurrentlyMaxSpan;
    }

    public void setConsumeConcurrentlyMaxSpan(int consumeConcurrentlyMaxSpan) {
        this.consumeConcurrentlyMaxSpan = consumeConcurrentlyMaxSpan;
    }

    public ConsumeFromWhere getConsumeFromWhere() {
        return consumeFromWhere;
    }

    public void setConsumeFromWhere(ConsumeFromWhere consumeFromWhere) {
        this.consumeFromWhere = consumeFromWhere;
    }

    public int getConsumeMessageBatchMaxSize() {
        return consumeMessageBatchMaxSize;
    }

    public void setConsumeMessageBatchMaxSize(int consumeMessageBatchMaxSize) {
        this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public DefaultMQPushConsumerImpl getDefaultMQPushConsumerImpl() {
        return defaultMQPushConsumerImpl;
    }

    public MessageListener getMessageListener() {
        return messageListener;
    }

    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public MessageModel getMessageModel() {
        return messageModel;
    }

    public void setMessageModel(MessageModel messageModel) {
        this.messageModel = messageModel;
    }

    public int getPullBatchSize() {
        return pullBatchSize;
    }

    public void setPullBatchSize(int pullBatchSize) {
        this.pullBatchSize = pullBatchSize;
    }

    public long getPullInterval() {
        return pullInterval;
    }

    public void setPullInterval(long pullInterval) {
        this.pullInterval = pullInterval;
    }

    public int getPullThresholdForQueue() {
        return pullThresholdForQueue;
    }

    public void setPullThresholdForQueue(int pullThresholdForQueue) {
        this.pullThresholdForQueue = pullThresholdForQueue;
    }

    public int getPullThresholdForTopic() {
        return pullThresholdForTopic;
    }

    public void setPullThresholdForTopic(final int pullThresholdForTopic) {
        this.pullThresholdForTopic = pullThresholdForTopic;
    }

    public int getPullThresholdSizeForQueue() {
        return pullThresholdSizeForQueue;
    }

    public void setPullThresholdSizeForQueue(final int pullThresholdSizeForQueue) {
        this.pullThresholdSizeForQueue = pullThresholdSizeForQueue;
    }

    public int getPullThresholdSizeForTopic() {
        return pullThresholdSizeForTopic;
    }

    public void setPullThresholdSizeForTopic(final int pullThresholdSizeForTopic) {
        this.pullThresholdSizeForTopic = pullThresholdSizeForTopic;
    }

    public Map<String, String> getSubscription() {
        return subscription;
    }

    public void setSubscription(Map<String, String> subscription) {
        this.subscription = subscription;
    }

    /**
     * Send message back to broker which will be re-delivered in future.
     * 
     * <p> 将消息发送回broker，将来会重新发送。
     *
     * @param msg Message to send back. - 要发回的消息。
     * 
     * @param delayLevel delay level. - 延迟级别
     * 
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any broker error. - 如果有任何broker错误。
     * @throws InterruptedException if the thread is interrupted. - 如果线程被中断
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void sendMessageBack(MessageExt msg, int delayLevel)
        throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        this.defaultMQPushConsumerImpl.sendMessageBack(msg, delayLevel, null);
    }

    /**
     * Send message back to the broker whose name is <code>brokerName</code> and the message will be re-delivered in
     * future.
     * 
     * <p> 将消息发送回名为brokerName的代理，此消息将在以后重新发送。
     *
     * @param msg Message to send back. - 要发回的消息。
     * @param delayLevel delay level. - 延迟级别
     * @param brokerName broker name. - broker名称
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any broker error. - 如果有任何broker错误。
     * @throws InterruptedException if the thread is interrupted. - 如果线程被中断
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void sendMessageBack(MessageExt msg, int delayLevel, String brokerName)
        throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        this.defaultMQPushConsumerImpl.sendMessageBack(msg, delayLevel, brokerName);
    }

    @Override
    public Set<MessageQueue> fetchSubscribeMessageQueues(String topic) throws MQClientException {
        return this.defaultMQPushConsumerImpl.fetchSubscribeMessageQueues(topic);
    }

    /**
     * This method gets internal infrastructure readily to serve. Instances must call this method after configuration.
     * 
     * <p> 该方法使内部基础设施易于服务。 实例必须在配置后调用此方法。
     *
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void start() throws MQClientException {
        this.defaultMQPushConsumerImpl.start();
    }

    /**
     * Shut down this client and releasing underlying resources.
     * 
     * <p> 关闭此客户端并释放底层资源。
     */
    @Override
    public void shutdown() {
        this.defaultMQPushConsumerImpl.shutdown();
    }

    @Override
    @Deprecated
    public void registerMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
        this.defaultMQPushConsumerImpl.registerMessageListener(messageListener);
    }

    /**
     * Register a callback to execute on message arrival for concurrent consuming.
     * 
     * <p> 注册回调以在消息到达时执行以进行并发消费。
     *
     * @param messageListener message handling callback. - 消息处理回调。
     */
    @Override
    public void registerMessageListener(MessageListenerConcurrently messageListener) {
        this.messageListener = messageListener;
        this.defaultMQPushConsumerImpl.registerMessageListener(messageListener);
    }

    /**
     * Register a callback to execute on message arrival for orderly consuming.
     * 
     * <p> 注册回调以在消息到达时执行以便有序消费。
     *
     * @param messageListener message handling callback. - 消息处理回调。
     */
    @Override
    public void registerMessageListener(MessageListenerOrderly messageListener) {
        this.messageListener = messageListener;
        this.defaultMQPushConsumerImpl.registerMessageListener(messageListener);
    }

    /**
     * Subscribe a topic to consuming subscription.
     * 
     * <p> 订阅主题以消费订阅。
     *
     * @param topic topic to subscribe. - 要订阅的主题
     * @param subExpression subscription expression.it only support or operation such as "tag1 || tag2 || tag3" <br>
     * if null or * expression,meaning subscribe all
     * 
     * <p> 订阅表达式。仅支持或操作，如“tag1 || tag2 || tag3” 如果为null或*表达式，则表示订阅全部
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void subscribe(String topic, String subExpression) throws MQClientException {
        this.defaultMQPushConsumerImpl.subscribe(topic, subExpression);
    }

    /**
     * Subscribe a topic to consuming subscription.
     * 
     * <p> 订阅主题以消费订阅。
     *
     * @param topic topic to consume. - 要消费的主题.
     * 
     * @param fullClassName full class name,must extend org.apache.rocketmq.common.filter. MessageFilter
     * 
     * <p> 完整的类名，必须扩展org.apache.rocketmq.common.filter。的MessageFilter
     * 
     * @param filterClassSource class source code,used UTF-8 file encoding,must be responsible for your code safety
     * 
     * <p> 类源代码，使用UTF-8文件编码，必须负责您的代码安全
     */
    @Override
    public void subscribe(String topic, String fullClassName, String filterClassSource) throws MQClientException {
        this.defaultMQPushConsumerImpl.subscribe(topic, fullClassName, filterClassSource);
    }

    /**
     * Subscribe a topic by message selector.
     * 
     * <p> 通过消息选择器订阅主题。
     *
     * @param topic topic to consume.  - 要消费的主题.
     * 
     * @param messageSelector {@link org.apache.rocketmq.client.consumer.MessageSelector}
     * @see org.apache.rocketmq.client.consumer.MessageSelector#bySql
     * @see org.apache.rocketmq.client.consumer.MessageSelector#byTag
     */
    @Override
    public void subscribe(final String topic, final MessageSelector messageSelector) throws MQClientException {
        this.defaultMQPushConsumerImpl.subscribe(topic, messageSelector);
    }

    /**
     * Un-subscribe the specified topic from subscription.
     * 
     * <p> 从订阅中取消订阅指定的主题。
     *
     * @param topic message topic - 消息主题
     */
    @Override
    public void unsubscribe(String topic) {
        this.defaultMQPushConsumerImpl.unsubscribe(topic);
    }

    /**
     * Update the message consuming thread core pool size.
     * 
     * <p> 更新消耗线程核心池大小的消息。
     *
     * @param corePoolSize new core pool size. - 新的核心池大小。
     */
    @Override
    public void updateCorePoolSize(int corePoolSize) {
        this.defaultMQPushConsumerImpl.updateCorePoolSize(corePoolSize);
    }

    /**
     * Suspend pulling new messages.
     * 
     * <p> 暂停拉新消息。
     */
    @Override
    public void suspend() {
        this.defaultMQPushConsumerImpl.suspend();
    }

    /**
     * Resume pulling. - 恢复拉
     */
    @Override
    public void resume() {
        this.defaultMQPushConsumerImpl.resume();
    }

    public OffsetStore getOffsetStore() {
        return offsetStore;
    }

    public void setOffsetStore(OffsetStore offsetStore) {
        this.offsetStore = offsetStore;
    }

    public String getConsumeTimestamp() {
        return consumeTimestamp;
    }

    public void setConsumeTimestamp(String consumeTimestamp) {
        this.consumeTimestamp = consumeTimestamp;
    }

    public boolean isPostSubscriptionWhenPull() {
        return postSubscriptionWhenPull;
    }

    public void setPostSubscriptionWhenPull(boolean postSubscriptionWhenPull) {
        this.postSubscriptionWhenPull = postSubscriptionWhenPull;
    }

    public boolean isUnitMode() {
        return unitMode;
    }

    public void setUnitMode(boolean isUnitMode) {
        this.unitMode = isUnitMode;
    }

    public long getAdjustThreadPoolNumsThreshold() {
        return adjustThreadPoolNumsThreshold;
    }

    public void setAdjustThreadPoolNumsThreshold(long adjustThreadPoolNumsThreshold) {
        this.adjustThreadPoolNumsThreshold = adjustThreadPoolNumsThreshold;
    }

    public int getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(final int maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    public long getSuspendCurrentQueueTimeMillis() {
        return suspendCurrentQueueTimeMillis;
    }

    public void setSuspendCurrentQueueTimeMillis(final long suspendCurrentQueueTimeMillis) {
        this.suspendCurrentQueueTimeMillis = suspendCurrentQueueTimeMillis;
    }

    public long getConsumeTimeout() {
        return consumeTimeout;
    }

    public void setConsumeTimeout(final long consumeTimeout) {
        this.consumeTimeout = consumeTimeout;
    }
}
