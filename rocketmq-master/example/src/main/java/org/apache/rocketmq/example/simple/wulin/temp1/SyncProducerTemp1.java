package org.apache.rocketmq.example.simple.wulin.temp1;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class SyncProducerTemp1 {
    public static void main(String[] args) throws Exception {
        //Instantiate with a producer group name.
        DefaultMQProducer producer = new DefaultMQProducer("news_default_rocketmq_group");
        producer.setNamesrvAddr("192.168.0.107:9876;192.168.0.108:9876");
        //Launch the instance.
        producer.start();
        for (int i = 0; i < 10; i++) {
            //Create a message instance, specifying topic, tag and message body.
            Message msg = new Message("dev_5109_1__1",
            		"xxappId",
            		("Hello RocketMQ!哈哈!! "+i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            
            //Call send message to deliver message to one of brokers.
            SendResult sendResult = producer.send(msg);
            System.out.printf("%s%n", sendResult);
        }
        //Shut down once the producer instance is not longer in use.
        producer.shutdown();
    }
}