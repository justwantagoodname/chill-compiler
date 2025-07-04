grammar Selection;

expressionList: expression+;

expression: '(' funcName parameters ')';

funcName: expression | IDENT;

parameters: expression | IDENT* | QUOTA;


IDENT: [a-zA-Z_/?!][a-zA-Z_0-9/?!-]* ;
QUOTA: '\'' IDENT;
WS: [ \f\n\r\t]+ -> skip ;