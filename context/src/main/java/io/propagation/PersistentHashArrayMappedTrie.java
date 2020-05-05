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

package io.propagation;

import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * A persistent (copy-on-write) hash tree/trie. Collisions are handled linearly. Delete is not
 * supported, but replacement is. The implementation favors simplicity and low memory allocation
 * during insertion. Although the asymptotics are good, it is optimized for small sizes like less
 * than 20; "unbelievably large" would be 100.
 *
 * <p>Inspired by popcnt-based compression seen in Ideal Hash Trees, Phil Bagwell (2000). The rest
 * of the implementation is ignorant of/ignores the paper.
 */
final class PersistentHashArrayMappedTrie {

  private PersistentHashArrayMappedTrie() {}

  /** Returns the value with the specified key, or {@code null} if it does not exist. */
  @Nullable
  static Object get(Node root, Object key) {
    if (root == null) {
      return null;
    }
    return root.get(key, key.hashCode(), 0);
  }

  /** Returns a new root {@code Node} where the key is set to the specified value. */
  static <K, V> Node put(Node root, K key, V value) {
    if (root == null) {
      return new Leaf(key, value);
    } else {
      return root.put(key, value, key.hashCode(), 0);
    }
  }

  // Not actually annotated to avoid depending on guava
  // @VisibleForTesting
  static final class Leaf extends Node {
    private final Object key;
    private final Object value;

    Leaf(Object key, Object value) {
      this.key = key;
      this.value = value;
    }

    @Override
    int size() {
      return 1;
    }

    @Override
    @Nullable
    Object get(Object key, int hash, int bitsConsumed) {
      if (this.key == key) {
        return value;
      } else {
        return null;
      }
    }

    @Override
    Node put(Object key, Object value, int hash, int bitsConsumed) {
      int thisHash = this.key.hashCode();
      if (thisHash != hash) {
        // Insert
        return CompressedIndex.combine(new Leaf(key, value), hash, this, thisHash, bitsConsumed);
      } else if (this.key == key) {
        // Replace
        return new Leaf(key, value);
      } else {
        // Hash collision
        return new CollisionLeaf(this.key, this.value, key, value);
      }
    }

    @Override
    public String toString() {
      return String.format("Leaf(key=%s value=%s)", key, value);
    }
  }

  // Not actually annotated to avoid depending on guava
  // @VisibleForTesting
  static final class CollisionLeaf extends Node {
    // All keys must have same hash, but not have the same reference
    private final Object[] keys;
    private final Object[] values;

    // Not actually annotated to avoid depending on guava
    // @VisibleForTesting
    CollisionLeaf(Object key1, Object value1, Object key2, Object value2) {
      this(new Object[] {key1, key2}, new Object[] {value1, value2});
      assert key1 != key2;
      assert key1.hashCode() == key2.hashCode();
    }

    private CollisionLeaf(Object[] keys, Object[] values) {
      this.keys = keys;
      this.values = values;
    }

    @Override
    int size() {
      return values.length;
    }

    @Override
    @Nullable
    Object get(Object key, int hash, int bitsConsumed) {
      for (int i = 0; i < keys.length; i++) {
        if (keys[i] == key) {
          return values[i];
        }
      }
      return null;
    }

    @Override
    Node put(Object key, Object value, int hash, int bitsConsumed) {
      int thisHash = keys[0].hashCode();
      int keyIndex;
      if (thisHash != hash) {
        // Insert
        return CompressedIndex.combine(new Leaf(key, value), hash, this, thisHash, bitsConsumed);
      } else if ((keyIndex = indexOfKey(key)) != -1) {
        // Replace
        Object[] newKeys = Arrays.copyOf(keys, keys.length);
        Object[] newValues = Arrays.copyOf(values, keys.length);
        newKeys[keyIndex] = key;
        newValues[keyIndex] = value;
        return new CollisionLeaf(newKeys, newValues);
      } else {
        // Yet another hash collision
        Object[] newKeys = Arrays.copyOf(keys, keys.length + 1);
        Object[] newValues = Arrays.copyOf(values, keys.length + 1);
        newKeys[keys.length] = key;
        newValues[keys.length] = value;
        return new CollisionLeaf(newKeys, newValues);
      }
    }

    // -1 if not found
    private int indexOfKey(Object key) {
      for (int i = 0; i < keys.length; i++) {
        if (keys[i] == key) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public String toString() {
      StringBuilder valuesSb = new StringBuilder();
      valuesSb.append("CollisionLeaf(");
      for (int i = 0; i < values.length; i++) {
        valuesSb.append("(key=").append(keys[i]).append(" value=").append(values[i]).append(") ");
      }
      return valuesSb.append(")").toString();
    }
  }

  // Not actually annotated to avoid depending on guava
  // @VisibleForTesting
  static final class CompressedIndex extends Node {
    private static final int BITS = 5;
    private static final int BITS_MASK = 0x1F;

    final Node[] values;
    private final int size;
    final int bitmap;

    private CompressedIndex(int bitmap, Node[] values, int size) {
      this.bitmap = bitmap;
      this.values = values;
      this.size = size;
    }

    @Override
    int size() {
      return size;
    }

    @Override
    @Nullable
    Object get(Object key, int hash, int bitsConsumed) {
      int indexBit = indexBit(hash, bitsConsumed);
      if ((bitmap & indexBit) == 0) {
        return null;
      }
      int compressedIndex = compressedIndex(indexBit);
      return values[compressedIndex].get(key, hash, bitsConsumed + BITS);
    }

    @Override
    Node put(Object key, Object value, int hash, int bitsConsumed) {
      int indexBit = indexBit(hash, bitsConsumed);
      int compressedIndex = compressedIndex(indexBit);
      if ((bitmap & indexBit) == 0) {
        // Insert
        int newBitmap = bitmap | indexBit;
        Node[] newValues = new Node[values.length + 1];
        System.arraycopy(values, 0, newValues, 0, compressedIndex);
        newValues[compressedIndex] = new Leaf(key, value);
        System.arraycopy(
            values,
            compressedIndex,
            newValues,
            compressedIndex + 1,
            values.length - compressedIndex);
        return new CompressedIndex(newBitmap, newValues, size() + 1);
      } else {
        // Replace
        Node[] newValues = Arrays.copyOf(values, values.length);
        newValues[compressedIndex] =
            values[compressedIndex].put(key, value, hash, bitsConsumed + BITS);
        int newSize = size();
        newSize += newValues[compressedIndex].size();
        newSize -= values[compressedIndex].size();
        return new CompressedIndex(bitmap, newValues, newSize);
      }
    }

    static <K, V> Node combine(Node node1, int hash1, Node node2, int hash2, int bitsConsumed) {
      assert hash1 != hash2;
      int indexBit1 = indexBit(hash1, bitsConsumed);
      int indexBit2 = indexBit(hash2, bitsConsumed);
      if (indexBit1 == indexBit2) {
        Node node = combine(node1, hash1, node2, hash2, bitsConsumed + BITS);
        Node[] values = new Node[] {node};
        return new CompressedIndex(indexBit1, values, node.size());
      } else {
        // Make node1 the smallest
        if (uncompressedIndex(hash1, bitsConsumed) > uncompressedIndex(hash2, bitsConsumed)) {
          Node nodeCopy = node1;
          node1 = node2;
          node2 = nodeCopy;
        }
        Node[] values = new Node[] {node1, node2};
        return new CompressedIndex(indexBit1 | indexBit2, values, node1.size() + node2.size());
      }
    }

    @Override
    public String toString() {
      StringBuilder valuesSb = new StringBuilder();
      valuesSb
          .append("CompressedIndex(")
          .append(String.format("bitmap=%s ", Integer.toBinaryString(bitmap)));
      for (Node value : values) {
        valuesSb.append(value).append(" ");
      }
      return valuesSb.append(")").toString();
    }

    private int compressedIndex(int indexBit) {
      return Integer.bitCount(bitmap & (indexBit - 1));
    }

    private static int uncompressedIndex(int hash, int bitsConsumed) {
      return (hash >>> bitsConsumed) & BITS_MASK;
    }

    private static int indexBit(int hash, int bitsConsumed) {
      int uncompressedIndex = uncompressedIndex(hash, bitsConsumed);
      return 1 << uncompressedIndex;
    }
  }

  static final class EmptyNode extends Context {

    static final Context INSTANCE = new EmptyNode();

    private EmptyNode() {}

    @Override
    public <V1> Context withValue(Key<V1> k1, V1 v1) {
      return PersistentHashArrayMappedTrie.put(null, k1, v1);
    }

    @Override
    public <V1, V2> Context withValues(Key<V1> k1, V1 v1, Key<V2> k2, V2 v2) {
      return PersistentHashArrayMappedTrie.withValues(null, k1, v1, k2, v2);
    }

    @Override
    public <V1, V2, V3> Context withValues(
        Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3) {
      return PersistentHashArrayMappedTrie.withValues(null, k1, v1, k2, v2, k3, v3);
    }

    @Override
    public <V1, V2, V3, V4> Context withValues(
        Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3, Key<V4> k4, V4 v4) {
      return PersistentHashArrayMappedTrie.withValues(null, k1, v1, k2, v2, k3, v3, k4, v4);
    }

    @Override
    public UntypedContext toUntyped() {
      return UntypedContext.empty();
    }

    @Override
    @Nullable
    protected <T> T get(Key<T> key) {
      return null;
    }
  }

  abstract static class Node extends Context {
    @Override
    public <V1> Context withValue(Key<V1> k1, V1 v1) {
      return PersistentHashArrayMappedTrie.put(this, k1, v1);
    }

    @Override
    public <V1, V2> Context withValues(Key<V1> k1, V1 v1, Key<V2> k2, V2 v2) {
      return PersistentHashArrayMappedTrie.withValues(this, k1, v1, k2, v2);
    }

    @Override
    public <V1, V2, V3> Context withValues(
        Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3) {
      return PersistentHashArrayMappedTrie.withValues(this, k1, v1, k2, v2, k3, v3);
    }

    @Override
    public <V1, V2, V3, V4> Context withValues(
        Key<V1> k1, V1 v1, Key<V2> k2, V2 v2, Key<V3> k3, V3 v3, Key<V4> k4, V4 v4) {
      return PersistentHashArrayMappedTrie.withValues(this, k1, v1, k2, v2, k3, v3, k4, v4);
    }

    @Override
    public UntypedContext toUntyped() {
      return new DefaultUntypedContext(this);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T get(Key<T> key) {
      return (T) PersistentHashArrayMappedTrie.get(this, key);
    }

    abstract Object get(Object key, int hash, int bitsConsumed);

    abstract Node put(Object key, Object value, int hash, int bitsConsumed);

    abstract int size();
  }

  static Node withValues(Node root, Object k1, Object v1, Object k2, Object v2) {
    Node newKeyValueEntries = PersistentHashArrayMappedTrie.put(root, k1, v1);
    return PersistentHashArrayMappedTrie.put(newKeyValueEntries, k2, v2);
  }

  static Node withValues(
      Node root, Object k1, Object v1, Object k2, Object v2, Object k3, Object v3) {
    Node newKeyValueEntries = PersistentHashArrayMappedTrie.put(root, k1, v1);
    newKeyValueEntries = PersistentHashArrayMappedTrie.put(newKeyValueEntries, k2, v2);
    return PersistentHashArrayMappedTrie.put(newKeyValueEntries, k3, v3);
  }

  static Node withValues(
      Node root,
      Object k1,
      Object v1,
      Object k2,
      Object v2,
      Object k3,
      Object v3,
      Object k4,
      Object v4) {
    Node newKeyValueEntries = PersistentHashArrayMappedTrie.put(root, k1, v1);
    newKeyValueEntries = PersistentHashArrayMappedTrie.put(newKeyValueEntries, k2, v2);
    newKeyValueEntries = PersistentHashArrayMappedTrie.put(newKeyValueEntries, k3, v3);
    return PersistentHashArrayMappedTrie.put(newKeyValueEntries, k4, v4);
  }
}
