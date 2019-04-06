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

package org.apache.rocketmq.remoting;

/**
 * 基于netty实现远程客户端和远程服务端的公共接口,该接口由如下两个实现接口
 * <p> 1. RemotingClient : netty实现的远程客户端接口
 * <p> 2. RemotingServer : netty实现的远程服务端接口
 *
 */
public interface RemotingService {
    void start();

    void shutdown();

    /**
     * 注册rpc回调钩子
     * @param rpcHook rpc回调钩子
     */
    void registerRPCHook(RPCHook rpcHook);
}
