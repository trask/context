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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.SettableFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ThreadContext}. */
@RunWith(JUnit4.class)
@SuppressWarnings("CheckReturnValue") // false-positive in test for current ver errorprone plugin
public class ThreadContextTest {

  private static final ThreadContext.Key<String> PET = ThreadContext.key("pet");
  private static final ThreadContext.Key<String> FOOD =
      ThreadContext.keyWithDefault("food", "lasagna");
  private static final ThreadContext.Key<String> COLOR = ThreadContext.key("color");
  private static final ThreadContext.Key<Object> FAVORITE = ThreadContext.key("favorite");
  private static final ThreadContext.Key<Integer> LUCKY = ThreadContext.key("lucky");

  private ThreadContext observed;
  private final Runnable runner =
      new Runnable() {
        @Override
        public void run() {
          observed = ThreadContext.current();
        }
      };
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  @Before
  public void setUp() {
    ThreadContext.empty().attach();
  }

  @After
  public void tearDown() {
    scheduler.shutdown();
    assertEquals(ThreadContext.empty(), ThreadContext.current());
  }

  @Test
  public void defaultContext() throws Exception {
    final SettableFuture<ThreadContext> contextOfNewThread = SettableFuture.create();
    ThreadContext contextOfThisThread = ThreadContext.empty().withValue(PET, "dog");
    ThreadContext toRestore = contextOfThisThread.attach();
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                contextOfNewThread.set(ThreadContext.current());
              }
            })
        .start();
    assertNotNull(contextOfNewThread.get(5, TimeUnit.SECONDS));
    assertNotSame(contextOfThisThread, contextOfNewThread.get());
    assertSame(contextOfThisThread, ThreadContext.current());
    contextOfThisThread.detach(toRestore);
  }

  @Test
  public void rootCanBeAttached() {
    ThreadContext toRestore2 = ThreadContext.empty().attach();
    assertSame(ThreadContext.empty(), ThreadContext.current());
    ThreadContext.empty().detach(toRestore2);
  }

  @Test
  public void attachingNonCurrentReturnsCurrent() {
    ThreadContext initial = ThreadContext.current();
    ThreadContext base = initial.withValue(PET, "dog");
    assertSame(initial, base.attach());
    assertSame(base, initial.attach());
  }

  @Test
  public void detachingNonCurrentLogsSevereMessage() {
    final AtomicReference<LogRecord> logRef = new AtomicReference<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            logRef.set(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger logger = Logger.getLogger(ThreadBinding.storage().getClass().getName());
    try {
      logger.addHandler(handler);
      ThreadContext initial = ThreadContext.current();
      ThreadContext base = initial.withValue(PET, "dog");
      // Base is not attached
      base.detach(initial);
      assertSame(initial, ThreadContext.current());
      assertNotNull(logRef.get());
      assertEquals(Level.SEVERE, logRef.get().getLevel());
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  public void valuesAndOverrides() {
    ThreadContext base = ThreadContext.current().withValue(PET, "dog");
    ThreadContext child = base.withValues(PET, null, FOOD, "cheese");

    base.attach();

    assertEquals("dog", PET.get());
    assertEquals("lasagna", FOOD.get());
    assertNull(COLOR.get());

    child.attach();

    assertNull(PET.get());
    assertEquals("cheese", FOOD.get());
    assertNull(COLOR.get());

    child.detach(base);

    // Should have values from base
    assertEquals("dog", PET.get());
    assertEquals("lasagna", FOOD.get());
    assertNull(COLOR.get());

    base.detach(ThreadContext.empty());

    assertNull(PET.get());
    assertEquals("lasagna", FOOD.get());
    assertNull(COLOR.get());
  }

  @Test
  public void withValuesThree() {
    Object fav = new Object();
    ThreadContext base = ThreadContext.current().withValues(PET, "dog", COLOR, "blue");
    ThreadContext child = base.withValues(PET, "cat", FOOD, "cheese", FAVORITE, fav);

    ThreadContext toRestore = child.attach();

    assertEquals("cat", PET.get());
    assertEquals("cheese", FOOD.get());
    assertEquals("blue", COLOR.get());
    assertEquals(fav, FAVORITE.get());

    child.detach(toRestore);
  }

  @Test
  public void withValuesFour() {
    Object fav = new Object();
    ThreadContext base = ThreadContext.current().withValues(PET, "dog", COLOR, "blue");
    ThreadContext child = base.withValues(PET, "cat", FOOD, "cheese", FAVORITE, fav, LUCKY, 7);

    ThreadContext toRestore = child.attach();

    assertEquals("cat", PET.get());
    assertEquals("cheese", FOOD.get());
    assertEquals("blue", COLOR.get());
    assertEquals(fav, FAVORITE.get());
    assertEquals(7, (int) LUCKY.get());

    child.detach(toRestore);
  }

  @Test
  @SuppressWarnings("TryFailRefactoring")
  public void testWrapRunnable() {
    ThreadContext base = ThreadContext.current().withValue(PET, "cat");
    ThreadContext current = ThreadContext.current().withValue(PET, "fish");
    current.attach();

    base.wrapRunnable(runner).run();
    assertSame(base, observed);
    assertSame(current, ThreadContext.current());

    current.wrapRunnable(runner).run();
    assertSame(current, observed);
    assertSame(current, ThreadContext.current());

    base.run(runner);
    assertSame(base, observed);
    assertSame(current, ThreadContext.current());

    current.run(runner);
    assertSame(current, observed);
    assertSame(current, ThreadContext.current());

    final TestError err = new TestError();
    try {
      base.wrapRunnable(
              new Runnable() {
                @Override
                public void run() {
                  throw err;
                }
              })
          .run();
      fail("Expected exception");
    } catch (TestError ex) {
      assertSame(err, ex);
    }
    assertSame(current, ThreadContext.current());

    current.detach(ThreadContext.empty());
  }

  @Test
  @SuppressWarnings("TryFailRefactoring")
  public void testWrapCallable() throws Exception {
    ThreadContext base = ThreadContext.current().withValue(PET, "cat");
    ThreadContext current = ThreadContext.current().withValue(PET, "fish");
    current.attach();

    final Object ret = new Object();
    Callable<Object> callable =
        new Callable<Object>() {
          @Override
          public Object call() {
            runner.run();
            return ret;
          }
        };

    assertSame(ret, base.wrapCallable(callable).call());
    assertSame(base, observed);
    assertSame(current, ThreadContext.current());

    assertSame(ret, current.wrapCallable(callable).call());
    assertSame(current, observed);
    assertSame(current, ThreadContext.current());

    assertSame(ret, base.call(callable));
    assertSame(base, observed);
    assertSame(current, ThreadContext.current());

    assertSame(ret, current.call(callable));
    assertSame(current, observed);
    assertSame(current, ThreadContext.current());

    final TestError err = new TestError();
    try {
      base.wrapCallable(
              new Callable<Object>() {
                @Override
                public Object call() {
                  throw err;
                }
              })
          .call();
      fail("Excepted exception");
    } catch (TestError ex) {
      assertSame(err, ex);
    }
    assertSame(current, ThreadContext.current());

    current.detach(ThreadContext.empty());
  }

  @Test
  public void currentContextExecutor() {
    QueuedExecutor queuedExecutor = new QueuedExecutor();
    Executor executor = ThreadContext.currentContextExecutor(queuedExecutor);
    ThreadContext base = ThreadContext.current().withValue(PET, "cat");
    ThreadContext previous = base.attach();
    try {
      executor.execute(runner);
    } finally {
      base.detach(previous);
    }
    assertEquals(1, queuedExecutor.runnables.size());
    queuedExecutor.runnables.remove().run();
    assertSame(base, observed);
  }

  @Test
  public void fixedContextExecutor() {
    ThreadContext base = ThreadContext.current().withValue(PET, "cat");
    QueuedExecutor queuedExecutor = new QueuedExecutor();
    base.fixedContextExecutor(queuedExecutor).execute(runner);
    assertEquals(1, queuedExecutor.runnables.size());
    queuedExecutor.runnables.remove().run();
    assertSame(base, observed);
  }

  @Test
  public void typicalTryFinallyHandling() {
    ThreadContext base = ThreadContext.current().withValue(COLOR, "blue");
    ThreadContext previous = base.attach();
    try {
      assertSame(base, ThreadContext.current());
      // Do something
    } finally {
      base.detach(previous);
    }
    assertNotSame(base, ThreadContext.current());
  }

  private static class QueuedExecutor implements Executor {
    private final Queue<Runnable> runnables = new ArrayDeque<>();

    @Override
    public void execute(Runnable r) {
      runnables.add(r);
    }
  }

  /**
   * Tests initializing the {@link ThreadContext} class with a custom logger which uses
   * ThreadContext's storage when logging.
   */
  @Test
  public void initContextWithCustomClassLoaderWithCustomLogger() throws Exception {
    StaticTestingClassLoader classLoader =
        new StaticTestingClassLoader(
            getClass().getClassLoader(), Pattern.compile("io\\.grpc\\.[^.]+"));
    Class<?> runnable = classLoader.loadClass(LoadMeWithStaticTestingClassLoader.class.getName());

    ((Runnable) runnable.getDeclaredConstructor().newInstance()).run();
  }

  /**
   * Ensure that newly created threads can attach/detach a context. The current test thread already
   * has a context manually attached in {@link #setUp()}.
   */
  @Test
  public void newThreadAttachContext() throws Exception {
    ThreadContext parent = ThreadContext.current().withValue(COLOR, "blue");
    parent.call(
        new Callable<Object>() {
          @Override
          @Nullable
          public Object call() throws Exception {
            assertEquals("blue", COLOR.get());

            final ThreadContext child = ThreadContext.current().withValue(COLOR, "red");
            Future<String> workerThreadVal =
                scheduler.submit(
                    new Callable<String>() {
                      @Override
                      public String call() {
                        ThreadContext initial = ThreadContext.current();
                        assertNotNull(initial);
                        ThreadContext toRestore = child.attach();
                        try {
                          assertNotNull(toRestore);
                          return COLOR.get();
                        } finally {
                          child.detach(toRestore);
                          assertEquals(initial, ThreadContext.current());
                        }
                      }
                    });
            assertEquals("red", workerThreadVal.get());

            assertEquals("blue", COLOR.get());
            return null;
          }
        });
  }

  /**
   * Similar to {@link #newThreadAttachContext()} but without giving the new thread a specific ctx.
   */
  @Test
  public void newThreadWithoutContext() throws Exception {
    ThreadContext parent = ThreadContext.current().withValue(COLOR, "blue");
    parent.call(
        new Callable<Object>() {
          @Override
          @Nullable
          public Object call() throws Exception {
            assertEquals("blue", COLOR.get());

            Future<String> workerThreadVal =
                scheduler.submit(
                    new Callable<String>() {
                      @Override
                      public String call() {
                        assertNotNull(ThreadContext.current());
                        return COLOR.get();
                      }
                    });
            assertNull(workerThreadVal.get());

            assertEquals("blue", COLOR.get());
            return null;
          }
        });
  }

  @Test
  public void storageReturnsNullTest() throws Exception {
    Class<?> lazyStorageClass = Class.forName("io.propagation.thread.ThreadBinding$LazyStorage");
    Field storage = lazyStorageClass.getDeclaredField("storage");
    assertTrue(Modifier.isFinal(storage.getModifiers()));
    // use reflection to forcibly change the storage object to a test object
    storage.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    int storageModifiers = modifiersField.getInt(storage);
    modifiersField.set(storage, storageModifiers & ~Modifier.FINAL);
    Object o = storage.get(null);
    ThreadContextStorage originalStorage = (ThreadContextStorage) o;
    try {
      storage.set(
          null,
          new ThreadContextStorage() {
            @Override
            @Nullable
            public ThreadContext doAttach(ThreadContext toAttach) {
              return null;
            }

            @Override
            public void detach(ThreadContext toDetach, ThreadContext toRestore) {
              // noop
            }

            @Override
            @Nullable
            public ThreadContext current() {
              return null;
            }
          });
      // current() returning null gets transformed into ROOT
      assertEquals(ThreadContext.empty(), ThreadContext.current());

      // doAttach() returning null gets transformed into ROOT
      ThreadContext blueContext = ThreadContext.current().withValue(COLOR, "blue");
      ThreadContext toRestore = blueContext.attach();
      assertEquals(ThreadContext.empty(), toRestore);

      // final sanity check
      blueContext.detach(toRestore);
      assertEquals(ThreadContext.empty(), ThreadContext.current());
    } finally {
      // undo the changes
      storage.set(null, originalStorage);
      storage.setAccessible(false);
      modifiersField.set(storage, storageModifiers | Modifier.FINAL);
      modifiersField.setAccessible(false);
    }
  }

  @Test
  public void errorWhenAncestryLengthLong() {
    final AtomicReference<LogRecord> logRef = new AtomicReference<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            logRef.set(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger logger = Logger.getLogger(DefaultThreadContext.class.getName());
    try {
      logger.addHandler(handler);
      ThreadContext ctx = ThreadContext.current();
      for (int i = 0; i < DefaultThreadContext.CONTEXT_DEPTH_WARN_THRESH; i++) {
        assertNull(logRef.get());
        ctx = ctx.withValue(PET, "tiger");
      }
      ctx = ctx.withValue(PET, "lion");
      assertEquals("lion", PET.get(ctx));
      assertNotNull(logRef.get());
      assertNotNull(logRef.get().getThrown());
      assertEquals(Level.SEVERE, logRef.get().getLevel());
    } finally {
      logger.removeHandler(handler);
    }
  }

  // UsedReflectively
  public static final class LoadMeWithStaticTestingClassLoader implements Runnable {
    @Override
    public void run() {
      Logger logger = Logger.getLogger(ThreadContext.class.getName());
      logger.setLevel(Level.ALL);
      Handler handler =
          new Handler() {
            @Override
            public void publish(LogRecord record) {
              ThreadContext ctx = ThreadContext.current();
              ThreadContext previous = ctx.attach();
              ctx.detach(previous);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      logger.addHandler(handler);

      try {
        assertNotNull(ThreadContext.empty());
      } finally {
        logger.removeHandler(handler);
      }
    }
  }

  /** Allows more precise catch blocks than plain Error to avoid catching AssertionError. */
  private static final class TestError extends Error {}
}
