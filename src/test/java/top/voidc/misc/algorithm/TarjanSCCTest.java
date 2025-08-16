package top.voidc.misc.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.voidc.misc.ds.ChilletGraph;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TarjanSCC class.
 */
class TarjanSCCTest {

    private ChilletGraph<String> graph;

    @BeforeEach
    void setUp() {
        graph = new ChilletGraph<>();
    }

    @Test
    void testEmptyGraph() {
        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);
        assertTrue(sccs.isEmpty(), "An empty graph should have no SCCs.");
    }

    @Test
    void testSingleNodeGraph() {
        graph.createNewNode("A");
        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);

        assertEquals(1, sccs.size(), "A single node graph should have one SCC.");
        assertEquals(1, sccs.get(0).nodes().size(), "The SCC should contain one node.");
        assertEquals("A", sccs.get(0).nodes().get(0), "The SCC should contain the correct node.");
    }

    @Test
    void testDirectedAcyclicGraph() {
        // A -> B -> C
        // A -> D
        graph.createNewNodes(Arrays.asList("A", "B", "C", "D"));
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("A", "D");

        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);
        assertEquals(4, sccs.size(), "A DAG with 4 nodes should have 4 SCCs.");

        Set<Set<String>> actualSccs = convertSccResultToSet(sccs);
        Set<Set<String>> expectedSccs = new HashSet<>(Arrays.asList(
                new HashSet<>(Arrays.asList("A")),
                new HashSet<>(Arrays.asList("B")),
                new HashSet<>(Arrays.asList("C")),
                new HashSet<>(Arrays.asList("D"))
        ));

        assertEquals(expectedSccs, actualSccs, "Each node in the DAG should be its own SCC.");
    }

    @Test
    void testSimpleCycle() {
        // A -> B -> C -> A
        graph.createNewNodes(Arrays.asList("A", "B", "C"));
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);
        assertEquals(1, sccs.size(), "A simple cycle should form a single SCC.");

        Set<String> sccNodes = new HashSet<>(sccs.get(0).nodes());
        Set<String> expectedNodes = new HashSet<>(Arrays.asList("A", "B", "C"));

        assertEquals(expectedNodes, sccNodes, "The SCC should contain all nodes of the cycle.");
    }

    @Test
    void testMultipleSCCsAndBridge() {
        // SCC 1: A, B, C form a cycle
        // SCC 2: D, E form a cycle
        // Bridge: B -> D
        // SCC 3: F (isolated node)
        graph.createNewNodes(Arrays.asList("A", "B", "C", "D", "E", "F"));
        // SCC 1
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");
        // SCC 2
        graph.addEdge("D", "E");
        graph.addEdge("E", "D");
        // Bridge
        graph.addEdge("B", "D");

        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);
        assertEquals(3, sccs.size(), "Should find three SCCs.");

        Set<Set<String>> actualSccs = convertSccResultToSet(sccs);
        Set<Set<String>> expectedSccs = new HashSet<>(Arrays.asList(
                new HashSet<>(Arrays.asList("A", "B", "C")),
                new HashSet<>(Arrays.asList("D", "E")),
                new HashSet<>(Arrays.asList("F"))
        ));

        assertEquals(expectedSccs, actualSccs, "The identified SCCs do not match the expected components.");
    }

    @Test
    void testDisconnectedGraphWithCycles() {
        // Component 1 (Cycle)
        graph.createNewNodes(Arrays.asList("A", "B", "C"));
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        // Component 2 (Cycle)
        graph.createNewNodes(Arrays.asList("D", "E", "F"));
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");

        // Component 3 (Single node)
        graph.createNewNode("G");

        List<TarjanSCC.SCC<String>> sccs = TarjanSCC.tarjan(graph);
        assertEquals(3, sccs.size(), "A disconnected graph with 3 components should result in 3 SCCs.");

        Set<Set<String>> actualSccs = convertSccResultToSet(sccs);
        Set<Set<String>> expectedSccs = new HashSet<>(Arrays.asList(
                new HashSet<>(Arrays.asList("A", "B", "C")),
                new HashSet<>(Arrays.asList("D", "E", "F")),
                new HashSet<>(Arrays.asList("G"))
        ));

        assertEquals(expectedSccs, actualSccs, "SCCs for the disconnected graph are incorrect.");
    }

    /**
     * Helper method to convert the list of SCC objects to a Set of Sets of nodes.
     * This makes comparison independent of the order of SCCs and nodes within them.
     */
    private <T> Set<Set<T>> convertSccResultToSet(List<TarjanSCC.SCC<T>> sccs) {
        return sccs.stream()
                .map(scc -> new HashSet<>(scc.nodes()))
                .collect(Collectors.toSet());
    }
}