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
package org.apache.rocketmq.client.consumer.store;

/**
 * 读取偏移量方式
 * @author ThinkPad
 *
 */
public enum ReadOffsetType {
    /**
     * From memory - 从内存中读取
     */
    READ_FROM_MEMORY,
    /**
     * From storage - 从存储中读取
     */
    READ_FROM_STORE,
    /**
     * From memory,then from storage - 先从内存中读取,若不成功,则重存储中读取
     */
    MEMORY_FIRST_THEN_STORE;
}
