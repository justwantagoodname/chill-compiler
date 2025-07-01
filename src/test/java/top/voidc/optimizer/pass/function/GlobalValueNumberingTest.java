package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.constant.IceFunction;

import org.junit.jupiter.api.Test;
import top.voidc.misc.Log;

public class GlobalValueNumberingTest {
    @Test
    public void simpleGVN() {
        IceFunction function = IceFunction.fromTextIR("""
                define void @testFunction() {
                    %entry:
                        %0 = add i32 1, 2
                        %1 = add i32 1, 2
                        %p = add i32 2, 1
                        %2 = add i32 5, 6
                        %3 = add i32 1, 2
                        %4 = add i32 5, 6
                        %q = add i32 100, 300
                        %lmq = add i32 6, 5
                        %ouc = add i32 300, 100
                        %important = add i32 %1, %3
                        %other = add i32 %lmq, %ouc
                        ret void
                }
                """);

        GlobalValueNumbering gvn = new GlobalValueNumbering();
        gvn.run(function);
        Log.d(function.getTextIR());
    }

    @Test
    public void testcase30GVN() {
        IceFunction function =  IceFunction.fromTextIR("""
                define i32 @main() {
                %entry:
                	br label %while.cond
                %while.cond:
                	%14 = phi i32 [ 0, %entry ], [ %11, %if.end1 ], [ %13, %if.then1 ]
                	%15 = phi i32 [ 0, %entry ], [ %9, %if.end1 ], [ %15, %if.then1 ]
                	%3 = icmp slt i32 %14, 100
                	br i1 %3, label %while.body, label %while.end
                %while.end:
                	ret i32 %15
                %while.body:
                	%6 = icmp eq i32 %14, 50
                	br i1 %6, label %if.then1, label %if.end1
                %if.end1:
                	%9 = add i32 %15, %14
                	%11 = add i32 %14, 1
                	br label %while.cond
                %if.then1:
                	%13 = add i32 %14, 1
                	br label %while.cond
                }
                """);

        GlobalValueNumbering gvn = new GlobalValueNumbering();
        gvn.run(function);
        Log.d(function.getTextIR());
    }
}
