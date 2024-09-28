package top.voidc.ir;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyParser;

import java.io.File;

public class IceContext {

    File source;

    private SysyParser.CompUnitContext ast;

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
        assert source != null;
        this.source = source;
    }

    public File getSource() {
        assert source != null;
        return this.source;
    }

    public void setAst(SysyParser.CompUnitContext ast) {
        assert ast != null;
        this.ast = ast;
    }

    public SysyParser.CompUnitContext getAst() {
        assert ast != null;
        return this.ast;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
}
