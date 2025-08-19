package top.voidc.misc.algorithm;

import top.voidc.misc.ds.ChilletGraph;

import java.util.List;

import java.util.*;

/**
 * Tarjan 求强连通分量
 * @author Gemini Pro 2.5
 */
public class TarjanSCC {

    /**
     * 强连通分量 (Strongly Connected Component)
     * Represents a single SCC, containing an ID and the list of nodes within it.
     *
     * @param <T> The data type of the nodes in the graph.
     */
    public record SCC<T>(int id, List<T> nodes) {
        public SCC(int id, List<T> nodes) {
            this.id = id;
            this.nodes = Collections.unmodifiableList(nodes);
        }

        @Override
        public String toString() {
            return "SCC " + id + ": " + nodes;
        }
    }

    /**
     * Finds all strongly connected components in a directed graph using Tarjan's algorithm.
     * @param graph The input graph of type ChilletGraph.
     * @param <T> The data type of the nodes in the graph.
     * @return A list of all strongly connected components found in the graph.
     */
    public static <T> List<SCC<T>> tarjan(ChilletGraph<T> graph) {
        int n = graph.getNodeCount();
        if (n == 0) {
            return new ArrayList<>();
        }

        // dfn (discovery time) of each node
        int[] dfn = new int[n];
        // low-link value of each node
        int[] low = new int[n];
        // To check if a node is unvisited
        Arrays.fill(dfn, -1);

        // Stack to keep track of nodes in the current path
        Deque<Integer> stack = new ArrayDeque<>();
        // To quickly check if a node is on the stack
        boolean[] onStack = new boolean[n];

        // List to store the resulting SCCs
        List<SCC<T>> result = new ArrayList<>();

        // A single-element array to simulate a mutable integer (pass-by-reference) for time and sccId
        int[] time = {0};
        int[] sccId = {0};

        // Iterate through all nodes to handle disconnected graphs
        for (int i = 0; i < n; i++) {
            if (dfn[i] == -1) { // If node i is unvisited
                findSccsRecursive(i, graph, dfn, low, stack, onStack, time, sccId, result);
            }
        }

        return result;
    }

    /**
     * A recursive helper function to perform DFS for Tarjan's algorithm.
     * @param u The current node's ID to visit.
     * @param graph The graph.
     * @param dfn Array of discovery times.
     * @param low Array of low-link values.
     * @param stack The recursion stack.
     * @param onStack Boolean array to track nodes on the stack.
     * @param time The current discovery time.
     * @param sccId The ID for the next SCC to be found.
     * @param result The list to store the found SCCs.
     * @param <T> The data type of the nodes.
     */
    private static <T> void findSccsRecursive(int u, ChilletGraph<T> graph, int[] dfn, int[] low, Deque<Integer> stack, boolean[] onStack, int[] time, int[] sccId, List<SCC<T>> result) {
        // Set the discovery time and low-link value for the current node 'u'
        dfn[u] = low[u] = time[0]++;
        stack.push(u);
        onStack[u] = true;

        // Explore neighbors of 'u'
        for (int v : graph.getNeighbors(u)) {
            if (dfn[v] == -1) { // If neighbor 'v' has not been visited
                findSccsRecursive(v, graph, dfn, low, stack, onStack, time, sccId, result);
                // After recursion, update low-link value of 'u'
                low[u] = Math.min(low[u], low[v]);
            } else if (onStack[v]) { // If 'v' is on the stack, it's a back-edge
                // Update low-link value of 'u' based on discovery time of 'v'
                low[u] = Math.min(low[u], dfn[v]);
            }
        }

        // If 'u' is the root of an SCC (its low-link value is equal to its discovery time)
        if (low[u] == dfn[u]) {
            List<T> currentSccNodes = new ArrayList<>();
            while (true) {
                int node = stack.pop();
                onStack[node] = false;
                currentSccNodes.add(graph.getNodeValue(node));
                if (node == u) {
                    break;
                }
            }
            // A new SCC is found
            result.add(new SCC<>(sccId[0]++, currentSccNodes));
        }
    }
}