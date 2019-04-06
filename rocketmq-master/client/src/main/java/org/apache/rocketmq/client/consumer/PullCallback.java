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
package org.apache.rocketmq.client.consumer;

/**
 * Async message pulling interface
 * 
 * <p> 异步拉取消息接口
 */
public interface PullCallback {
	
	/**
	 * 成功拉取消息的回调方法
	 * @param pullResult 要拉取消息的请求
	 */
    void onSuccess(final PullResult pullResult);

    /**
     * 拉取过程中出现异常的回调方法
     * @param e
     */
    void onException(final Throwable e);
}
