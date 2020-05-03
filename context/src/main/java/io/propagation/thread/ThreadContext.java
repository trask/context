/*
 * Copyright 2020, Propagation.io Authors
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

import io.propagation.Context;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * A context propagation mechanism which can carry scoped-values across API boundaries and between
 * threads. Examples of state propagated via context include:
 *
 * <ul>
 *   <li>Security principals and credentials.
 *   <li>Local and distributed tracing information.
 * </ul>
 *
 * <p>A ThreadContext object can be {@link #attach attached} to the {@link ThreadStorage}, which
 * effectively forms a <b>scope</b> for the context. The scope is bound to the current thread.
 * Within a scope, its ThreadContext is accessible even across API boundaries, through {@link
 * #current}. The scope is later exited by {@link #detach detaching} the ThreadContext.
 *
 * <p>ThreadContext objects are immutable and inherit state from their parent. To add or overwrite
 * the current state a new context object must be created and then attached, replacing the
 * previously bound context. For example:
 *
 * <pre>
 *   ThreadContext withCredential = ThreadContext.current().withValue("key", cred);
 *   withCredential.run(new Runnable() {
 *     public void run() {
 *        readUserRecords(userId, ThreadContext.current().get("key"));
 *     }
 *   });
 * </pre>
 *
 * <p>Notes and cautions on use:
 *
 * <ul>
 *   <li>Every {@code attach()} should have a {@code detach()} in the same method. Breaking this
 *       rules may lead to memory leaks.
 *   <li>While ThreadContext objects are immutable they do not place such a restriction on the state
 *       they store.
 *   <li>ThreadContext is not intended for passing optional parameters to an API and developers
 *       should take care to avoid excessive dependence on context when designing an API.
 *   <li>Do not mock this class. Use {@link #empty()} for a non-null instance.
 * </ul>
 */
/* @DoNotMock("Use empty() for a non-null ThreadContext") // commented out to avoid dependencies  */
public abstract class ThreadContext {

  /**
   * The logical root context which is the ultimate ancestor of all contexts.
   *
   * <p>Never assume this is the default context for new threads, because {@link ThreadStorage} may
   * define a default context that is different.
   */
  public static ThreadContext empty() {
    return DefaultThreadContext.EMPTY;
  }

  /** Return the context associated with the current scope, will never return {@code null}. */
  public static ThreadContext current() {
    return ThreadBinding.current();
  }

  public static ThreadContext wrap(Context context) {
    return new DefaultThreadContext(context);
  }

  public abstract Object get(Object key);

  /**
   * Create a new context with the given key value set.
   *
   * <pre>
   *   ThreadContext withCredential = ThreadContext.current().withValue("key", cred);
   *   withCredential.run(new Runnable() {
   *     public void run() {
   *        readUserRecords(userId, ThreadContext.current().get("key"));
   *     }
   *   });
   * </pre>
   *
   * <p>Note that multiple calls to {@code #withValue} can be chained together. That is,
   *
   * <pre>
   * context.withValues(K1, V1, K2, V2);
   * // is the same as
   * context.withValue(K1, V1).withValue(K2, V2);
   * </pre>
   *
   * <p>Nonetheless, {@link ThreadContext} should not be treated like a general purpose map with a
   * large number of keys and values — combine multiple related items together into a single key
   * instead of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract ThreadContext withValue(Object k1, Object v1);

  /** Create a new context with the given key value set. */
  public abstract ThreadContext withValues(Object k1, Object v1, Object k2, Object v2);

  /** Create a new context with the given key value set. */
  public abstract ThreadContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3);

  /**
   * Create a new context with the given key value set.
   *
   * <p>For more than 4 key-value pairs, note that multiple calls to {@link #withValue} can be
   * chained together. That is,
   *
   * <pre>
   * context.withValues(K1, V1, K2, V2);
   * // is the same as
   * context.withValue(K1, V1).withValue(K2, V2);
   * </pre>
   *
   * <p>Nonetheless, {@link ThreadContext} should not be treated like a general purpose map with a
   * large number of keys and values — combine multiple related items together into a single key
   * instead of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract ThreadContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3, Object k4, Object v4);

  /**
   * Attach this context, thus enter a new scope within which this context is {@link #current}. The
   * previously current context is returned.
   *
   * <p>Instead of using {@code attach()} and {@link #detach(ThreadContext)} most use-cases are
   * better served by using the {@link #run(Runnable)} or {@link
   * #call(java.util.concurrent.Callable)} to execute work immediately within a context's scope. If
   * work needs to be done in other threads it is recommended to use the 'wrap' methods or to use a
   * propagating executor.
   *
   * <p>All calls to {@code attach()} should have a corresponding {@link #detach(ThreadContext)}
   * within the same method:
   *
   * <pre>{@code ThreadContext previous = someContext.attach();
   * try {
   *   // Do work
   * } finally {
   *   someContext.detach(previous);
   * }}</pre>
   */
  public abstract ThreadContext attach();

  /**
   * Reverse an {@code attach()}, restoring the previous context and exiting the current scope.
   *
   * <p>This context should be the same context that was previously {@link #attach attached}. The
   * provided replacement should be what was returned by the same {@link #attach attach()} call. If
   * an {@code attach()} and a {@code detach()} meet above requirements, they match.
   *
   * <p>It is expected that between any pair of matching {@code attach()} and {@code detach()}, all
   * {@code attach()}es and {@code detach()}es are called in matching pairs. If this method finds
   * that this context is not {@link #current current}, either you or some code in-between are not
   * detaching correctly, and a SEVERE message will be logged but the context to attach will still
   * be bound. <strong>Never</strong> use {@code ThreadContext.current().detach()}, as this will
   * compromise this error-detecting mechanism.
   */
  public abstract void detach(ThreadContext toAttach);

  /**
   * Immediately run a {@link Runnable} with this context as the {@link #current} context.
   *
   * @param r {@link Runnable} to run.
   */
  public abstract void run(Runnable r);

  /**
   * Immediately call a {@link Callable} with this context as the {@link #current} context.
   *
   * @param c {@link Callable} to call.
   * @return result of call.
   */
  public abstract <V> V call(Callable<V> c) throws Exception;

  /**
   * Wrap a {@link Runnable} so that it executes with this context as the {@link #current} context.
   */
  public abstract Runnable wrapRunnable(final Runnable r);

  /**
   * Wrap a {@link Callable} so that it executes with this context as the {@link #current} context.
   */
  public abstract <C> Callable<C> wrapCallable(final Callable<C> c);

  /**
   * Wrap an {@link Executor} so that it always executes with this context as the {@link #current}
   * context. It is generally expected that {@link #currentContextExecutor(Executor)} would be used
   * more commonly than this method.
   *
   * <p>One scenario in which this executor may be useful is when a single thread is sharding work
   * to multiple threads.
   *
   * @see #currentContextExecutor(Executor)
   */
  public abstract Executor fixedContextExecutor(final Executor e);

  /**
   * Create an executor that propagates the {@link #current} context when {@link Executor#execute}
   * is called as the {@link #current} context of the {@code Runnable} scheduled. <em>Note that this
   * is a static method.</em>
   *
   * @see #fixedContextExecutor(Executor)
   */
  public static Executor currentContextExecutor(final Executor e) {
    return ThreadBinding.currentContextExecutor(e);
  }

  public abstract Context unwrap();
}
