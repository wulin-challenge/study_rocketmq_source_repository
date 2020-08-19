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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageExtBatch;
import org.apache.rocketmq.store.config.FlushDiskType;
import org.apache.rocketmq.store.util.LibC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.nio.ch.DirectBuffer;

/**
 * 文件存储的核心映射文件,即内存映射文件
 *
 */
public class MappedFile extends ReferenceResource {
	/**
	 * 操作系统每页大小,默认页大小为4k
	 */
    public static final int OS_PAGE_SIZE = 1024 * 4;
    protected static final Logger log = LoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    /**
     * JVM中映射的虚拟内存总大小
     */
    private static final AtomicLong TOTAL_MAPPED_VIRTUAL_MEMORY = new AtomicLong(0);

    /**
     * JVM中mmap(memory mapping 内存映射)的数量,即 当前 JVM实例中 MappedFile对象个数
     */
    private static final AtomicInteger TOTAL_MAPPED_FILES = new AtomicInteger(0);
    /**
     * 当前写文件的位置
     * <p> 当前该文件的写指针,从0开始（内存映射文件中的写指针） 。
     */
    protected final AtomicInteger wrotePosition = new AtomicInteger(0);
    
    /**
     * 当前文件的已经提交指针，如果开启 transientStorePoolEnable,则数据会存储在TransientStorePool中,
     * 然后提交到内存映射ByteBuffer中,再刷写到磁盘.
     */
    //ADD BY ChenYang
    protected final AtomicInteger committedPosition = new AtomicInteger(0);
    
    /**
     * 刷写到磁盘指针，该指针之前的数据已经持久化到磁盘中 。(即已经刷盘的指针位置)
     */
    private final AtomicInteger flushedPosition = new AtomicInteger(0);
    /**
     * 映射文件的大小
     */
    protected int fileSize;
    /**
     * 映射的fileChannel对象,即文件管道
     */
    protected FileChannel fileChannel;
    /**
     * Message will put to here first, and then reput to FileChannel if writeBuffer is not null.
     * 
     * <p> 消息将首先放在此处，然后如果writeBuffer不为null则重新输出到FileChannel。
     * 
     * <p> 堆内存 ByteBuffer,如果不为空,数据首先将存储在该Buffer中,然后提交到MappedFile对应的内存映射文件Buffer. 
     * transientStorePoolEnable为true时不为空.
     */
    protected ByteBuffer writeBuffer = null;
    
    /**
     * 堆内存池,transientStorePoolEnable为true时启用.
     */
    protected TransientStorePool transientStorePool = null;
    /**
     * 映射的文件名
     */
    private String fileName;
    /**
     * 映射的起始偏移量
     */
    private long fileFromOffset;
    /**
     * 映射的物理文件
     */
    private File file;
    /**
     * 物理文件对应的内存映射 Buffer。
     */
    private MappedByteBuffer mappedByteBuffer;
    /**
     * 文件最后一次内容写入时间 。
     */
    private volatile long storeTimestamp = 0;
    /**
     * <p> 是否是 MappedFileQueue 队列中第一个文件 。
     */
    private boolean firstCreateInQueue = false;

    public MappedFile() {
    }

    public MappedFile(final String fileName, final int fileSize) throws IOException {
        init(fileName, fileSize);
    }

    public MappedFile(final String fileName, final int fileSize,
        final TransientStorePool transientStorePool) throws IOException {
        init(fileName, fileSize, transientStorePool);
    }

    public static void ensureDirOK(final String dirName) {
        if (dirName != null) {
            File f = new File(dirName);
            if (!f.exists()) {
                boolean result = f.mkdirs();
                log.info(dirName + " mkdir " + (result ? "OK" : "Failed"));
            }
        }
    }

    public static void clean(final ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect() || buffer.capacity() == 0)
            return;
        invoke(invoke(viewed(buffer), "cleaner"), "clean");
    }

    private static Object invoke(final Object target, final String methodName, final Class<?>... args) {
        return AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    Method method = method(target, methodName, args);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static Method method(Object target, String methodName, Class<?>[] args)
        throws NoSuchMethodException {
        try {
            return target.getClass().getMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            return target.getClass().getDeclaredMethod(methodName, args);
        }
    }

    private static ByteBuffer viewed(ByteBuffer buffer) {
        String methodName = "viewedBuffer";

        Method[] methods = buffer.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals("attachment")) {
                methodName = "attachment";
                break;
            }
        }

        ByteBuffer viewedBuffer = (ByteBuffer) invoke(buffer, methodName);
        if (viewedBuffer == null)
            return buffer;
        else
            return viewed(viewedBuffer);
    }

    public static int getTotalMappedFiles() {
        return TOTAL_MAPPED_FILES.get();
    }

    public static long getTotalMappedVirtualMemory() {
        return TOTAL_MAPPED_VIRTUAL_MEMORY.get();
    }

    public void init(final String fileName, final int fileSize,
        final TransientStorePool transientStorePool) throws IOException {
        init(fileName, fileSize);
        
        //如果 transientstorePooIEnabIe 为 true ，则初始化 MappedFile 的 writeBuffer
        this.writeBuffer = transientStorePool.borrowBuffer();
        this.transientStorePool = transientStorePool;
    }

    private void init(final String fileName, final int fileSize) throws IOException {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.file = new File(fileName);
        
        //初始化 fileFromOffset 为文件名，也就是文件名代表该文件的起始偏移量
        this.fileFromOffset = Long.parseLong(this.file.getName());
        boolean ok = false;

        ensureDirOK(this.file.getParent());

        try {
            this.fileChannel = new RandomAccessFile(this.file, "rw").getChannel();
            this.mappedByteBuffer = this.fileChannel.map(MapMode.READ_WRITE, 0, fileSize);
            TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(fileSize);
            TOTAL_MAPPED_FILES.incrementAndGet();
            ok = true;
        } catch (FileNotFoundException e) {
            log.error("create file channel " + this.fileName + " Failed. ", e);
            throw e;
        } catch (IOException e) {
            log.error("map file " + this.fileName + " Failed. ", e);
            throw e;
        } finally {
            if (!ok && this.fileChannel != null) {
                this.fileChannel.close();
            }
        }
    }

    /**
     * 得到映射文件的最后修改时间
     * @return
     */
    public long getLastModifiedTimestamp() {
        return this.file.lastModified();
    }

    public int getFileSize() {
        return fileSize;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public AppendMessageResult appendMessage(final MessageExtBrokerInner msg, final AppendMessageCallback cb) {
        return appendMessagesInner(msg, cb);
    }

    public AppendMessageResult appendMessages(final MessageExtBatch messageExtBatch, final AppendMessageCallback cb) {
        return appendMessagesInner(messageExtBatch, cb);
    }

    public AppendMessageResult appendMessagesInner(final MessageExt messageExt, final AppendMessageCallback cb) {
        assert messageExt != null;
        assert cb != null;

        int currentPos = this.wrotePosition.get();

        if (currentPos < this.fileSize) {
            ByteBuffer byteBuffer = writeBuffer != null ? writeBuffer.slice() : this.mappedByteBuffer.slice();
            byteBuffer.position(currentPos);
            AppendMessageResult result = null;
            if (messageExt instanceof MessageExtBrokerInner) {
                result = cb.doAppend(this.getFileFromOffset(), byteBuffer, this.fileSize - currentPos, (MessageExtBrokerInner) messageExt);
            } else if (messageExt instanceof MessageExtBatch) {
                result = cb.doAppend(this.getFileFromOffset(), byteBuffer, this.fileSize - currentPos, (MessageExtBatch) messageExt);
            } else {
                return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);
            }
            this.wrotePosition.addAndGet(result.getWroteBytes());
            this.storeTimestamp = result.getStoreTimestamp();
            return result;
        }
        log.error("MappedFile.appendMessage return null, wrotePosition: {} fileSize: {}", currentPos, this.fileSize);
        return new AppendMessageResult(AppendMessageStatus.UNKNOWN_ERROR);
    }

    /**
     * 得到映射的起始偏移量
     * @return
     */
    public long getFileFromOffset() {
        return this.fileFromOffset;
    }

    public boolean appendMessage(final byte[] data) {
    	//找出当前要写入位置
        int currentPos = this.wrotePosition.get();

        //如果当前位置加上要写入的数据大小小于等于文件大小，则说明剩余空间足够写入。
        if ((currentPos + data.length) <= this.fileSize) {
            try {
            	//则由内存对象 mappedByteBuffer 创建一个指向同一块内存的
            	//ByteBuffer 对象，并将内存对象的写入指针指向写入位置；然后将该二进制信
            	//息写入该内存对象，同时将 wrotePostion 值增加消息的大小；
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data));
            } catch (Throwable e) {
                log.error("Error occurred when append message to mappedFile.", e);
            }
            this.wrotePosition.addAndGet(data.length);
            return true;
        }

        return false;
    }

    /**
     * Content of data from offset to offset + length will be wrote to file.
     * 
     * <p> 从偏移量到偏移量+长度的数据内容将写入文件。
     *
     * @param offset The offset of the subarray to be used.
     * 
     * <p> 要使用的子数组的偏移量。
     * 
     * @param length The length of the subarray to be used.
     * 
     * <p> 要使用的子数组的长度。
     */
    public boolean appendMessage(final byte[] data, final int offset, final int length) {
        int currentPos = this.wrotePosition.get();

        if ((currentPos + length) <= this.fileSize) {
            try {
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data, offset, length));
            } catch (Throwable e) {
                log.error("Error occurred when append message to mappedFile.", e);
            }
            this.wrotePosition.addAndGet(length);
            return true;
        }

        return false;
    }

    /**
     * <p> 当前刷盘的位置
     * 
     * <p> 详解: 刷盘指的是将内存中的 数 据刷写到磁 盘 ,永久存储在磁盘中
     * 
     * <p> 知识扩展: flush不要翻译为刷新,应翻译为刷盘
     * 
     * <p>===================================================================================
     * <p> {@link MappedByteBuffer#force()}
     * <p> 将此缓冲区所做的内容更改强制写入包含映射文件的存储设备中。 
     * <P> 如果映射到此缓冲区中的文件位于本地存储设备上，那么当此方法返回时，可以保证自此缓冲区创建以来，或自最后一次调用此方法以来，
     * 已经将对缓冲区所做的所有更改写入到该设备。 如果文件不在本地设备上，则无法作出这样的保证。
     * <p>===================================================================================
     * 
     * @param flushLeastPages 刷盘最小页数
     * 
     * @return The current flushed position
     */
    public int flush(final int flushLeastPages) {
    	// 判断是否具备刷盘条件
        if (this.isAbleToFlush(flushLeastPages)) {
            if (this.hold()) {
            	//具有有效数据的最大位置
                int value = getReadPosition();

                try {
                    //We only append data to fileChannel or mappedByteBuffer, never both.
                	// 我们只将数据附加到fileChannel或mappedByteBuffer，而不是两者。
                    if (writeBuffer != null || this.fileChannel.position() != 0) {
                    	//强制将所有对此通道的文件更新写入包含该文件的存储设备中。
                        this.fileChannel.force(false);
                    } else {
                    	//将此缓冲区所做的内容更改强制写入包含映射文件的存储设备中
                        this.mappedByteBuffer.force();
                    }
                } catch (Throwable e) {
                    log.error("Error occurred when force data to disk.", e);
                }

                this.flushedPosition.set(value);
                this.release();
            } else {
                log.warn("in flush, hold failed, flush offset = " + this.flushedPosition.get());
                this.flushedPosition.set(getReadPosition());
            }
        }
        return this.getFlushedPosition();
    }

    /**
     * 内存映射文件的提交动作由该 MappedFile的commit方法实现 
     * @param commitLeastPages 为本次提交最小的页数，如果待提交数据不满commitLeastPages ，则不执行本次提交操作，待下次提交
     * @return
     */
    public int commit(final int commitLeastPages) {
        if (writeBuffer == null) {
            //no need to commit data to file channel, so just regard wrotePosition as committedPosition.
        	//无需将数据提交到文件通道，因此只需将writePosition视为committedPosition。
            return this.wrotePosition.get();
        }
        
        // 如果待提交数据不满commitLeastPages ，则不执行本次提交操作，待下次提交
        if (this.isAbleToCommit(commitLeastPages)) {
        	
        	//MappedFile的父类是ReferenceResource，该父类作用是记录MappedFile中的引用次数，为正表示资源可用，刷盘前加一，
        	//然后将wrotePosotion的值赋给committedPosition，再减一。
            if (this.hold()) {
                commit0(commitLeastPages);
                this.release();
            } else {
                log.warn("in commit, hold failed, commit offset = " + this.committedPosition.get());
            }
        }

        /*
         * writeBuff1巳r 如果为空，直接返回wrotePosition 指针 ,无须执行 commit 操作， 表 明 commit 操作主体是 writeBuffer 。
         */
        // All dirty data has been committed to FileChannel.
        // 所有脏数据都已提交给FileChannel。
        if (writeBuffer != null && this.transientStorePool != null && this.fileSize == this.committedPosition.get()) {
            this.transientStorePool.returnBuffer(writeBuffer);
            this.writeBuffer = null;
        }

        return this.committedPosition.get();
    }

    /**
     * 具体的提交实 现
     * 
     * <p> 详解: commit 的作用就是将 MappedFile#­writeBuffer 中 的数据提交到文件通道FileChannel中.
     * 
     * <p> 知识学习: ByteBuffer使用技巧：slice()方法创建一个共享缓存区,
     * 与原先的ByteBuffer共享内存但维护一套独立的指针(position、mark、limit)。
     * 
     * @param commitLeastPages 为本次提交最小的页数
     */
    protected void commit0(final int commitLeastPages) {
    	//当前该文件的写指针
        int writePos = this.wrotePosition.get();
        
        //上次提交位置
        int lastCommittedPosition = this.committedPosition.get();

        if (writePos - this.committedPosition.get() > 0) {
            try {
            	//首先创 建 writeBuffer 的共享缓存区
                ByteBuffer byteBuffer = writeBuffer.slice();
                
                //然后将新创 建 的 position 回退 到上一次提交的位置(committedPosition)
                byteBuffer.position(lastCommittedPosition);
                
                //设置 limit为wrotePosition(当前最大有效数据指针)
                byteBuffer.limit(writePos);
                
                //然后 把 commitedPosition到 wrotePosition的数据复制(写 入)到FileChannel中
                this.fileChannel.position(lastCommittedPosition);
                this.fileChannel.write(byteBuffer);
                
                //然后更新 committedPosition 指针为 wrotePosition
                this.committedPosition.set(writePos);
            } catch (Throwable e) {
                log.error("Error occurred when commit data to FileChannel.", e);
            }
        }
    }

    /**
     * 判断是否具备刷盘条件
     * @param flushLeastPages 最小刷盘页数
     * @return
     */
    private boolean isAbleToFlush(final int flushLeastPages) {
    	//已经刷盘的指针位置(上次刷盘的指针位置)
        int flush = this.flushedPosition.get();
        //读取具有有效数据的最大位置(即待刷盘的指针位置)
        int write = getReadPosition();

        //若文件已经写满,则直接刷盘
        if (this.isFull()) {
            return true;
        }

        /*
         * <p> 若 flushLeastPages[最小刷盘页数]大于0
         * 
         * <p> 若 [待刷盘总页数]-[上次刷盘总页数]=[实际刷盘页数]
         * <p> 若 [实际刷盘页数]>=[最小刷盘页数],则直接返回true
         * 
         * <p> 注: (write / OS_PAGE_SIZE):[待刷盘总页数],(flush / OS_PAGE_SIZE):[上次刷盘总页数]
         */
        if (flushLeastPages > 0) {
            return ((write / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE)) >= flushLeastPages;
        }

        //若flushLeastPages[最小刷盘页数]小于0,而[待刷盘指针位置]>[已经刷盘指针位置],则直接返回true
        return write > flush;
    }

    /**
     * 如果待提交数据不满commitLeastPages ，则不执行本次提交操作，待下次提交
     * @param commitLeastPages 为本次提交最小的页数
     * @return
     */
    protected boolean isAbleToCommit(final int commitLeastPages) {
        int flush = this.committedPosition.get();
        int write = this.wrotePosition.get();

        //首先判断MappedFile文件是否已经写满，即wrotePosition等于fileSize，若写满则进行刷盘操作 
        if (this.isFull()) {
            return true;
        }

        /*
         * 如果 commitLeastPages 大于 0,则比较 wrotePosition（ 当前 writeBuffe 的写指针）与
         * 上一次提交的指针（committedPosition)的差值,除 以 OS_ PAGE_ SIZE 得到当前脏页的数量,
         * 如果大于 commitLeastPages 则返回true
         */
        if (commitLeastPages > 0) {
        	//检测内存中尚未刷盘的消息页数是否大于最小刷盘页数，不够页数也暂时不刷盘。 
            return ((write / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE)) >= commitLeastPages;
        }

        //如果 commitLeastPages 小 于 0 表示只要存在脏页就提交 。
        return write > flush;
    }

    public int getFlushedPosition() {
        return flushedPosition.get();
    }

    public void setFlushedPosition(int pos) {
        this.flushedPosition.set(pos);
    }

    /**
     * 判断当前文件是否已经写满
     * @return
     */
    public boolean isFull() {
        return this.fileSize == this.wrotePosition.get();
    }

    /**
     * 读取指定位置开始的指定消息大小的消息内容
     * 
     * <p>详解: 查找pos到当前最大可读之间的数据,由于在整个写入期间都未曾改变MappedByteBuffer的指针,
     * 所以mappedByteBuffer.slice()方法返回的共享缓存区空间为整个MappedFile,然后通过设置bytBuffer的position为待查找的值,
     * 读取字节为当前可读字节长度,最终返回的ByteBuffer的limit(可读最大长度)为size.
     * 整个共享缓存区的容量为(MappedFile#fileSize-pos),故在操作SelectMappedBufferResult不能对包含在里面的ByteBuffer调用flip方法.
     * 
     * <p> 知识扩展: 操作ByteBuffer时如果使用了slice()方法，对其ByteBuffer进行读取时一般手动指定position与limit指针，
     * 而不是调用flip方法来切换读写状态.
     * 
     * @param pos 从什么位置开始读取消息内容
     * @param size 消息内容的长度
     * @return
     */
    public SelectMappedBufferResult selectMappedBuffer(int pos, int size) {
    	//获取 MappedFile 最大读指针
        int readPosition = getReadPosition();
        if ((pos + size) <= readPosition) {

            if (this.hold()) {
                ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
                byteBuffer.position(pos);
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(this.fileFromOffset + pos, byteBufferNew, size, this);
            } else {
                log.warn("matched, but hold failed, request pos: " + pos + ", fileFromOffset: "
                    + this.fileFromOffset);
            }
        } else {
            log.warn("selectMappedBuffer request pos invalid, request pos: " + pos + ", size: " + size
                + ", fileFromOffset: " + this.fileFromOffset);
        }

        return null;
    }

    /**
     * 读取从指定位置开始的所有消息内容
     * @param pos 从什么位置开始读取消息内容
     * @return
     */
    public SelectMappedBufferResult selectMappedBuffer(int pos) {
        int readPosition = getReadPosition();
        if (pos < readPosition && pos >= 0) {
            if (this.hold()) {
                ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
                byteBuffer.position(pos);
                int size = readPosition - pos;
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(this.fileFromOffset + pos, byteBufferNew, size, this);
            }
        }

        return null;
    }

    @Override
    public boolean cleanup(final long currentRef) {
    	//当前资源是否可用,true:当前资源可用,false:当前资源不可用
        if (this.isAvailable()) {
            log.error("this file[REF:" + currentRef + "] " + this.fileName
                + " have not shutdown, stop unmapping.");
            return false;
        }

        if (this.isCleanupOver()) {
            log.error("this file[REF:" + currentRef + "] " + this.fileName
                + " have cleanup, do not do it again.");
            return true;
        }

        clean(this.mappedByteBuffer);
        TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(this.fileSize * (-1));
        TOTAL_MAPPED_FILES.decrementAndGet();
        log.info("unmap file[REF:" + currentRef + "] " + this.fileName + " OK");
        return true;
    }

    /**
     * MappedFile 销毁
     * @param intervalForcibly 表示拒绝被销毁的最大存活时间 。
     * @return
     */
    public boolean destroy(final long intervalForcibly) {
        this.shutdown(intervalForcibly);

        if (this.isCleanupOver()) {
            try {
            	
            	/*
            	 * 关闭 文件通道， 删除物理文件 。
            	 */
                this.fileChannel.close();
                log.info("close file channel " + this.fileName + " OK");

                long beginTime = System.currentTimeMillis();
                boolean result = this.file.delete();
                log.info("delete file[REF:" + this.getRefCount() + "] " + this.fileName
                    + (result ? " OK, " : " Failed, ") + "W:" + this.getWrotePosition() + " M:"
                    + this.getFlushedPosition() + ", "
                    + UtilAll.computeEclipseTimeMilliseconds(beginTime));
            } catch (Exception e) {
                log.warn("close file channel " + this.fileName + " Failed. ", e);
            }

            return true;
        } else {
            log.warn("destroy mapped file[REF:" + this.getRefCount() + "] " + this.fileName
                + " Failed. cleanupOver: " + this.cleanupOver);
        }

        return false;
    }

    /**
     * 得到 当前写文件的位置 
     * @return
     */
    public int getWrotePosition() {
        return wrotePosition.get();
    }

    /**
     * 设置 当前写文件的位置 
     * @param pos
     */
    public void setWrotePosition(int pos) {
        this.wrotePosition.set(pos);
    }

    /**
     * 获取 MappedFile 最大读指针
     * 
     * <p> 详解: RocketMQ文件的一个组织方式是内存映射文件,预先申请一块连续的固定大小的内存,
     * 需要一套指针标识当前最大有效数据的位置,获取最大有效数据偏移量的方法由MappedFile的getReadPosition方法实现
     * 
     * @return The max position which have valid data
     * 
     * <p> 具有有效数据的最大位置
     */
    public int getReadPosition() {
    	/*
    	 * 若writeBuffer为null,则得到wrotePosition(当前文件写位置)
    	 * 若writeBuffer不为null,则得到committedPosition(当前文件的已经提交指针位置)
    	 */
        return this.writeBuffer == null ? this.wrotePosition.get() : this.committedPosition.get();
    }

    /**
     * 设置 当前文件的已经提交指针
     * @param pos
     */
    public void setCommittedPosition(int pos) {
        this.committedPosition.set(pos);
    }

    public void warmMappedFile(FlushDiskType type, int pages) {
        long beginTime = System.currentTimeMillis();
        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        int flush = 0;
        long time = System.currentTimeMillis();
        for (int i = 0, j = 0; i < this.fileSize; i += MappedFile.OS_PAGE_SIZE, j++) {
            byteBuffer.put(i, (byte) 0);
            // force flush when flush disk type is sync
            if (type == FlushDiskType.SYNC_FLUSH) {
                if ((i / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE) >= pages) {
                    flush = i;
                    mappedByteBuffer.force();
                }
            }

            // prevent gc
            if (j % 1000 == 0) {
                log.info("j={}, costTime={}", j, System.currentTimeMillis() - time);
                time = System.currentTimeMillis();
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                }
            }
        }

        // force flush when prepare load finished
        if (type == FlushDiskType.SYNC_FLUSH) {
            log.info("mapped file warm-up done, force to disk, mappedFile={}, costTime={}",
                this.getFileName(), System.currentTimeMillis() - beginTime);
            mappedByteBuffer.force();
        }
        log.info("mapped file warm-up done. mappedFile={}, costTime={}", this.getFileName(),
            System.currentTimeMillis() - beginTime);

        this.mlock();
    }

    public String getFileName() {
        return fileName;
    }

    public MappedByteBuffer getMappedByteBuffer() {
        return mappedByteBuffer;
    }

    public ByteBuffer sliceByteBuffer() {
        return this.mappedByteBuffer.slice();
    }

    public long getStoreTimestamp() {
        return storeTimestamp;
    }

    public boolean isFirstCreateInQueue() {
        return firstCreateInQueue;
    }

    public void setFirstCreateInQueue(boolean firstCreateInQueue) {
        this.firstCreateInQueue = firstCreateInQueue;
    }

    public void mlock() {
        final long beginTime = System.currentTimeMillis();
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        {
            int ret = LibC.INSTANCE.mlock(pointer, new NativeLong(this.fileSize));
            log.info("mlock {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
        }

        {
            int ret = LibC.INSTANCE.madvise(pointer, new NativeLong(this.fileSize), LibC.MADV_WILLNEED);
            log.info("madvise {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
        }
    }

    public void munlock() {
        final long beginTime = System.currentTimeMillis();
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        int ret = LibC.INSTANCE.munlock(pointer, new NativeLong(this.fileSize));
        log.info("munlock {} {} {} ret = {} time consuming = {}", address, this.fileName, this.fileSize, ret, System.currentTimeMillis() - beginTime);
    }

    //testable
    File getFile() {
        return this.file;
    }

    @Override
    public String toString() {
        return this.fileName;
    }
}
