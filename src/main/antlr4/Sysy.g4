grammar Sysy;

@header {
package top.voidc.frontend.parser;
}

compUnit: (decl | externFuncDef | funcDef | COMMENT | LINE_COMMENT)* ;

decl: constDecl | varDecl ;

constDecl: 'const' primitiveType constDef (',' constDef)* ';' ;

primitiveType: 'int' | 'float' ;

constDef: Ident ('[' constExp ']')* '=' initVal ;

varDecl: primitiveType varDef (',' varDef)* ';' ;

varDef: Ident ('[' constExp ']')* ('=' initVal)? ;

initVal: exp
       | '{' (initVal (',' initVal)*)? '}' ;

externFuncDef: 'extern' funcType Ident '(' (funcPrototypeParams)? ')' ';' ;

funcPrototypeParams: funcPrototypeParam (',' funcPrototypeParam)* (',' '...')?;

funcPrototypeParam: primitiveType Ident? ('[' ']' funcFParamArrayItem*)? ;

funcDef: funcType Ident '(' (funcFParams)? ')' block ;

funcType: 'void' | primitiveType ;

funcFParams: funcFParam (',' funcFParam)* ;

funcFParam: primitiveType Ident ('[' ']' funcFParamArrayItem*)? ;

funcFParamArrayItem: '[' exp ']';

block: '{' (blockItem)* '}' ;

blockItem: decl | stmt | COMMENT | LINE_COMMENT ;

lVal: Ident ('[' exp ']')* ;

number: IntConst | FloatConst ;

stmt: assignStmt
    | exprStmt
    | block
    | ifStmt
    | whileStmt
    | breakStmt
    | continueStmt
    | returnStmt
    ;

assignStmt: lVal '=' exp ';' ;
exprStmt: (exp)? ';';
ifStmt: 'if' '(' cond ')' stmt ('else' stmt)? ;
whileStmt: 'while' '(' cond ')' stmt ;
breakStmt: 'break' ';' ;
continueStmt: 'continue' ';' ;
returnStmt: 'return' (exp)? ';' ;


string: StringLiteral;
funcCall: Ident '(' (funcRParams)? ')' ;

exp: unaryOp=('+' | '-') exp
   | unaryOp='!' exp
   | exp arithOp=('*' | '/' | '%') exp
   | exp arithOp=('+' | '-') exp
   | exp relOp=('<' | '<=' | '>' | '>=') exp
   | exp relOp=('==' | '!=') exp
   | exp logicOp='&&' exp
   | exp logicOp='||' exp
   | '(' exp ')'
   | number
   | lVal
   | string
   | funcCall
   ;

funcRParams: exp (',' exp)* ;
cond: exp ;
constExp: exp ;

COMMENT: '/*' .*? '*/' -> skip ;
LINE_COMMENT: '//' ~[\r\n]* -> skip ;

StringLiteral: '"' ( ~["\\] | '\\' . )* '"' ;
Ident: [a-zA-Z_][a-zA-Z_0-9]* ;
IntConst: DEC_CONST | OCT_CONST | HEX_CONST ;
FloatConst: DECIMAL_FLOAT_CONST | HEX_FLOAT_CONST;
WS: [ \f\n\r\t]+ -> skip ;

fragment DEC_CONST: [1-9][0-9]*;
fragment OCT_CONST: '0' [0-7]*;
fragment HEX_CONST: ('0x'|'0X') [0-9a-fA-F]+;
fragment DECIMAL_FLOAT_CONST: DECIMAL_DIGIT_FLOAT | DECIMAL_FRAC_FLOAT ;
fragment DECIMAL_DIGIT_FLOAT: [0-9]+([eE][-+]?[0-9]+);
fragment DECIMAL_FRAC_FLOAT: (([0-9]* '.' [0-9]+)|[0-9]+ '.' )([eE][-+]?[0-9]+)?;
fragment HEX_FLOAT_CONST: ('0x'|'0X') [0-9a-fA-F]* ('.')? [0-9a-fA-F]* ([pP] [-+]? [0-9]+)?;