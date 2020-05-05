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

import io.propagation.Context;
import io.propagation.UntypedContext;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DefaultThreadContext extends ThreadContext {
  private static final Logger log = Logger.getLogger(DefaultThreadContext.class.getName());

  // Long chains of contexts are suspicious and usually indicate a misuse of Context.
  // The threshold is arbitrarily chosen.
  // VisibleForTesting
  static final int CONTEXT_DEPTH_WARN_THRESH = 1000;

  static final ThreadContext EMPTY = new DefaultThreadContext();

  private final Context context;
  // The number parents between this context and the root context.
  final int generation;

  private DefaultThreadContext(Context context, DefaultThreadContext parent) {
    this.context = context;
    this.generation = parent.generation + 1;
    validateGeneration(generation);
  }

  /** Construct for {@link #EMPTY}. */
  private DefaultThreadContext() {
    this.context = Context.empty();
    this.generation = 0;
    validateGeneration(generation);
  }

  // this is used by ThreadContext.of(Context)
  DefaultThreadContext(Context context) {
    this.context = context;
    this.generation = 0;
    validateGeneration(generation);
  }

  @Override
  public <V1> ThreadContext withValue(Context.Key<V1> k1, V1 v1) {
    return new DefaultThreadContext(context.withValue(k1, v1), this);
  }

  @Override
  public <V1, V2> ThreadContext withValues(Context.Key<V1> k1, V1 v1, Context.Key<V2> k2, V2 v2) {
    return new DefaultThreadContext(context.withValues(k1, v1, k2, v2), this);
  }

  @Override
  public <V1, V2, V3> ThreadContext withValues(
      Context.Key<V1> k1, V1 v1, Context.Key<V2> k2, V2 v2, Context.Key<V3> k3, V3 v3) {
    return new DefaultThreadContext(context.withValues(k1, v1, k2, v2, k3, v3), this);
  }

  @Override
  public <V1, V2, V3, V4> ThreadContext withValues(
      Context.Key<V1> k1,
      V1 v1,
      Context.Key<V2> k2,
      V2 v2,
      Context.Key<V3> k3,
      V3 v3,
      Context.Key<V4> k4,
      V4 v4) {
    return new DefaultThreadContext(context.withValues(k1, v1, k2, v2, k3, v3, k4, v4), this);
  }

  @Override
  public ThreadContext attach() {
    return ThreadBinding.attach(this);
  }

  @Override
  public void detach(ThreadContext toAttach) {
    checkNotNull(toAttach, "toAttach");
    ThreadBinding.detach(this, toAttach);
  }

  @Override
  public void run(Runnable r) {
    ThreadBinding.run(this, r);
  }

  @Override
  public <V> V call(Callable<V> c) throws Exception {
    return ThreadBinding.call(this, c);
  }

  @Override
  public Runnable wrapRunnable(Runnable r) {
    return ThreadBinding.wrap(this, r);
  }

  @Override
  public <C> Callable<C> wrapCallable(Callable<C> c) {
    return ThreadBinding.wrap(this, c);
  }

  @Override
  public Executor fixedContextExecutor(final Executor e) {
    return ThreadBinding.fixedContextExecutor(this, e);
  }

  @Override
  public UntypedContext toUntyped() {
    return context.toUntyped();
  }

  @Override
  protected <T> T get(Context.Key<T> key) {
    return key.get(context);
  }

  private static void checkNotNull(Object reference, String errorMessage) {
    if (reference == null) {
      throw new NullPointerException(errorMessage);
    }
  }

  /**
   * If the ancestry chain length is unreasonably long, then print an error to the log and record
   * the stack trace.
   */
  private static void validateGeneration(int generation) {
    if (generation == CONTEXT_DEPTH_WARN_THRESH) {
      log.log(
          Level.SEVERE,
          "Context ancestry chain length is abnormally long. "
              + "This suggests an error in application code. "
              + "Length exceeded: "
              + CONTEXT_DEPTH_WARN_THRESH,
          new Exception());
    }
  }
}
