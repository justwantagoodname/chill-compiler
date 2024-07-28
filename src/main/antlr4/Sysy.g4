grammar Sysy;

@header {
package top.voidc.frontend.parser;
}

s: 'hello' ID;
ID: [a-z]+;
WS: [ \t\r\n]+ -> skip;

