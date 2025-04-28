package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceParser;
import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;

public class TypeVisitor extends IceBaseVisitor<IceType> {
    @Override
    public IceType visitDerivedType(IceParser.DerivedTypeContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public IceType visitArrayType(IceParser.ArrayTypeContext ctx) {
        int size = Integer.parseInt(ctx.NUMBER().getText());
        IceType elementType = visit(ctx.type());
        return new IceArrayType(elementType, size);
    }

    @Override
    public IceType visitPointerType(IceParser.PointerTypeContext ctx) {
        IceType baseType;
        if (ctx.baseType() != null) {
            baseType = visit(ctx.baseType());
        } else {
            baseType = visit(ctx.arrayType());
        }
        
        // 根据星号数量构建嵌套的指针类型
        int stars = ctx.stars().getText().length();
        IceType type = baseType;
        for (int i = 0; i < stars; i++) {
            type = new IcePtrType<>(type);
        }
        return type;
    }

    @Override
    public IceType visitType(IceParser.TypeContext ctx) {
        return visitChildren(ctx);
    }
    
    @Override
    public IceType visitBaseType(IceParser.BaseTypeContext ctx) {
        return switch (ctx.getText()) {
            case "void" -> IceType.VOID;
            case "i1" -> IceType.I1;
            case "i8" -> IceType.I8;
            case "i32" -> IceType.I32;
            case "float" -> IceType.F32;
            default -> throw new IllegalArgumentException("Unknown base type: " + ctx.getText());
        };
    }
}
