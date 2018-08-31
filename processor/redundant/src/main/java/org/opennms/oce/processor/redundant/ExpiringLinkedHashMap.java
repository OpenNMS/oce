/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.processor.redundant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * A {@link LinkedHashMap} with keys that expire automatically after a predetermined delay.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class ExpiringLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    /**
     * The time to live for entries in milliseconds.
     */
    private final long timeToLiveMillis;

    /**
     * A queue to manage expiring keys.
     */
    private final BlockingQueue<DelayedKey> expiryQueue = new DelayQueue<>();

    /**
     * The synchronized map wrapping this map to ensure thread safety. This instance is used as a shared lock to ensure
     * thread safety when expired entries are removed.
     */
    private final Map<K, V> synchronizedWrapperMap;

    /**
     * Constructor.
     * <p>
     * The time to live is converted to milliseconds from the given time unit which can cause a potential loss of
     * precision if using a time unit smaller than milliseconds.
     *
     * @param timeToLive the time to live for entries
     * @param timeUnit   the time unit for the time to live
     */
    private ExpiringLinkedHashMap(long timeToLive, TimeUnit timeUnit) {
        timeToLiveMillis = timeUnit.toMillis(timeToLive);
        synchronizedWrapperMap = Collections.synchronizedMap(this);
        // Spawn a thread that will run forever that manages expiring the keys
        Executors.newSingleThreadExecutor().submit(this::expireEntries);
    }

    /**
     * Get a thread safe instance of an {@link ExpiringLinkedHashMap}.
     * <p>
     * The instance returned is conditionally thread safe wrapped using {@link java.util.Collections#synchronizedMap}.
     * The thread-safe caveats for the synchronized map apply to this map as well.
     *
     * @param timeToLive the time to live for entries
     * @param timeUnit   the time unit for the time to live
     * @return a conditionally thread-safe new instance
     */
    static <K, V> Map<K, V> getNewSynchronizedInstance(long timeToLive, TimeUnit timeUnit) {
        return new ExpiringLinkedHashMap<K, V>(timeToLive, timeUnit).synchronizedWrapperMap;
    }

    /**
     * A task that expires entries as they are dequeued.
     */
    @SuppressWarnings("InfiniteLoopStatement")
    private void expireEntries() {
        while (true) {
            try {
                // Block until there is an expired entry to deal with
                DelayedKey delayedKey = expiryQueue.take();

                // We need to obtain the lock on the synchronized map while we are removing expired entries
                synchronized (synchronizedWrapperMap) {
                    remove(delayedKey.getKey());
                }
            } catch (InterruptedException ignored) {
                // No-op since we just try again
            }
        }
    }

    @Override
    public void clear() {
        expiryQueue.clear();
        super.clear();
    }

    @Override
    public V put(K key, V value) {
        expiryQueue.offer(new DelayedKey(timeToLiveMillis, key));

        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /**
     * A simple object for associating a delay time with a map key.
     */
    private class DelayedKey implements Delayed {
        /**
         * The delay time in milliseconds.
         */
        private final long delayTime;

        /**
         * The time this was created.
         */
        private final long startTime;

        /**
         * The associated key.
         */
        private final K key;

        /**
         * Constructor.
         *
         * @param delayTime the delay time for this key
         * @param key       the key
         */
        DelayedKey(long delayTime, K key) {
            this.delayTime = delayTime;
            this.startTime = System.currentTimeMillis();
            this.key = key;
        }

        /**
         * Get the associated key.
         *
         * @return the key
         */
        K getKey() {
            return key;
        }

        /**
         * Get the remaining delay
         *
         * @return the remaining delay
         */
        private long getRemainingDelay() {
            return (startTime + delayTime) - System.currentTimeMillis();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(getRemainingDelay(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getRemainingDelay(), o.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DelayedKey that = (DelayedKey) o;
            return delayTime == that.delayTime &&
                    startTime == that.startTime &&
                    Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(delayTime, startTime, key);
        }
    }
}
