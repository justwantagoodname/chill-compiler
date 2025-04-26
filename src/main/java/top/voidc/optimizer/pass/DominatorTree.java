package top.voidc.optimizer.pass;

import com.ibm.icu.text.ArabicShaping;
import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceLoadInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class DominatorTree {
    private final int blocksSize;
    private IceFunction function;

    private IceBlock[] dfsSortedBlocks;
    private HashMap<IceBlock, Integer> dfsBlockIndex;

    // parent on dfs tree
    int[] parent;
    // successors on dfs tree
    ArrayList<ArrayList<Integer>> successors;

    // for union-find set use
    int[] ancestor;
    int[] semi;
    int[] label;

    int[] idom;

    // bucket for each node
    ArrayList<ArrayList<Integer>> bucket;

    public DominatorTree(IceFunction function) {
        this.function = function;
        this.blocksSize = function.getBlocksSize();
        this.ancestor = new int[blocksSize];
        this.semi = new int[blocksSize];
        this.label = new int[blocksSize];
        this.idom = new int[blocksSize];
        this.dfsSortedBlocks = new IceBlock[blocksSize];
        this.dfsBlockIndex = new HashMap<>();
        this.parent = new int[blocksSize];

        this.bucket = new ArrayList<>(blocksSize);
        for (int i = 0; i < blocksSize; ++i) {
            bucket.add(new ArrayList<>());
        }
        this.successors = new ArrayList<>(blocksSize);
        for (int i = 0; i < blocksSize; ++i) {
            successors.add(new ArrayList<>());
        }

        this.buildTree(function.getEntryBlock());
    }

    public IceFunction getFunction() {
        return function;
    }

    public IceBlock getDominator(IceBlock block) {
        int index = dfsBlockIndex.get(block);
        if (index == 0) {
            return null;
        }
        return dfsSortedBlocks[idom[index]];
    }

    public ArrayList<IceBlock> getDominatees(IceBlock block) {
        int index = dfsBlockIndex.get(block);

        ArrayList<IceBlock> result = new ArrayList<>();
        for (int i : successors.get(index)) {
            result.add(dfsSortedBlocks[i]);
        }

        return result;
    }

    public ArrayList<IceBlock> dfsOnTree(IceBlock startBlock) {
        ArrayList<IceBlock> result = new ArrayList<>();
        Stack<Integer> stack = new Stack<>();
        stack.push(dfsBlockIndex.get(startBlock));

        while (!stack.isEmpty()) {
            int index = stack.pop();
            result.add(dfsSortedBlocks[index]);

            for (int successor : successors.get(index)) {
                stack.push(successor);
            }
        }
        return result;
    }

    private void buildTree(IceBlock root) {
        dfs(root);
        initUnionFindSet();
        getSemiAndIdom();

        for (int i = 0; i < blocksSize; ++i) {
            int idomIndex = idom[i];
            if (idomIndex != -1) {
                successors.get(idomIndex).add(i);
            }
        }
    }

    private void dfs(IceBlock root) {
        // pair used for stack, to store the block and its parent index
        record Pair(IceBlock block, int parentIndex) {};

        Stack<Pair> stack = new Stack<>();
        stack.push(new Pair(root, -1));

        parent[0] = -1;

        int index = 0;
        while (!stack.isEmpty()) {
            Pair pair = stack.pop();
            IceBlock block = pair.block;
            int parentIndex = pair.parentIndex;

            if (dfsBlockIndex.containsKey(block)) {
                continue;
            }

            parent[index] = parentIndex;
            semi[index] = index;
            label[index] = index;
            ancestor[index] = 0;

            dfsBlockIndex.put(block, index);
            dfsSortedBlocks[index] = block;

            for (IceBlock successor : block.successors()) {
                stack.push(new Pair(successor, index));
            }

            ++index;
        }

        if (index != blocksSize) {
            throw new RuntimeException("DFS block index size does not match blocks size: " + index + " != " + blocksSize);
        }
    }

    private void getSemiAndIdom() {
        for (int i = 0; i < blocksSize; ++i) {
            bucket.add(new ArrayList<>());
        }

        for (int i = blocksSize - 1; i >= 2; --i) {
            for (var v : dfsSortedBlocks[i].predecessors()) {
                int u = eval(dfsBlockIndex.get(v));
                if (semi[u] < semi[i]) {
                    semi[i] = semi[u];
                }
            }
            bucket.get(semi[i]).add(i);
            link(parent[i], i);

            for (var v : bucket.get(parent[i])) {
                int u = eval(v);
                if (semi[u] < semi[v]) {
                    idom[v] = u;
                } else {
                    idom[v] = parent[i];
                }
            }
        }

        for (int i = 1; i < blocksSize; ++i) {
            if (idom[i] != semi[i]) {
                idom[i] = idom[idom[i]];
            }
        }
        idom[0] = -1;
    }

    private void initUnionFindSet() {
        for (int i = 0; i < blocksSize; i++) {
            ancestor[i] = 0;
            semi[i] = i;
            label[i] = i;
        }
    }

    private int eval(int v) {
        if (ancestor[v] == 0) {
            return v;
        }
        compress(v);
        return label[v];
    }

    private void compress(int v) {
        if (ancestor[ancestor[v]] != 0) {
            compress(ancestor[v]);

            if (semi[label[ancestor[v]]] < semi[label[v]]) {
                label[v] = label[ancestor[v]];
            }

            ancestor[v] = ancestor[ancestor[v]];
        }
    }

    private void link(int u, int w) {
        ancestor[w] = u;
    }
}
