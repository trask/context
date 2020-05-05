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

/**
 * Defines the mechanisms for attaching and detaching the "current" context. The constructor for
 * extending classes <em>must not</em> trigger any activity that can use ThreadContext, which
 * includes logging, otherwise it can trigger an infinite initialization loop. Extending classes
 * must not assume that only one instance will be created; ThreadContext guarantees it will only use
 * one instance, but it may create multiple and then throw away all but one.
 *
 * <p>The default implementation will put the current context in a {@link ThreadLocal}. If an
 * alternative implementation named {@code
 * io.propagation.thread.override.ThreadContextStorageOverride} exists in the classpath, it will be
 * used instead of the default implementation.
 *
 * <p>This API is <a href="https://github.com/grpc/grpc-java/issues/2462">experimental</a> and
 * subject to change.
 */
public abstract class ThreadContextStorage {
  /**
   * Implements {@link ThreadBinding#attach}.
   *
   * <p>Caution: {@link ThreadBinding#attach} interprets a return value of {@code null} to mean the
   * same thing as {@link ThreadContext#empty}.
   *
   * <p>See also: {@link #current}.
   *
   * @param toAttach the context to be attached
   * @return A {@link ThreadContext} that should be passed back into {@link #detach(ThreadContext,
   *     ThreadContext)} as the {@code toRestore} parameter. {@code null} is a valid return value,
   *     but see caution note.
   */
  public abstract ThreadContext doAttach(ThreadContext toAttach);

  /**
   * Implements {@link ThreadBinding#detach}.
   *
   * @param toDetach the context to be detached. Should be, or be equivalent to, the current context
   *     of the current scope
   * @param toRestore the context to be the current. Should be, or be equivalent to, the context of
   *     the outer scope
   */
  public abstract void detach(ThreadContext toDetach, ThreadContext toRestore);

  /**
   * Implements {@link ThreadBinding#current}.
   *
   * <p>Caution: {@link ThreadBinding#current} interprets a return value of {@code null} to mean the
   * same thing as {@code ThreadContext{@link ThreadContext#empty }}.
   *
   * <p>See also {@link #doAttach(ThreadContext)}.
   *
   * @return The context of the current scope. {@code null} is a valid return value, but see caution
   *     note.
   */
  public abstract ThreadContext current();
}
