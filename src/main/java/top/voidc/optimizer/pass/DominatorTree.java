package top.voidc.optimizer.pass;

import top.voidc.misc.ds.ChilletGraph;
import java.util.*;

/**
 * A class to compute the dominator tree for a directed graph using the Lengauer-Tarjan algorithm.
 * @param <T> The type of data stored in the graph nodes.
 * @author Gemini 2.5 Pro
 */
public class DominatorTree<T> {

    private final ChilletGraph<T> graph;
    private final int entryNodeId;

    // Final results of the algorithm, optimized with arrays.
    // Maps a node ID to its immediate dominator's ID. -1 if it has no dominator.
    private final int[] finalIdom;
    // Maps a node ID to the list of node IDs it immediately dominates.
    private final List<Integer>[] finalDomChildren;

    // Data structures for the Lengauer-Tarjan algorithm
    private final int n; // Number of nodes in the graph
    private final int[] dfsNumber; // DFS number for each node ID
    private final int[] vertex;    // Maps a DFS number back to its node ID
    private final int[] parent;    // Parent of each node in the DFS spanning tree
    private final int[] semi;      // Semi-dominator for each node ID
    private final int[] idom;      // Immediate dominator for each node ID (intermediate)
    private final int[] ancestor;  // Used for the LINK-EVAL forest data structure
    private final int[] label;     // Used for the LINK-EVAL forest data structure
    private final List<List<Integer>> pred;   // Predecessor lists for each node
    private final List<List<Integer>> bucket; // Buckets for semi-dominators
    private int dfsCounter; // Counter for assigning DFS numbers

    /**
     * Constructs a DominatorTree from a ChilletGraph and an entry node ID.
     * This constructor runs the Lengauer-Tarjan algorithm to build the tree.
     * @param graph The input graph.
     * @param entryNodeId The ID of the entry node of the graph.
     */
    @SuppressWarnings("unchecked")
    public DominatorTree(ChilletGraph<T> graph, int entryNodeId) {
        this.graph = Objects.requireNonNull(graph, "Graph cannot be null");
        if (entryNodeId < 0 || entryNodeId >= graph.getNodeCount()) {
            throw new IllegalArgumentException("Invalid entry node ID");
        }
        this.entryNodeId = entryNodeId;
        this.n = graph.getNodeCount();

        // Initialize data structures for the algorithm and results
        this.finalIdom = new int[n];
        this.finalDomChildren = new List[n];
        this.dfsNumber = new int[n];
        this.vertex = new int[n + 1];
        this.parent = new int[n];
        this.semi = new int[n];
        this.idom = new int[n];
        this.ancestor = new int[n];
        this.label = new int[n];
        this.pred = new ArrayList<>(n);
        this.bucket = new ArrayList<>(n);
        this.dfsCounter = 0;

        for (int i = 0; i < n; i++) {
            finalDomChildren[i] = new ArrayList<>();
            pred.add(new ArrayList<>());
            bucket.add(new ArrayList<>());
            parent[i] = -1;
            ancestor[i] = -1;
            // Initialize semi-dominator and label to the node itself
            semi[i] = i;
            label[i] = i;
        }
        Arrays.fill(this.finalIdom, -1); // Initialize with -1 (no dominator)

        buildPredecessors();
        runLengauerTarjan();
        buildResultMaps();
    }

    /**
     * Builds the predecessor lists for every node in the graph.
     */
    private void buildPredecessors() {
        for (int u = 0; u < n; u++) {
            for (int v : graph.getNeighbors(u)) {
                pred.get(v).add(u);
            }
        }
    }

    /**
     * Executes the main logic of the Lengauer-Tarjan algorithm.
     */
    private void runLengauerTarjan() {
        // Step 1: Perform a DFS traversal from the entry node.
        // This computes dfsNumber, parent, and vertex arrays for all reachable nodes.
        dfs(entryNodeId);

        // Process nodes in reverse DFS order, starting from the second to last.
        for (int i = dfsCounter; i >= 2; i--) {
            int w = vertex[i];

            // Step 2: Compute semi-dominators.
            // For each predecessor v of w, find the ancestor u=eval(v) with the
            // minimum semi-dominator, and update semi[w] if necessary.
            for (int v : pred.get(w)) {
                if (dfsNumber[v] > 0) { // Only consider reachable predecessors
                    int u = eval(v);
                    if (dfsNumber[semi[u]] < dfsNumber[semi[w]]) {
                        semi[w] = semi[u];
                    }
                }
            }
            bucket.get(semi[w]).add(w);
            link(parent[w], w);

            // Step 3 (Implicit part): For nodes in the bucket of parent[w],
            // their immediate dominator can be partially determined.
            for (int v : bucket.get(parent[w])) {
                int u = eval(v);
                idom[v] = (semi[u] == semi[v]) ? parent[w] : u;
            }
            bucket.get(parent[w]).clear();
        }

        // Step 3 (Explicit part): Compute the final immediate dominators.
        // Iterate in DFS order. For any node w where idom[w] != semi[w],
        // its true idom is the idom of its semi-dominator.
        for (int i = 2; i <= dfsCounter; i++) {
            int w = vertex[i];
            if (idom[w] != semi[w]) {
                idom[w] = idom[idom[w]];
            }
        }
    }

    /**
     * Recursive DFS function for Step 1 of the algorithm.
     * @param u The current node ID to visit.
     */
    private void dfs(int u) {
        dfsCounter++;
        dfsNumber[u] = dfsCounter;
        vertex[dfsCounter] = u;

        for (int v : graph.getNeighbors(u)) {
            if (dfsNumber[v] == 0) { // If v has not been visited
                parent[v] = u;
                dfs(v);
            }
        }
    }

    /**
     * Performs path compression for the EVAL function.
     * @param v The node ID to start compression from.
     */
    private void compress(int v) {
        if (ancestor[ancestor[v]] != -1) {
            compress(ancestor[v]);
            if (dfsNumber[semi[label[ancestor[v]]]] < dfsNumber[semi[label[v]]]) {
                label[v] = label[ancestor[v]];
            }
            ancestor[v] = ancestor[ancestor[v]];
        }
    }

    /**
     * The EVAL function for the forest data structure. Finds the ancestor
     * of v with the minimum semi-dominator in the current forest.
     * @param v The node ID to evaluate.
     * @return The ancestor with the minimum semi-dominator.
     */
    private int eval(int v) {
        if (ancestor[v] == -1) {
            return v;
        }
        compress(v);
        return label[v];
    }

    /**
     * The LINK function for the forest data structure. Links node q as a child of p.
     * @param p The parent node ID.
     * @param q The child node ID.
     */
    private void link(int p, int q) {
        ancestor[q] = p;
    }

    /**
     * Populates the final result arrays (finalIdom and finalDomChildren) from the
     * computed idom array.
     */
    private void buildResultMaps() {
        // The algorithm uses the entry node itself as a sentinel for its own dominator.
        idom[entryNodeId] = entryNodeId;

        for (int i = 1; i <= dfsCounter; i++) {
            int w = vertex[i];
            if (w == entryNodeId) {
                finalIdom[w] = -1; // The entry node has no dominator.
                continue;
            }
            int dominatorId = idom[w];
            finalIdom[w] = dominatorId;
            finalDomChildren[dominatorId].add(w);
        }
    }

    /**
     * 获取 idom(node)
     * @param node 支配树上的基本块
     * @return idom(node), 如果是入口块或节点不可达则返回null
     */
    public T getDominator(T node) {
        int nodeId = graph.getNodeId(node);
        int dominatorId = finalIdom[nodeId];

        if (dominatorId == -1) {
            // Node is the entry node or is unreachable.
            return null;
        }
        return graph.getNodeValue(dominatorId);
    }

    /**
     * 获取 node 直接支配的所有基本块
     * @param node 基本块
     * @return node 直接支配的所有基本块
     */
    public List<T> getDominatees(T node) {
        int nodeId = graph.getNodeId(node);
        List<Integer> dominateeIds = finalDomChildren[nodeId]; // This list is guaranteed to exist.
        List<T> dominatees = new ArrayList<>(dominateeIds.size());
        for (int id : dominateeIds) {
            dominatees.add(graph.getNodeValue(id));
        }
        return dominatees;
    }
}
