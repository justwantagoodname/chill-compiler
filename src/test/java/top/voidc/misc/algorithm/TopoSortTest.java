package top.voidc.misc.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.voidc.misc.ds.ChilletGraph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TopoSortTest {

    /**
     * Helper method to validate if a list is a correct topological sort for a given graph.
     * It checks two conditions:
     * 1. The sorted list contains the same number of nodes as the graph.
     * 2. For every edge u -> v (excluding self-loops), u appears before v in the list.
     *
     * @param graph       The original graph.
     * @param sortedList  The list returned by the topoSort method.
     * @param <T>         The type of data in the nodes.
     */
    private <T> void assertIsValidTopologicalSort(ChilletGraph<T> graph, List<T> sortedList) {
        // 1. Check if the number of nodes is correct
        assertEquals(graph.getNodeCount(), sortedList.size(), "The number of nodes in the sorted list should match the graph.");

        // 2. Check the ordering constraint for every edge
        Map<T, Integer> nodePositions = new HashMap<>();
        for (int i = 0; i < sortedList.size(); i++) {
            nodePositions.put(sortedList.get(i), i);
        }

        for (int i = 0; i < graph.getNodeCount(); i++) {
            T uValue = graph.getNodeValue(i);
            int uPosition = nodePositions.get(uValue);

            for (int neighborId : graph.getNeighbors(i)) {
                // Ignore self-loops as per the requirement
                if (i == neighborId) {
                    continue;
                }
                T vValue = graph.getNodeValue(neighborId);
                int vPosition = nodePositions.get(vValue);

                assertTrue(uPosition < vPosition,
                        "Dependency violation: Node " + uValue + " must appear before " + vValue);
            }
        }
    }

    @Test
    @DisplayName("Test with a basic Directed Acyclic Graph (DAG)")
    void testBasicDAG() {
        ChilletGraph<String> graph = new ChilletGraph<>();
        List<String> nodes = Arrays.asList("A", "B", "C", "D", "E", "F");
        graph.createNewNodes(nodes);

        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("F", "C");

        List<String> sortedList = TopoSort.topoSort(graph);

        // A possible valid order is [F, A, C, B, D, E] or [A, F, B, C, D, E], etc.
        // We validate the dependencies instead of checking for a specific order.
        assertIsValidTopologicalSort(graph, sortedList);
    }

    @Test
    @DisplayName("Test a DAG with self-loops")
    void testGraphWithSelfLoops() {
        ChilletGraph<Integer> graph = new ChilletGraph<>();
        graph.createNewNodes(Arrays.asList(1, 2, 3, 4, 5));

        // Dependencies
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(1, 4);
        graph.addEdge(4, 5);
        graph.addEdge(3, 5);

        // Self-loops
        graph.addEdge(1, 1);
        graph.addEdge(3, 3);
        graph.addEdge(5, 5);

        List<Integer> sortedList = TopoSort.topoSort(graph);
        assertIsValidTopologicalSort(graph, sortedList);
    }

    @Test
    @DisplayName("Test a linear graph (chain)")
    void testLinearGraph() {
        ChilletGraph<String> graph = new ChilletGraph<>();
        graph.createNewNodes(Arrays.asList("Task 1", "Task 2", "Task 3", "Task 4"));

        graph.addEdge("Task 1", "Task 2");
        graph.addEdge("Task 2", "Task 3");
        graph.addEdge("Task 3", "Task 4");

        List<String> sortedList = TopoSort.topoSort(graph);

        // In this case, there is only one valid topological sort
        List<String> expected = Arrays.asList("Task 1", "Task 2", "Task 3", "Task 4");
        assertEquals(expected, sortedList, "The sorted order for a linear graph should be sequential.");
        assertIsValidTopologicalSort(graph, sortedList);
    }

    @Test
    @DisplayName("Test with a disconnected graph")
    void testDisconnectedGraph() {
        ChilletGraph<Integer> graph = new ChilletGraph<>();
        graph.createNewNodes(Arrays.asList(1, 2, 3, 4, 5, 6));

        // Component 1
        graph.addEdgeById(1, 2);
        graph.addEdgeById(2, 3);

        // Component 2
        graph.addEdgeById(4, 5);

        // Component 3 (isolated node)
        // Node 6 has no edges

        List<Integer> sortedList = TopoSort.topoSort(graph);
        assertIsValidTopologicalSort(graph, sortedList);
    }

    @Test
    @DisplayName("Test with a more complex DAG")
    void testComplexDAG() {
        ChilletGraph<String> graph = new ChilletGraph<>();
        graph.createNewNodes(Arrays.asList("Undershorts", "Pants", "Belt", "Shirt", "Tie", "Jacket", "Socks", "Shoes", "Watch"));

        graph.addEdge("Undershorts", "Pants");
        graph.addEdge("Undershorts", "Shoes");
        graph.addEdge("Pants", "Shoes");
        graph.addEdge("Pants", "Belt");
        graph.addEdge("Belt", "Jacket");
        graph.addEdge("Shirt", "Belt");
        graph.addEdge("Shirt", "Tie");
        graph.addEdge("Tie", "Jacket");
        graph.addEdge("Socks", "Shoes");

        // "Watch" is an independent item

        List<String> sortedList = TopoSort.topoSort(graph);
        assertIsValidTopologicalSort(graph, sortedList);
    }

    @Test
    @DisplayName("Test with an empty graph")
    void testEmptyGraph() {
        ChilletGraph<String> graph = new ChilletGraph<>();
        List<String> sortedList = TopoSort.topoSort(graph);
        assertTrue(sortedList.isEmpty(), "Topological sort of an empty graph should be an empty list.");
    }

    @Test
    @DisplayName("Test with a single node and no edges")
    void testSingleNodeGraph() {
        ChilletGraph<Integer> graph = new ChilletGraph<>();
        graph.createNewNode(42);

        List<Integer> sortedList = TopoSort.topoSort(graph);
        assertEquals(Arrays.asList(42), sortedList, "Graph with a single node should return a list with that node.");
    }

    @Test
    @DisplayName("Test with a single node and a self-loop")
    void testSingleNodeWithSelfLoop() {
        ChilletGraph<Integer> graph = new ChilletGraph<>();
        graph.createNewNode(100);
        graph.addEdge(100, 100);

        List<Integer> sortedList = TopoSort.topoSort(graph);
        assertEquals(Arrays.asList(100), sortedList, "Graph with a single node and a self-loop should be handled correctly.");
    }
}