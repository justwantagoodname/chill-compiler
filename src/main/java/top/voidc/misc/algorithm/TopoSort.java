package top.voidc.misc.algorithm;

import top.voidc.misc.Log;
import top.voidc.misc.ds.ChilletGraph;

import java.util.List;


import java.util.*;

/**
 * @author Gemini 2.5 Pro
 */
public class TopoSort {

    /**
     * Performs a topological sort on a directed graph that may contain self-loops.
     * The graph is expected to be a Directed Acyclic Graph (DAG) otherwise,
     * with the only cycles being self-loops, which are ignored during the sort.
     *
     * @param <T>   the type of the data stored in the graph nodes
     * @param graph the input graph to be sorted
     * @return a list of node values in topologically sorted order
     */
    public static <T> List<T> topoSort(ChilletGraph<T> graph) {
        int nodeCount = graph.getNodeCount();
        int[] inDegree = new int[nodeCount];
        List<T> sortedOrder = new ArrayList<>(nodeCount);
        Queue<Integer> queue = new LinkedList<>();

        // 1. Calculate the in-degree of each node.
        // An edge from u to v increases the in-degree of v.
        for (int u = 0; u < nodeCount; u++) {
            List<Integer> neighbors = graph.getNeighbors(u);
            for (int v : neighbors) {
                // Ignore self-loops
                if (u != v) {
                    inDegree[v]++;
                }
            }
        }

        // 2. Initialize the queue with all nodes that have an in-degree of 0.
        for (int i = 0; i < nodeCount; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }

        // 3. Process the nodes in the queue.
        while (!queue.isEmpty()) {
            int u = queue.poll();
            sortedOrder.add(graph.getNodeValue(u));

            // For each neighbor of the current node, decrease its in-degree.
            // If a neighbor's in-degree becomes 0, add it to the queue.
            for (int v : graph.getNeighbors(u)) {
                // Ignore self-loops
                if (u != v) {
                    inDegree[v]--;
                    if (inDegree[v] == 0) {
                        queue.offer(v);
                    }
                }
            }
        }

        // If the sorted order does not contain all nodes, there must be a cycle
        // (other than the self-loops we are ignoring).
        if (sortedOrder.size() != nodeCount) {
            // This part can be used to handle cycle detection if necessary,
            // but based on the problem description, we assume the only cycles are self-loops.
            // For a robust implementation, you might throw an exception here.
            Log.w("Warning: The graph has a cycle other than self-loops, or is disconnected. " +
                    "Topological sort may be incomplete.");
        }

        return sortedOrder;
    }
}
