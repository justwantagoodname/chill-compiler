package top.voidc.ir.ice.parser;

import org.junit.jupiter.api.Test;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceUndef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IRBuilderTest {
    @Test
    public void testIceConstantParser() {
        final var intValue = IceConstantData.fromTextIR("1");
        assertEquals(intValue, IceConstantData.create(1));

        final var floatValue = IceConstantData.fromTextIR("1.5");

        assertEquals(floatValue, IceConstantData.create(1.5f));

        assertEquals(IceConstantData.fromTextIR("true"), IceConstantData.create(true));

        assertEquals(IceConstantData.fromTextIR("false"), IceConstantData.create(false));
    }
}
