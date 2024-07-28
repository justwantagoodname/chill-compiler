grammar Sysy;

@header {
package top.voidc.frontend.parser;
}

compUnit: (decl | funcDef | COMMENT | LINE_COMMENT)* ;

decl: constDecl | varDecl ;

constDecl: 'const' primitiveType constDef (',' constDef)* ';' ;

primitiveType: 'int' | 'float' ;

constDef: Ident ('[' constExp ']')* '=' constInitVal ;

constInitVal: constExp
            | '{' (constInitVal (',' constInitVal)*)? '}' ;

varDecl: primitiveType varDef (',' varDef)* ';' ;

varDef: Ident ('[' constExp ']')*
       | Ident ('[' constExp ']')* '=' initVal ;

initVal: exp
        | '{' (initVal (',' initVal)*)? '}' ;

funcDef: funcType Ident '(' (funcFParams)? ')' block ;

funcType: 'void' | primitiveType ;

funcFParams: funcFParam (',' funcFParam)* ;

funcFParam: primitiveType Ident ('[' ']' ('[' exp ']')*)? ;

block: '{' (blockItem)* '}' ;

blockItem: decl | stmt | COMMENT | LINE_COMMENT ;

lVal: Ident ('[' exp ']')* ;

number: IntConst | FloatConst ;

stmt: lVal '=' exp ';'
    | (exp)? ';'
    | block
    | 'if' '(' cond ')' stmt ('else' stmt)?
    | 'while' '(' cond ')' stmt
    | 'break' ';'
    | 'continue' ';'
    | 'return' (exp)? ';' ;


exp: ('+' | '-') exp
   | '!' exp
   | exp ('*' | '/' | '%') exp
   | exp ('+' | '-') exp
   | exp ('<' | '<=' | '>' | '>=') exp
   | exp ('==' | '!=') exp
   | exp '&&' exp
   | exp '||' exp
   | '(' exp ')'
   | number
   | lVal
   | StringLiteral
   | Ident '(' (funcRParams)? ')' // 函数调用
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
fragment DECIMAL_FLOAT_CONST: [0-9]* ('.')? [0-9]* ([eE] [-+]? [0-9]+)? ;
fragment HEX_FLOAT_CONST: ('0x'|'0X') [0-9a-zA-Z]* ('.')? [0-9a-zA-Z]* ([pP] [-+]? [0-9]+)?;