package org.apache.rocketmq.example.simple.wulin.syncOrAsync;

import java.io.UnsupportedEncodingException;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

public class SyncProducerTest {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException, RemotingException, MQBrokerException, InterruptedException {
    	  DefaultMQProducer producer = new DefaultMQProducer("sync_message_group");
          producer.setNamesrvAddr("localhost:9876");
          //Launch the instance.
          producer.start();
          //Create a message instance, specifying topic, tag and message body.
          Message msg = new Message("test_sync_topic","5100",
          		("Hello RocketMQ!哈哈!! ").getBytes(RemotingHelper.DEFAULT_CHARSET));
          
          //Call send message to deliver message to one of brokers.
          SendResult sendResult = producer.send(msg);
          producer.send(msg, new SendCallback(){

			@Override
			public void onSuccess(SendResult sendResult) {
//				System.out.println();
//				sendResult.getMessageQueue()args;
			}

			@Override
			public void onException(Throwable e) {
				
			}
          });
          System.out.printf("%s%n", sendResult);
          //Shut down once the producer instance is not longer in use.
          producer.shutdown();
    }
    // 在应用退出前，可以销毁Producer对象
    // 注意：如果不销毁也没有问题
//        producer.shutdown();

}