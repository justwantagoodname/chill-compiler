package top.voidc.misc.scev;

import java.util.HashMap;
import java.util.Map;

public class SCEVTest {
    public static void main(String[] args){
        // 创建循环上下文
        Map<String, SCEVValue.LoopInfo> context = new HashMap<>();
        context.put("loop1", new SCEVValue.LoopInfo(100)); // 设置循环迭代次数为100

        // 构建 i 的 SCEV 表达式: {0, +, 1}<loop1>
        SCEVValue iStart = new SCEVIntConst(0);
        SCEVValue iStep = new SCEVIntConst(1);
        SCEVValue i = new SCEVAddRec(iStart, iStep, "loop1");

        // 构建用于 j 计算的 i 值（i+1）
        SCEVValue iForJ = new SCEVAdd(i, new SCEVIntConst(1)); // 因为循环体中先执行 i++

        // 构建 j 的 SCEV 表达式: {0, +, (i+1)*3}<loop1>
        SCEVValue jIncrement = new SCEVMultiply(iForJ, new SCEVIntConst(3));
        SCEVValue j = new SCEVAddRec(new SCEVIntConst(0), jIncrement, "loop1");

        // 打印原始表达式
        System.out.println("原始表达式:");
        System.out.println("i = " + i);
        System.out.println("j = " + j);


        // 简化表达式
        SCEVValue simplifiedI = i.simplify();
        System.out.println("\ni简化后表达式: " + simplifiedI);

        // 计算最终值
        SCEVValue resulti = simplifiedI.evaluate(context);
        System.out.println("\ni计算结果: " + resulti);

        // 计算最终值
        SCEVValue orgResult = j.evaluate(context);
        System.out.println("\nj原始计算结果: " + orgResult);

        // 简化表达式
        SCEVValue simplifiedJ = j.simplify();
        System.out.println("\nj简化后表达式: " + simplifiedJ);

        // 计算最终值
        SCEVValue result = simplifiedJ.evaluate(context);
        System.out.println("\nj计算结果: " + result);

        // 验证结果（使用数学公式）
        SCEVValue expected = new SCEVIntConst(3 * 100 * 101 / 2); // 3 * n * (n+1)/2
        System.out.println("验证结果 (3*100*101/2): " + expected);
        System.out.println("结果验证: " + (result == expected ? "成功" : "失败"));
    }
}
