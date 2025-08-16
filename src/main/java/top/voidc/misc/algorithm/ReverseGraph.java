package top.voidc.misc.algorithm;

import top.voidc.misc.ds.ChilletGraph;

/**
 * 构建有向图的反向图。
 */
public class ReverseGraph {
    /**
     * 反转图的边。
     *
     * @param graph 原图
     * @return 反向图
     */
    public static <T> ChilletGraph<T> reverse(ChilletGraph<T> graph) {
        ChilletGraph<T> reversedGraph = new ChilletGraph<>();
        for (int i = 0; i < graph.getNodeCount(); i++) {
            T node = graph.getNodeValue(i);
            reversedGraph.createNewNode(node);
        }
        for (int i = 0; i < graph.getNodeCount(); i++) {
            T node = graph.getNodeValue(i);
            for (Integer neighborId : graph.getNeighbors(i)) {
                T neighbor = graph.getNodeValue(neighborId);
                reversedGraph.addEdge(neighbor, node);
            }
        }
        return reversedGraph;
    }
}
