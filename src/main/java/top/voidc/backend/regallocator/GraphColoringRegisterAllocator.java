package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;
import java.util.*;

/**
 * 图染色寄存器分配器
 */
@Pass(group = {"O0", "backend"})
public class GraphColoringRegisterAllocator implements CompilePass<IceMachineFunction> {
    private final LivenessAnalysis.LivenessResult livenessResult;
    private final boolean insInsertOpt;
    private Map<IceType, List<IceMachineRegister>> phyRegsByType;
    private int maxReg;

    public GraphColoringRegisterAllocator(LivenessAnalysis.LivenessResult livenessResult) {
        this.insInsertOpt = true;
        this.livenessResult = livenessResult;
        
        // 初始化物理寄存器池
        List<String> regNames = List.of("x9", "x10", "x11", "x12", "x13", "x14", "x15");
        this.maxReg = regNames.size();
        this.phyRegsByType = new HashMap<>();
    }

    @Override
    public boolean run(IceMachineFunction function) {
        // 初始化不同类型的寄存器池
        if (phyRegsByType.isEmpty()) {
            for (IceType type : List.of(IceType.I32, IceType.I64 /*, IceType.F32, IceType.F64, IceType.PTR*/)) {
                List<IceMachineRegister> typeRegs = new ArrayList<>();
                for (String regName : List.of("x9", "x10", "x11", "x12", "x13", "x14", "x15")) {
                    IceMachineRegister baseReg = function.getPhysicalRegister(regName);
                    if (baseReg == null) {
                        throw new IllegalStateException("Physical register not found: " + regName + " for type " + type);
                    }
                    typeRegs.add(baseReg); // 只存物理寄存器本体
                }
                phyRegsByType.put(type, typeRegs);
            }
        }

        // 为当前函数创建新的分配器实例
        FunctionAllocator allocator = new FunctionAllocator(function);
        allocator.doColor();
        return true;
    }

    private class FunctionAllocator {
        private final IceMachineFunction function;
        private final HashSet<IceMachineRegister> nodes = new HashSet<>();
        private final HashMap<IceMachineRegister, HashSet<IceMachineRegister>> edges = new HashMap<>();
        private HashMap<IceMachineRegister, HashSet<IceMachineRegister>> temp;
        private final HashMap<IceMachineRegister, IceMachineRegister> color = new HashMap<>();
        private final HashMap<IceMachineRegister, ArrayList<IceMachineInstruction>> reg2ins = new HashMap<>();

        // 工作列表
        private final ArrayList<IceMachineRegister> simplifyList = new ArrayList<>();
        private final ArrayList<IceMachineInstruction> coalesceList = new ArrayList<>();
        private final ArrayList<IceMachineRegister> freezeList = new ArrayList<>();
        private final HashSet<IceMachineRegister> spillList = new HashSet<>();

        // 处理完的节点
        private final ArrayList<IceMachineRegister> spilledNodes = new ArrayList<>();
        private final ArrayList<IceMachineRegister> coalescedNodes = new ArrayList<>();

        // 选择栈
        private final Stack<IceMachineRegister> simplifiedNodes = new Stack<>();
        private final HashSet<IceMachineRegister> helpFind = new HashSet<>();

        // 移动相关
        private final HashSet<IceMachineInstruction> coalescedMoves = new HashSet<>();
        private final HashSet<IceMachineInstruction> constrainedMoves = new HashSet<>();
        private final HashSet<IceMachineInstruction> frozenMoves = new HashSet<>();
        private final HashSet<IceMachineInstruction> activeMoves = new HashSet<>();
        private final HashMap<IceMachineRegister, IceMachineRegister> alias = new HashMap<>();
        private final HashMap<IceMachineRegister, ArrayList<IceMachineInstruction>> moveReg2ins = new HashMap<>();

        private final HashMap<IceMachineRegister, IceMachineRegister> preColored = new HashMap<>();
        private final HashMap<Integer, IceStackSlot> slotMap = new HashMap<>();
        private final HashSet<Integer> hasSpilled = new HashSet<>();

        // 统计每个虚拟寄存器的使用次数
        private final Map<IceMachineRegister, Integer> useCount = new HashMap<>();

        public FunctionAllocator(IceMachineFunction function) {
            this.function = function;
        }

        private void doColor() {
            boolean finished = false;
            preWork();
            int spilledSize = 0;

            while (!finished) {
                flush();
                buildConflictAndList();
                copyTempGraph();

                while (!(simplifyList.isEmpty() && coalesceList.isEmpty() &&
                        freezeList.isEmpty() && spillList.isEmpty())) {
                    if (!simplifyList.isEmpty()) {
                        simplify();
                    }
                    if (!coalesceList.isEmpty()) {
                        coalesce();
                    }
                    if (!freezeList.isEmpty()) {
                        freeze();
                    }
                    if (!spillList.isEmpty()) {
                        spill();
                    }
                }

                stain();
                if (spilledSize == spilledNodes.size() && spilledSize != 0) {
                    IceMachineRegister added = null;
                    for (IceMachineRegister reg : spilledNodes) {
                        for (IceMachineRegister adj : edges.get(reg)) {
                            if (!spilledNodes.contains(adj)) {
                                added = adj;
                                break;
                            }
                        }
                        if (added != null) {
                            break;
                        }
                    }
                    spilledNodes.add(added);
                }
                spilledSize = spilledNodes.size();
                if (spilledNodes.isEmpty()) {
                    finished = true;
                    continue;
                }

                dealSpill();
            }

            // 替换虚拟寄存器为物理寄存器
            for (IceBlock block : function) {
                for (var instruction : block) {
                    IceMachineInstruction instr = (IceMachineInstruction) instruction;
                    // 替换源操作数中的虚拟寄存器
                    for (var operand : instr.getSourceOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView view) {
                            IceMachineRegister reg = view.getRegister();
                            if (reg.isVirtualize() && color.containsKey(reg)) {
                                instr.replaceOperand(operand, color.get(reg).createView(view.getType()));
                            }
                        }
                    }
                    // 替换目标寄存器
                    if (instr.getResultReg() != null) {
                        IceMachineRegister reg = instr.getResultReg().getRegister();
                        if (reg.isVirtualize() && color.containsKey(reg)) {
                            instr.replaceOperand(instr.getResultReg(),
                                    color.get(reg).createView(instr.getResultReg().getType()));
                        }
                    }
                }
            }

            if (insInsertOpt) {
                optimize();
            }
        }

        private void flush() {
            temp = new HashMap<>();
            simplifyList.clear();
            coalesceList.clear();
            freezeList.clear();
            spillList.clear();
            spilledNodes.clear();
            coalescedNodes.clear();
            simplifiedNodes.clear();
            helpFind.clear();
            coalescedMoves.clear();
            constrainedMoves.clear();
            frozenMoves.clear();
            activeMoves.clear();
            alias.clear();
            color.clear();
        }

        private void preWork() {
            // 收集所有虚拟寄存器
            for (var block : function) {
                for (var instruction : block) {
                    IceMachineInstruction instr = (IceMachineInstruction) instruction;
                    for (var operand : instr.getOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView) {
                            if (registerView.getRegister().isVirtualize()) {
                                nodes.add(registerView.getRegister());
                            }
                        }
                    }
                }
            }
            // 统计每个虚拟寄存器的使用次数
            countUses();
        }

        private void countUses() {
            useCount.clear();
            for (var block : function) {
                for (var instruction : block) {
                    for (var operand : ((IceMachineInstruction) instruction).getOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView rv) {
                            var reg = rv.getRegister();
                            if (reg.isVirtualize()) {
                                useCount.put(reg, useCount.getOrDefault(reg, 0) + 1);
                            }
                        }
                    }
                }
            }
        }

        private void buildConflictAndList() {
            // 获取或运行活跃性分析
            assert livenessResult != null : "Liveness analysis result is null";

            // 初始化冲突图
            for (IceMachineRegister reg : nodes) {
                edges.putIfAbsent(reg, new HashSet<>());
            }

            // 使用活跃性信息构建冲突图
            Map<IceBlock, LivenessAnalysis.BlockLivenessData> blockLivenessData =
                    livenessResult.getLivenessData(function);

            for (var block : function) {
                // 获取块末尾的活跃寄存器
                Set<IceValue> liveOut = blockLivenessData.get(block).liveOut();
                Set<IceMachineRegister> liveRegs = new HashSet<>();

                // 过滤虚拟寄存器
                for (IceValue value : liveOut) {
                    if (value instanceof IceMachineRegister reg && reg.isVirtualize()) {
                        liveRegs.add(reg);
                    }
                }

                // 反向遍历指令，更新活跃信息并添加冲突边
                for (int i = block.size() - 1; i >= 0; i--) {
                    IceMachineInstruction instr = (IceMachineInstruction) block.get(i);

                    // 处理定义的寄存器
                    if (instr.getResultReg() != null) {
                        IceMachineRegister defined = instr.getResultReg().getRegister();
                        if (defined.isVirtualize()) {
                            // 添加与当前活跃寄存器的冲突
                            for (IceMachineRegister live : liveRegs) {
                                if (!defined.equals(live)) {
                                    edges.get(defined).add(live);
                                    edges.get(live).add(defined);
                                }
                            }
                            liveRegs.remove(defined);
                        }
                    }

                    // 处理使用的寄存器
                    for (var operand : instr.getSourceOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView regView) {
                            IceMachineRegister used = regView.getRegister();
                            if (used.isVirtualize()) {
                                liveRegs.add(used);
                            }
                        }
                    }
                }
            }

            // 制作工作列表
            makeWorkList();
        }

        private void makeWorkList() {
            for (IceMachineRegister reg : nodes) {
                if (edges.get(reg).size() < maxReg) {
                    simplifyList.add(reg);
                } else {
                    spillList.add(reg);
                }
            }
        }

        private void copyTempGraph() {
            temp = new HashMap<>();
            for (IceMachineRegister reg : edges.keySet()) {
                HashSet<IceMachineRegister> tempSet = new HashSet<>(edges.get(reg));
                temp.put(reg, tempSet);
            }
        }

        private void simplify() {
            while (!simplifyList.isEmpty()) {
                IceMachineRegister reg = simplifyList.remove(0);
                if (!helpFind.contains(reg)) {
                    simplifiedNodes.add(reg);
                    helpFind.add(reg);
                }
                removeNodeInGraph(reg);
            }
        }

        private void removeNodeInGraph(IceMachineRegister node) {
            HashSet<IceMachineRegister> nodes = temp.remove(node);
            if (nodes == null) return;
            for (IceMachineRegister reg : nodes) {
                renewReg(reg);
                temp.getOrDefault(reg, new HashSet<>()).remove(node);
            }
        }

        private void renewReg(IceMachineRegister reg) {
            if (temp.getOrDefault(reg, new HashSet<>()).size() <= maxReg) {
                spillList.remove(reg);
                simplifyList.add(reg);
            }
        }

        private void coalesce() {
            if (coalesceList.isEmpty()) {
                return;
            }

            IceMachineInstruction mov = coalesceList.remove(0);

            // 确保指令是移动指令
            if (!isMoveInstruction(mov)) {
                return;
            }

            // 获取源和目标寄存器
            IceMachineRegister.RegisterView srcView = null;
            IceMachineRegister.RegisterView dstView = null;

            for (var operand : mov.getSourceOperands()) {
                if (operand instanceof IceMachineRegister.RegisterView view) {
                    srcView = view;
                    break;
                }
            }

            dstView = mov.getResultReg();

            if (srcView == null || dstView == null) {
                return;
            }

            IceMachineRegister src = srcView.getRegister();
            IceMachineRegister dst = dstView.getRegister();

            // 获取合并后的别名
            IceMachineRegister u = getAlias(src);
            IceMachineRegister v = getAlias(dst);

            // 如果源和目标已经合并，直接将移动指令添加到已合并列表
            if (u.equals(v)) {
                coalescedMoves.add(mov);
                return;
            }

            // 检查是否可以合并
            boolean canCoalesce = true;
            for (IceMachineRegister t : edges.get(v)) {
                if (edges.get(u).contains(t) || t.equals(u)) {
                    canCoalesce = false;
                    break;
                }
            }

            if (canCoalesce) {
                // 合并节点
                coalescedMoves.add(mov);
                coalescedNodes.add(v);
                alias.put(v, u);

                // 合并边
                HashSet<IceMachineRegister> vAdj = edges.get(v);
                for (IceMachineRegister t : vAdj) {
                    edges.get(u).add(t);
                    edges.get(t).add(u);
                }

                // 更新工作列表
                if (edges.get(u).size() >= maxReg && simplifyList.contains(u)) {
                    simplifyList.remove(u);
                    spillList.add(u);
                }
            } else {
                constrainedMoves.add(mov);
            }
        }

        private boolean isMoveInstruction(IceMachineInstruction instr) {
            return instr.toString().startsWith("MOV") ||
                    instr.toString().startsWith("LDR") && instr.getSourceOperands().size() == 1;
        }

        private void freeze() {
            simplifyList.addAll(freezeList);
            freezeList.clear();
        }

        private void spill() {
            IceMachineRegister reg = selectSpill();
            simplifyList.add(reg);
            spillList.remove(reg);
        }

        private IceMachineRegister selectSpill() {
            IceMachineRegister best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (IceMachineRegister reg : spillList) {
                if (hasSpilled.contains(reg.hashCode())) continue;
                int uses = useCount.getOrDefault(reg, 1); // 防止除0
                int degree = edges.getOrDefault(reg, new HashSet<>()).size();
                double score = degree == 0 ? uses : (double) uses / degree;
                if (score < bestScore) {
                    bestScore = score;
                    best = reg;
                }
            }
            if (best == null) {
                // fallback: 选择 spillList 中第一个
                best = spillList.iterator().next();
            }
            hasSpilled.add(best.hashCode());
            return best;
        }

        private void stain() {
            // 记录每个类型物理寄存器的使用次数
            Map<IceType, Map<IceMachineRegister, Integer>> typeRegUsage = new HashMap<>();
            for (IceType type : phyRegsByType.keySet()) {
                typeRegUsage.put(type, new HashMap<>());
            }
            while (!simplifiedNodes.isEmpty()) {
                IceMachineRegister node = simplifiedNodes.pop();
                helpFind.remove(node);
                // 获取节点的类型
                IceType nodeType = node.getType();
                // 获取该类型可用的物理寄存器，类型不匹配直接报错
                List<IceMachineRegister> typeRegs = phyRegsByType.get(nodeType);
                if (typeRegs == null || typeRegs.isEmpty()) {
                    throw new IllegalStateException("No physical registers for type: " + nodeType);
                }
                List<IceMachineRegister> availableRegs = new ArrayList<>(typeRegs);
                // 过滤掉已被相邻节点使用的寄存器
                for (IceMachineRegister adj : edges.getOrDefault(node, new HashSet<>())) {
                    IceMachineRegister aliasReg = getAlias(adj);
                    if (color.containsKey(aliasReg)) {
                        availableRegs.remove(color.get(aliasReg));
                    }
                }
                if (availableRegs.isEmpty()) {
                    spilledNodes.add(node);
                } else {
                    // 选择使用次数最少的寄存器，确保类型匹配
                    Map<IceMachineRegister, Integer> usageMap = typeRegUsage.get(nodeType);
                    IceMachineRegister bestReg = availableRegs.stream()
                            .min(Comparator.comparingInt(reg -> usageMap.getOrDefault(reg, 0)))
                            .orElse(availableRegs.get(0));
                    color.put(node, bestReg);
                    usageMap.put(bestReg, usageMap.getOrDefault(bestReg, 0) + 1);
                }
            }
            // 处理已合并的节点
            for (IceMachineRegister reg : coalescedNodes) {
                IceMachineRegister aliasReg = getAlias(reg);
                color.put(reg, color.get(aliasReg));
            }
        }

        private IceMachineRegister getAlias(IceMachineRegister reg) {
            while (coalescedNodes.contains(reg)) {
                reg = alias.get(reg);
            }
            return reg;
        }

        private void dealSpill() {
            for (IceMachineRegister reg : spilledNodes) {
                // 获取虚拟寄存器的类型
                IceType regType = reg.getType();
                // 为溢出的寄存器分配栈空间
                IceStackSlot slot = slotMap.computeIfAbsent(reg.hashCode(),
                        k -> function.allocateVariableStackSlot(regType));
                slot.setAlignment(4);  // 默认4字节对齐
                // 遍历所有使用该寄存器的指令
                for (var block : function) {
                    for (int i = 0; i < block.size(); i++) {
                        var instruction = (IceMachineInstruction) block.get(i);
                        boolean usesReg = false;
                        boolean defsReg = false;
                        // 检查源操作数
                        for (var operand : instruction.getSourceOperands()) {
                            if (operand instanceof IceMachineRegister.RegisterView registerView) {
                                if (registerView.getRegister().equals(reg)) {
                                    usesReg = true;
                                    break;
                                }
                            }
                        }
                        // 检查目标寄存器
                        var resultReg = instruction.getResultReg();
                        if (resultReg != null && resultReg.getRegister().equals(reg)) {
                            defsReg = true;
                        }
                        if (usesReg) {
                            // 只用 x9-x15 物理寄存器池
                            List<IceMachineRegister> typeRegs = phyRegsByType.get(IceType.I64);
                            if (typeRegs == null || typeRegs.isEmpty()) {
                                throw new IllegalStateException("No physical registers for spill reload");
                            }
                            IceMachineRegister tempReg = typeRegs.get(0);
                            // 用虚拟寄存器类型生成物理寄存器视图
                            var tempRegView = tempReg.createView(regType);
                            var load = new ARM64Instruction("LDR {dst}, {local:src}", tempRegView, slot);
                            load.setParent(block);
                            block.add(i, load);
                            i++;
                            instruction.replaceOperand(reg.createView(regType), tempRegView);
                        }
                        if (defsReg) {
                            List<IceMachineRegister> typeRegs = phyRegsByType.get(IceType.I64);
                            if (typeRegs == null || typeRegs.isEmpty()) {
                                throw new IllegalStateException("No physical registers for spill store");
                            }
                            IceMachineRegister tempReg = typeRegs.get(0);
                            var tempRegView = tempReg.createView(regType);
                            instruction.replaceOperand(reg.createView(regType), tempRegView);
                            var store = new ARM64Instruction("STR {src}, {local:target}", tempRegView, slot);
                            store.setParent(block);
                            block.add(i + 1, store);
                            i++;
                        }
                    }
                }
            }
        }

        private void optimize() {
            optimizeConsecutiveLoadStore();
            optimizeRedundantLoads();
        }

        private void optimizeConsecutiveLoadStore() {
            // 消除连续的加载/存储指令
            for (var block : function) {
                for (int i = 0; i < block.size() - 1; i++) {
                    var instr1 = (IceMachineInstruction) block.get(i);
                    var instr2 = (IceMachineInstruction) block.get(i + 1);

                    // 检查STR后跟LDR的模式，且目标相同
                    if (instr1.toString().startsWith("STR") &&
                            instr2.toString().startsWith("LDR")) {
                        // 进一步检查寄存器和栈槽是否相同（简化检查）
                        if (instr1.toString().contains(instr2.toString().substring(4))) {
                            block.remove(i + 1);
                            i--; // 回退一步继续检查
                        }
                    }
                }
            }
        }

        private void optimizeRedundantLoads() {
            // 消除冗余的加载指令
            for (var block : function) {
                Map<String, Integer> lastLoadPos = new HashMap<>();

                for (int i = 0; i < block.size(); i++) {
                    var instr = (IceMachineInstruction) block.get(i);

                    // 如果是LDR指令
                    if (instr.toString().startsWith("LDR")) {
                        String loadKey = instr.toString().substring(4); // 简化：使用指令字符串作为键

                        if (lastLoadPos.containsKey(loadKey)) {
                            // 找到了重复加载
                            int lastPos = lastLoadPos.get(loadKey);

                            // 检查是否可以删除当前加载
                            boolean canRemove = true;
                            for (int j = lastPos + 1; j < i; j++) {
                                var midInstr = (IceMachineInstruction) block.get(j);
                                // 如果中间有修改寄存器的指令，则不能删除
                                if (midInstr.toString().contains(loadKey.split(",")[0].trim())) {
                                    canRemove = false;
                                    break;
                                }
                            }

                            if (canRemove) {
                                block.remove(i);
                                i--;
                                continue;
                            }
                        }

                        lastLoadPos.put(loadKey, i);
                    }
                }
            }
        }
    }
}
