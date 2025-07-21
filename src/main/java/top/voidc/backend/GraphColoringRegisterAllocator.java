package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.arm64.instr.ARM64Register;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;

import top.voidc.misc.annotation.Pass;
import top.voidc.misc.ds.ChilletGraph;

import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O1", "backend"})
public class GraphColoringRegisterAllocator implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {
    private final LivenessAnalysis.LivenessResult livenessResult;
    private final IceContext iceContext;

    // ARM64 integer allocatable registers (x9-x15)
    private static final List<String> ALLOCATABLE_REGS = List.of("x9", "x10", "x11", "x12", "x13", "x14", "x15");

    public GraphColoringRegisterAllocator(LivenessAnalysis.LivenessResult livenessResult, IceContext iceContext) {
        this.livenessResult = livenessResult;
        this.iceContext = iceContext;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        if (!(target instanceof ARM64Function mf)) return false;
        // 1. Collect all virtual registers
        Set<IceMachineRegister> vregs = new HashSet<>();
        for (var reg : mf.getAllRegisters()) {
            if (reg.isVirtualize()) vregs.add(reg);
        }
        if (vregs.isEmpty()) return false;

        // 2. Build interference graph
        ChilletGraph<IceMachineRegister> igraph = new ChilletGraph<>();
        vregs.forEach(igraph::createNewNode);
        var liveness = livenessResult.getLivenessData(target);
        for (var block : target.blocks()) {
            Set<IceMachineRegister> live = new HashSet<>();
            var blockLive = liveness.get(block);
            if (blockLive != null) {
                for (var val : blockLive.liveOut()) {
                    if (val instanceof IceMachineRegister reg && reg.isVirtualize()) {
                        live.add(reg);
                    }
                }
            }
            // For each instruction, add edges between defs and live
            List<IceMachineInstruction> instructions = new ArrayList<>();
            for (var instr : block) instructions.add((IceMachineInstruction) instr);
            Collections.reverse(instructions);
            for (var instr : instructions) {
                IceMachineRegister.RegisterView def = instr.getResultReg();
                if (def != null && def.getRegister().isVirtualize()) {
                    
                    for (var reg : live) {
                        if (!reg.equals(def.getRegister())) {
                            try { igraph.addEdge(def.getRegister(), reg); } catch (Exception ignored) {}
                        }
                    }
                    live.remove(def.getRegister());
                }
                for (var operand : instr.getSourceOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv && rv.getRegister().isVirtualize()) {
                        live.add(rv.getRegister());
                    }
                }
            }
        }

        // 3. Color the graph (register allocation)
        var colorMap = igraph.getColors(ALLOCATABLE_REGS.size()); // node -> color index
        Map<IceMachineRegister, ARM64Register> regAssign = new HashMap<>();
        Set<IceMachineRegister> spilled = new HashSet<>();
        for (var reg : vregs) {
            int color = colorMap.getOrDefault(reg, -1);
            if (color >= 0 && color < ALLOCATABLE_REGS.size()) {
                regAssign.put(reg, (ARM64Register) mf.getPhysicalRegister(ALLOCATABLE_REGS.get(color)));
            } else {
                spilled.add(reg);
            }
        }

        // 4. For spilled registers, allocate stack slots
        Map<IceMachineRegister, IceStackSlot> spillSlots = new HashMap<>();
        for (var reg : spilled) {
            spillSlots.put(reg, mf.allocateVariableStackSlot(reg.getType()));
        }

        // 5. Rewrite instructions: replace vregs with physical regs, insert loads/stores for spills
        boolean changed = false;
        for (var block : target) {
            ListIterator<IceInstruction> it = block.listIterator();
            while (it.hasNext()) {
                int idx = it.nextIndex();
                var obj = it.next();
                var instr = (IceMachineInstruction) obj;
                // Insert loads for spilled source operands
                List<IceMachineInstruction> loads = new ArrayList<>();
                List<IceValue> srcOperands = new ArrayList<>(instr.getSourceOperands());
                for (var operand : srcOperands) {
                    if (operand instanceof IceMachineRegister.RegisterView rv && spilled.contains(rv.getRegister())) {
                        var slot = spillSlots.get(rv.getRegister());
                        var phyReg = mf.getPhysicalRegister(ALLOCATABLE_REGS.getFirst()); // Always use x9 for reload
                        var load = new ARM64Instruction("LDR {dst}, {local:src}", phyReg.createView(rv.getType()), slot);
                        loads.add(load);
                        instr.replaceOperand(operand, load.getResultReg());
                        changed = true;
                    } else if (operand instanceof IceMachineRegister.RegisterView rv && regAssign.containsKey(rv.getRegister())) {
                        var phyReg = regAssign.get(rv.getRegister());
                        instr.replaceOperand(operand, phyReg.createView(rv.getType()));
                        changed = true;
                    }
                }
                if (!loads.isEmpty()) {
                    for (var load : loads) load.setParent(block);
                    block.addAll(idx, loads);
                    idx += loads.size();
                    it = block.listIterator(idx + 1);
                }
                // Insert store for spilled def
                var def = instr.getResultReg();
                if (def != null && spilled.contains(def.getRegister())) {
                    var slot = spillSlots.get(def.getRegister());
                    var phyReg = mf.getPhysicalRegister(ALLOCATABLE_REGS.getFirst()); // Always use x9 for store
                    instr.replaceOperand(def, phyReg.createView(def.getType()));
                    var store = new ARM64Instruction("STR {src}, {local:target}", phyReg.createView(def.getType()), slot);
                    store.setParent(block);
                    block.add(it.nextIndex(), store);
                    changed = true;
                    it = block.listIterator(it.nextIndex() + 1);
                } else if (def != null && regAssign.containsKey(def.getRegister())) {
                    var phyReg = regAssign.get(def.getRegister());
                    instr.replaceOperand(def, phyReg.createView(def.getType()));
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }
}
