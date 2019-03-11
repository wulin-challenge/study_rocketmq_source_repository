package org.apache.rocketmq.example.simple.wulin;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class SyncProducer {
    public static void main(String[] args) throws Exception {
        //Instantiate with a producer group name.
        DefaultMQProducer producer = new DefaultMQProducer("please_rename_unique_group_name_producer");
        producer.setNamesrvAddr("127.0.0.1:9876");
        //Launch the instance.
        producer.start();
//        MessageExt viewMessage = producer.viewMessage("C0A8C70100002A9F000000000000331F");
//        String body = new String(viewMessage.getBody(),RemotingHelper.DEFAULT_CHARSET);
      
        for (int i = 0; i < 5; i++) {
            //Create a message instance, specifying topic, tag and message body.
        	Thread.sleep(1000);
            Message msg = new Message("TopicTest",
            		"TagA",
            		("Hello RocketMQ!哈哈!! "+i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            
            //Call send message to deliver message to one of brokers.
            SendResult sendResult = producer.send(msg);
            String offsetMsgId = sendResult.getOffsetMsgId();
            

            System.out.printf("%s%n", sendResult);
        }
        
        
        //Shut down once the producer instance is not longer in use.
        producer.shutdown();
    }
}