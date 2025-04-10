package top.voidc.ir;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyParser;

import java.io.File;
import java.util.Objects;

public class IceContext {

    File source;

    private SysyParser.CompUnitContext ast;

    private SysyParser parser;

    private final SymbolTable symbolTable = new SymbolTable();

    private IceUnit result;


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
}
