package top.voidc.misc.ds;

import java.util.HashMap;
import java.util.Map;

/**
 * 泛型并查集（Union-Find Set）实现，支持路径压缩与按秩合并优化。
 * 可用于处理集合划分、图连通性等问题。
 *
 * @param <T> 元素类型，必须实现 equals 和 hashCode 方法
 * @author ChatGPT
 */
public class UnionFindSet<T> {
    private final Map<T, T> parent = new HashMap<>();
    private final Map<T, Integer> rank = new HashMap<>();

    /**
     * 初始化一个新的元素集合。若元素已存在则不进行任何操作。
     *
     * @param item 待初始化的元素
     */
    public void makeSet(T item) {
        if (!parent.containsKey(item)) {
            parent.put(item, item);
            rank.put(item, 0);
        }
    }

    /**
     * 查找元素所在集合的代表（根），并进行路径压缩以加速后续查询。
     * 若元素尚未加入集合，将自动调用 makeSet 进行初始化。
     *
     * @param item 要查找的元素
     * @return 元素所在集合的代表元素
     */
    public T find(T item) {
        if (!parent.containsKey(item)) {
            makeSet(item);
        }

        T root = item;
        // 查找根节点
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }

        // 路径压缩
        T current = item;
        while (!current.equals(root)) {
            T next = parent.get(current);
            parent.put(current, root);
            current = next;
        }

        return root;
    }

    /**
     * 合并两个元素所在的集合。若它们已经属于同一集合，则不进行操作。
     * 使用按秩合并策略以减少树的高度。
     *
     * @param a 第一个元素
     * @param b 第二个元素
     */
    public void union(T a, T b) {
        T rootA = find(a);
        T rootB = find(b);
        if (rootA.equals(rootB)) return;

        int rankA = rank.get(rootA);
        int rankB = rank.get(rootB);

        if (rankA < rankB) {
            parent.put(rootA, rootB);
        } else {
            parent.put(rootB, rootA);
            if (rankA == rankB) {
                rank.put(rootA, rankA + 1);
            }
        }
    }

    /**
     * 判断两个元素是否属于同一集合。
     * 若元素尚未加入集合，将自动初始化。
     *
     * @param a 第一个元素
     * @param b 第二个元素
     * @return 若属于同一集合则返回 true，否则返回 false
     */
    public boolean connected(T a, T b) {
        return find(a).equals(find(b));
    }
}
