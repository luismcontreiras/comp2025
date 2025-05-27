package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.specs.util.SpecsStrings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Enum that mirrors the nodes supported by the AST.
 *
 * The constants have been adapted to match the alternative labels and rule names in your updated Javamm grammar.
 */
public enum Kind {
    // Program structure
    PROGRAM,
    IMPORT_STMT,      // from #ImportStmt in importDecl
    CLASS_DECL,
    VAR_DECL,
    METHOD_DECL,
    PARAM, // from #ParamExp in param

    // Type nodes (from the type rule alternatives)
    TYPE,             // from #Var in type
    VAR_ARRAY,        // from #VarArray in type
    VAR_ARGS,         // from #VarArgs in type

    // Statement nodes
    STMT,
    ASSIGN_STMT,      // for assignment statements (#AssignStmt)
    RETURN_STMT,      // for return statements (if distinct)
    WHILE_STMT, // for while statements, e.g., 'while' '(' expr ')' stmt (#WhileStmt)
    IF_ELSE_STMT, // from ifElse alternatives labeled #IfElseStmt
    BLOCK_STMT,       // from other: '{' ( stmt )* '}' #BlockStmt
    FOR_STMT,         // from other: 'for' '(' stmt expr ';' expr ')' stmt #ForStmt
    EXPR_STMT,        // from other: expr ';' #ExprStmt

    // Expression nodes
    PARENTHESIZED_EXPR,   // from #ParenthesizedExpr
    ARRAY_LITERAL_EXPR,   // from #ArrayLiteralExpr
    INTEGER_LITERAL,      // from #IntegerLiteral
    BOOLEAN_TRUE,         // from #BooleanTrue
    BOOLEAN_FALSE,        // from #BooleanFalse
    VAR_REF_EXPR,         // from #VarRefExpr
    THIS_EXPR,            // from #ThisExpr
    UNARY_EXPR,           // from #UnaryExpr
    NEW_INT_ARRAY_EXPR,   // from #NewIntArrayExpr
    NEW_OBJECT_EXPR,      // from #NewObjectExpr
    POSTFIX_EXPR,         // from #PostfixExpr
    ARRAY_ACCESS_EXPR,    // from #ArrayAccessExpr
    ARRAY_LENGTH_EXPR,    // from #ArrayLengthExpr
    METHOD_CALL_EXPR,     // from #MethodCallExpr
    BINARY_EXPR,         // from all binary operations unified as #BinaryExpr

    ARRAY_ASSIGN;

    private final String name;

    private Kind(String name) {
        this.name = name;
    }

    private Kind() {
        this.name = SpecsStrings.toCamelCase(name(), "_", true);
    }

    public static Kind fromString(String kind) {
        for (Kind k : Kind.values()) {
            if (k.getNodeName().equals(kind)) {
                return k;
            }
        }
        throw new RuntimeException("Could not convert string '" + kind + "' to a Kind");
    }

    public static List<String> toNodeName(Kind firstKind, Kind... otherKinds) {
        var nodeNames = new ArrayList<String>();
        nodeNames.add(firstKind.getNodeName());
        for (Kind kind : otherKinds) {
            nodeNames.add(kind.getNodeName());
        }
        return nodeNames;
    }

    public String getNodeName() {
        return name;
    }

    @Override
    public String toString() {
        return getNodeName();
    }

    /**
     * Tests if the given JmmNode has the same kind as this type.
     *
     * @param node the AST node to check.
     * @return true if the node is an instance of this Kind.
     */
    public boolean check(JmmNode node) {
        return node.isInstance(this);
    }

    /**
     * Performs a check and throws if the test fails. Otherwise, does nothing.
     *
     * @param node the AST node to check.
     */
    public void checkOrThrow(JmmNode node) {
        if (!check(node)) {
            throw new RuntimeException("Node '" + node + "' is not a '" + getNodeName() + "'");
        }
    }

    /**
     * Checks if the node matches any of the given kinds.
     *
     * @param node the AST node to check.
     * @param kindsToTest the kinds to test against.
     * @return true if the node matches any of the given kinds.
     */
    public static boolean check(JmmNode node, Kind... kindsToTest) {
        for (Kind k : kindsToTest) {
            if (k.check(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks and throws if the node does not match any of the given kinds.
     *
     * @param node the AST node to check.
     * @param kindsToTest the kinds to test against.
     */
    public static void checkOrThrow(JmmNode node, Kind... kindsToTest) {
        if (!check(node, kindsToTest)) {
            throw new RuntimeException("Node '" + node + "' is not any of " + Arrays.asList(kindsToTest));
        }
    }
}