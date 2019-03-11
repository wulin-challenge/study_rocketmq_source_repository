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
package org.apache.rocketmq.namesrv.routeinfo;

import io.netty.channel.Channel;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.ChannelEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * <p> 1. 可以看到这个类实现了ChannelEventListener接口，除了onChannelConnect外，
 * 其余各个方法均委托给namesrvController的routeInfoManager的onChannelDestroy方法
 * 
 * <p>2. 这里需要netty的一些基础，简单来说每一个broker与namesrv通过一个“通道”channel进行“沟通”。
 * namesrv通过监测这些通道是否发生某些事件，去做出相应的变动。可以点进routeInfoManager的onChannelDestroy方法看看，
 * 对于宕机的broker是如何处理的。
 *
 *<p> 说明 : 该类负责Broker连接事件的处理,实现了ChannelEventListener,主要用来管理RouteInfoManager的brokerLiveTable。
 * BrokerHousekeepingService实现了ChannelEventListener接口, 
 * 并且NettyRemotingServer启动时会启动BrokerHousekeepingService。
 * 
 * <p> BrokerHousekeepingService会对连接事件, 连接关闭事件, 异常事件,闲置事件进行处理，进而来判断管理存活的broker。

 */
public class BrokerHousekeepingService implements ChannelEventListener {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);
    private final NamesrvController namesrvController;

    public BrokerHousekeepingService(NamesrvController namesrvController) {
        this.namesrvController = namesrvController;
    }

    @Override
    public void onChannelConnect(String remoteAddr, Channel channel) {
    }

    @Override
    public void onChannelClose(String remoteAddr, Channel channel) {
        this.namesrvController.getRouteInfoManager().onChannelDestroy(remoteAddr, channel);
    }

    @Override
    public void onChannelException(String remoteAddr, Channel channel) {
        this.namesrvController.getRouteInfoManager().onChannelDestroy(remoteAddr, channel);
    }

    @Override
    public void onChannelIdle(String remoteAddr, Channel channel) {
        this.namesrvController.getRouteInfoManager().onChannelDestroy(remoteAddr, channel);
    }
}
