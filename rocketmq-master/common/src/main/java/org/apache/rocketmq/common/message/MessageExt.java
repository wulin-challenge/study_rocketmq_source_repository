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
package org.apache.rocketmq.common.message;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import org.apache.rocketmq.common.TopicFilterType;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;

public class MessageExt extends Message {
    private static final long serialVersionUID = 5720810158625748049L;

    /**
     * 队列Id
     * <p> 占用字节长度 为 4 个字节
     */
    private int queueId;

    /**
     * 代表这个消息的大小
     * <p> 占用字节长度 为 4 个字节
     */
    private int storeSize;

    /**
     * 队列偏移量
     * <p> 占用字节长度 为 8 个字节
     * <p> 注意: 这个值是个自增值不是真正的 consume queue 的偏移量，可以代表这个consumeQueue 队列或者 tranStateTable 队列中消息的个数
     */
    private long queueOffset;
    
    /**
     * sysFlag
     * <p> 占用字节长度 为 8 个字节
     * <p> 注意: 指明消息是事物事物状态等消息特征，二进制为四个字节从右往左数：
     * <ul>
     * <li> 当 4 个字节均为 0（值为 0）时表示非事务消息;
     * <li> 当第 1 个字 节为 1（值为 1）时表示表示消息是压缩的（Compressed）;
     * <li> 当第 2 个字节为 1（值为 2） 表示多消息（MultiTags）;
     * <li> 当第 3 个字节为 1（值为 4）时表示 prepared 消息;
     * <li> 当第 4 个字节为 1（值为 8）时表示commit 消息; 
     * <li> 当第 3/4 个字节均为 1 时（值为 12）时表示 rollback 消息;
     * <li> 当第 3/4 个字节均为 0 时表 示非事务消息;
     * </ul>
     */
    private int sysFlag;
    
    /**
     * 消息产生端(producer)的时间戳
     * <p> 占用字节长度 为 8 个字节
     */
    private long bornTimestamp;
    
    /**
     * 消息产生端(producer)地址(address:port)
     * <p> 占用字节长度 为 8 个字节
     */
    private SocketAddress bornHost;

    /**
     * 消息在broker存储时间
     * <p> 占用字节长度 为 8 个字节
     */
    private long storeTimestamp;
    
    /**
     * 消息存储到broker的地址(address:port)
     * <p> 占用字节长度 为 8 个字节
     */
    private SocketAddress storeHost;
    
    private String msgId;
    private long commitLogOffset;
    
    /**
     * 消息体 BODY CRC
     * <p> 占用字节长度 为 4 个字节
     */
    private int bodyCRC;
    
    /**
     * 消息被某个订阅组重新消费了几次（订阅组 之间独立计数）,因为重试消息发送到了topic 名字为%retry%groupName的队
     * 列 queueId=0的队列中去了，成功消费一次记 录为0；
     * <p> 占用字节长度 为 8 个字节
     */
    private int reconsumeTimes;

    /**
     * 表示是prepared状态的事物消息
     * <p> 占用字节长度 为 8 个字节
     */
    private long preparedTransactionOffset;

    public MessageExt() {
    }

    public MessageExt(int queueId, long bornTimestamp, SocketAddress bornHost, long storeTimestamp,
        SocketAddress storeHost, String msgId) {
        this.queueId = queueId;
        this.bornTimestamp = bornTimestamp;
        this.bornHost = bornHost;
        this.storeTimestamp = storeTimestamp;
        this.storeHost = storeHost;
        this.msgId = msgId;
    }

    public static TopicFilterType parseTopicFilterType(final int sysFlag) {
        if ((sysFlag & MessageSysFlag.MULTI_TAGS_FLAG) == MessageSysFlag.MULTI_TAGS_FLAG) {
            return TopicFilterType.MULTI_TAG;
        }

        return TopicFilterType.SINGLE_TAG;
    }

    public static ByteBuffer socketAddress2ByteBuffer(final SocketAddress socketAddress, final ByteBuffer byteBuffer) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
        byteBuffer.put(inetSocketAddress.getAddress().getAddress(), 0, 4);
        byteBuffer.putInt(inetSocketAddress.getPort());
        byteBuffer.flip();
        return byteBuffer;
    }

    public static ByteBuffer socketAddress2ByteBuffer(SocketAddress socketAddress) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        return socketAddress2ByteBuffer(socketAddress, byteBuffer);
    }

    public ByteBuffer getBornHostBytes() {
        return socketAddress2ByteBuffer(this.bornHost);
    }

    public ByteBuffer getBornHostBytes(ByteBuffer byteBuffer) {
        return socketAddress2ByteBuffer(this.bornHost, byteBuffer);
    }

    public ByteBuffer getStoreHostBytes() {
        return socketAddress2ByteBuffer(this.storeHost);
    }

    public ByteBuffer getStoreHostBytes(ByteBuffer byteBuffer) {
        return socketAddress2ByteBuffer(this.storeHost, byteBuffer);
    }

    public int getQueueId() {
        return queueId;
    }

    public void setQueueId(int queueId) {
        this.queueId = queueId;
    }

    public long getBornTimestamp() {
        return bornTimestamp;
    }

    public void setBornTimestamp(long bornTimestamp) {
        this.bornTimestamp = bornTimestamp;
    }

    public SocketAddress getBornHost() {
        return bornHost;
    }

    public void setBornHost(SocketAddress bornHost) {
        this.bornHost = bornHost;
    }

    public String getBornHostString() {
        if (this.bornHost != null) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) this.bornHost;
            return inetSocketAddress.getAddress().getHostAddress();
        }

        return null;
    }

    public String getBornHostNameString() {
        if (this.bornHost != null) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) this.bornHost;
            return inetSocketAddress.getAddress().getHostName();
        }

        return null;
    }

    public long getStoreTimestamp() {
        return storeTimestamp;
    }

    public void setStoreTimestamp(long storeTimestamp) {
        this.storeTimestamp = storeTimestamp;
    }

    public SocketAddress getStoreHost() {
        return storeHost;
    }

    public void setStoreHost(SocketAddress storeHost) {
        this.storeHost = storeHost;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public int getSysFlag() {
        return sysFlag;
    }

    public void setSysFlag(int sysFlag) {
        this.sysFlag = sysFlag;
    }

    public int getBodyCRC() {
        return bodyCRC;
    }

    public void setBodyCRC(int bodyCRC) {
        this.bodyCRC = bodyCRC;
    }

    public long getQueueOffset() {
        return queueOffset;
    }

    public void setQueueOffset(long queueOffset) {
        this.queueOffset = queueOffset;
    }

    public long getCommitLogOffset() {
        return commitLogOffset;
    }

    public void setCommitLogOffset(long physicOffset) {
        this.commitLogOffset = physicOffset;
    }

    public int getStoreSize() {
        return storeSize;
    }

    public void setStoreSize(int storeSize) {
        this.storeSize = storeSize;
    }

    public int getReconsumeTimes() {
        return reconsumeTimes;
    }

    public void setReconsumeTimes(int reconsumeTimes) {
        this.reconsumeTimes = reconsumeTimes;
    }

    public long getPreparedTransactionOffset() {
        return preparedTransactionOffset;
    }

    public void setPreparedTransactionOffset(long preparedTransactionOffset) {
        this.preparedTransactionOffset = preparedTransactionOffset;
    }

    @Override
    public String toString() {
        return "MessageExt [queueId=" + queueId + ", storeSize=" + storeSize + ", queueOffset=" + queueOffset
            + ", sysFlag=" + sysFlag + ", bornTimestamp=" + bornTimestamp + ", bornHost=" + bornHost
            + ", storeTimestamp=" + storeTimestamp + ", storeHost=" + storeHost + ", msgId=" + msgId
            + ", commitLogOffset=" + commitLogOffset + ", bodyCRC=" + bodyCRC + ", reconsumeTimes="
            + reconsumeTimes + ", preparedTransactionOffset=" + preparedTransactionOffset
            + ", toString()=" + super.toString() + "]";
    }
}
