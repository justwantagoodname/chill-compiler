package top.voidc.misc.ds;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Random;

public class ChilletGraph<T> {
    private class Node {
        int color = 0;
        T name;
        LinkedList<Node> edges = new LinkedList<>();

        Node(T name) {
            this.name = name;
        }
    }

    private Hashtable<T, Node> nodes = new Hashtable<>();

    public void createNewNode(T name) {
        if (nodes.containsKey(name)) {
            throw new RuntimeException("Node " + name + " already exists");
        }

        nodes.put(name, new Node(name));
    }

    public void removeNode(T name) {
        if (!nodes.containsKey(name)) {
            throw new RuntimeException("Node " + name + " does not exist");
        }

        Node node = nodes.get(name);
        for (Node n : node.edges) {
            node.edges.remove(n);
        }
        nodes.remove(name);
    }

    public void connectNode(T u, T v) {
        if (!nodes.containsKey(u)) {
            throw new RuntimeException("Node " + u + " does not exist");
        }
        if (!nodes.containsKey(v)) {
            throw new RuntimeException("Node " + v + " does not exist");
        }

        Node p = nodes.get(u);
        Node q = nodes.get(v);
        p.edges.add(q);
        q.edges.add(p);
    }

    /**
     * 用 0 ~ (k - 1) 共 k 种颜色尝试对图进行涂色，返回冲突尽量少的方案
     * @param k 颜色数量
     * @return 哈希映射，节点名字 -> 节点颜色
     */
    public Hashtable<T, Integer> getColors(int k) {
        minConflicts(k, 1000);

        Hashtable<T, Integer> colors = new Hashtable<>();
        for (Node n : nodes.values()) {
            colors.put(n.name, n.color);
        }

        return colors;
    }

    /**
     * Min-Conflicts 图染色算法，求仅使用 k 种颜色的情况下冲突最少的染色方案
     * <br>
     * 1. 随机生成一组颜色
     * 2. 随机挑选一个处于冲突状态的点 (不一定是冲突最多的)，将它着色为冲突最小的颜色，若有多个颜色就随机一个
     * 3. 重复执行 2 直到达到 max_steps 或没有冲突
     * @param k 颜色数量
     * @param maxSteps 迭代次数
     */
    private void minConflicts(int k, int maxSteps) {
        Random rand = new Random();
        for (Node n :  nodes.values()) {
            n.color = rand.nextInt(k);
        }

        ArrayList<Node> conflicted = new ArrayList<>();
        ArrayList<Integer> availableColors = new ArrayList<>();

        for (int step = 0; step < maxSteps; ++step) {
            conflicted.clear();
            for (Node n :  nodes.values()) {
                if (conflictCount(n, -1) > 0) {
                    conflicted.add(n);
                }
            }

            if (conflicted.isEmpty()) {
                return;
            }

            // randomly choose one in conflicted
            Node p = conflicted.get(rand.nextInt(conflicted.size()));
            availableColors.clear();
            int minConflicted = -1;
            for (int c = 0; c < k; ++c) {
                int cnt = conflictCount(p, c);
                if (minConflicted == -1 || cnt < minConflicted) {
                    minConflicted = cnt;
                    availableColors.clear();
                    availableColors.add(c);
                } else if (minConflicted == cnt) {
                    availableColors.add(c);
                }
            }

            p.color = availableColors.get(rand.nextInt(availableColors.size()));
        }
    }

    /**
     * 计算当 node 颜色为 color 时的冲突数量
     * 如果 color == -1，则计算 node 当前颜色下的冲突数量
     * @param node 被检测节点
     * @param color 假设颜色，若 -1 则保持当前颜色
     * @return 冲突数量
     */
    private int conflictCount(Node node, int color) {
        final int c;
        if (color == -1) {
            c = node.color;
        } else {
            c = color;
        }

        return Math.toIntExact(node.edges.stream().filter(e -> e.color == c).count());
    }
}
