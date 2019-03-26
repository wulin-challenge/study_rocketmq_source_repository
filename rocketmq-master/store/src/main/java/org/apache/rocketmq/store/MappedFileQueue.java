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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 映射文件队列
 * 
 * <p> MappedFileQueue是MappedFile的管理容器，MappedFileQueue是对存储目录的封装，
 * 例如CommitLog文件的存储路径${ROCKET_HOME}/store/commitlog/,该目录下会存在多个内存映射文件(MappedFile)。
 *
 */
public class MappedFileQueue {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);
    private static final Logger LOG_ERROR = LoggerFactory.getLogger(LoggerName.STORE_ERROR_LOGGER_NAME);

    private static final int DELETE_FILES_BATCH_MAX = 10;

    /**
     * 存储目 录 。
     */
    private final String storePath;

    /**
     * 单个文件的存储大小 。
     */
    private final int mappedFileSize;

    /**
     * MappedFile 文件集合 。
     */
    private final CopyOnWriteArrayList<MappedFile> mappedFiles = new CopyOnWriteArrayList<MappedFile>();

    /**
     * 创建 MappedFile 服务类 。
     */
    private final AllocateMappedFileService allocateMappedFileService;

    /**
     * 当前刷盘指针,表示该指针之前的所有数据全部持久化到磁盘.
     */
    private long flushedWhere = 0;
    
    /**
     * 当前数据提交指针,内存中 ByteBuffer当前的写指针,该值大于等于flushedWhere.
     */
    private long committedWhere = 0;

    private volatile long storeTimestamp = 0;

    public MappedFileQueue(final String storePath, int mappedFileSize,
        AllocateMappedFileService allocateMappedFileService) {
        this.storePath = storePath;
        this.mappedFileSize = mappedFileSize;
        this.allocateMappedFileService = allocateMappedFileService;
    }

    public void checkSelf() {

        if (!this.mappedFiles.isEmpty()) {
            Iterator<MappedFile> iterator = mappedFiles.iterator();
            MappedFile pre = null;
            while (iterator.hasNext()) {
                MappedFile cur = iterator.next();

                if (pre != null) {
                    if (cur.getFileFromOffset() - pre.getFileFromOffset() != this.mappedFileSize) {
                        LOG_ERROR.error("[BUG]The mappedFile queue's data is damaged, the adjacent mappedFile's offset don't match. pre file {}, cur file {}",
                            pre.getFileName(), cur.getFileName());
                    }
                }
                pre = cur;
            }
        }
    }

    /**
     * 根据消息存储时间戳来查找 MappdFile.从 MappedFile 列表中第一个文件开始查找，
     * 找到第一个最后一次更新时间大于待查找时间戳的文件，如果不存在，则返回最后一个MappedFile 文件 。
     * 
     * @param timestamp 要查找的时间戳
     * @return
     */
    public MappedFile getMappedFileByTime(final long timestamp) {
    	//拷贝映射文件,若reservedMappedFiles=0,则拷贝所有的映射文件
        Object[] mfs = this.copyMappedFiles(0);

        if (null == mfs)
            return null;

        for (int i = 0; i < mfs.length; i++) {
            MappedFile mappedFile = (MappedFile) mfs[i];
            if (mappedFile.getLastModifiedTimestamp() >= timestamp) {
                return mappedFile;
            }
        }

        return (MappedFile) mfs[mfs.length - 1];
    }

    /**
     * 拷贝映射文件,若reservedMappedFiles=0,则拷贝所有的映射文件
     * @param reservedMappedFiles
     * @return
     */
    private Object[] copyMappedFiles(final int reservedMappedFiles) {
        Object[] mfs;

        if (this.mappedFiles.size() <= reservedMappedFiles) {
            return null;
        }

        mfs = this.mappedFiles.toArray();
        return mfs;
    }

    /**
     * 删除offset之后的所有文件.遍历目录下的文件,如果文件的尾部偏移量小于offset则跳过该文件,如果尾部的偏移量大于offset,
     * 则进一步比较offset与文件的开始偏移量,如果offset大于文件的起始偏移量,说明当前文件包含了有效偏移里,
     * 设置MappedFile的flushedPosition和commitedPosition；如果offset小于文件的起始偏移量,说明该文件是有效文件后面创建的,
     * 调用MappedFile#destory释放MappedFile占用的内存资源(内存映射与内存通道等),然后加入到待删除文件列表中,
     * 最终调用deleteExpiredFile将文件从物理磁盘删除
     * @param offset
     */
    public void truncateDirtyFiles(long offset) {
        List<MappedFile> willRemoveFiles = new ArrayList<MappedFile>();

        for (MappedFile file : this.mappedFiles) {
            long fileTailOffset = file.getFileFromOffset() + this.mappedFileSize;
            if (fileTailOffset > offset) {
                if (offset >= file.getFileFromOffset()) {
                    file.setWrotePosition((int) (offset % this.mappedFileSize));
                    file.setCommittedPosition((int) (offset % this.mappedFileSize));
                    file.setFlushedPosition((int) (offset % this.mappedFileSize));
                } else {
                    file.destroy(1000);
                    willRemoveFiles.add(file);
                }
            }
        }

        this.deleteExpiredFile(willRemoveFiles);
    }

    /**
     * 删除过期文件
     * @param files 要清除的过期文件
     */
    void deleteExpiredFile(List<MappedFile> files) {

        if (!files.isEmpty()) {

            Iterator<MappedFile> iterator = files.iterator();
            while (iterator.hasNext()) {
                MappedFile cur = iterator.next();
                if (!this.mappedFiles.contains(cur)) {
                    iterator.remove();
                    log.info("This mappedFile {} is not contained by mappedFiles, so skip it.", cur.getFileName());
                }
            }

            try {
                if (!this.mappedFiles.removeAll(files)) {
                    log.error("deleteExpiredFile remove failed.");
                }
            } catch (Exception e) {
                log.error("deleteExpiredFile has exception.", e);
            }
        }
    }

    public boolean load() {
        File dir = new File(this.storePath);
        File[] files = dir.listFiles();
        if (files != null) {
            // ascending order
            Arrays.sort(files);
            for (File file : files) {

                if (file.length() != this.mappedFileSize) {
                    log.warn(file + "\t" + file.length()
                        + " length not matched message store config value, ignore it");
                    return true;
                }

                try {
                    MappedFile mappedFile = new MappedFile(file.getPath(), mappedFileSize);

                    mappedFile.setWrotePosition(this.mappedFileSize);
                    mappedFile.setFlushedPosition(this.mappedFileSize);
                    mappedFile.setCommittedPosition(this.mappedFileSize);
                    this.mappedFiles.add(mappedFile);
                    log.info("load " + file.getPath() + " OK");
                } catch (IOException e) {
                    log.error("load file " + file + " error", e);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 统计内存数据中还未刷盘的大小
     * @return
     */
    public long howMuchFallBehind() {
        if (this.mappedFiles.isEmpty())
            return 0;

        long committed = this.flushedWhere;
        if (committed != 0) {
            MappedFile mappedFile = this.getLastMappedFile(0, false);
            if (mappedFile != null) {
                return (mappedFile.getFileFromOffset() + mappedFile.getWrotePosition()) - committed;
            }
        }

        return 0;
    }

    public MappedFile getLastMappedFile(final long startOffset, boolean needCreate) {
        long createOffset = -1;
        MappedFile mappedFileLast = getLastMappedFile();

        //列表为空或者最后一个对象对应的文件已经写满，则创建一个新的文件（即新的 MapedFile 对象）
        if (mappedFileLast == null) {
            createOffset = startOffset - (startOffset % this.mappedFileSize);
        }
        
        if (mappedFileLast != null && mappedFileLast.isFull()) {
            createOffset = mappedFileLast.getFileFromOffset() + this.mappedFileSize;
        }

        /*
         * 若存在最后一个文件（对应最后一个 MapedFile 对象） 并且未写满，则直接返回最后一个 MapedFile 对象。
         * 若列表为空，则创建的新文件的文件名（即 fileFromOffset 值）为 0；若最后一个文件写满，则新文件的文件
         * 名等于最后一个文件的fileFromOffset+fileSize
         */
        if (createOffset != -1 && needCreate) {
            String nextFilePath = this.storePath + File.separator + UtilAll.offset2FileName(createOffset);
            String nextNextFilePath = this.storePath + File.separator
                + UtilAll.offset2FileName(createOffset + this.mappedFileSize);
            MappedFile mappedFile = null;

            /*
             * 若在 Broker 启动初始化的时候会创建了后台服务线程（ AllocateMapedFileService 服务） ，则调用
             * AllocateMapedFileService.putRequestAndReturnMapedFile 方法，在该方法中用下一个文件的文件路径、
             * 下下一个文件的路径、文件大小为参数初始化AllocateRequest 对象，并放入该服务线程的
             * requestQueue:PriorityBlockingQueue<AllocateRequest>变量中，由该线程在后台监听 requestQueue 
             * 队列，若该队列中存在 AllocateRequest 对象，则利用该对象的变量值创建 MapedFile 对象（即在磁盘中生成了对应的物理文件） 
             * ，并存入 AllocateRequest 对象的 MapedFile 变量中，并且在下一个新文件之后继续将下下一个新文件也创建了
             */
            if (this.allocateMappedFileService != null) {
                mappedFile = this.allocateMappedFileService.putRequestAndReturnMappedFile(nextFilePath,
                    nextNextFilePath, this.mappedFileSize);
            } else {
                try {
                    mappedFile = new MappedFile(nextFilePath, this.mappedFileSize);
                } catch (IOException e) {
                    log.error("create mappedFile exception", e);
                }
            }

            //最后将创建或返回的 MapedFile 对象存入 MapedFileQueue 的 MapedFile 列表中，并返回该 MapedFile 对象给调用者
            if (mappedFile != null) {
                if (this.mappedFiles.isEmpty()) {
                    mappedFile.setFirstCreateInQueue(true);
                }
                this.mappedFiles.add(mappedFile);
            }

            return mappedFile;
        }

        return mappedFileLast;
    }

    public MappedFile getLastMappedFile(final long startOffset) {
        return getLastMappedFile(startOffset, true);
    }

    /**
     * 得到最后一个映射文件
     * @return
     */
    public MappedFile getLastMappedFile() {
        MappedFile mappedFileLast = null;

        while (!this.mappedFiles.isEmpty()) {
            try {
                mappedFileLast = this.mappedFiles.get(this.mappedFiles.size() - 1);
                break;
            } catch (IndexOutOfBoundsException e) {
                //continue;
            } catch (Exception e) {
                log.error("getLastMappedFile has exception.", e);
                break;
            }
        }

        return mappedFileLast;
    }

    public boolean resetOffset(long offset) {
        MappedFile mappedFileLast = getLastMappedFile();

        if (mappedFileLast != null) {
            long lastOffset = mappedFileLast.getFileFromOffset() +
                mappedFileLast.getWrotePosition();
            long diff = lastOffset - offset;

            final int maxDiff = this.mappedFileSize * 2;
            if (diff > maxDiff)
                return false;
        }

        ListIterator<MappedFile> iterator = this.mappedFiles.listIterator();

        while (iterator.hasPrevious()) {
            mappedFileLast = iterator.previous();
            if (offset >= mappedFileLast.getFileFromOffset()) {
                int where = (int) (offset % mappedFileLast.getFileSize());
                mappedFileLast.setFlushedPosition(where);
                mappedFileLast.setWrotePosition(where);
                mappedFileLast.setCommittedPosition(where);
                break;
            } else {
                iterator.remove();
            }
        }
        return true;
    }

    /**
     * 获取存储文件最小偏移量
     * @return
     */
    public long getMinOffset() {

        if (!this.mappedFiles.isEmpty()) {
            try {
                return this.mappedFiles.get(0).getFileFromOffset();
            } catch (IndexOutOfBoundsException e) {
                //continue;
            } catch (Exception e) {
                log.error("getMinOffset has exception.", e);
            }
        }
        return -1;
    }

    /**
     * 获取存储文件的最大偏移量.返回最后一个MappedFile文件的fileFromOffset加上MappedFile文件当前的写指针.
     * @return
     */
    public long getMaxOffset() {
        MappedFile mappedFile = getLastMappedFile();
        if (mappedFile != null) {
            return mappedFile.getFileFromOffset() + mappedFile.getReadPosition();
        }
        return 0;
    }

    /**
     * 返回存储文件当前的写指针.返回最后一个文件的 fileFromOffset加上当前写指针位置。
     * @return
     */
    public long getMaxWrotePosition() {
        MappedFile mappedFile = getLastMappedFile();
        if (mappedFile != null) {
            return mappedFile.getFileFromOffset() + mappedFile.getWrotePosition();
        }
        return 0;
    }

    public long remainHowManyDataToCommit() {
        return getMaxWrotePosition() - committedWhere;
    }

    public long remainHowManyDataToFlush() {
        return getMaxOffset() - flushedWhere;
    }

    public void deleteLastMappedFile() {
        MappedFile lastMappedFile = getLastMappedFile();
        if (lastMappedFile != null) {
            lastMappedFile.destroy(1000);
            this.mappedFiles.remove(lastMappedFile);
            log.info("on recover, destroy a logic mapped file " + lastMappedFile.getFileName());

        }
    }

    /**
     * 删除过期文件
     * 
     * <p> 执行文件销毁与删除.从倒数第二个文件开始遍历,计算文件的最大存活时间(＝文件的最后一次更新时间＋文件存活时间(默认72小时)),
     * 如果当前时间大于文件的最大存活时间或需要强制删除文件(当磁盘使用超过设定的阔值)时则执行MappedFiJe#destory方法,
     * 清除MappedFile占有的相关资源,如果执行成功,将该文件加入到待删除文件列表中,
     * 然后统一执行File#delete方法将文件从物理磁盘中删除.
     * 
     * @param expiredTime 文件的过期时间
     * <p>
     * @param deleteFilesInterval 删除物理文件的间隔,因为在一次清除过程中,可能需要被删除的文件不止一个,该值指定两次删除文件的间隔时间.
     * <p>
     * @param intervalForcibly 在清除过期文件时,如果该文件被其他线程所占用(引用次数大于0,比如读取消息),此时会阻止此次删除任务,
     *  同时在第一次试图删除该文件时记录当前时间戳,destroyMapedFilelntervalForcibly表示第一次拒绝删除之后能保留的最大时间,
     *  在此时间内,同样可以被拒绝删除,同时会将引用减少1000个,超过该时间间隔后,文件将被强制删除.
     *  <p>
     * @param cleanImmediately 是否立即清除
     * @return
     */
    public int deleteExpiredFileByTime(final long expiredTime,
        final int deleteFilesInterval,
        final long intervalForcibly,
        final boolean cleanImmediately) {
        Object[] mfs = this.copyMappedFiles(0);

        if (null == mfs)
            return 0;

        int mfsLength = mfs.length - 1;
        int deleteCount = 0;
        List<MappedFile> files = new ArrayList<MappedFile>();
        if (null != mfs) {
            for (int i = 0; i < mfsLength; i++) {
                MappedFile mappedFile = (MappedFile) mfs[i];
                long liveMaxTimestamp = mappedFile.getLastModifiedTimestamp() + expiredTime;
                if (System.currentTimeMillis() >= liveMaxTimestamp || cleanImmediately) {
                    if (mappedFile.destroy(intervalForcibly)) {
                        files.add(mappedFile);
                        deleteCount++;

                        if (files.size() >= DELETE_FILES_BATCH_MAX) {
                            break;
                        }

                        if (deleteFilesInterval > 0 && (i + 1) < mfsLength) {
                            try {
                                Thread.sleep(deleteFilesInterval);
                            } catch (InterruptedException e) {
                            }
                        }
                    } else {
                        break;
                    }
                } else {
                    //avoid deleting files in the middle
                    break;
                }
            }
        }

        deleteExpiredFile(files);

        return deleteCount;
    }

    public int deleteExpiredFileByOffset(long offset, int unitSize) {
        Object[] mfs = this.copyMappedFiles(0);

        List<MappedFile> files = new ArrayList<MappedFile>();
        int deleteCount = 0;
        if (null != mfs) {

            int mfsLength = mfs.length - 1;

            for (int i = 0; i < mfsLength; i++) {
                boolean destroy;
                MappedFile mappedFile = (MappedFile) mfs[i];
                SelectMappedBufferResult result = mappedFile.selectMappedBuffer(this.mappedFileSize - unitSize);
                if (result != null) {
                    long maxOffsetInLogicQueue = result.getByteBuffer().getLong();
                    result.release();
                    destroy = maxOffsetInLogicQueue < offset;
                    if (destroy) {
                        log.info("physic min offset " + offset + ", logics in current mappedFile max offset "
                            + maxOffsetInLogicQueue + ", delete it");
                    }
                } else if (!mappedFile.isAvailable()) { // Handle hanged file.
                    log.warn("Found a hanged consume queue file, attempting to delete it.");
                    destroy = true;
                } else {
                    log.warn("this being not executed forever.");
                    break;
                }

                if (destroy && mappedFile.destroy(1000 * 60)) {
                    files.add(mappedFile);
                    deleteCount++;
                } else {
                    break;
                }
            }
        }

        deleteExpiredFile(files);

        return deleteCount;
    }

    /**
     * 将内存数据刷盘到磁盘
     * @param flushLeastPages 刷盘最小页数 ,flushLeastPages<=0,直接刷盘能刷盘的数据
     * @return
     */
    public boolean flush(final int flushLeastPages) {
        boolean result = true;
        //按偏移量查找映射文件。
        MappedFile mappedFile = this.findMappedFileByOffset(this.flushedWhere, this.flushedWhere == 0);
        if (mappedFile != null) {
            long tmpTimeStamp = mappedFile.getStoreTimestamp();
            //刷盘
            int offset = mappedFile.flush(flushLeastPages);
            long where = mappedFile.getFileFromOffset() + offset;
            result = where == this.flushedWhere;
            this.flushedWhere = where;
            if (0 == flushLeastPages) {
                this.storeTimestamp = tmpTimeStamp;
            }
        }

        return result;
    }

    /**
     * 根据 committedWhere(当前数据提交指针) 查找映射文件,让后进行数据移交
     * @param commitLeastPages 为本次提交最小的页数，如果待提交数据不满commitLeastPages ，则不执行本次提交操作，待下次提交
     * @return
     */
    public boolean commit(final int commitLeastPages) {
        boolean result = true;
        //按偏移量查找映射文件。
        MappedFile mappedFile = this.findMappedFileByOffset(this.committedWhere, this.committedWhere == 0);
        if (mappedFile != null) {
        	//内存映射文件的提交动作由该 MappedFile的commit方法实现 
            int offset = mappedFile.commit(commitLeastPages);
            long where = mappedFile.getFileFromOffset() + offset;
            result = where == this.committedWhere;
            this.committedWhere = where;
        }

        return result;
    }

    /**
     * Finds a mapped file by offset.
     * 
     * <p> 按偏移量查找映射文件。
     *
     * @param offset Offset. - 要查找的偏移量
     * 
     * @param returnFirstOnNotFound If the mapped file is not found, then return the first one.
     * 
     * <p> 如果找不到映射文件，则返回第一个文件。
     * 
     * @return Mapped file or null (when not found and returnFirstOnNotFound is <code>false</code>).
     * 
     * <p> 映射文件或null（未找到时，returnFirstOnNotFound为false）。
     */
    public MappedFile findMappedFileByOffset(final long offset, final boolean returnFirstOnNotFound) {
        try {
        	//得到第一个映射文件
            MappedFile mappedFile = this.getFirstMappedFile();
            if (mappedFile != null) {
                int index = (int) ((offset / this.mappedFileSize) - (mappedFile.getFileFromOffset() / this.mappedFileSize));
                if (index < 0 || index >= this.mappedFiles.size()) {
                    LOG_ERROR.warn("Offset for {} not matched. Request offset: {}, index: {}, " +
                            "mappedFileSize: {}, mappedFiles count: {}",
                        mappedFile,
                        offset,
                        index,
                        this.mappedFileSize,
                        this.mappedFiles.size());
                }

                try {
                    return this.mappedFiles.get(index);
                } catch (Exception e) {
                    if (returnFirstOnNotFound) {
                        return mappedFile;
                    }
                    LOG_ERROR.warn("findMappedFileByOffset failure. ", e);
                }
            }
        } catch (Exception e) {
            log.error("findMappedFileByOffset Exception", e);
        }

        return null;
    }

    /**
     * 得到第一个映射文件
     * @return 返回映射文件中的第一个
     */
    public MappedFile getFirstMappedFile() {
        MappedFile mappedFileFirst = null;

        if (!this.mappedFiles.isEmpty()) {
            try {
                mappedFileFirst = this.mappedFiles.get(0);
            } catch (IndexOutOfBoundsException e) {
                //ignore
            } catch (Exception e) {
                log.error("getFirstMappedFile has exception.", e);
            }
        }

        return mappedFileFirst;
    }

    /**
     * 按偏移量查找映射文件。
     * @param offset 要查找的偏移量
     * @return
     */
    public MappedFile findMappedFileByOffset(final long offset) {
        return findMappedFileByOffset(offset, false);
    }

    public long getMappedMemorySize() {
        long size = 0;

        Object[] mfs = this.copyMappedFiles(0);
        if (mfs != null) {
            for (Object mf : mfs) {
                if (((ReferenceResource) mf).isAvailable()) {
                    size += this.mappedFileSize;
                }
            }
        }

        return size;
    }

    public boolean retryDeleteFirstFile(final long intervalForcibly) {
        MappedFile mappedFile = this.getFirstMappedFile();
        if (mappedFile != null) {
            if (!mappedFile.isAvailable()) {
                log.warn("the mappedFile was destroyed once, but still alive, " + mappedFile.getFileName());
                boolean result = mappedFile.destroy(intervalForcibly);
                if (result) {
                    log.info("the mappedFile re delete OK, " + mappedFile.getFileName());
                    List<MappedFile> tmpFiles = new ArrayList<MappedFile>();
                    tmpFiles.add(mappedFile);
                    this.deleteExpiredFile(tmpFiles);
                } else {
                    log.warn("the mappedFile re delete failed, " + mappedFile.getFileName());
                }

                return result;
            }
        }

        return false;
    }

    public void shutdown(final long intervalForcibly) {
        for (MappedFile mf : this.mappedFiles) {
            mf.shutdown(intervalForcibly);
        }
    }

    public void destroy() {
        for (MappedFile mf : this.mappedFiles) {
            mf.destroy(1000 * 3);
        }
        this.mappedFiles.clear();
        this.flushedWhere = 0;

        // delete parent directory
        File file = new File(storePath);
        if (file.isDirectory()) {
            file.delete();
        }
    }

    /**
     * 得到当前刷盘指针,表示该指针之前的所有数据全部持久化到磁盘.
     * @return
     */
    public long getFlushedWhere() {
        return flushedWhere;
    }

    public void setFlushedWhere(long flushedWhere) {
        this.flushedWhere = flushedWhere;
    }

    public long getStoreTimestamp() {
        return storeTimestamp;
    }

    public List<MappedFile> getMappedFiles() {
        return mappedFiles;
    }

    public int getMappedFileSize() {
        return mappedFileSize;
    }

    public long getCommittedWhere() {
        return committedWhere;
    }

    public void setCommittedWhere(final long committedWhere) {
        this.committedWhere = committedWhere;
    }
}
