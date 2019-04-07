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
package org.apache.rocketmq.filtersrv;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.filtersrv.RegisterFilterServerRequestHeader;
import org.apache.rocketmq.common.protocol.header.filtersrv.RegisterFilterServerResponseHeader;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public class FilterServerOuterAPI {
    private final RemotingClient remotingClient;

    public FilterServerOuterAPI() {
        this.remotingClient = new NettyRemotingClient(new NettyClientConfig());
    }

    public void start() {
        this.remotingClient.start();
    }

    public void shutdown() {
        this.remotingClient.shutdown();
    }

    /**
     * 注册filter到broker上
     * 
     * <p> 详细: FilterServer在启动时向Broker注册自己,在Broker端维护该Broker的FilterServer信息, 
     * 并定时监控FilterServer的状态,然后Broker通过与所有NameServer的心跳包向NameServer注册Broker上存储的FilterServer列表, 
     * 指引消息消费者正确从FilterServer上拉取消息.
     * 
     * @param brokerAddr broker的地址
     * @param filterServerAddr filterServer的地址
     * @return
     * @throws RemotingCommandException
     * @throws RemotingConnectException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws InterruptedException
     * @throws MQBrokerException
     */
    public RegisterFilterServerResponseHeader registerFilterServerToBroker(
        final String brokerAddr,
        final String filterServerAddr
    ) throws RemotingCommandException, RemotingConnectException, RemotingSendRequestException,
        RemotingTimeoutException, InterruptedException, MQBrokerException {
        RegisterFilterServerRequestHeader requestHeader = new RegisterFilterServerRequestHeader();
        requestHeader.setFilterServerAddr(filterServerAddr);
        RemotingCommand request =
            RemotingCommand.createRequestCommand(RequestCode.REGISTER_FILTER_SERVER, requestHeader);

        RemotingCommand response = this.remotingClient.invokeSync(brokerAddr, request, 3000);
        assert response != null;
        switch (response.getCode()) {
            case ResponseCode.SUCCESS: {
                RegisterFilterServerResponseHeader responseHeader =
                    (RegisterFilterServerResponseHeader) response
                        .decodeCommandCustomHeader(RegisterFilterServerResponseHeader.class);

                return responseHeader;
            }
            default:
                break;
        }

        throw new MQBrokerException(response.getCode(), response.getRemark());
    }
}
