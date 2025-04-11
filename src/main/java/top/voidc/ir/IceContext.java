package top.voidc.ir;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.ice.constant.IceFunction;

import java.io.File;
import java.util.Objects;
import java.util.Stack;

public class IceContext {

    File source;

    private SysyParser.CompUnitContext ast;

    private SysyParser parser;

    private final SymbolTable symbolTable = new SymbolTable();

    private IceUnit result;

    private IceFunction currentFunction;

    public record IceIfLabel(IceBlock trueLabel, IceBlock falseLabel) { }

    public record IceLoopLabel(IceBlock condLabel, IceBlock endLabel) { }

    private Stack<IceIfLabel> ifLabelStack = new Stack<>();
    private Stack<IceLoopLabel> loopLabelStack = new Stack<>();

    public IceContext() {
    }

    public IceUnit getCurrentIR() {
        return result;
    }

    public void setCurrentIR(IceUnit result) {
        this.result = result;
    }

    public void setSource(File source) {
        Objects.requireNonNull(source);
        this.source = source;
    }

    public File getSource() {
        return this.source;
    }

    public void setAst(SysyParser.CompUnitContext ast) {
        Objects.requireNonNull(ast);
        this.ast = ast;
    }

    public SysyParser.CompUnitContext getAst() {
        return this.ast;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void setParser(SysyParser parser) {
        Objects.requireNonNull(parser);
        this.parser = parser;
    }

    public SysyParser getParser() {
        return this.parser;
    }

    public IceFunction getCurrentFunction() {
        return currentFunction;
    }

    public void setCurrentFunction(IceFunction currentFunction) {
        this.currentFunction = currentFunction;
    }

    public Stack<IceIfLabel> getIfLabelStack() {
        return ifLabelStack;
    }

    public void setIfLabelStack(Stack<IceIfLabel> ifLabelStack) {
        this.ifLabelStack = ifLabelStack;
    }

    public Stack<IceLoopLabel> getLoopLabelStack() {
        return loopLabelStack;
    }

    public void setLoopLabelStack(Stack<IceLoopLabel> loopLabelStack) {
        this.loopLabelStack = loopLabelStack;
    }
}
