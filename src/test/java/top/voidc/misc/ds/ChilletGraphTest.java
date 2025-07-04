package top.voidc.misc.ds;

import top.voidc.misc.Log;

import org.junit.jupiter.api.Test;

public class ChilletGraphTest {
    @Test
    public void graphSimpleTest() {
        ChilletGraph<String> g = new ChilletGraph<>();
        g.createNewNode("A");
        g.createNewNode("B");
        g.createNewNode("C");

        g.addEdge("A", "B");
        g.addEdge("A", "C");
        g.addEdge("B", "C");

        var solution = g.getColors(3);

        for (String node : solution.keySet()) {
            Log.d(node + " -> " + solution.get(node));
        }
    }
}
