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

import org.apache.rocketmq.common.UtilAll;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageDecoderTest {

    @Test
    public void testDecodeProperties() {
        MessageExt messageExt = new MessageExt();

        messageExt.setMsgId("645100FA00002A9F000000489A3AA09E");
        messageExt.setTopic("abc");
        messageExt.setBody("hello!q!".getBytes());
        try {
            messageExt.setBornHost(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        } catch (UnknownHostException e) {
            e.printStackTrace();
            assertThat(Boolean.FALSE).isTrue();
        }
        messageExt.setBornTimestamp(System.currentTimeMillis());
        messageExt.setCommitLogOffset(123456);
        messageExt.setPreparedTransactionOffset(0);
        messageExt.setQueueId(0);
        messageExt.setQueueOffset(123);
        messageExt.setReconsumeTimes(0);
        try {
            messageExt.setStoreHost(new InetSocketAddress(InetAddress.getLocalHost(), 0));
        } catch (UnknownHostException e) {
            e.printStackTrace();
            assertThat(Boolean.FALSE).isTrue();
        }

        messageExt.putUserProperty("a", "123");
        messageExt.putUserProperty("b", "hello");
        messageExt.putUserProperty("c", "3.14");

        byte[] msgBytes = new byte[0];
        try {
            msgBytes = MessageDecoder.encode(messageExt, false);
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(Boolean.FALSE).isTrue();
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(msgBytes.length);
        byteBuffer.put(msgBytes);

        Map<String, String> properties = MessageDecoder.decodeProperties(byteBuffer);

        assertThat(properties).isNotNull();
        assertThat("123").isEqualTo(properties.get("a"));
        assertThat("hello").isEqualTo(properties.get("b"));
        assertThat("3.14").isEqualTo(properties.get("c"));
    }
    
    @Test
    public void decode() throws UnknownHostException{
    	byte i = (byte)(192);
    	String msgId = "C0A8C70100002A9F000000000000331F";
    	
//    	MessageId decodeMessageId = MessageDecoder.decodeMessageId(msgId);
    	
    	MessageId decodeMessageId = decodeMessageId(msgId);
    	
    	System.out.println(decodeMessageId);
    }
    
    @Test
    public void testIpEncoded() throws UnknownHostException{
    	InetAddress localHost = InetAddress.getByName("192.168.0.121");
    	
    	byte[] address = localHost.getAddress();
    	
    	InetAddress byAddress = InetAddress.getByAddress(address);
    	
    	String ip = UtilAll.bytes2string(address);
    	
    	byte[] string2bytes = UtilAll.string2bytes("000000000000331F");
    	
    	ByteBuffer wrap = ByteBuffer.wrap(string2bytes);
    	
    	int int1 = wrap.getInt();
    	
    	String port = UtilAll.bytes2string(string2bytes);
    	
    	System.out.println();
    }
    
    public static MessageId decodeMessageId(final String msgId) throws UnknownHostException {
        SocketAddress address;
        long offset;

        byte[] ip = UtilAll.string2bytes(msgId.substring(0, 8));
        
        byte[] port = UtilAll.string2bytes(msgId.substring(8, 16));
        ByteBuffer bb = ByteBuffer.wrap(port);
        int portInt = bb.getInt(0);
        address = new InetSocketAddress(InetAddress.getByAddress(ip), portInt);

        // offset
        byte[] data = UtilAll.string2bytes(msgId.substring(16, 32));
        bb = ByteBuffer.wrap(data);
        offset = bb.getLong(0);

        return new MessageId(address, offset);
    }

}
