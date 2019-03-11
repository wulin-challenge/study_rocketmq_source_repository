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
package org.apache.rocketmq.client.producer;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.Validators;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;

/**
 * This class is the entry point for applications intending to send messages.
 * </p>
 * 
 * 此类是打算发送消息的应用程序的入口点。</p>
 *
 * It's fine to tune fields which exposes getter/setter methods, but keep in mind, all of them should work well out of
 * box for most scenarios.
 * </p>
 * 
 * 调整暴露getter / setter方法的字段是很好的，但请记住，对于大多数情况，所有这些方法都应该可以很好地开箱即用。</p>
 *
 * This class aggregates various <code>send</code> methods to deliver messages to brokers. Each of them has pros and
 * cons; you'd better understand strengths and weakness of them before actually coding.
 * </p>
 * 
 * 此类聚合各种发送方法以将消息传递给代理。 他们每个人都有利弊; 在实际编码之前，你最好先了解它们的优点和缺点。</p>
 *
 * <p>
 * <strong>Thread Safety:</strong> After configuring and starting process, this class can be regarded as thread-safe
 * and used among multiple threads context.
 * </p>
 * 
 * <p> 线程安全：配置和启动进程后，可以将此类视为线程安全的，并在多线程上下文中使用。
 */
public class DefaultMQProducer extends ClientConfig implements MQProducer {

    /**
     * Wrapping internal implementations for virtually all methods presented in this class.
     * 
     * <p> 包含此类中提供的几乎所有方法的内部实现。
     */
    protected final transient DefaultMQProducerImpl defaultMQProducerImpl;

    /**
     * Producer group conceptually aggregates all producer instances of exactly same role, which is particularly
     * important when transactional messages are involved.
     * </p>
     * 
     * 生产者组在概念上聚合完全相同角色的所有生产者实例，这在涉及事务性消息时尤为重要。</p>
     *
     * For non-transactional messages, it does not matter as long as it's unique per process.
     * </p>
     * 
     * 对于非事务性消息，只要每个进程的唯一性就无关紧要。</p>
     *
     * <p> See {@linktourl http://rocketmq.apache.org/docs/core-concept/} for more discussion.
     * 
     * <p> 有关更多讨论，请参阅{@linktourl http://rocketmq.apache.org/docs/core-concept/}。
     */
    private String producerGroup;

    /**
     * Just for testing or demo program
     * 
     * <p> 仅用于测试或演示程序
     */
    private String createTopicKey = MixAll.DEFAULT_TOPIC;

    /**
     * Number of queues to create per default topic.
     * 
     * <p> 每个默认主题要创建的队列数。
     */
    private volatile int defaultTopicQueueNums = 4;

    /**
     * Timeout for sending messages.
     * 
     * <p> 发送消息超时。
     */
    private int sendMsgTimeout = 3000;

    /**
     * Compress message body threshold, namely, message body larger than 4k will be compressed on default.
     * 
     * <p> 压缩消息体阈值，即大于4k的消息体将默认压缩。
     */
    private int compressMsgBodyOverHowmuch = 1024 * 4;

    /**
     * Maximum number of retry to perform internally before claiming sending failure in synchronous mode.
     * </p>
     * 
     * 在同步模式下声明发送失败之前内部执行的最大重试次数。</p>
     *
     * <p> This may potentially cause message duplication which is up to application developers to resolve.
     * 
     * <p> 这可能会导致消息重复，这取决于应用程序开发人员要解决的问题。
     */
    private int retryTimesWhenSendFailed = 2;

    /**
     * Maximum number of retry to perform internally before claiming sending failure in asynchronous mode.
     * </p>
     * 
     * <p> 在异步模式下声明发送失败之前内部执行的最大重试次数。
     *
     * <p> This may potentially cause message duplication which is up to application developers to resolve.
     * 
     * <p> 这可能会导致消息重复，这取决于应用程序开发人员要解决的问题。
     */
    private int retryTimesWhenSendAsyncFailed = 2;

    /**
     * Indicate whether to retry another broker on sending failure internally.
     * 
     * <p> 消息重试时选择另外一个 Broker 时,是否不等待存储结果就返回 ， 默认为 false 。
     */
    private boolean retryAnotherBrokerWhenNotStoreOK = false;

    /**
     * Maximum allowed message size in bytes.
     * 
     * <p> 允许的最大消息大小（字节）。
     */
    private int maxMessageSize = 1024 * 1024 * 4; // 4M

    /**
     * Default constructor.
     * 
     * <p> 默认构造器
     */
    public DefaultMQProducer() {
        this(MixAll.DEFAULT_PRODUCER_GROUP, null);
    }

    /**
     * Constructor specifying both producer group and RPC hook.
     * 
     * <p> 构造函数，指定生成器组和RPC挂钩。
     *
     * @param producerGroup Producer group, see the name-sake field.
     * 
     * <p> 生产人组，请参阅名称 - 清单字段。
     * 
     * @param rpcHook RPC hook to execute per each remoting command execution.
     * 
     * <p> 每个远程执行命令执行执行的RPC钩子.
     */
    public DefaultMQProducer(final String producerGroup, RPCHook rpcHook) {
        this.producerGroup = producerGroup;
        defaultMQProducerImpl = new DefaultMQProducerImpl(this, rpcHook);
    }

    /**
     * Constructor specifying producer group.
     * 
     * <p> 指定生产者组的构造函数。
     *
     * @param producerGroup Producer group, see the name-sake field.
     * 
     * <p> 生产人组，请参阅名称 - 清单字段。
     */
    public DefaultMQProducer(final String producerGroup) {
        this(producerGroup, null);
    }

    /**
     * Constructor specifying the RPC hook.
     * 
     * <p> 指定RPC钩子的构造函数。
     *
     * @param rpcHook RPC hook to execute per each remoting command execution.
     * 
     * <p> 每个远程执行命令执行执行的RPC挂钩。
     */
    public DefaultMQProducer(RPCHook rpcHook) {
        this(MixAll.DEFAULT_PRODUCER_GROUP, rpcHook);
    }

    /**
     * Start this producer instance.
     * </p>
     * 
     * 启动此生产者实例。</p>
     *
     * <strong>
     * Much internal initializing procedures are carried out to make this instance prepared, thus, it's a must to invoke
     * this method before sending or querying messages.
     * </strong>
     * </p>
     * 
     * <p> 执行了许多内部初始化过程以使该实例准备好，因此，在发送或查询消息之前必须调用此方法。
     *
     * @throws MQClientException if there is any unexpected error.
     * 
     * <p> 如果有任何意外错误。
     */
    @Override
    public void start() throws MQClientException {
        this.defaultMQProducerImpl.start();
    }

    /**
     * This method shuts down this producer instance and releases related resources.
     * 
     * <p> 此方法关闭此生产者实例并释放相关资源。
     */
    @Override
    public void shutdown() {
        this.defaultMQProducerImpl.shutdown();
    }

    /**
     * Fetch message queues of topic <code>topic</code>, to which we may send/publish messages.
     * 
     * <p> 获取主题主题的消息队列，我们可以向其发送/发布消息。
     *
     * @param topic Topic to fetch. - Topic to fetch.
     * 
     * <p> 要获取的主题。  - 获取主题。
     * 
     * @return List of message queues readily to send messages to
     * 
     * <p> 容易发送消息的消息队列列表
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * 
     * <p> 如果有任何客户端错误。
     */
    @Override
    public List<MessageQueue> fetchPublishMessageQueues(String topic) throws MQClientException {
        return this.defaultMQProducerImpl.fetchPublishMessageQueues(topic);
    }

    /**
     * Send message in synchronous mode. This method returns only when the sending procedure totally completes.
     * </p>
     * 
     * <p> 以同步模式发送消息。 仅当发送过程完全完成时，此方法才会返回。
     *
     * <p> <strong>Warn:</strong> this method has internal retry-mechanism, that is, internal implementation will retry
     * {@link #retryTimesWhenSendFailed} times before claiming failure. As a result, multiple messages may potentially
     * delivered to broker(s). It's up to the application developers to resolve potential duplication issue.
     *
     * <p> 警告：此方法具有内部重试机制，即内部实现将在声明失败之前重试retryTimesWhenSendFailed次。 因此，可能会向代理发送多条消息。 应用程序开发人员可以解决潜在的重复问题。
     * 
     * @param msg Message to send. - 要发送的消息。
     * 
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * 
     * <p> SendResult实例通知发件人可交付物的详细信息，例如消息的消息ID，指示代理存储/复制状态的SendStatus，发送到的消息队列等。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(
        Message msg) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg);
    }

    /**
     * Same to {@link #send(Message)} with send timeout specified in addition.
     * 
     * <p> 与发送（消息）相同，并另外指定发送超时。
     *
     * @param msg Message to send. - 要发送的消息
     * @param timeout send timeout. 发送消息的超时时间
     * 
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * 
     * <p> SendResult实例通知发件人可交付物的详细信息，例如消息的消息ID，指示代理存储/复制状态的SendStatus，发送到的消息队列等。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(Message msg,
        long timeout) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg, timeout);
    }

    /**
     * Send message to broker asynchronously.
     * </p>
     * 
     * 异步发送消息给代理。</p>
     *
     * This method returns immediately. On sending completion, <code>sendCallback</code> will be executed.
     * </p>
     *
     * 此方法立即返回。 发送完成后，将执行sendCallback。</p>
     * 
     * <p> Similar to {@link #send(Message)}, internal implementation would potentially retry up to
     * {@link #retryTimesWhenSendAsyncFailed} times before claiming sending failure, which may yield message duplication
     * and application developers are the one to resolve this potential issue.
     * 
     * <p> 与send（Message）类似，内部实现可能会在声明发送失败之前重试retryTimesWhenSendAsyncFailed次数，
     * 这可能会产生消息重复，应用程序开发人员可以解决此潜在问题。
     *
     * @param msg Message to send. - 要发送的消息
     * @param sendCallback Callback to execute on sending completed, either successful or unsuccessful.
     * 
     * <p> 回调执行发送完成，成功或不成功。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg,
        SendCallback sendCallback) throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, sendCallback);
    }

    /**
     * Same to {@link #send(Message, SendCallback)} with send timeout specified in addition.
     * 
     * <p> 与send（Message，SendCallback）相同，并另外指定发送超时。
     *
     * @param msg message to send. - 要发送的消息。
     * @param sendCallback Callback to execute. - sendCallback回调执行。
     * @param timeout send timeout. - 发送消息超时时间
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg, SendCallback sendCallback, long timeout)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, sendCallback, timeout);
    }

    /**
     * Similar to <a href="https://en.wikipedia.org/wiki/User_Datagram_Protocol">UDP</a>, this method won't wait for
     * acknowledgement from broker before return. Obviously, it has maximums throughput yet potentials of message loss.
     *
     * <p> 与UDP类似，此方法不会在返回之前等待来自代理的确认。 显然，它具有最大的吞吐量但消息丢失的潜力。
     *
     * @param msg Message to send. - 要发送的消息
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void sendOneway(Message msg) throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.sendOneway(msg);
    }

    /**
     * Same to {@link #send(Message)} with target message queue specified in addition.
     * 
     * <p> 与发送（消息）相同，另外指定了目标消息队列。
     *
     * @param msg Message to send. - 要发送的消息
     * @param mq Target message queue. - 目标消息队列。
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * 
     * <p> SendResult实例通知发件人可交付物的详细信息，例如消息的消息ID，指示代理存储/复制状态的SendStatus，发送到的消息队列等。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(Message msg, MessageQueue mq)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg, mq);
    }

    /**
     * Same to {@link #send(Message)} with target message queue and send timeout specified.
     * 
     * <p> 与发送（消息）目标消息队列和指定的发送超时相同。
     *
     * @param msg Message to send. - 要发送的消息。
     * @param mq Target message queue. - 目标消息队列。
     * @param timeout send timeout. - 发送消息超时
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * 
     * <p> SendResult实例通知发件人可交付物的详细信息，例如消息的消息ID，指示代理存储/复制状态的SendStatus，发送到的消息队列等。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(Message msg, MessageQueue mq, long timeout)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg, mq, timeout);
    }

    /**
     * Same to {@link #send(Message, SendCallback)} with target message queue specified.
     * 
     * <p> 与指定目标消息队列的send（Message，SendCallback）相同。
     *
     * @param msg Message to send. - 要发送的消息
     * @param mq Target message queue. - 目标消息队列。
     * @param sendCallback Callback to execute on sending completed, either successful or unsuccessful.
     * 
     * <p> 回调执行发送完成，成功或不成功。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg, MessageQueue mq, SendCallback sendCallback)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, mq, sendCallback);
    }

    /**
     * Same to {@link #send(Message, SendCallback)} with target message queue and send timeout specified.
     * 
     * <p> 与发送（Message，SendCallback）目标消息队列和指定的发送超时相同。
     *
     * @param msg Message to send. - 要发送的消息。
     * @param mq Target message queue. - 目标消息队列。
     * @param sendCallback Callback to execute on sending completed, either successful or unsuccessful.
     * 
     * <p> 回调执行发送完成，成功或不成功。
     * 
     * @param timeout Send timeout. - 发送消息超时
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg, MessageQueue mq, SendCallback sendCallback, long timeout)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, mq, sendCallback, timeout);
    }

    /**
     * Same to {@link #sendOneway(Message)} with target message queue specified.
     * 
     * <p> 与指定目标消息队列的sendOneway（Message）相同。
     *
     * @param msg Message to send. - 要发送的消息
     * @param mq Target message queue. - 目标消息队列。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void sendOneway(Message msg,
        MessageQueue mq) throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.sendOneway(msg, mq);
    }

    /**
     * Same to {@link #send(Message)} with message queue selector specified.
     * 
     * <p> 与发送（消息）相同，并指定了消息队列选择器。
     *
     * @param msg Message to send.
     * 
     * <p> 要发送的消息。
     * 
     * @param selector Message queue selector, through which we get target message queue to deliver message to.
     * 
     * <p> 消息队列选择器，通过它我们获得目标消息队列以传递消息。
     * 
     * @param arg Argument to work along with message queue selector.
     * 
     * <p> 与消息队列选择器一起工作的参数。
     * 
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * 
     * <p> SendResult实例通知发件人可交付物的详细信息，例如消息的消息ID，指示代理存储/复制状态的SendStatus，发送到的消息队列等。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(Message msg, MessageQueueSelector selector, Object arg)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg, selector, arg);
    }

    /**
     * Same to {@link #send(Message, MessageQueueSelector, Object)} with send timeout specified.
     * 
     * <p> 与send（Message，MessageQueueSelector，Object）相同，并指定发送超时。
     *
     * @param msg Message to send. - 要发送的消息
     * @param selector Message queue selector, through which we get target message queue to deliver message to.
     * 
     * <p> 消息队列选择器，通过它我们获得目标消息队列以传递消息。
     * 
     * @param arg Argument to work along with message queue selector.
     * @param timeout Send timeout. - 发送消息超时时间
     * @return {@link SendResult} instance to inform senders details of the deliverable, say Message ID of the message,
     * {@link SendStatus} indicating broker storage/replication status, message queue sent to, etc.
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws MQBrokerException if there is any error with broker. - 如果broker有任何错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public SendResult send(Message msg, MessageQueueSelector selector, Object arg, long timeout)
        throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(msg, selector, arg, timeout);
    }

    /**
     * Same to {@link #send(Message, SendCallback)} with message queue selector specified.
     * 
     * <p> 消息选择器，通过它获取目标消息队列。
     *
     * @param msg Message to send.- 要发送的消息
     * @param selector Message selector through which to get target message queue.
     * 
     * <p> 消息选择器，通过它获取目标消息队列。
     * 
     * @param arg Argument used along with message queue selector.
     * 
     * <p> 与消息队列选择器一起使用的参数。
     * 
     * @param sendCallback callback to execute on sending completion.
     * 
     * <p> 回调在发送完成时执行。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg, MessageQueueSelector selector, Object arg, SendCallback sendCallback)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, selector, arg, sendCallback);
    }

    /**
     * Same to {@link #send(Message, MessageQueueSelector, Object, SendCallback)} with timeout specified.
     *
     * @param msg Message to send. - 要发送的消息
     * @param selector Message selector through which to get target message queue.
     * @param arg Argument used along with message queue selector.
     * 
     * <p> 与消息队列选择器一起使用的参数。
     * 
     * @param sendCallback callback to execute on sending completion.
     * 
     * <p> 回调在发送完成时执行。
     * 
     * @param timeout Send timeout. - 发送消息超时时间
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void send(Message msg, MessageQueueSelector selector, Object arg, SendCallback sendCallback, long timeout)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.send(msg, selector, arg, sendCallback, timeout);
    }

    /**
     * Same to {@link #sendOneway(Message)} with message queue selector specified.
     *
     * @param msg Message to send. - 要发送的消息
     * @param selector Message queue selector, through which to determine target message queue to deliver message
     * 
     * <p> 消息队列选择器，用于确定要传递消息的目标消息队列
     * 
     * @param arg Argument used along with message queue selector.
     * 
     * <p> 与消息队列选择器一起使用的参数。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public void sendOneway(Message msg, MessageQueueSelector selector, Object arg)
        throws MQClientException, RemotingException, InterruptedException {
        this.defaultMQProducerImpl.sendOneway(msg, selector, arg);
    }

    /**
     * This method is to send transactional messages.
     * 
     * <p> 此方法用于发送事务性消息。
     *
     * @param msg Transactional message to send. - 要发送的事务消息。
     * @param tranExecuter local transaction executor. - 本地事务执行人。
     * @param arg Argument used along with local transaction executor.
     * 
     * <p> 与本地事务执行程序一起使用的参数。
     * 
     * @return Transaction result.
     * 
     * <p> 交易结果。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public TransactionSendResult sendMessageInTransaction(Message msg, LocalTransactionExecuter tranExecuter,
        final Object arg)
        throws MQClientException {
        throw new RuntimeException("sendMessageInTransaction not implement, please use TransactionMQProducer class");
    }

    /**
     * Create a topic on broker.
     * 
     * <p> 在broker上创建主题。
     *
     * @param key accesskey - 访问key
     * @param newTopic topic name - 主题名称
     * @param queueNum topic's queue number - 主题的队列数
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void createTopic(String key, String newTopic, int queueNum) throws MQClientException {
        createTopic(key, newTopic, queueNum, 0);
    }

    /**
     * Create a topic on broker.
     * 
     * <p> 在broker上创建主题。
     *
     * @param key accesskey - 访问key
     * @param newTopic topic name - 主题名称
     * @param queueNum topic's queue number - 主题的队列数
     * @param topicSysFlag topic system flag - 主题系统标志
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public void createTopic(String key, String newTopic, int queueNum, int topicSysFlag) throws MQClientException {
        this.defaultMQProducerImpl.createTopic(key, newTopic, queueNum, topicSysFlag);
    }

    /**
     * Search consume queue offset of the given time stamp.
     * 
     * <p> 搜索消耗给定时间戳的队列偏移量。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @param timestamp from when in milliseconds. - 从毫秒开始。
     * @return Consume queue offset. - 消耗队列偏移量。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public long searchOffset(MessageQueue mq, long timestamp) throws MQClientException {
        return this.defaultMQProducerImpl.searchOffset(mq, timestamp);
    }

    /**
     * Query maximum offset of the given message queue.
     * 
     * <p> 查询给定消息队列的最大偏移量。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return maximum offset of the given consume queue.
     * 
     * <p> 给定消耗队列的最大偏移量。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public long maxOffset(MessageQueue mq) throws MQClientException {
        return this.defaultMQProducerImpl.maxOffset(mq);
    }

    /**
     * Query minimum offset of the given message queue.
     * 
     * <p> 查询给定消息队列的最小偏移量。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return minimum offset of the given message queue.
     * 
     * <p> 给定消耗队列的最小偏移量。
     * 
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public long minOffset(MessageQueue mq) throws MQClientException {
        return this.defaultMQProducerImpl.minOffset(mq);
    }

    /**
     * Query earliest message store time.
     * 
     * <p> 查询最早的消息存储时间。
     *
     * @param mq Instance of MessageQueue - MessageQueue实例
     * @return earliest message store time. - 最早的消息存储时间。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     */
    @Override
    public long earliestMsgStoreTime(MessageQueue mq) throws MQClientException {
        return this.defaultMQProducerImpl.earliestMsgStoreTime(mq);
    }

    /**
     * Query message of the given offset message ID.
     * 
     * <p> 查询给定偏移消息ID的消息。
     *
     * @param offsetMsgId message id - 消息Id
     * @return Message specified.  - 指定消息。
     * @throws MQBrokerException if there is any broker error. - 如果有任何broker错误。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public MessageExt viewMessage(
        String offsetMsgId) throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        return this.defaultMQProducerImpl.viewMessage(offsetMsgId);
    }

    /**
     * Query message by key.
     * 
     * <p> 按key查询消息。
     *
     * @param topic message topic - 消息主题
     * @param key message key index word - 消息key的索引
     * @param maxNum max message number - 最大消息数
     * @param begin from when
     * @param end to when
     * @return QueryResult instance contains matched messages. - QueryResult实例包含匹配的消息。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws InterruptedException if the thread is interrupted. - 如果线程被中断
     */
    @Override
    public QueryResult queryMessage(String topic, String key, int maxNum, long begin, long end)
        throws MQClientException, InterruptedException {
        return this.defaultMQProducerImpl.queryMessage(topic, key, maxNum, begin, end);
    }

    /**
     * Query message of the given message ID.
     * 
     * <p> 查询给定消息ID的消息。
     *
     * @param topic Topic - 主题
     * @param msgId Message ID - 消息Id
     * @return Message specified. - 指定的消息
     * @throws MQBrokerException if there is any broker error. - 如果有任何broker错误。
     * @throws MQClientException if there is any client error. - 如果有任何客户端错误。
     * @throws RemotingException if there is any network-tier error. - 如果有任何网络层错误。
     * @throws InterruptedException if the sending thread is interrupted. - 如果发送线程被中断。
     */
    @Override
    public MessageExt viewMessage(String topic,
        String msgId) throws RemotingException, MQBrokerException, InterruptedException, MQClientException {
        try {
            MessageId oldMsgId = MessageDecoder.decodeMessageId(msgId);
            return this.viewMessage(msgId);
        } catch (Exception e) {
        }
        return this.defaultMQProducerImpl.queryMessageByUniqKey(topic, msgId);
    }

    @Override
    public SendResult send(
        Collection<Message> msgs) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(batch(msgs));
    }

    @Override
    public SendResult send(Collection<Message> msgs,
        long timeout) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(batch(msgs), timeout);
    }

    @Override
    public SendResult send(Collection<Message> msgs,
        MessageQueue messageQueue) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(batch(msgs), messageQueue);
    }

    @Override
    public SendResult send(Collection<Message> msgs, MessageQueue messageQueue,
        long timeout) throws MQClientException, RemotingException, MQBrokerException, InterruptedException {
        return this.defaultMQProducerImpl.send(batch(msgs), messageQueue, timeout);
    }

    /**
     * Sets an Executor to be used for executing callback methods.
     * If the Executor is not set, {@link NettyRemotingClient#publicExecutor} will be used.
     * 
     * <p> 设置用于执行回调方法的Executor。 如果未设置Executor，将使用NettyRemotingClient.publicExecutor。
     *
     * @param callbackExecutor the instance of Executor
     * 
     * <p> Executor的实例
     */
    public void setCallbackExecutor(final ExecutorService callbackExecutor) {
        this.defaultMQProducerImpl.setCallbackExecutor(callbackExecutor);
    }

    private MessageBatch batch(Collection<Message> msgs) throws MQClientException {
        MessageBatch msgBatch;
        try {
            msgBatch = MessageBatch.generateFromList(msgs);
            for (Message message : msgBatch) {
                Validators.checkMessage(message, this);
                MessageClientIDSetter.setUniqID(message);
            }
            msgBatch.setBody(msgBatch.encode());
        } catch (Exception e) {
            throw new MQClientException("Failed to initiate the MessageBatch", e);
        }
        return msgBatch;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getCreateTopicKey() {
        return createTopicKey;
    }

    public void setCreateTopicKey(String createTopicKey) {
        this.createTopicKey = createTopicKey;
    }

    public int getSendMsgTimeout() {
        return sendMsgTimeout;
    }

    public void setSendMsgTimeout(int sendMsgTimeout) {
        this.sendMsgTimeout = sendMsgTimeout;
    }

    public int getCompressMsgBodyOverHowmuch() {
        return compressMsgBodyOverHowmuch;
    }

    public void setCompressMsgBodyOverHowmuch(int compressMsgBodyOverHowmuch) {
        this.compressMsgBodyOverHowmuch = compressMsgBodyOverHowmuch;
    }

    public DefaultMQProducerImpl getDefaultMQProducerImpl() {
        return defaultMQProducerImpl;
    }

    public boolean isRetryAnotherBrokerWhenNotStoreOK() {
        return retryAnotherBrokerWhenNotStoreOK;
    }

    public void setRetryAnotherBrokerWhenNotStoreOK(boolean retryAnotherBrokerWhenNotStoreOK) {
        this.retryAnotherBrokerWhenNotStoreOK = retryAnotherBrokerWhenNotStoreOK;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getDefaultTopicQueueNums() {
        return defaultTopicQueueNums;
    }

    public void setDefaultTopicQueueNums(int defaultTopicQueueNums) {
        this.defaultTopicQueueNums = defaultTopicQueueNums;
    }

    public int getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(int retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public boolean isSendMessageWithVIPChannel() {
        return isVipChannelEnabled();
    }

    public void setSendMessageWithVIPChannel(final boolean sendMessageWithVIPChannel) {
        this.setVipChannelEnabled(sendMessageWithVIPChannel);
    }

    public long[] getNotAvailableDuration() {
        return this.defaultMQProducerImpl.getNotAvailableDuration();
    }

    public void setNotAvailableDuration(final long[] notAvailableDuration) {
        this.defaultMQProducerImpl.setNotAvailableDuration(notAvailableDuration);
    }

    public long[] getLatencyMax() {
        return this.defaultMQProducerImpl.getLatencyMax();
    }

    public void setLatencyMax(final long[] latencyMax) {
        this.defaultMQProducerImpl.setLatencyMax(latencyMax);
    }

    public boolean isSendLatencyFaultEnable() {
        return this.defaultMQProducerImpl.isSendLatencyFaultEnable();
    }

    public void setSendLatencyFaultEnable(final boolean sendLatencyFaultEnable) {
        this.defaultMQProducerImpl.setSendLatencyFaultEnable(sendLatencyFaultEnable);
    }

    public int getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(final int retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }
}
