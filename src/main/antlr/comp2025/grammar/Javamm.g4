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
    : withElse
    | noElse
    ;

otherStmt
    : '{' ( stmt )* '}'
    | 'while' '(' expr ')' stmt
    | expr ';'
    | ID '=' expr ';'
    | ID '[' expr ']' '=' expr ';'
    ;

withElse
    : 'if' '(' expr ')' withElse 'else' withElse
    | otherStmt
    ;

noElse
    : 'if' '(' expr ')' stmt
    | 'if' '(' expr ')' withElse 'else' noElse
    ;

expr
    : '(' expr ')' #ParenthesizedExpr
    | '[' ( expr ( ',' expr )* )? ']' #ArrayLiteralExpr
    | value=INT #IntegerLiteral
    | value='true' #BooleanTrue
    | value='false' #BooleanFalse
    | value=ID #IdentifierExpr
    | 'this' #ThisExpr
    | op='!' expr #UnaryExpr
    | 'new' 'int' '[' expr ']' #NewIntArrayExpr
    | 'new' value=ID '(' ')' #NewObjectExpr
    | value=ID op=('++' | '--') #PostfixExpr
    | left=expr '[' right=expr ']' #ArrayAccessExpr
    | left=expr '.' 'length' #ArrayLengthExpr
    | left=expr '.' method=ID '(' ( expr ( ',' expr )* )? ')' #MethodCallExpr
    | left=expr op=('*' | '/') right=expr #MultiplicativeExpr
    | left=expr op=('+' | '-') right=expr #AdditiveExpr
    | left=expr op=('<' | '>') right=expr #RelationalExpr
    | left=expr op=('<=' | '>=' | '==' | '!=') right=expr #EqualityExpr
    | left=expr op='&&' right=expr #LogicalAndExpr
    | left=expr op='||' right=expr #LogicalOrExpr
    | left=expr op=('+=' | '-=' | '*=' | '/=') right=expr #AssignmentExpr
    ;



