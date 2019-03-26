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
package org.apache.rocketmq.store.index;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.store.MappedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexFile {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);
    
    /**
     * hashSlot的大小
     */
    private static int hashSlotSize = 4;
    private static int indexSize = 20;
    
    /**
     * 非法索引
     */
    private static int invalidIndex = 0;
    /**
     * hash槽数量
     */
    private final int hashSlotNum;
    
    /**
     * 索引数量/最大条目数
     * <p> 注意:一个索引文件最多能创建的索引数量
     */
    private final int indexNum;
    private final MappedFile mappedFile;
    private final FileChannel fileChannel;
    
    /**
     * 该 mappedByteBuffer 为 slot Table
     * <p> ( 里面的每一项保存的是这个 topic-key 是第几个索引；根据 topic-key 的 Hash 值除以 500W 取余得到这个 Slot Table 的序列号，然后将此 
     * 索引的顺序个数存入此 Table 中。 Slottable 的位置（ absSlotPos）的计算公式： 40+keyHash%（ 500W） *4；
     */
    private final MappedByteBuffer mappedByteBuffer;
    private final IndexHeader indexHeader;

    public IndexFile(final String fileName, final int hashSlotNum, final int indexNum,
        final long endPhyOffset, final long endTimestamp) throws IOException {
        int fileTotalSize =
            IndexHeader.INDEX_HEADER_SIZE + (hashSlotNum * hashSlotSize) + (indexNum * indexSize);
        this.mappedFile = new MappedFile(fileName, fileTotalSize);
        this.fileChannel = this.mappedFile.getFileChannel();
        this.mappedByteBuffer = this.mappedFile.getMappedByteBuffer();
        this.hashSlotNum = hashSlotNum;
        this.indexNum = indexNum;

        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        this.indexHeader = new IndexHeader(byteBuffer);

        if (endPhyOffset > 0) {
            this.indexHeader.setBeginPhyOffset(endPhyOffset);
            this.indexHeader.setEndPhyOffset(endPhyOffset);
        }

        if (endTimestamp > 0) {
            this.indexHeader.setBeginTimestamp(endTimestamp);
            this.indexHeader.setEndTimestamp(endTimestamp);
        }
    }

    public String getFileName() {
        return this.mappedFile.getFileName();
    }

    public void load() {
        this.indexHeader.load();
    }

    public void flush() {
        long beginTime = System.currentTimeMillis();
        if (this.mappedFile.hold()) {
            this.indexHeader.updateByteBuffer();
            this.mappedByteBuffer.force();
            this.mappedFile.release();
            log.info("flush index file eclipse time(ms) " + (System.currentTimeMillis() - beginTime));
        }
    }

    /**
     * 判断该索引文件是否已经写满
     * @return 若已经写满则返回true,否则返回false
     */
    public boolean isWriteFull() {
    	//如果 索引头部的索引个数大于等于indexNum(最大索引个数),则代表该文件的索引个数已经写满
        return this.indexHeader.getIndexCount() >= this.indexNum;
    }

    public boolean destroy(final long intervalForcibly) {
        return this.mappedFile.destroy(intervalForcibly);
    }

    /**
     * 向index文件写入索引消息
     * @param key - 消息索引key
     * @param phyOffset - 消息物理偏移量
     * @param storeTimestamp - 消息存储时间戳
     * @return
     */
    public boolean putKey(final String key, final long phyOffset, final long storeTimestamp) {
    	//如果当前已使用条目大于等于允许最大条目数时,则返回fasle,表示当前索引文件已写满.
        if (this.indexHeader.getIndexCount() < this.indexNum) {
        	//将索引key进行hash的hash方法
            int keyHash = indexKeyHashMethod(key);
            
            //如果当前索引文件未写满则根据key算出key的hashcode,然后keyHash对hash槽数量取余定位到hasbcod巳对应的hash槽下标
            int slotPos = keyHash % this.hashSlotNum;
            
            /**
             * hashcode 对应的 hash 槽的物理地址为 IndexHeader 头部（40 字节）加上下标乘以每个 hash 槽的大小（4 字节） 。
             */
            //首先根据 key 的 Hash 值计算出 absSlotPos 值；(absSlotPos:绝对哈希槽位置)
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;

            FileLock fileLock = null;

            try {

                // fileLock = this.fileChannel.lock(absSlotPos, hashSlotSize,
                // false);
            	
            	/**
            	 * 根据 absSlotPos值作为 index文件的读取开始偏移量读取 4个字节的值，即为了避免 KEY 值的 hash 冲突，
            	 * 将之前的 key 值的索引顺序数给冲突了，故先从 slot Table 中的取当前存储的索引顺序数，
            	 * 若该值小于零或者大于当前的索引总数（ IndexHeader 的 indexCount 值）则视为无效，即置为 0；否则取出该位置的值，
            	 * 放入当前写入索引消息的 Index Linked 的 slotValue 字段中；
            	 */
                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);
                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()) {
                    slotValue = invalidIndex;
                }

                //计算当前存时间距离第一个索引消息落在 Broker 的时间戳beginTimestamp 的差值，
                //放入当前写入索引消息的 Index Linked 的 timeOffset字段中
                long timeDiff = storeTimestamp - this.indexHeader.getBeginTimestamp();

                timeDiff = timeDiff / 1000;

                if (this.indexHeader.getBeginTimestamp() <= 0) {
                    timeDiff = 0;
                } else if (timeDiff > Integer.MAX_VALUE) {
                    timeDiff = Integer.MAX_VALUE;
                } else if (timeDiff < 0) {
                    timeDiff = 0;
                }

                //计算 absIndexPos 值，然后根据数据结构上值写入 Index Linked 中；
                int absIndexPos =
                    IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize
                        + this.indexHeader.getIndexCount() * indexSize;

                this.mappedByteBuffer.putInt(absIndexPos, keyHash);
                this.mappedByteBuffer.putLong(absIndexPos + 4, phyOffset);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8, (int) timeDiff);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8 + 4, slotValue);

                //将索引总数写入 slot Table 的 absSlotPos 位置；
                this.mappedByteBuffer.putInt(absSlotPos, this.indexHeader.getIndexCount());

                //若为第一个索引，则更新 IndexHeader 的 beginTimestamp 和beginPhyOffset 字段；
                if (this.indexHeader.getIndexCount() <= 1) {
                    this.indexHeader.setBeginPhyOffset(phyOffset);
                    this.indexHeader.setBeginTimestamp(storeTimestamp);
                }

                //更新 IndexHeader 的 endTimestamp 和 endPhyOffset 字段；将 IndexHeader 的 hashSlotCount 和 indexCount 字段值加 1；
                this.indexHeader.incHashSlotCount();
                this.indexHeader.incIndexCount();
                this.indexHeader.setEndPhyOffset(phyOffset);
                this.indexHeader.setEndTimestamp(storeTimestamp);

                return true;
            } catch (Exception e) {
                log.error("putKey exception, Key: " + key + " KeyHashCode: " + key.hashCode(), e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }
            }
        } else {
            log.warn("Over index file capacity: index count = " + this.indexHeader.getIndexCount()
                + "; index max num = " + this.indexNum);
        }

        return false;
    }

    /**
     * 将索引key进行hash的hash方法
     * @param key - 索引key
     * @return 返回索引key的hash值
     */
    public int indexKeyHashMethod(final String key) {
        int keyHash = key.hashCode();
        int keyHashPositive = Math.abs(keyHash);
        if (keyHashPositive < 0)
            keyHashPositive = 0;
        return keyHashPositive;
    }

    public long getBeginTimestamp() {
        return this.indexHeader.getBeginTimestamp();
    }

    public long getEndTimestamp() {
        return this.indexHeader.getEndTimestamp();
    }

    public long getEndPhyOffset() {
        return this.indexHeader.getEndPhyOffset();
    }

    public boolean isTimeMatched(final long begin, final long end) {
        boolean result = begin < this.indexHeader.getBeginTimestamp() && end > this.indexHeader.getEndTimestamp();
        result = result || (begin >= this.indexHeader.getBeginTimestamp() && begin <= this.indexHeader.getEndTimestamp());
        result = result || (end >= this.indexHeader.getBeginTimestamp() && end <= this.indexHeader.getEndTimestamp());
        return result;
    }

    /**
     * 根据 索引 key 查找消息
     * @param phyOffsets 查找到的消息物理偏移量.
     * @param key 索引 key 
     * @param maxNum 本次查找最大消息条数
     * @param begin 开始时间戳 。
     * @param end 结束时间戳 。
     * @param lock
     */
    public void selectPhyOffset(final List<Long> phyOffsets, final String key, final int maxNum,
        final long begin, final long end, boolean lock) {
        if (this.mappedFile.hold()) {
        	//将索引key进行hash的hash方法
            int keyHash = indexKeyHashMethod(key);
            //然后keyHash对hash槽数量取余定位到hashcode对应的hash槽下标
            int slotPos = keyHash % this.hashSlotNum;
            
            //hashcode对应的hash槽的物理地址为IndexHeader头部(40字节)加上下标乘以每个hash槽的大小(4字节).
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;

            FileLock fileLock = null;
            try {
                if (lock) {
                    // fileLock = this.fileChannel.lock(absSlotPos,
                    // hashSlotSize, true);
                }

                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);
                // if (fileLock != null) {
                // fileLock.release();
                // fileLock = null;
                // }

                //如果对应的Hash槽中存储的数据小于1或大于当前索引条目个数则表示该HashCode没有对应的条目,直接返回.
                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()
                    || this.indexHeader.getIndexCount() <= 1) {
                } else {
                	
                	/*
                	 * 由于会存在hash冲突,根据slotValue定位该hash槽最新的一个Item条目,将存储的物理偏移加入
                	 * 到phyOffsets中,然后继续验证Item条目中存储的上一个Index下标,如果大于等于l并且小于最大条目数,
                	 * 则继续查找,否则结束查找.
                	 */
                    for (int nextIndexToRead = slotValue; ; ) {
                        if (phyOffsets.size() >= maxNum) {
                            break;
                        }

                        //根据Index下标定位到条目的起始物理偏移量,然后依次读取hashcode、物理偏移量、时间差、上一个条目的Index下标.
                        int absIndexPos =
                            IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize
                                + nextIndexToRead * indexSize;

                        int keyHashRead = this.mappedByteBuffer.getInt(absIndexPos);
                        long phyOffsetRead = this.mappedByteBuffer.getLong(absIndexPos + 4);

                        long timeDiff = (long) this.mappedByteBuffer.getInt(absIndexPos + 4 + 8);
                        int prevIndexRead = this.mappedByteBuffer.getInt(absIndexPos + 4 + 8 + 4);

                        /*
                         * 如果存储的时间差小于0,则直接结束；如果hashcode匹配并且消息存储时间介于待查找
                         * 时间start、end之间则将消息物理偏移量加入到phyOffsets,并验证条目的前一个Index索引,
                         * 如果索引大于等于l并且小于Index条目数,则继续查找,否则结束整个查找.
                         */
                        if (timeDiff < 0) {
                            break;
                        }

                        timeDiff *= 1000L;

                        long timeRead = this.indexHeader.getBeginTimestamp() + timeDiff;
                        boolean timeMatched = (timeRead >= begin) && (timeRead <= end);

                        if (keyHash == keyHashRead && timeMatched) {
                            phyOffsets.add(phyOffsetRead);
                        }

                        if (prevIndexRead <= invalidIndex
                            || prevIndexRead > this.indexHeader.getIndexCount()
                            || prevIndexRead == nextIndexToRead || timeRead < begin) {
                            break;
                        }

                        nextIndexToRead = prevIndexRead;
                    }
                }
            } catch (Exception e) {
                log.error("selectPhyOffset exception ", e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }

                this.mappedFile.release();
            }
        }
    }
}
