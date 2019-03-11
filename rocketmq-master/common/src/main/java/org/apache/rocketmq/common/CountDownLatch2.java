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

package org.apache.rocketmq.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Add reset feature for @see java.util.concurrent.CountDownLatch2
 * 
 * <p> 为@see java.util.concurrent.CountDownLatch2添加重置功能
 */
public class CountDownLatch2 {
    private final Sync sync;

    /**
     * Constructs a {@code CountDownLatch2} initialized with the given count.
     * 
     * <p> 构造一个使用给定计数初始化的CountDownLatch2。
     *
     * @param count the number of times {@link #countDown} must be invoked before threads can pass through {@link
     * #await}
     * 
     * <p> 在线程可以通过await之前必须调用countDown的次数
     * 
     * @throws IllegalArgumentException if {@code count} is negative
     * 
     * <p> 如果计数是负数
     */
    public CountDownLatch2(int count) {
        if (count < 0)
            throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
     * 
     * <p> 除非线程被中断，否则导致当前线程等待锁存器倒计数到零。
     *
     * <p>If the current count is zero then this method returns immediately.
     * 
     * <p> 如果当前计数为零，则此方法立即返回。
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of two things happen:
     * 
     * <p> 如果当前计数大于零，则当前线程将被禁用以进行线程调度，并且在发生以下两种情况之一之前处于休眠状态：
     * 
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * 
     * <li> 由于countDown方法的调用，计数达到零; 要么
     * 
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * 
     * <li> 某些其他线程会中断当前线程。
     * </ul>
     *
     * <p>If the current thread:
     * 
     * <p> 如果当前线程：
     * 
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * 
     * <li> 在进入此方法时设置了中断状态; 要么
     * 
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * 
     * <li> 等待时被打断
     * 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * 
     * <p> 然后抛出InterruptedException并清除当前线程的中断状态。
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     * 
     * <p> 如果当前线程在等待时被中断
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted},
     * or the specified waiting time elapses.
     * 
     * <p> 导致当前线程等待，直到锁存器倒计数到零，除非线程被中断，或者指定的等待时间过去。
     *
     * <p>If the current count is zero then this method returns immediately
     * with the value {@code true}.
     * 
     * <p> 如果当前计数为零，则此方法立即返回值true。
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of three things happen:
     * 
     * <p> 如果当前计数大于零，则当前线程将被禁用以进行线程调度，并且在发生以下三种情况之一之前处于休眠状态：
     * 
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link #countDown} method; or
     * 
     * <li> 由于countDown方法的调用，计数达到零;要么
     * 
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * 
     * <li> 某些其他线程中断当前线程;要么
     * 
     * <li>The specified waiting time elapses.
     * 
     * <li> 经过指定的等待时间。
     * 
     * </ul>
     *
     * <p>If the count reaches zero then the method returns with the
     * value {@code true}.
     * 
     * <p> 如果计数达到零，则该方法返回值true。
     *
     * <p>If the current thread:
     * 
     * <p> 如果当前线程：
     * 
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * 
     * <li> 在进入此方法时设置了中断状态;要么
     * 
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * 
     * <li> 等待时被打断
     * 
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * 
     * <p> 然后抛出InterruptedException并清除当前线程的中断状态。
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     * 
     * <p> 如果指定的等待时间过去，则返回值false。如果时间小于或等于零，则该方法将不会等待。
     *
     * @param timeout the maximum time to wait
     * 
     * <p> 最长等待时间
     * 
     * @param unit the time unit of the {@code timeout} argument
     * 
     * <p> 超时参数的时间单位
     * 
     * @return {@code true} if the count reached zero and {@code false} if the waiting time elapsed before the count
     * reached zero
     * 
     * <p> 如果计数达到零，则返回true;如果在计数达到零之前等待时间已过，则返回false
     * 
     * @throws InterruptedException if the current thread is interrupted while waiting
     * 
     * <p> 如果当前线程在等待时被中断
     */
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     * 
     * <p> 减少锁存器的计数，如果计数达到零则释放所有等待的线程。
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     * 
     * <p> 如果当前计数大于零，则递减。 如果新计数为零，则重新启用所有等待线程以进行线程调度。
     *
     * <p>If the current count equals zero then nothing happens.
     * 
     * <p> 如果当前计数等于零，则没有任何反应。
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    /**
     * Returns the current count.
     * 
     * <p> 返回当前计数。
     *
     * <p>This method is typically used for debugging and testing purposes.
     * 
     * <p> 此方法通常用于调试和测试目的。
     *
     * @return the current count - 目前的数量
     */
    public long getCount() {
        return sync.getCount();
    }

    public void reset() {
        sync.reset();
    }

    /**
     * Returns a string identifying this latch, as well as its state.
     * The state, in brackets, includes the String {@code "Count ="}
     * followed by the current count.
     * 
     * <p> 返回标识此锁存器的字符串及其状态。 括号中的状态包括字符串“Count =”，后跟当前计数。
     *
     * @return a string identifying this latch, as well as its state
     * 
     * <p> 标识此锁存器的字符串及其状态
     */
    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }

    /**
     * Synchronization control For CountDownLatch2.
     * Uses AQS state to represent count.
     * 
     * <p> 同步控制用于CountDownLatch2。 使用AQS状态来表示计数。
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        private final int startCount;

        Sync(int count) {
            this.startCount = count;
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (; ; ) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c - 1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }

        protected void reset() {
            setState(startCount);
        }
    }
}
