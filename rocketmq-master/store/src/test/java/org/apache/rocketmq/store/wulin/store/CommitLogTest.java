package org.apache.rocketmq.store.wulin.store;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;

import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.store.CommitLog;
import org.apache.rocketmq.store.DefaultMessageStore;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.MappedFile;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.SelectMappedBufferResult;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Before;
import org.junit.Test;

/**
 * 提交日志测试
 *
 */
public class CommitLogTest {
	
	CommitLog commitLog;
	DefaultMessageStore messageStore;
	
	@Before
	public void init() throws IOException{
		 MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
	        messageStoreConfig.setMapedFileSizeCommitLog(1024 * 8);
	        messageStoreConfig.setMapedFileSizeConsumeQueue(1024 * 4);
	        messageStoreConfig.setMaxHashSlotNum(100);
	        messageStoreConfig.setMaxIndexNum(100 * 10);
	        messageStoreConfig.setStorePathRootDir(System.getProperty("user.home") + File.separator + "unitteststore");
	        messageStoreConfig.setStorePathCommitLog(System.getProperty("user.home") + File.separator + "unitteststore" + File.separator + "commitlog");
	        //too much reference
	        messageStore = new DefaultMessageStore(messageStoreConfig, null, null, null);
	        commitLog = new CommitLog(messageStore);
	}

	@Test
	public void testPutMessage() throws UnsupportedEncodingException{
		MessageExtBrokerInner msgbi = new MessageExtBrokerInner();
		msgbi.setTopic("CommitLogTest");
		msgbi.setTags("TagA");
		msgbi.setBody(("Hello RocketMQ!哈哈!!").getBytes(RemotingHelper.DEFAULT_CHARSET));
		msgbi.setBornHost(new InetSocketAddress("127.0.0.1", 123));
		msgbi.setStoreHost(new InetSocketAddress("127.0.0.1", 124));
		commitLog.putMessage(msgbi);
	}
	
	@Test
	public void testGetMessage() throws Exception{
//		commitLog.load();
		messageStore.load();
		messageStore.start();
		GetMessageResult message2 = messageStore.getMessage(null, "CommitLogTest", 0, 0, 1, null);
		SelectMappedBufferResult message = commitLog.getMessage(0, 300);
		
		
		
		System.out.println(message);
		System.out.println(message2);
	}
}
