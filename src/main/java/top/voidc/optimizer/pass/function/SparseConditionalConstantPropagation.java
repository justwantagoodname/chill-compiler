package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;

import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;

enum LatticeValue {
    Undefined,
    Constant,
    Overdefined
}

class ValueLatticeElement {
    private LatticeValue state;
    private IceConstant constant;

    ValueLatticeElement(IceConstant constant) {
        if (constant != null) {
            state = LatticeValue.Constant;
            this.constant = constant;
        } else {
            state = LatticeValue.Undefined;
        }
    }

    public LatticeValue getState() {
        return state;
    }

    public Optional<IceConstant> getConstant() {
        return state == LatticeValue.Constant ? Optional.of(constant) : Optional.empty();
    }

    public void markConstant(IceConstant c) {
        if (state == LatticeValue.Undefined) {
            state = LatticeValue.Constant;
            constant = c;
        } else if (state == LatticeValue.Constant && !constant.equals(c)) {
            state = LatticeValue.Overdefined;
            constant = null;
        }
    }

    public void markOverdefined() {
        state = LatticeValue.Overdefined;
        constant = null;
    }

    public boolean isConstant() {
        return state == LatticeValue.Constant;
    }

    public boolean isOverdefined() {
        return state == LatticeValue.Overdefined;
    }
}

class SCCPSolver {
    private final IceFunction function;

    // 边分析器用的记录
    private record Edge(IceBlock from, IceBlock to) {
    }

    private final Map<IceValue, ValueLatticeElement> valueLattice = new HashMap<>();
    private final Set<IceBlock> executableBlocks = new HashSet<>();
    private final Queue<IceBlock> blockWorkList = new ArrayDeque<>();
    private final Queue<Edge> edgeWorkList = new ArrayDeque<>();
    private final Queue<IceInstruction> instWorkList = new ArrayDeque<>();

    SCCPSolver(IceFunction function) {
        this.function = function;
    }

    private ValueLatticeElement getLattice(IceValue value) {
        return valueLattice.computeIfAbsent(value, k -> new ValueLatticeElement(value instanceof IceConstant ? (IceConstant) value : null));
    }

    private void markBlockExecutable(IceBlock block) {
        if (executableBlocks.add(block)) {
            blockWorkList.add(block);
        }
    }

    private void markEdgeExecutable(IceBlock from, IceBlock to) {
        edgeWorkList.add(new Edge(from, to));
    }

    private void processBlock(IceBlock block) {
        for (IceInstruction inst : block.getInstructions()) {
            instWorkList.add(inst);
        }

        // 不知道在这里处理边是不是对的
        // 先删了
//        IceInstruction terminator = block.getInstructions().get(block.getInstructions().size() - 1);
//        if (terminator instanceof IceBranchInstruction br) {
//            visitBranch(br);
//        }
    }

    private void processEdge(Edge edge) {
        IceBlock from = edge.from;
        IceBlock to = edge.to;

        for (IceInstruction inst : to.getInstructions()) {
            if (inst instanceof IcePHINode phiNode) {
                visitPHI(phiNode, from);
            }
        }

        // 标记边到达的块可执行
        markBlockExecutable(to);
    }

    private void processInstruction(IceInstruction inst) {
//        if (inst instanceof IceBranchInstruction) return;

        // 替换当前指令中的所有常量操作数
//        List<IceValue> operands = inst.getOperandsList();
//        for (int i = 0; i < operands.size(); ++i) {
//            IceValue opr = inst.getOperand(i);
//            ValueLatticeElement lat = getLattice(opr);
//            if (lat.isConstant()) {
//                IceConstant constant = lat.getConstant().orElseThrow();
//                inst.replaceOperand(opr, constant);
//            }
//        }

        if (inst instanceof IceBinaryInstruction bin) {
            visitBin(bin);
        } else if (inst instanceof IceIcmpInstruction cmp) {
            // 先处理是 icmp 的情况
            visitIcmp(cmp);
        } else if (inst instanceof IceBranchInstruction br) {
            visitBranch(br);
        }
    }

    private void visitBranch(IceBranchInstruction inst) {
        // 如果没有条件，直接标记分支可执行
        if (!inst.isConditional()) {
            markEdgeExecutable(inst.getParent(), inst.getTargetBlock());
            return;
        }

        ValueLatticeElement condLat = getLattice(inst.getCondition());
        if (condLat.isConstant()) {
            IceConstantBoolean cond = (IceConstantBoolean) condLat.getConstant().orElseThrow();
            markEdgeExecutable(inst.getParent(), cond.getValue() == 1 ? inst.getTrueBlock() : inst.getFalseBlock());

            // 删除不能走的分支
            if (cond.getValue() == 1) {
                // 删除 false 分支
                IceBranchInstruction trueBranch = new IceBranchInstruction(inst.getParent(), inst.getTrueBlock());
                inst.getParent().addInstruction(trueBranch);
                inst.getParent().removeInstruction(inst);
            } else {
                // 删除 true 分支
                IceBranchInstruction falseBranch = new IceBranchInstruction(inst.getParent(), inst.getFalseBlock());
                inst.getParent().addInstruction(falseBranch);
                inst.getParent().removeInstruction(inst);
            }
        } else {
            markEdgeExecutable(inst.getParent(), inst.getTrueBlock());
            markEdgeExecutable(inst.getParent(), inst.getFalseBlock());
        }
    }

    private void visitIcmp(IceIcmpInstruction inst) {
        var operands = inst.getOperandsList();
        ValueLatticeElement a = getLattice(operands.get(0));
        ValueLatticeElement b = getLattice(operands.get(1));
        ValueLatticeElement lat = getLattice(inst);
        if (a.isConstant() && b.isConstant()) {
            // icmp 指令的操作数是整数类型，因此这里可以直接强制转换为整数
            IceConstantInt ca = (IceConstantInt) a.getConstant().orElseThrow();
            IceConstantInt cb = (IceConstantInt) b.getConstant().orElseThrow();
            long va = ca.getValue();
            long vb = cb.getValue();

            boolean result = switch (inst.getCmpType()) {
                case EQ -> va == vb;
                case NE -> va != vb;
                case SLT -> va < vb;
                case SLE -> va <= vb;
                case SGT -> va > vb;
                case SGE -> va >= vb;
                default -> throw new IllegalArgumentException("Unsupported comparison type: " + inst.getCmpType());
            };

            lat.markConstant(new IceConstantBoolean(result));
        } else if (a.isOverdefined() || b.isOverdefined()) {
            lat.markOverdefined();
        }
        enqueueUsers(inst);
    }

    private <T extends Number> T calculateHelper(T opr1, T opr2, InstructionType type) {
        Number result;

        switch (type) {
            case ADD -> result = opr1.intValue() + opr2.intValue();
            case SUB -> result = opr1.intValue() - opr2.intValue();
            case MUL -> result = opr1.intValue() * opr2.intValue();
            case SDIV -> result = opr1.intValue() / opr2.intValue();
            case FADD -> result = opr1.floatValue() + opr2.floatValue();
            case FSUB -> result = opr1.floatValue() - opr2.floatValue();
            case FMUL -> result = opr1.floatValue() * opr2.floatValue();
            case FDIV -> result = opr1.floatValue() / opr2.floatValue();
            default -> throw new IllegalArgumentException("Unsupported operation type: " + type);
        }

        return (T) result;
    }

    private void visitBin(IceBinaryInstruction bin) {
        var operands = bin.getOperandsList();
        ValueLatticeElement a = getLattice(operands.get(0));
        ValueLatticeElement b = getLattice(operands.get(1));
        ValueLatticeElement lat = getLattice(bin);
        if (a.isConstant() && b.isConstant()) {
            IceConstant ca = a.getConstant().orElseThrow();
            IceConstant cb = b.getConstant().orElseThrow();
            if (ca instanceof IceConstantInt va && cb instanceof IceConstantInt vb) {
                int vaCast = (int) va.getValue();
                int vbCast = (int) vb.getValue();
                lat.markConstant(new IceConstantInt(calculateHelper(vaCast, vbCast, bin.getInstructionType())));
            } else if (ca instanceof IceConstantFloat va && cb instanceof IceConstantFloat vb) {
                lat.markConstant(new IceConstantFloat(calculateHelper(va.getValue(), vb.getValue(), bin.getInstructionType())));
            } else {
                // 其它类型不处理，直接标记为 overdefined
                lat.markOverdefined();
            }

        } else if (a.isOverdefined() || b.isOverdefined()) {
            lat.markOverdefined();
        }
        enqueueUsers(bin);
    }

    private void visitPHI(IcePHINode phiNode, IceBlock block) {
        IceValue incomingValue = phiNode.getIncomingValue(block);
        ValueLatticeElement phiLat = getLattice(phiNode);
        ValueLatticeElement opLat = getLattice(incomingValue);
        if (opLat.isOverdefined()) {
            phiLat.markOverdefined();
        } else if (opLat.isConstant()) {
            IceConstant opConst = opLat.getConstant().orElseThrow();
            phiLat.markConstant(opConst);
        }
        if (phiLat.getState() != LatticeValue.Overdefined) {
            enqueueUsers(phiNode);
        }
    }

    private void enqueueUsers(IceValue v) {
        List<IceUser> users = v instanceof IceInstruction ? v.getUsersList() : Collections.emptyList();
        for (IceUser user : users) {
            if (user instanceof IceInstruction inst) {
                if (inst instanceof IceBranchInstruction) {
                    // 分支指令不需要入队
                    continue;
                }
                instWorkList.add(inst);
            }
        }
    }

    public void solve() {
        markBlockExecutable(function.getEntryBlock());
        while (!blockWorkList.isEmpty() || !edgeWorkList.isEmpty() || !instWorkList.isEmpty()) {
            if (!blockWorkList.isEmpty()) {
                processBlock(blockWorkList.poll());
            } else if (!edgeWorkList.isEmpty()) {
                processEdge(edgeWorkList.poll());
            } else {
                processInstruction(instWorkList.poll());
            }
        }
    }

    public void rewriteProgram() {
        // 常量折叠
        for (IceBlock block : function.getBlocks()) {
            List<IceInstruction> instructions = block.getInstructions();
            for (int i = 0; i < instructions.size(); ++i) {
                ValueLatticeElement lat = getLattice(instructions.get(i));
                if (lat.isConstant()) {
                    // 从 user 里替换这个常量
                    IceConstant constant = lat.getConstant().orElseThrow();
                    List<IceUser> users = instructions.get(i).getUsersList();

                    // 这里不能改 enhanced for-loop!!!
                    for (int j = 0; j < users.size(); ++j) {
                        IceUser user = users.get(j);
                        if (user instanceof IceInstruction inst) {
                            inst.replaceOperand(instructions.get(i), constant);
                        }
                    }

                    instructions.remove(i);
                    // 删除了一个指令，后面的指令往前移动，调整 i
                    --i;
                }
            }
        }
    }
}

@Pass
public class SparseConditionalConstantPropagation implements CompilePass<IceFunction> {
    @Override
    public void run(IceFunction target) {
        SCCPSolver solver = new SCCPSolver(target);
        solver.solve();
        solver.rewriteProgram();
    }

    @Override
    public String getName() {
        return "Sparse Conditional Constant Propagation";
    }
}
