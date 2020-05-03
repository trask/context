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

public abstract class Context {

  /**
   * Create a {@link Key} with the given debug name. Multiple different keys may have the same name;
   * the name is intended for debugging purposes and does not impact behavior.
   */
  public static <T> Key<T> key(String name) {
    return new Key<>(name);
  }

  /**
   * Create a {@link Key} with the given debug name and default value. Multiple different keys may
   * have the same name; the name is intended for debugging purposes and does not impact behavior.
   */
  public static <T> Key<T> keyWithDefault(String name, T defaultValue) {
    return new Key<>(name, defaultValue);
  }

  public static Context empty() {
    return PersistentHashArrayMappedTrie.EmptyNode.INSTANCE;
  }

  /**
   * Create a new context with the given key value set.
   *
   * <p>Note that multiple calls to {@code #withValue} can be chained together. That is,
   *
   * <pre>
   * context.withValues(K1, V1, K2, V2);
   * // is the same as
   * context.withValue(K1, V1).withValue(K2, V2);
   * </pre>
   *
   * <p>Nonetheless, {@link Context} should not be treated like a general purpose map with a large
   * number of keys and values — combine multiple related items together into a single key instead
   * of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract <V1> Context withValue(Key<V1> k1, V1 v1);

  /** Create a new context with the given key value set. */
  public abstract <V1, V2> Context withValues(Key<V1> k1, V1 v1, Key<V2> k2, V2 v2);

  /** Create a new context with the given key value set. */
  public abstract <V1, V2, V3> Context withValues(
      Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3);

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
   * <p>Nonetheless, {@link Context} should not be treated like a general purpose map with a large
   * number of keys and values — combine multiple related items together into a single key instead
   * of separating them. But if the items are unrelated, have separate keys for them.
   */
  public abstract <V1, V2, V3, V4> Context withValues(
      Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3, Key<V4> k4, V4 v4);

  protected abstract <T> T get(Key<T> key);

  /** Key for indexing values stored in a context. */
  public static class Key<T> {
    private final String name;
    private final T defaultValue;

    protected Key(String name) {
      this(name, null);
    }

    protected Key(String name, T defaultValue) {
      this.name = checkNotNull(name, "name");
      this.defaultValue = defaultValue;
    }

    /** Get the value from the specified context for this key. */
    public T get(Context context) {
      T value = context.get(this);
      return value == null ? defaultValue : value;
    }

    @Override
    public String toString() {
      return name;
    }

    private static <T> T checkNotNull(T reference, String errorMessage) {
      if (reference == null) {
        throw new NullPointerException(errorMessage);
      }
      return reference;
    }
  }
}
