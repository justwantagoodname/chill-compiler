// Generated from Ice.g4 by ANTLR 4.12.0

package top.voidc.frontend.parser;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link IceParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface IceVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link IceParser#module}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModule(IceParser.ModuleContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#moduleDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModuleDecl(IceParser.ModuleDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#globalDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGlobalDecl(IceParser.GlobalDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#functionDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDecl(IceParser.FunctionDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#functionBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionBody(IceParser.FunctionBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#basicBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBasicBlock(IceParser.BasicBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#instruction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInstruction(IceParser.InstructionContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#terminatorInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerminatorInstr(IceParser.TerminatorInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#allocaInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAllocaInstr(IceParser.AllocaInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#loadInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLoadInstr(IceParser.LoadInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#storeInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStoreInstr(IceParser.StoreInstrContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UnconditionalBranch}
	 * labeled alternative in {@link IceParser#branchInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnconditionalBranch(IceParser.UnconditionalBranchContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ConditionalBranch}
	 * labeled alternative in {@link IceParser#branchInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalBranch(IceParser.ConditionalBranchContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VoidReturn}
	 * labeled alternative in {@link IceParser#returnInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVoidReturn(IceParser.VoidReturnContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ValueReturn}
	 * labeled alternative in {@link IceParser#returnInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueReturn(IceParser.ValueReturnContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#arithmeticInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithmeticInstr(IceParser.ArithmeticInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#callInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCallInstr(IceParser.CallInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#getElementPtrInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGetElementPtrInstr(IceParser.GetElementPtrInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#phiInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPhiInstr(IceParser.PhiInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#compareInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompareInstr(IceParser.CompareInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#convertInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConvertInstr(IceParser.ConvertInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#unreachableInstr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnreachableInstr(IceParser.UnreachableInstrContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(IceParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#baseType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBaseType(IceParser.BaseTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#derivedType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDerivedType(IceParser.DerivedTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#arrayType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayType(IceParser.ArrayTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#pointerType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPointerType(IceParser.PointerTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#stars}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStars(IceParser.StarsContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(IceParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#constant}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstant(IceParser.ConstantContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#binOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBinOp(IceParser.BinOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#cmpOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCmpOp(IceParser.CmpOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#convertOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConvertOp(IceParser.ConvertOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#argList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgList(IceParser.ArgListContext ctx);
	/**
	 * Visit a parse tree produced by {@link IceParser#pointer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPointer(IceParser.PointerContext ctx);
}