package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.constant.IceConstantInt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IcePHINodeTest {
    @Test
    public void testBasicPhi() {
        IcePHINode phiNode = new IcePHINode(null, "phi", IceType.I32);

        IceBlock block1 = new IceBlock(null, "b1");
        IceBlock block2 = new IceBlock(null, "b2");

        phiNode.addBranch(block1, new IceValue("a", IceType.I32));
        phiNode.addBranch(block2, new IceConstantInt(5));

        StringBuilder sb = new StringBuilder();
        phiNode.getTextIR(sb);
        String expected = "%phi = phi i32 [ %a, %b1 ], [ 5, %b2 ]";
        assertEquals(expected, sb.toString());
    }
}
