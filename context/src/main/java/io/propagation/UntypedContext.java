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

package io.propagation;

public abstract class UntypedContext {

  public static UntypedContext empty() {
    return DefaultUntypedContext.EMPTY;
  }

  public abstract Object get(Object key);

  /**
   * Create a new context with the given key value set.
   *
   * <p>Note that multiple calls to {@code #withValue} can be chained together. That is,
   *
   * <pre>
   * context.withValues(k1, v1, k2, v2);
   * // is the same as
   * context.withValue(k1, v1).withValue(k2, v2);
   * </pre>
   *
   * <p>Nonetheless, {@link UntypedContext} should not be treated like a general purpose map with a
   * large number of keys and values — combine multiple related items together into a single key
   * instead of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract UntypedContext withValue(Object k1, Object v1);

  /** Create a new context with the given key value set. */
  public abstract UntypedContext withValues(Object k1, Object v1, Object k2, Object v2);

  /** Create a new context with the given key value set. */
  public abstract UntypedContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3);

  /**
   * Create a new context with the given key value set.
   *
   * <p>For more than 4 key-value pairs, note that multiple calls to {@link #withValue} can be
   * chained together. That is,
   *
   * <pre>
   * context.withValues(k1, v1, k2, v2);
   * // is the same as
   * context.withValue(k1, v1).withValue(k2, v2);
   * </pre>
   *
   * <p>Nonetheless, {@link UntypedContext} should not be treated like a general purpose map with a
   * large number of keys and values — combine multiple related items together into a single key
   * instead of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract UntypedContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3, Object k4, Object v4);

  public abstract Context toTyped();
}
