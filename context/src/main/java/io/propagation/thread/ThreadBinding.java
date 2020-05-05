/*
 * Copyright The Propagation.io Authors
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

package io.propagation.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

class ThreadBinding {
  private static final Logger log = Logger.getLogger(ThreadBinding.class.getName());

  // VisibleForTesting
  static ThreadContextStorage storage() {
    return LazyStorage.storage;
  }

  // Lazy-loaded storage. Delaying storage initialization until after class initialization makes it
  // much easier to avoid circular loading since there can still be references to ThreadContext as
  // long as they don't depend on storage, like key() and currentContextExecutor(). It also makes it
  // easier to handle exceptions.
  private static final class LazyStorage {
    static final ThreadContextStorage storage;

    static {
      AtomicReference<Throwable> deferredStorageFailure = new AtomicReference<>();
      storage = createStorage(deferredStorageFailure);
      Throwable failure = deferredStorageFailure.get();
      // Logging must happen after storage has been set, as loggers may use ThreadContext.
      if (failure != null) {
        log.log(Level.FINE, "Storage override doesn't exist. Using default", failure);
      }
    }

    private static ThreadContextStorage createStorage(
        AtomicReference<? super ClassNotFoundException> deferredStorageFailure) {
      try {
        Class<?> clazz =
            Class.forName("io.propagation.thread.override.ThreadContextStorageOverride");
        // The override's constructor is prohibited from triggering any code that can loop back to
        // ThreadContext
        return clazz.asSubclass(ThreadContextStorage.class).getConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        deferredStorageFailure.set(e);
        return new DefaultThreadContextStorage();
      } catch (Exception e) {
        throw new RuntimeException("Storage override failed to initialize", e);
      }
    }
  }

  static ThreadContext current() {
    ThreadContext current = storage().current();
    if (current == null) {
      return ThreadContext.empty();
    }
    return current;
  }

  static ThreadContext attach(ThreadContext toAttach) {
    ThreadContext prev = storage().doAttach(toAttach);
    if (prev == null) {
      return ThreadContext.empty();
    }
    return prev;
  }

  static void detach(ThreadContext toDetach, ThreadContext toAttach) {
    storage().detach(toDetach, toAttach);
  }

  static void run(ThreadContext context, Runnable r) {
    ThreadContext previous = attach(context);
    try {
      r.run();
    } finally {
      detach(context, previous);
    }
  }

  static <V> V call(ThreadContext context, Callable<V> c) throws Exception {
    ThreadContext previous = attach(context);
    try {
      return c.call();
    } finally {
      detach(context, previous);
    }
  }

  static Runnable wrap(final ThreadContext context, final Runnable r) {
    return new Runnable() {
      @Override
      public void run() {
        ThreadContext previous = attach(context);
        try {
          r.run();
        } finally {
          detach(context, previous);
        }
      }
    };
  }

  static <C> Callable<C> wrap(final ThreadContext context, final Callable<C> c) {
    return new Callable<C>() {
      @Override
      public C call() throws Exception {
        ThreadContext previous = attach(context);
        try {
          return c.call();
        } finally {
          detach(context, previous);
        }
      }
    };
  }

  static Executor fixedContextExecutor(final ThreadContext context, final Executor e) {
    final class FixedContextExecutor implements Executor {
      @Override
      public void execute(Runnable r) {
        e.execute(wrap(context, r));
      }
    }

    return new FixedContextExecutor();
  }

  static Executor currentContextExecutor(final Executor e) {
    final class CurrentContextExecutor implements Executor {
      @Override
      public void execute(Runnable r) {
        e.execute(wrap(current(), r));
      }
    }

    return new CurrentContextExecutor();
  }

  private ThreadBinding() {}
}
