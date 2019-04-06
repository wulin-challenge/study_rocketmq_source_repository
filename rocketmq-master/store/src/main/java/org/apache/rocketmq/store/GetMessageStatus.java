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
package org.apache.rocketmq.store;

public enum GetMessageStatus {

	/**
	 * 找到消息
	 */
    FOUND,
    
    /**
     * 没有匹配的消息
     */
    NO_MATCHED_MESSAGE,

    /**
     * 消息存放在下个commitlog文件中
     */
    MESSAGE_WAS_REMOVING,

    /**
     * 消息物理偏移量为空OFFSET_OVERFLOW_ONE:offset越界一个
     * <p> 是根据Consumequeue的偏移量没有找到内容,将偏移量定位到下一个ConsumeQueue,
     * 其实就是offset＋(一个ConsumeQueue包含多少个条目=MappedFileSize/20).
     */
    OFFSET_FOUND_NULL,

    OFFSET_OVERFLOW_BADLY,

    /**
     * 待拉取offset等于消息队列最大的偏移量,如果有新的消息到达,此时会创建一个新的ConsumeQueue文件,
     * 按照上一个ConsueQueue的最大偏移量就是下一个文件的起始偏移量,所以如果按照该offset第二次拉取消息时能成功.
     */
    OFFSET_OVERFLOW_ONE,

    OFFSET_TOO_SMALL,

    /**
     * 未找到队列NO_MESSAGE_IN＿QUEUE:队列中未包含消息OFFSET_OVERFLOW_BADLY:offset越界OFFSET_TOO_SMALL:offset未在消息队列中
     */
    NO_MATCHED_LOGIC_QUEUE,

    NO_MESSAGE_IN_QUEUE,
}
