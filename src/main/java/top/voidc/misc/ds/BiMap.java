package top.voidc.misc.ds;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 双向映射（BiMap）实现，可以高效查询键到值的映射以及值到键的映射。
 */
public class BiMap<K, V> {
    private final Map<K, V> forward = new HashMap<>();
    private final Map<V, K> reverse = new HashMap<>();

    /**
     * Associates the specified key with the specified value in this map.
     * If the key or value already exists, the old mappings are replaced.
     */
    public V put(K key, V value) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");

        // Remove existing mappings to maintain one-to-one
        if (forward.containsKey(key)) {
            V oldValue = forward.get(key);
            reverse.remove(oldValue);
        }
        if (reverse.containsKey(value)) {
            K oldKey = reverse.get(value);
            forward.remove(oldKey);
        }

        forward.put(key, value);
        reverse.put(value, key);
        return value;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or null if this map contains no mapping for the key.
     */
    public V getValue(K key) {
        return forward.get(key);
    }

    /**
     * Returns the key to which the specified value is mapped,
     * or null if this map contains no mapping for the value.
     */
    public K getKey(V value) {
        return reverse.get(value);
    }

    /**
     * Removes the mapping for a key from this map if present.
     */
    public V removeByKey(K key) {
        V removed = forward.remove(key);
        if (removed != null) {
            reverse.remove(removed);
        }
        return removed;
    }

    /**
     * Removes the mapping for a value from this map if present.
     */
    public K removeByValue(V value) {
        K removed = reverse.remove(value);
        if (removed != null) {
            forward.remove(removed);
        }
        return removed;
    }

    /**
     * Returns true if this map contains a mapping for the specified key.
     */
    public boolean containsKey(K key) {
        return forward.containsKey(key);
    }

    /**
     * Returns true if this map contains a mapping for the specified value.
     */
    public boolean containsValue(V value) {
        return reverse.containsKey(value);
    }

    /**
     * Returns the number of key-value mappings in this map.
     */
    public int size() {
        return forward.size();
    }

    /**
     * Removes all the mappings from this map.
     */
    public void clear() {
        forward.clear();
        reverse.clear();
    }

    /**
     * Returns a Set view of the keys contained in this map.
     */
    public Set<K> keySet() {
        return forward.keySet();
    }

    /**
     * Returns a Collection view of the values contained in this map.
     */
    public Collection<V> values() {
        return forward.values();
    }

    @Override
    public String toString() {
        return "BiMap" + forward;
    }
}
