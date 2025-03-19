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
    : 'import' name+=ID ('.' name+=ID)* ';' #ImportStmt
    ;

classDecl
    : 'class' name=ID ( 'extends' extendedClass=ID )? '{' ( varDecl )* ( methodDecl )* '}'
    ;

varDecl
    : type name=ID ';'
    ;

methodDecl
    : ('public')? type name=ID '(' ( param ( ',' param )* )? ')' '{' ( varDecl)* ( stmt )* 'return' expr ';' '}'
    | ('public')? 'static' 'void' 'main' '(' 'String' '[' ']' name=ID ')' '{' ( varDecl )* ( stmt )* '}'
    ;

param
    :  type name=ID #ParamExp
    ;

type
    : value=( 'int' | 'String' | 'boolean' | 'double' | 'float' | ID ) #Var
    | value=( 'int' | 'String' | 'boolean' | 'double' | 'float' | ID ) '[' ']' #VarArray
    | value='int' '...'  #VarArgs
    ;

stmt
    : withElse #IfStmt
    | noElse #IfStmt
    ;

otherStmt
    : '{' ( stmt )* '}' #BlockStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | 'for' '(' stmt expr ';' expr ')' stmt #ForStmt
    | expr ';' #ExprStmt
    | name=ID '=' expr ';' #AssignStmt
    | name=ID '[' expr ']' '=' expr ';' #AssignStmt
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
    | value=ID #VarRefExpr
    | 'this' #ThisExpr
    | op='!' expr #UnaryExpr
    | 'new' 'int' '[' expr ']' #NewIntArrayExpr
    | 'new' value=ID '(' ')' #NewObjectExpr
    | value=ID op=('++' | '--') #PostfixExpr
    | expr '[' expr ']' #ArrayAccessExpr
    | expr '.' 'length' #ArrayLengthExpr
    | expr '.' method=ID '(' ( expr ( ',' expr )* )? ')' #MethodCallExpr
    | expr op=('*' | '/') expr #BinaryExpr
    | expr op=('+' | '-') expr #BinaryExpr
    | expr op=('<' | '>') expr #BinaryExpr
    | expr op=('<=' | '>=' | '==' | '!=') expr #BinaryExpr
    | expr op='&&' expr #BinaryExpr
    | expr op='||' expr #BinaryExpr
    | expr op=('+=' | '-=' | '*=' | '/=') expr #BinaryExpr
    ;



