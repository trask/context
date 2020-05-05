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

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ThreadContextStorageTest {
  private static final ThreadContext.Key<Object> KEY = ThreadContext.key("test-key");
  private static final ThreadContextStorage storage = new DefaultThreadContextStorage();

  private ThreadContext contextBeforeTest;

  @Before
  public void saveContext() {
    contextBeforeTest = storage.doAttach(ThreadContext.empty());
  }

  @After
  public void restoreContext() {
    storage.detach(ThreadContext.empty(), contextBeforeTest);
  }

  @Test
  public void detach_threadLocalClearedOnRoot() {
    ThreadContext context = ThreadContext.empty().withValue(KEY, new Object());
    ThreadContext old = storage.doAttach(context);
    assertThat(storage.current()).isSameInstanceAs(context);
    assertThat(DefaultThreadContextStorage.localContext.get()).isSameInstanceAs(context);
    storage.detach(context, old);
    // thread local must contain null to avoid leaking our ClassLoader via ROOT
    assertThat(DefaultThreadContextStorage.localContext.get()).isNull();
  }

  @Test
  public void detach_detachRoot() {
    final List<LogRecord> logs = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            logs.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };

    // Explicitly choose ROOT as the current context
    ThreadContext context = ThreadContext.empty();
    ThreadContext old = storage.doAttach(context);

    // Attach and detach a random context
    ThreadContext innerContext = ThreadContext.empty().withValue(KEY, new Object());
    storage.detach(innerContext, storage.doAttach(innerContext));

    Logger logger = Logger.getLogger(ThreadContextStorage.class.getName());
    logger.addHandler(handler);
    try {
      // Make sure detaching ROOT doesn't log a warning
      storage.detach(context, old);
    } finally {
      logger.removeHandler(handler);
    }
    assertThat(logs).isEmpty();
  }
}
