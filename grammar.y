%language "Java"

%token EQUAL '='
%token SEMICOLON ';'
%token <int> INT "integer"
%token <double> DOUBLE "double"

%right ','
%left "==" "!="
%left '>' ">=" '<' "<="
%left '^' '&' '|'
%left '+' '-'
%left '*' '/' '%'
%precedence '!' '-'

%%
program: varDeclStar;
varDecl: "var" identifier initializer | comma ;
comma: stmt ',' stmt ;
stmt: expr SEMICOLON | printStmt ;
printStmt: "print" expr SEMICOLON ;

expr: expr "==" expr
    | expr "!=" expr
    | expr '>' expr
    | expr ">=" expr


varDeclStar: varDecl varDeclStar | /* empty */ ;
initializer: EQUAL expr SEMICOLON | /* empty */ ;
comparisonStar: "!=" comparison comparisonStar
 | "==" comparison comparisonStar
 | /* empty */ ;
