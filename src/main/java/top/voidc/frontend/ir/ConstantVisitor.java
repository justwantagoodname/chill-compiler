package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.type.*;
import top.voidc.misc.Log;

public class ConstantVisitor extends IceBaseVisitor<IceConstantData> {
    
    @Override
    public IceConstantData visitConstant(IceParser.ConstantContext ctx) {
        try {
            if (ctx.NUMBER() != null) {
                String text = ctx.NUMBER().getText();
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    // 十六进制
                    return IceConstantData.create(Long.parseLong(text.substring(2), 16));
                } else if (text.length() > 1 && text.startsWith("0")) {
                    // 八进制
                    return IceConstantData.create(Long.parseLong(text.substring(1), 8));
                } else {
                    // 十进制
                    return IceConstantData.create(Long.parseLong(text));
                }
            }
            
            if (ctx.FLOAT() != null) {
                return IceConstantData.create(Float.parseFloat(ctx.FLOAT().getText()));
            }
            
            if (ctx.getText().equals("true")) {
                return IceConstantData.create(true);
            }
            if (ctx.getText().equals("false")) {
                return IceConstantData.create(false);
            }
            if (ctx.getText().equals("undef")) {
                return IceConstantData.create("undef"); // TODO: 改成 IceUndefConstant
            }
            Log.should(false, "Unknown constant type: " + ctx.getText());
            return null;
        } catch (NumberFormatException e) {
            Log.should(false, "Failed to parse constant: " + ctx.getText());
            return null;
        }
    }

}
