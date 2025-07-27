package top.voidc.misc.ds;

import java.util.Objects;

/**
 * 疾旋鼬图
 * @param <T> 图上节点保存的数据类型
 */
public class ChilletGraph<T> {

    private int nextNodeId = 0;
    private final BiMap<T, Integer> nodeMap = new BiMap<>();

    public Integer createNewNode(T name) {
        if (nodeMap.containsKey(name)) {
            return nodeMap.getValue(name);
        }
        nodeMap.put(name, nextNodeId++);
        return nextNodeId - 1;
    }

    /**
     * 添加一条 u 和 v 之间的 双向边
     * @param u 节点 1
     * @param v 节点 2
     */
    public void addEdge(T u, T v) {
        Objects.requireNonNull(u);
        Objects.requireNonNull(v);
        Integer uId = nodeMap.getValue(u);
        Integer vId = nodeMap.getValue(v);
        if (uId == null || vId == null) {
            throw new IllegalArgumentException("One or both nodes do not exist in the graph.");
        }
        addEdge(uId, vId);
    }

    /**
     * 添加一条 u 和 v 之间的 双向边
     * @param u 节点 1 的 ID
     * @param v 节点 2 的 ID
     */
    public void addEdge(int u, int v) {

    }
}
