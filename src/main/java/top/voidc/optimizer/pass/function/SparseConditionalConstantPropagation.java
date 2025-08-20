package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.instruction.*;

import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;


@Pass(
        group = {"O0"} // O0 吧因为有些 10 / 5 这种常量除法会被优化掉
)
public class SparseConditionalConstantPropagation implements CompilePass<IceFunction> {

    private static class SCCPSolver {
        public boolean isChanged() {
            return changed;
        }

        public class ValueLatticeElement {

            enum LatticeValue {
                Undefined,
                Constant,
                Overdefined
            }

            private LatticeValue state;
            private IceConstantData constant;
            private final IceValue bindValue;

            ValueLatticeElement(IceValue bindValue, IceConstantData constant) {
                this.bindValue = bindValue;
                if (constant != null) { // Note: Undef 同样是常量 它和格值里面的 Undefined 是不一样的
                    state = LatticeValue.Constant;
                    this.constant = constant;
                } else {
                    state = LatticeValue.Undefined;
                }
            }

            public Optional<IceConstantData> getConstant() {
                return state == LatticeValue.Constant ? Optional.of(constant) : Optional.empty();
            }

            public void markConstant(IceConstantData c) {
                if (state == LatticeValue.Undefined) {
                    if (bindValue != null) enqueueUsers(bindValue);

                    state = LatticeValue.Constant;
                    constant = c;
                } else if (state == LatticeValue.Constant && !constant.equals(c)) {
                    if (bindValue != null) enqueueUsers(bindValue);

                    state = LatticeValue.Overdefined;
                    constant = null;
                }
            }

            public void markOverdefined() {
                if (state != LatticeValue.Overdefined && bindValue != null) enqueueUsers(bindValue);

                state = LatticeValue.Overdefined;
                constant = null;
            }

            public boolean isConstant() {
                return state == LatticeValue.Constant;
            }

            public boolean isOverdefined() {
                return state == LatticeValue.Overdefined;
            }

            public boolean isUndefined() {
                return state == LatticeValue.Undefined;
            }

            @Override
            public String toString() {
                return "ValueLatticeElement{" +
                        "state=" + state +
                        ", constant=" + constant +
                        ", bindValue=" + bindValue +
                        '}';
            }
        }

        private boolean changed = false;
        private final IceFunction function;
        private final Map<IceValue, ValueLatticeElement> valueLattice = new HashMap<>();
        private final Set<IceBlock> executableBlocks = new HashSet<>();
        private final Queue<IceValue> workList = new ArrayDeque<>();
        private final Set<IceBlock> totalBlocks = new HashSet<>();

        SCCPSolver(IceFunction function) {
            this.function = function;
            // 将函数参数全部标记为 overdefined
            function.getParameters().stream().map(this::getLattice).forEach(ValueLatticeElement::markOverdefined);
        }

        private ValueLatticeElement getLattice(IceValue value) {
            if (value instanceof IceConstantData) {
                // 常量不放进valueLattice中
                return new ValueLatticeElement(value, (IceConstantData) value);
            }
            return valueLattice.computeIfAbsent(value, _ ->
                    new ValueLatticeElement(value, null));
        }

        private void markBlockExecutable(IceBlock block) {
            if (executableBlocks.add(block)) {
                workList.add(block);
            }
        }

        private void processBlock(IceBlock block) {
            workList.addAll(block);
        }

        private void processInstruction(IceInstruction inst) {
            if (!executableBlocks.contains(inst.getParent())) {
                return; // 如果指令在不可达块中，则直接跳过，不进行任何处理
            }

            switch (inst) {
                case IceBinaryInstruction bin -> visitBin(bin);
                case IceCmpInstruction cmp -> visitCmp(cmp);
                case IceBranchInstruction branch -> visitBranch(branch);
                case IcePHINode phiNode -> visitPHI(phiNode);
                case IceNegInstruction neg -> visitUnary(neg);
                case IceConvertInstruction convert -> visitConvert(convert);
                case IceSelectInstruction select -> visitSelect(select);
                default -> {
                    if (!inst.getType().isVoid()) {
                        getLattice(inst).markOverdefined();
                    }
                }
            }
        }

        private void revisitPHINodes(IceBlock block) {
            // PHI 节点总是位于块的开头
            for (IceInstruction instruction : block) {
                if (instruction instanceof IcePHINode phi) {
                    workList.add(phi);
                } else {
                    break; // 遇到非 PHI 指令即停止
                }
            }
        }

        private void visitConvert(IceConvertInstruction convert) {
            var sourceLat = getLattice(convert.getOperand());
            var convertLat = getLattice(convert);
            if (sourceLat.isConstant()) {
                var toType = convert.getType();
                var sourceConstant = sourceLat.getConstant().orElseThrow();
                convertLat.markConstant(sourceConstant.castTo(toType));
            } else {
                convertLat.markOverdefined(); // 如果源操作数不是常量，则转换结果也是 overdefined
            }
        }

        private void visitSelect(IceSelectInstruction select) {
            ValueLatticeElement condLat = getLattice(select.getCondition());
            var selectLat = getLattice(select);
            if (condLat.isConstant()) {
                IceConstantBoolean cond = (IceConstantBoolean) condLat.getConstant().orElseThrow();
                // 条件是常量，所以只有一条路径是可执行的
                if (cond.getValue() == 1) {
                    var trueLat = getLattice(select.getTrueValue());
                    if (trueLat.isOverdefined()) selectLat.markOverdefined();
                     else selectLat.markConstant(trueLat.getConstant().orElseThrow());
                } else {
                    var falseLat = getLattice(select.getFalseValue());
                    if (falseLat.isOverdefined()) selectLat.markOverdefined();
                    else selectLat.markConstant(falseLat.getConstant().orElseThrow());
                }
            } else {
                // 条件不是常量，两个分支都可能是可执行的
                selectLat.markOverdefined();
            }
        }

        private void visitBranch(IceBranchInstruction inst) {

            // 如果没有条件，分支总是会跳转。
            if (!inst.isConditional()) {
                final var targetBlock = inst.getTargetBlock();
                markBlockExecutable(targetBlock);
                revisitPHINodes(targetBlock); // 强制重新求值目标块中的 PHI 节点。
                return;
            }

            ValueLatticeElement condLat = getLattice(inst.getCondition());
            if (condLat.isConstant()) {
                IceConstantBoolean cond = (IceConstantBoolean) condLat.getConstant().orElseThrow();
                // 条件是常量，所以只有一条路径是可执行的
                if (cond.getValue() == 1) {
                    final var trueBlock = inst.getTrueBlock();
                    markBlockExecutable(trueBlock);
                    revisitPHINodes(trueBlock); // 在 true 路径上重新求值 PHI
                } else {
                    final var falseBlock = inst.getFalseBlock();
                    markBlockExecutable(falseBlock);
                    revisitPHINodes(falseBlock); // 在 false 路径上重新求值 PHI
                }
            } else {
                // 条件不是常量，两条路径都可能是可执行的
                final var trueBlock = inst.getTrueBlock();
                final var falseBlock = inst.getFalseBlock();
                markBlockExecutable(trueBlock);
                markBlockExecutable(falseBlock);
                revisitPHINodes(trueBlock);
                revisitPHINodes(falseBlock);
            }
        }

        private void visitCmp(IceCmpInstruction cmp) {
            ValueLatticeElement a = getLattice(cmp.getLhs());
            ValueLatticeElement b = getLattice(cmp.getRhs());
            ValueLatticeElement lat = getLattice(cmp);
            if (a.isConstant() && b.isConstant()) {
                final var ca = a.getConstant().orElseThrow();
                final var cb = b.getConstant().orElseThrow();

                var result = switch (cmp) {
                    case IceCmpInstruction.Icmp icmp -> switch (icmp.getCmpType()) {
                        case EQ -> ca.eq(cb);
                        case NE -> ca.ne(cb);
                        case SLT -> ca.lt(cb);
                        case SLE -> ca.le(cb);
                        case SGT -> ca.gt(cb);
                        case SGE -> ca.ge(cb);
                    };
                    case IceCmpInstruction.Fcmp fcmp -> switch (fcmp.getCmpType()) {
                        case OEQ -> ca.eq(cb);
                        case ONE -> ca.ne(cb);
                        case OLT -> ca.lt(cb);
                        case OLE -> ca.le(cb);
                        case OGT -> ca.gt(cb);
                        case OGE -> ca.ge(cb);
                    };
                    default -> throw new IllegalArgumentException("Unknown comparison type: " + cmp);
                };

                lat.markConstant(result);
            } else if (a.isOverdefined() || b.isOverdefined()) {
                lat.markOverdefined();
            }
        }

        private void visitBin(IceBinaryInstruction bin) {
            ValueLatticeElement a = getLattice(bin.getLhs());
            ValueLatticeElement b = getLattice(bin.getRhs());
            ValueLatticeElement lat = getLattice(bin);
            if (a.isConstant() && b.isConstant()) {
                final var ca = a.getConstant().orElseThrow();
                final var cb = b.getConstant().orElseThrow();
                final var result = switch (bin) {
                    case IceBinaryInstruction.Add _, IceBinaryInstruction.FAdd _ -> ca.plus(cb);
                    case IceBinaryInstruction.Sub _, IceBinaryInstruction.FSub _ -> ca.minus(cb);
                    case IceBinaryInstruction.Mul _, IceBinaryInstruction.FMul _ -> ca.multiply(cb);
                    case IceBinaryInstruction.SDiv _, IceBinaryInstruction.FDiv _ -> ca.divide(cb);
                    case IceBinaryInstruction.Mod _ -> ca.mod(cb);
                    default -> throw new IllegalArgumentException("Unsupported operation type: " + bin);
                };
                lat.markConstant(result);
            } else if (a.isOverdefined() || b.isOverdefined()) {
                lat.markOverdefined();
            }
        }

        private void visitUnary(IceNegInstruction neg) {
            final var lat = getLattice(neg);
            final var a = getLattice(neg.getOperand(0));
            if (a.isConstant()) {
                final var ca = a.getConstant().orElseThrow();
                final var result = IceConstantData.create(0).minus(ca);
                lat.markConstant(result);
            } else if (a.isOverdefined()) {
                lat.markOverdefined();
            }
        }

        private void visitPHI(IcePHINode phiNode) {
            final var phiLat = getLattice(phiNode);
            final var reachableIncomingValues = phiNode.getBranches().stream()
                    .filter(icePHIBranch -> executableBlocks.contains(icePHIBranch.block()))
                    .map(icePHIBranch -> getLattice(icePHIBranch.value())).toList(); // 仅仅获取可达块的值


            if (reachableIncomingValues.isEmpty()
                    || reachableIncomingValues.stream().allMatch(ValueLatticeElement::isUndefined)) return;
            // 全是 undefined 此时可以忽略

            if (reachableIncomingValues.stream().anyMatch(ValueLatticeElement::isOverdefined)) {
                phiLat.markOverdefined();
            } else {
                assert reachableIncomingValues.stream()
                        .allMatch(valLat -> valLat.isConstant() || valLat.isUndefined());
                // 全是常量或者 undefined
                final var constantValues = reachableIncomingValues.stream()
                        .filter(ValueLatticeElement::isConstant)
                        .map(ValueLatticeElement::getConstant)
                        .flatMap(Optional::stream)
                        .distinct().toList();
                if (constantValues.size() == 1) {
                    phiLat.markConstant(constantValues.getFirst());
                } else {
                    phiLat.markOverdefined();
                }
            }
        }

        private void enqueueUsers(IceValue v) {
            List<IceUser> users = v instanceof IceInstruction ? v.users() : Collections.emptyList();
            for (IceUser user : users) {
                if (user instanceof IceInstruction inst) {
                    workList.add(inst);
                }
            }
        }

        public void solve() {
            totalBlocks.addAll(function.getBlocks());

            markBlockExecutable(function.getEntryBlock());
            while (!workList.isEmpty()) {
                final var value = workList.poll();
//                Log.d("Processing value: " + value);
                switch (value) {
                    case IceBlock block -> processBlock(block); // 处理块
                    case IceInstruction instruction -> processInstruction(instruction); // 处理指令
                    default -> throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
                }
            }
        }

        public void rewriteProgram() {
            final var deadBlocks = new HashSet<>(totalBlocks);
            deadBlocks.removeAll(executableBlocks);

            // 常量折叠
            for (IceBlock block : executableBlocks) {
                block.safeForEach(instruction -> {
                    switch (instruction) {
                        case null -> {}
                        case IceBranchInstruction _ -> {}
                        case IceLoadInstruction _, IceStoreInstruction _ -> {} // 不做别名分析动不了这俩
                        case IceRetInstruction _ -> {} // 返回的操作数如果是常量早就被替换了
                        case IcePHINode phi -> List.copyOf(phi.getBranches()).forEach(phiBranch -> {
                            if (deadBlocks.contains(phiBranch.block())) {
                                phi.removeOperand(phiBranch.block());
                            }
                        });
                        case IceSelectInstruction select -> {
                            ValueLatticeElement lat = getLattice(select);
                            if (lat.isConstant()) {
                                // 从 user 里替换这个常量
                                final var constant = lat.getConstant().orElseThrow();
                                instruction.replaceAllUsesWith(constant);
                                block.remove(instruction);
                                changed = true;
                            } else {
                                // select 的条件如果是常量也可以替换
                                ValueLatticeElement condLat = getLattice(select.getCondition());
                                if (condLat.isConstant()) {
                                    IceConstantBoolean cond = (IceConstantBoolean) condLat.getConstant().orElseThrow();
                                    IceValue replacement = cond.getValue() == 1 ? select.getTrueValue() : select.getFalseValue();
                                    instruction.replaceAllUsesWith(replacement);

                                    block.remove(instruction);
                                    changed = true;
                                }
                            }
                        }
                        default -> {
                            ValueLatticeElement lat = getLattice(instruction);
                            if (lat.isConstant()) {
                                // 从 user 里替换这个常量
                                final var constant = lat.getConstant().orElseThrow();
                                instruction.replaceAllUsesWith(constant);
                                block.remove(instruction);
                                changed = true;
                            }
                        }
                    }
                });

                final var terminal = block.getLast();
                if (terminal instanceof IceBranchInstruction branch) {

                    if (branch.isConditional()) {
                        // 实际删除死代码
                        if (branch.getCondition() instanceof IceConstantBoolean constCond) {
                            if (constCond.equals(IceConstantData.create(true))) {
                                // 删除 false 分支
                                final var targetBlock = branch.getTrueBlock();
                                branch.destroy();
                                IceBranchInstruction trueBranch = new IceBranchInstruction(block, targetBlock);
                                block.addInstruction(trueBranch);
                            } else {
                                // 删除 true 分支
                                final var targetBlock = branch.getFalseBlock();
                                branch.destroy();
                                IceBranchInstruction trueBranch = new IceBranchInstruction(block, targetBlock);
                                block.addInstruction(trueBranch);
                            }
                            changed = true;
                        }
                    } else {
                        // 理论上移除不了直接转跳的分支，如果移除了那就是bug
                        assert executableBlocks.contains(branch.getTargetBlock());
                    }
                }
            }

            // 删除不可达块
            for (IceBlock block : deadBlocks) {
                block.destroy();
                changed = true;
            }

            // 对于剩下的块中的 phi 节点，如果只有一个分支，则尝试删除 phiNode 中处理过了
        }
    }

    @Override
    public boolean run(IceFunction target) {
        SCCPSolver solver = new SCCPSolver(target);
        solver.solve();
        solver.rewriteProgram();
        return solver.isChanged();
    }

    @Override
    public String getName() {
        return "Sparse Conditional Constant Propagation";
    }
}
