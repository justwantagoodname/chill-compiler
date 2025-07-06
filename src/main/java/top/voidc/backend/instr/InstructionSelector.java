package top.voidc.backend.instr;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.*;

/**
 * 基于DAG的指令选择器 在一个基本块上进行指令选择，计算得到的结果存储在虚拟寄存器中
 */
public class InstructionSelector {

    public record MatchResult(
            int cost,
            InstructionPattern<?> matchedPattern
    ) {}

    private final IceFunction iceFunction;
    private final IceMachineFunction machineFunction;
    private final Collection<InstructionPattern<?>> patternPack;
    private final Map<IceValue, MatchResult> costCache = new HashMap<>();
    private final IceBlock block;
    private final List<IceMachineInstruction> emittedInstructions = new ArrayList<>();

    public InstructionSelector(IceFunction function, IceMachineFunction machineFunction, IceBlock block, Collection<InstructionPattern<?>> patternPack) {
        this.patternPack = patternPack;
        this.iceFunction = function;
        this.machineFunction = machineFunction;
        this.block = block;
    }

    public IceFunction getIceFunction() {
        return iceFunction;
    }

    public IceMachineFunction getMachineFunction() {
        return machineFunction;
    }

    /**
     * 将IceUnit中的每个函数进行执行选择
     * @return 匹配结果，如果是null代码匹配失败
     */
    public boolean doSelection() {
        for (var instruction : block) {
            if (instruction instanceof IcePHINode) {
                // TODO: 需要特殊处理
                throw new IllegalStateException("必须先进行SSA消除才能开始指令选择");
            }
            if (select(instruction) == null) return false;
        }

        // 2. 寻找根节点
        Set<IceValue> allOperands = new HashSet<>();
        for (IceInstruction instruction : block) {
            allOperands.addAll(instruction.getOperands());
        }

        // emit内部会使用valueToVRegMap来防止重复生成
        for (IceInstruction instruction : block) {
            // 如果一个指令本身没有被用作操作数（并且它有副作用，如store, ret），它就是根
            if (!allOperands.contains(instruction) || hasSideEffect(instruction)) {
                emit(instruction);
            }
        }
        return true;
    }

    private boolean hasSideEffect(IceInstruction instruction) {
        return instruction instanceof IceStoreInstruction ||
                instruction instanceof IceLoadInstruction ||
                instruction instanceof IceRetInstruction ||
                instruction instanceof IceBranchInstruction ||
                instruction instanceof IceCallInstruction;
    }

    public MatchResult select(IceValue value) {
        if (costCache.containsKey(value)) {
            return costCache.get(value);
        }

        var currentCost = Integer.MAX_VALUE;
        InstructionPattern<?> currentPattern = null;

        for (var pattern : patternPack) {
            if (pattern.test(this, value)) {
                // 计算代价
                var cost = pattern.getCostForValue(this, value);
                if (cost < currentCost) {
                    currentCost = cost;
                    currentPattern = pattern;
                }
            }
        }

        if (currentPattern == null) {
            throw new RuntimeException("Selection Error! No Pattern Usable for " + value);
        }

        var result = new MatchResult(currentCost, currentPattern);
        costCache.put(value, result);

        return result;
    }


    /**
     * 为节点生成对应的指令
     * @param value 要生成指令的节点
     * @return 指令结果存放的寄存器，如果无结果指令，那么返回null
     */
    public IceMachineRegister emit(IceValue value) {
        // 如果这个值已经计算过并存放在某个虚拟寄存器中，直接返回该寄存器
        return machineFunction.getRegisterForValue(value).orElseGet(() -> {
            MatchResult match = costCache.get(value);
            // 从cache中获取为该值选择的最佳模式
            if (match == null) {
                // 这说明当前此模式没有匹配过这很明显是有问题的
                throw new IllegalStateException("Emit Error! Value was not selected: " + value);
            }

            // 使用模式的emit方法来生成指令
            // emit方法内部会递归调用 selector.emit(operand) 来获取操作数寄存器
            final var resultReg = match.matchedPattern().emitForValue(this, value);

            // 将IR值和它的虚拟寄存器关联起来
            if (resultReg != null) {
                if (resultReg.isVirtualize()) {
                    // 如果是虚拟寄存器，绑定到虚拟寄存器
                    machineFunction.bindVirtualRegisterToValue(value, resultReg);
                } else {
                    // 如果是物理寄存器，直接绑定
                    machineFunction.bindPhysicalRegisterToValue(value, resultReg);
                }
            }
            return resultReg;
        });
    }

    /**
     * 在emit内部被InstructionPattern调用，用于将生成的指令添加到最终列表中
     */
    public IceMachineInstruction addEmittedInstruction(IceMachineInstruction instruction) {
        this.emittedInstructions.add(instruction);
        return instruction;
    }


    /**
     * @return 最终匹配完成的指令
     */
    public List<IceMachineInstruction> getResult() {
        return emittedInstructions;
    }
}
