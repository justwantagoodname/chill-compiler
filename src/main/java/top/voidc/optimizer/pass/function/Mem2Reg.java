package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.optimizer.pass.Pass;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Memory to Register Promotion
 *
 * This pass creates SSA IR, and promotes memory accesses to register accesses.
 * This pass will try to delete alloca instructions, and replace them with ice-ir registers.
 */
public class Mem2Reg implements Pass<IceFunction> {
    @Override
    public void run(IceFunction target) {
        // hashtable: IceValue -> counting
        Hashtable<IceValue, Integer> allocaTable = new Hashtable<>();

        for (IceInstruction instr : target.getEntryBlock().getInstructions()) {
            if (instr instanceof IceAllocaInstruction value) {
                IceType type = ((IcePtrType<?>) value.getType()).getPointTo();

                if (type instanceof IceArrayType) {
                    continue;
                }

                allocaTable.put(instr, 0);
            }
        }


    }
}
