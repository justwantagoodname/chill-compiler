package top.voidc.misc.ds;

import java.util.*;

/**
 * 疾旋鼬图 将节点映射到整数ID上然后使用邻接表存储边
 * @param <T> 图上节点保存的数据类型
 */
public class ChilletGraph<T> {
    private final BiMap<T, Integer> nodeMap = new BiMap<>();
    private final ArrayList<ArrayList<Integer>> adjacencyList;
    private int edgeCount;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    public ChilletGraph() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public ChilletGraph(int initialCapacity) {
        adjacencyList = new ArrayList<>(initialCapacity);
    }

    /**
     * 创建一个新的节点，并返回其 ID
     * 如果节点已存在，则返回其现有 ID
     * @param nodeValue 节点的值
     * @return 节点的 ID
     */
    public Integer createNewNode(T nodeValue) {
        Objects.requireNonNull(nodeValue);
        Integer existingId = nodeMap.getValue(nodeValue);
        if (existingId != null) {
            return existingId;
        }

        int newId = nodeMap.size();
        nodeMap.put(nodeValue, newId);
        adjacencyList.add(new ArrayList<>());
        return newId;
    }

    /**
     * 批量创建新节点
     * @param nodeValues 节点值集合
     * @return 节点ID列表
     */
    public List<Integer> createNewNodes(Collection<T> nodeValues) {
        Objects.requireNonNull(nodeValues);
        List<Integer> nodeIds = new ArrayList<>(nodeValues.size());
        
        // 预分配空间
        int potentialNewNodes = 0;
        for (T value : nodeValues) {
            if (!nodeMap.containsKey(value)) {
                potentialNewNodes++;
            }
        }
        ensureCapacity(nodeMap.size() + potentialNewNodes);

        // 批量创建节点
        for (T value : nodeValues) {
            nodeIds.add(createNewNode(value));
        }
        return nodeIds;
    }

    /**
     * 添加一条 u 和 v 之间的单向边
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
     * 添加一条 u 和 v 之间的单向边
     * @param u 节点 1 的 ID
     * @param v 节点 2 的 ID
     */
    public void addEdge(int u, int v) {
        if (u < 0 || u >= nodeMap.size() || v < 0 || v >= nodeMap.size()) {
            throw new IllegalArgumentException("Invalid node ID.");
        }
        if (!adjacencyList.get(u).contains(v)) {
            adjacencyList.get(u).add(v);
            edgeCount++;
        }
    }

    /**
     * 从一个节点到多个节点批量添加边（使用节点值）
     * @param from 起始节点
     * @param to 目标节点集合
     */
    public void addEdges(T from, Collection<T> to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        
        Integer fromId = nodeMap.getValue(from);
        if (fromId == null) {
            throw new IllegalArgumentException("Source node does not exist in the graph.");
        }
        
        int[] toIds = getNodeIds(to);
        addEdges(fromId, toIds);
    }

    /**
     * 从一个节点到多个节点批量添加边（使用节点ID）
     * @param from 起始节点ID
     * @param toNodes 目标节点ID数组
     */
    public void addEdges(int from, int[] toNodes) {
        if (from < 0 || from >= nodeMap.size()) {
            throw new IllegalArgumentException("Invalid source node ID.");
        }
        
        ArrayList<Integer> adjacentList = adjacencyList.get(from);
        for (int to : toNodes) {
            if (to < 0 || to >= nodeMap.size()) {
                throw new IllegalArgumentException("Invalid target node ID: " + to);
            }
            if (!adjacentList.contains(to)) {
                adjacentList.add(to);
                edgeCount++;
            }
        }
    }

    /**
     * 确保图有足够的容量容纳指定数量的节点
     * @param minCapacity 最小所需容量
     */
    public void ensureCapacity(int minCapacity) {
        while (adjacencyList.size() < minCapacity) {
            adjacencyList.add(new ArrayList<>());
        }
    }

    /**
     * 获取节点的邻接点列表
     * @param nodeId 节点ID
     * @return 邻接点ID列表
     */
    public List<Integer> getNeighbors(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeMap.size()) {
            throw new IllegalArgumentException("Invalid node ID");
        }
        return Collections.unmodifiableList(adjacencyList.get(nodeId));
    }

    /**
     * 获取邻接表的二维数组表示，用于JNI交互
     * @return 邻接表的二维数组表示
     */
    public int[][] getAdjacencyMatrix() {
        int n = nodeMap.size();
        int[][] matrix = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> neighbors = adjacencyList.get(i);
            matrix[i] = new int[neighbors.size()];
            for (int j = 0; j < neighbors.size(); j++) {
                matrix[i][j] = neighbors.get(j);
            }
        }
        return matrix;
    }

    /**
     * 获取一组节点的ID
     * @param nodes 节点集合
     * @return 节点ID数组
     */
    public int[] getNodeIds(Collection<T> nodes) {
        int[] ids = new int[nodes.size()];
        int i = 0;
        for (T node : nodes) {
            Integer id = nodeMap.getValue(node);
            if (id == null) {
                throw new IllegalArgumentException("Node does not exist: " + node);
            }
            ids[i++] = id;
        }
        return ids;
    }

    /**
     * 获取一个节点的ID
     * @param node 节点
     * @return 节点ID
     */
    public int getNodeId(T node) {
        Integer id = nodeMap.getValue(node);
        if (id == null) {
            throw new IllegalArgumentException("Node does not exist: " + node);
        }
        return id;
    }

    /**
     * 获取节点数量
     * @return 节点数量
     */
    public int getNodeCount() {
        return nodeMap.size();
    }

    /**
     * 获取边数量
     * @return 边数量
     */
    public int getEdgeCount() {
        return edgeCount;
    }

    /**
     * 根据节点ID获取节点值
     * @param nodeId 节点ID
     * @return 节点值
     * @throws IllegalArgumentException 如果节点ID无效
     */
    public T getNodeValue(int nodeId) {
        if (nodeId < 0 || nodeId >= nodeMap.size()) {
            throw new IllegalArgumentException("Invalid node ID: " + nodeId);
        }
        T value = nodeMap.getKey(nodeId);
        if (value == null) {
            throw new IllegalStateException("No value found for node ID: " + nodeId);
        }
        return value;
    }

    /**
     * <a href="https://csacademy.com/app/graph_editor/">Graph Editor</a>
     * @return 图的编辑器字符串表示
     * 该字符串可以直接在图编辑器中使用
     */
    public String getGraphEditorString() {
        var sb = new StringBuilder();
        for (var i = 0; i < adjacencyList.size(); i++) {
            for (var neighbor : adjacencyList.get(i)) {
                sb.append(i).append(" ").append(neighbor).append("\n");
            }
        }
        
        return sb.toString();
    }
}
