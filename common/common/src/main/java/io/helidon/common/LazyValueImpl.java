/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.common;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

class LazyValueImpl<T> implements LazyValue<T> {
    private final Lock theLock = new ReentrantLock();

    private volatile T value;

    private Supplier<T> delegate;
    private volatile boolean loaded;

    LazyValueImpl(Supplier<T> supplier) {
        this.delegate = supplier;
    }

    @Override
    public T get() {
        if (loaded) {
            return value;
        }

        // not loaded (probably)
        theLock.lock();

        try {
            if (loaded) {
                return value;
            }
            value = delegate.get();
            loaded = true;
            delegate = null;
        } finally {
            theLock.unlock();
        }

        return value;
    }
}
