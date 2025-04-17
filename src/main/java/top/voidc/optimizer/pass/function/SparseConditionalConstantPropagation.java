package top.voidc.optimizer.pass.function;

import com.ibm.icu.impl.UResource;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;
import top.voidc.ir.ice.type.IceType;

import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;

enum LatticeValue {
    Undefined,
    Constant,
    Overdefined
}

class ValueLatticeElement {
    private LatticeValue state = LatticeValue.Undefined;
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
    private record Edge(IceBlock from, IceBlock to) {}

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

        IceInstruction terminate = block.getInstructions().isEmpty() ? null : block.getInstructions().get(block.getInstructions().size() - 1);
        if (terminate instanceof IceBranchInstruction br) {
            ValueLatticeElement condLat = getLattice(br.getCondition());
            if (condLat.isConstant()) {
                IceConstantBoolean cond = (IceConstantBoolean) condLat.getConstant().orElseThrow();
                markEdgeExecutable(block, cond.getValue() == 1 ? br.getTrueBlock() : br.getFalseBlock());
            } else {
                markEdgeExecutable(block, br.getTrueBlock());
                markEdgeExecutable(block, br.getFalseBlock());
            }
        }
    }

    private void processEdge(Edge edge) {
        IceBlock to = edge.to;

        for (IceInstruction inst : to.getInstructions()) {
            if (inst instanceof IcePHINode phiNode) {
                visitPHI(phiNode, to);
            }
        }
    }

    private void processInstruction(IceInstruction inst) {
        if (inst instanceof IceBranchInstruction) return;

        if (inst instanceof IceBinaryInstruction bin) {
            visitBin(bin);
        }
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
            // 简化示例，仅处理整数相加
            IceConstant ca = a.getConstant().orElseThrow();
            IceConstant cb = b.getConstant().orElseThrow();
            if (ca instanceof IceConstantInt va && cb instanceof IceConstantInt vb) {
                int vaCast = (int) va.getValue();
                int vbCast = (int) vb.getValue();
                lat.markConstant(new IceConstantInt(calculateHelper(vaCast, vbCast, bin.getInstructionType())));
            } else if (ca instanceof IceConstantFloat va && cb instanceof IceConstantFloat vb) {
                lat.markConstant(new IceConstantFloat(calculateHelper(va.getValue(), vb.getValue(), bin.getInstructionType())));
            } else {
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
        List<IceUser> users = v instanceof IceInstruction ? ((IceInstruction)v).getUsersList() : Collections.emptyList();
        for (IceUser user : users) {
            if (user instanceof IceInstruction inst) {
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
                    for (int j = 0; j < users.size(); ++j) {
                        IceUser user = users.get(j);
                        if (user instanceof IceInstruction inst) {
                            inst.replaceOperand(instructions.get(i), constant);
                        }
                    }

                    instructions.remove(i);
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
