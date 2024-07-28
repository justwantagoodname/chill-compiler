// Generated from Sysy.g4 by ANTLR 4.12.0

package top.voidc.frontend.parser;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SysyParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SysyVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link SysyParser#compUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompUnit(SysyParser.CompUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecl(SysyParser.DeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstDecl(SysyParser.ConstDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#primitiveType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimitiveType(SysyParser.PrimitiveTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstDef(SysyParser.ConstDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#varDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDecl(SysyParser.VarDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#varDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDef(SysyParser.VarDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#initVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInitVal(SysyParser.InitValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncDef(SysyParser.FuncDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncType(SysyParser.FuncTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcFParams}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncFParams(SysyParser.FuncFParamsContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcFParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncFParam(SysyParser.FuncFParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(SysyParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#blockItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockItem(SysyParser.BlockItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#lVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLVal(SysyParser.LValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumber(SysyParser.NumberContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStmt(SysyParser.StmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#exp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExp(SysyParser.ExpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcRParams}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncRParams(SysyParser.FuncRParamsContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#cond}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCond(SysyParser.CondContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constExp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstExp(SysyParser.ConstExpContext ctx);
}