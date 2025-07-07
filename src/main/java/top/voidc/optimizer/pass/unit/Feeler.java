package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceUnit;
import top.voidc.optimizer.pass.CompilePass;

import java.util.Map;

import top.voidc.ir.IceBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import top.voidc.ir.ice.constant.IceExternFunction;

public class Feeler implements CompilePass<IceUnit> {
    static public Map<String, Object> detected = new HashMap<>();

    private String feelerName = "";

    public void setFeelerName(String name){
        this.feelerName = name;
    }

    @Override
    public String getName() {
        return "Feeler";
    }

    @Override
    public boolean run(IceUnit target) {

        Map<String, Object> feelerData = new HashMap<>();

        feelerData.put("llvm", target.getTextIR());
        feelerData.put("graphviz", FeelerDataPasser.getFeelerData(target));

        detected.put(feelerName, feelerData);

        return false;
    }

    private static class FeelerDataPasser {
        static public Map<String, Object> getFeelerData(IceUnit target) {
            Map<String, Object> data = new HashMap<>();
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            // 节点计数器（确保全局唯一ID）
            AtomicInteger nodeCounter = new AtomicInteger(0);
            // 存储基本块到节点ID的映射
            Map<IceBlock, Integer> blockToIdMap = new HashMap<>();
            // 存储函数入口节点ID
            Map<String, Integer> funcEntryNodes = new HashMap<>();

            // 直接处理传入的target单元
            var functions = target.getFunctions();
            functions.forEach(func -> {
                if(func instanceof IceExternFunction){
                    return;
                }

                List<IceBlock> blocks = func.blocks();
                String funcName = func.getName();

                // 1. 创建函数入口节点
                int funcNodeId = nodeCounter.getAndIncrement();
                funcEntryNodes.put(funcName, funcNodeId);
                nodes.add(createNode(funcNodeId, "@" + funcName));

                // 2. 为每个基本块创建节点
                blocks.forEach(block -> {
                    int blockNodeId = nodeCounter.getAndIncrement();
                    blockToIdMap.put(block, blockNodeId);
                    String irText = block.getTextIR();
                    nodes.add(createNode(blockNodeId, irText));
                });

                // 3. 连接函数入口到第一个基本块
                if (!blocks.isEmpty()) {
                    int firstBlockId = blockToIdMap.get(blocks.get(0));
                    edges.add(createEdge(funcNodeId, firstBlockId, ""));
                }

                // 4. 处理基本块间的边
                blocks.forEach(block -> {
                    int sourceId = blockToIdMap.get(block);
                    List<IceBlock> successors = block.getSuccessors();

                    successors.forEach(succ -> {
                        if (blockToIdMap.containsKey(succ)) {
                            int targetId = blockToIdMap.get(succ);
                            // 获取分支标签（示例中的条件文本）
                            String label = getBranchLabel(block, succ);
                            edges.add(createEdge(sourceId, targetId, label));
                        }
                    });
                });
            });

            data.put("nodes", nodes);
            data.put("edges", edges);
            return data;
        }


        // 创建节点数据
        private static Map<String, Object> createNode(int id, String label) {
            Map<String, String> nodeData = new HashMap<>();
            nodeData.put("id", String.valueOf(id));
            nodeData.put("label", label);

            Map<String, Object> node = new HashMap<>();
            node.put("data", nodeData);
            return node;
        }

        // 创建边数据
        private static Map<String, Object> createEdge(int source, int target, String label) {
            Map<String, String> edgeData = new HashMap<>();
            edgeData.put("id", source + "->" + target);
            edgeData.put("source", String.valueOf(source));
            edgeData.put("target", String.valueOf(target));
            edgeData.put("label", label);

            Map<String, Object> edge = new HashMap<>();
            edge.put("data", edgeData);
            return edge;
        }

        // 获取分支标签（需要根据实际框架实现）
        private static String getBranchLabel(IceBlock source, IceBlock target) {
            // 实际实现需根据Ice框架的API获取分支条件
            // 示例：return source.getBranchCondition(target);
            return ""; // 默认返回空字符串
        }
    }

}


