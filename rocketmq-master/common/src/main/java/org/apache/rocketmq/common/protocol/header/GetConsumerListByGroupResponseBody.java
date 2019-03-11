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

package org.apache.rocketmq.common.protocol.header;

import java.util.List;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;

/**
 * 消费者端通过远程调用 broker,然后使用 group 查找 消费者Id列表
 *
 */
public class GetConsumerListByGroupResponseBody extends RemotingSerializable {
	
	/**
	 * 消费者Id列表
	 * <p> 说明: 一个单元数据构成: 消费者Ip@消费者的pid , 例如: 192.168.199.1@18004
	 */
    private List<String> consumerIdList;

    public List<String> getConsumerIdList() {
        return consumerIdList;
    }

    public void setConsumerIdList(List<String> consumerIdList) {
        this.consumerIdList = consumerIdList;
    }
}
