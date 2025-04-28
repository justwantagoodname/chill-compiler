package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FunctionVisitor extends IceBaseVisitor<IceFunction> {

    private final Map<String, IceValue> environment;

    public FunctionVisitor(Map<String, IceValue> environment) {
        this.environment = environment;
    }

    public FunctionVisitor() {
        this.environment = new HashMap<>();
    }

    private final TypeVisitor typeVisitor = new TypeVisitor();

    @Override
    public IceFunction visitFunctionDecl(IceParser.FunctionDeclContext ctx) {
        // 1. 解析函数返回类型和名称
        var returnType = typeVisitor.visit(ctx.type(0));
        var funcName = ctx.GLOBAL_IDENTIFIER().getText().substring(1); // 去掉@前缀

        // 2. 创建函数实例
        var function = new IceFunction(funcName);
        function.setReturnType(returnType);
        environment.put(funcName, function);

        // 3. 处理参数类型和名称
        for (var i = 0; i < ctx.IDENTIFIER().size(); i++) {
            var paramType = typeVisitor.visit(ctx.type(i + 1)); // +1跳过返回类型
            var paramName = ctx.IDENTIFIER(i).getText().substring(1); // 去掉%前缀
            var param = new IceValue(paramName, paramType);
            function.addParameter(param);
            environment.put(paramName, param);
        }

        // 4. 第一轮：创建所有基本块
        for (var blockCtx : ctx.functionBody().basicBlock()) {
            var blockName = blockCtx.IDENTIFIER().getText().substring(1);
            var block = new IceBlock(function, blockName);
            environment.put(blockName, block);
            if (blockName.equals("entry")) {
                function.setEntryBlock(block);
            }
        }

        // 5. 第二轮：处理所有基本块的指令
        for (var blockCtx : ctx.functionBody().basicBlock()) {
            var blockName = blockCtx.IDENTIFIER().getText().substring(1);
            var block = (IceBlock) environment.get(blockName);
            var blockVisitor = new IceBlockVisitor(function, this.environment, block);
            blockVisitor.visit(blockCtx);
        }

        return function;
    }
}
