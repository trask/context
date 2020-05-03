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

import javax.annotation.Nullable;

public class DefaultUntypedContext extends UntypedContext {

  static final UntypedContext EMPTY = new DefaultUntypedContext();

  @Nullable private final PersistentHashArrayMappedTrie.Node keyValueEntries;

  // this is used by UntypedContext.wrap(Context)
  DefaultUntypedContext(Context context) {
    this.keyValueEntries = null;
  }

  /** Construct for {@link #EMPTY}. */
  private DefaultUntypedContext() {
    this.keyValueEntries = null;
  }

  @Override
  @Nullable
  public Object get(Object key) {
    return PersistentHashArrayMappedTrie.get(keyValueEntries, key);
  }

  @Override
  public UntypedContext withValue(Object k1, Object v1) {
    return new DefaultUntypedContext(PersistentHashArrayMappedTrie.put(keyValueEntries, k1, v1));
  }

  @Override
  public UntypedContext withValues(Object k1, Object v1, Object k2, Object v2) {
    return new DefaultUntypedContext(
        PersistentHashArrayMappedTrie.withValues(keyValueEntries, k1, v1, k2, v2));
  }

  @Override
  public UntypedContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3) {
    return new DefaultUntypedContext(
        PersistentHashArrayMappedTrie.withValues(keyValueEntries, k1, v1, k2, v2, k3, v3));
  }

  @Override
  public UntypedContext withValues(
      Object k1, Object v1, Object k2, Object v2, Object k3, Object v3, Object k4, Object v4) {
    return new DefaultUntypedContext(
        PersistentHashArrayMappedTrie.withValues(keyValueEntries, k1, v1, k2, v2, k3, v3, k4, v4));
  }

  @Override
  public Context toTyped() {
    return keyValueEntries == null ? Context.empty() : keyValueEntries;
  }
}
