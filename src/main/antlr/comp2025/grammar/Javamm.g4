grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

INT : '0' | [1-9] [0-9]*;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]*;


MULTI_LINE_COMMENT : '/*' .*? '*/' -> skip ;
END_OF_LINE_COMMENT : '//' .*? '\n' -> skip ;
WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' ID ('.' ID)* ';'
    ;

classDecl
    : 'class' ID ( 'extends' ID )? '{' ( varDecl )* ( methodDecl )* '}'
    ;

varDecl
    : type ID ';'
    ;

methodDecl
    : ('public')? type ID '(' ( type ID ( ',' type ID )* )? ')' '{' ( varDecl)* ( stmt )* 'return' expr ';' '}'
    | ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' ID ')' '{' ( varDecl )* ( stmt )* '}'
    ;

type
    : 'int' '[' ']'
    | 'int' '...'
    | 'String'
    | 'boolean'
    | 'int'
    | ID
    ;

stmt
    : '{' ( stmt )* '}'
    | 'if' '(' expr ')' stmt 'else' stmt
    | 'while' '(' expr ')' stmt
    | expr ';'
    | ID '=' expr ';'
    | ID '[' expr ']' '=' expr ';'
    ;

expr
    : '(' expr ')' #Parenthesis
    | '[' ( expr ( ',' expr )* )? ']' #ArrayLiteral
    | INT #Integer
    | 'true' #BoolTrue
    | 'false' #BoolFalse
    | ID #Identifier
    | 'this' #This
    | '!' expr #Negation
    | 'new' 'int' '[' expr ']' #NewIntArray
    | 'new' ID '(' ')' #NewObject
    | expr '[' expr ']' #ArrayAccess
    | expr '.' 'length' #Length
    | expr '.' ID '(' ( expr ( ',' expr )* )? ')' #MethodCal
    | expr ('*' | '/') expr #BinaryOp
    | expr ('+' | '-') expr #BinaryOp
    | expr '<' expr #BinaryOp
    | expr '&&' expr #BinaryOp
    ;


