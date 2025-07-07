package top.voidc.backend.instr;

import org.junit.jupiter.api.Test;
import top.voidc.ir.ice.constant.IceFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SSADestructionTest {

    @Test
    public void testSimplePhiElimination() {
        var function = IceFunction.fromTextIR("""
                define i32 @foo(i32 %x) {
                entry:
                	%0 = icmp sgt i32 %x, 0
                	br i1 %0, label %if.then, label %if.else
                if.else:
                	br label %if.end
                if.then:
                	br label %if.end
                if.end:
                	%1 = phi i32 [ 2, %if.else ], [ 1, %if.then ]
                	ret i32 %1
                }
                """);

        final var ssaDestruction = new SSADestruction();
        ssaDestruction.run(function);
        var expected = """
                define i32 @foo(i32 %x) {
                entry:
                	%0 = icmp sgt i32 %x, 0
                	br i1 %0, label %if.then, label %if.else
                if.else:
                	copy i32 2 to i32 %1
                	br label %if.end
                if.then:
                	copy i32 1 to i32 %1
                	br label %if.end
                if.end:
                	%1 = phi i32 [ 2, %if.else ], [ 1, %if.then ] ; removed
                	ret i32 %1
                }""";

        assertEquals(expected, function.getTextIR());
    }

    @Test
    public void testCriticalEdgeSplit() {
        var function = IceFunction.fromTextIR("""
                define i32 @foo(i32 %x) {
                entry:
                	%0 = icmp sgt i32 %x, 0
                	br i1 %0, label %if.then, label %if.end
                if.then:
                	br label %if.end
                if.end:
                	%1 = phi i32 [ 0, %entry ], [ 1, %if.then ]
                	ret i32 %1
                }
                """);

        final var ssaDestruction = new SSADestruction();
        ssaDestruction.run(function);

        var expected = """
                define i32 @foo(i32 %x) {
                entry:
                	%0 = icmp sgt i32 %x, 0
                	br i1 %0, label %if.then, label %L0
                L0:
                	copy i32 0 to i32 %1
                	br label %if.end
                if.then:
                	copy i32 1 to i32 %1
                	br label %if.end
                if.end:
                	%1 = phi i32 [ 0, %L0 ], [ 1, %if.then ] ; removed
                	ret i32 %1
                }""";
        assertEquals(expected, function.getTextIR());
    }
}
