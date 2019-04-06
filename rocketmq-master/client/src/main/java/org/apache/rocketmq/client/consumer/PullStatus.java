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
 * 拉消息状态
 *
 */
public enum PullStatus {
    /**
     * Founded
     * 
     * <p> 找消息
     */
    FOUND,
    /**
     * No new message can be pull
     * 
     * <p> 没有新消息
     */
    NO_NEW_MSG,
    /**
     * Filtering results can not match
     * 
     * <p> 没有匹配消息）
     */
    NO_MATCHED_MSG,
    /**
     * Illegal offset,may be too big or too small
     * 
     * <p> 非法偏移量，可能太大或太小
     */
    OFFSET_ILLEGAL
}
