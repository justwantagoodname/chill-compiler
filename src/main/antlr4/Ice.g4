grammar Ice;

@header {
package top.voidc.frontend.parser;
}

module
    : moduleDecl*
    ;

moduleDecl
    : globalDecl
    | functionDecl
    ;

globalDecl
    : '@' IDENTIFIER '=' 'global' type value
    ;

functionDecl
    : 'define' type GLOBAL_IDENTIFIER '(' (type IDENTIFIER)* ')' functionBody
    ;

functionBody
    : '{' basicBlock* '}'
    ;

// Basic blocks
basicBlock
    : IDENTIFIER ':' instruction* terminatorInstr
    ;

// Instructions
instruction
    : allocaInstr
    | loadInstr  
    | storeInstr
    | branchInstr
    | returnInstr
    | arithmeticInstr
    | callInstr
    | getElementPtrInstr
    | phiInstr
    | compareInstr
    | convertInstr
    | unreachableInstr
    ;

terminatorInstr
    : returnInstr
    | branchInstr
    | unreachableInstr
    ;

allocaInstr
    : IDENTIFIER '=' 'alloca' type (',' 'align' NUMBER)?
    ;

loadInstr
    : IDENTIFIER '=' 'load' type ',' type pointer (',' 'align' NUMBER)?
    ;

storeInstr
    : 'store' type value ',' type pointer (',' 'align' NUMBER)?
    ;

branchInstr
    : 'br' 'label' IDENTIFIER # UnconditionalBranch
    | 'br' type value ',' 'label' IDENTIFIER ',' 'label' IDENTIFIER # ConditionalBranch
    ;

returnInstr
    : 'ret' 'void' # VoidReturn
    | 'ret' type value # ValueReturn
    ;

arithmeticInstr
    : IDENTIFIER '=' binOp type value ',' value
    ;

callInstr
    : IDENTIFIER '=' 'call' type '@' IDENTIFIER '(' argList? ')'
    | 'call' 'void' '@' IDENTIFIER '(' argList? ')'
    ;

getElementPtrInstr
    : IDENTIFIER '=' 'getelementptr' type ',' type pointer (',' type value)*
    ;

phiInstr
    : IDENTIFIER '=' 'phi' type '[' value ',' IDENTIFIER ']' (',' '[' value ',' IDENTIFIER ']')*
    ;

compareInstr
    : IDENTIFIER '=' ('icmp'|'fcmp') cmpOp type value ',' value
    ;

convertInstr
    : IDENTIFIER '=' convertOp type value 'to' type
    ;

unreachableInstr
    : 'unreachable'
    ;

// Types
type
    : baseType
    | derivedType
    ;

baseType
    : 'void'
    | 'i1'
    | 'i8'
    | 'i32'
    | 'float'
    ;

derivedType
    : arrayType
    | pointerType
    ;

arrayType
    : '[' NUMBER 'x' type ']'
    ;

pointerType
    : baseType stars
    | arrayType stars
    ;

stars
    : '*'+
    ;

// Values
value
    : constant
    | IDENTIFIER
    | GLOBAL_IDENTIFIER
    ;

constant
    : NUMBER
    | FLOAT
    | 'true'
    | 'false'
    | 'null'
    | 'undef'
    ;

// Operators
binOp
    : 'add'
    | 'sub'
    | 'mul'
    | 'udiv'
    | 'sdiv'
    | 'urem'
    | 'srem'
    | 'and'
    | 'or'
    | 'xor'
    | 'shl'
    | 'lshr'
    | 'ashr'
    ;

cmpOp
    : 'eq'
    | 'ne'
    | 'ugt'
    | 'uge'
    | 'ult'
    | 'ule'
    | 'sgt'
    | 'sge'
    | 'slt'
    | 'sle'
    ;

convertOp
    : 'trunc'
    | 'zext'
    | 'sext'
    | 'fptrunc'
    | 'fpext'
    | 'fptoui'
    | 'fptosi'
    | 'uitofp'
    | 'sitofp'
    | 'ptrtoint'
    | 'inttoptr'
    | 'bitcast'
    ;

argList
    : type value (',' type value)*
    ;

pointer
    : IDENTIFIER
    | GLOBAL_IDENTIFIER
    ;

NAME: [a-zA-Z_][a-zA-Z_0-9]*;
IDENTIFIER : '%' NAME;
GLOBAL_IDENTIFIER : '@' NAME;
NUMBER : [0-9]+;
FLOAT : [0-9]+ '.' [0-9]* | '.' [0-9]+;
WS : [ \t\r\n]+ -> skip;
LINE_COMMENT : ';' .*? '\r'? '\n' -> skip;
