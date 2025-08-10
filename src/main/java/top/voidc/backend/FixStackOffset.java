package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.arm64.instr.pattern.LoadAndStorePattern;
import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Config;
import top.voidc.misc.Tool;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.List;

@Pass(group = {"O0", "backend"}, parallel = true)
public class FixStackOffset implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {


    /**
     * 判断偏移是否是合法的 ARM64 LDR/STR scaled offset。
     *
     * @param offset 要检查的立即数（单位：字节）
     * @param accessSize 访问大小，单位字节（如：1=byte, 2=half, 4=word, 8=double word）
     * @return 是否为合法的立即数偏移
     */
    public static boolean isValidScaledOffset(int offset, int accessSize) {
        // Scaled offset 要求偏移必须是访问大小的整数倍
        if (offset < 0 || offset % accessSize != 0) {
            return false;
        }

        int scaled = offset / accessSize;
        return Tool.inRange(scaled, 0, 4095);
    }

    private List<ARM64Instruction> loadIntToScratchRegister(IceBlock block, IceMachineRegister scratchRegister, int value) {
        var movList = LoadAndStorePattern.ImmediateLoader.loadImmediate32(scratchRegister.createView(IceType.I32), value);
        movList.forEach(instr -> instr.setParent(block));
        return movList;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        // 需要在分配器中保留一个寄存器在这里用
        var scratchRegister = target.getPhysicalRegister(Config.ARM_SCRATCH_REGISTER);
        var isChanged = false;

        for (var block : target) {
            for (var i = 0; i < block.size(); i++) {
                var machineInstruction = (IceMachineInstruction) block.get(i);
                var opcode = machineInstruction.getOpcode();
                // ADD 指令是用于计算栈上偏移的指令
                if (opcode.equals("LDR") || opcode.equals("STR") || opcode.equals("ADD")) {
                    for (var operand: machineInstruction.getSourceOperands()) {
                        if (operand instanceof IceStackSlot stackSlot) {
                            List<ARM64Instruction> loadImms;
                            IceMachineInstruction newInstr;
                            if ((opcode.equals("LDR") || opcode.equals("STR"))
                                && !isValidScaledOffset(stackSlot.getOffset(), machineInstruction.getOperand(0).getType().getByteSize())) {
                                // 偏移不能作为立即数需要调整
                                loadImms = loadIntToScratchRegister(machineInstruction.getParent(), scratchRegister, stackSlot.getOffset());

                                // 计算实际地址

                                loadImms.add(new ARM64Instruction("ADD {dst}, sp, {offset}",
                                        scratchRegister.createView(IceType.I64), scratchRegister.createView(IceType.I64)));
                                newInstr = switch (opcode) {
                                    case "LDR" ->
                                            new ARM64Instruction("LDR {dst}, [{offset}]", machineInstruction.getResultReg(), scratchRegister.createView(IceType.I64));
                                    case "STR" -> new ARM64Instruction("STR {src}, [{offset}]",
                                            (IceMachineRegister.RegisterView) machineInstruction.getSourceOperands().getFirst(), scratchRegister.createView(IceType.I64));
                                    default -> throw new IllegalStateException("Unexpected value: " + opcode);
                                };

                                // 替换原有指令
                                block.addAll(i, loadImms);
                                i += loadImms.size(); // 更新索引位置
                                machineInstruction.replaceAllUsesWith(newInstr);
                                block.set(i, newInstr); // 替换当前指令
                                machineInstruction.destroy();
                                break;
                            } else if (opcode.equals("ADD") && !Tool.isImm12(stackSlot.getOffset())) {
                                loadImms = loadIntToScratchRegister(machineInstruction.getParent(), scratchRegister, stackSlot.getOffset());
                                newInstr = new ARM64Instruction("ADD {dst}, sp, {offset}", machineInstruction.getResultReg(), scratchRegister.createView(IceType.I64));

                                // 替换原有指令
                                block.addAll(i, loadImms);
                                i += loadImms.size(); // 更新索引位置
                                machineInstruction.replaceAllUsesWith(newInstr);
                                block.set(i, newInstr); // 替换当前指令
                                machineInstruction.destroy();
                                break;
                            }
                        }
                    }
                }
            }
        }

        return isChanged;
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
    public int getArchitectureBitSize() {
        return 64;
    }
}
