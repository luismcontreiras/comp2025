package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.specs.util.SpecsStrings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Enum that mirrors the nodes that are supported by the AST.
 *
 * This enum allows you to handle nodes in a safer and more flexible way than using strings.
 * The constants have been adapted to match the alternative labels and rule names in the Javamm grammar.
 */
public enum Kind {
    // Program structure
    PROGRAM,
    IMPORT_STMT,      // corresponds to importDecl alternative label: #ImportStmt
    CLASS_DECL,
    VAR_DECL,
    METHOD_DECL,
    PARAM,            // corresponds to param alternative label: #ParamExp

    // Type nodes (from the type rule alternatives)
    TYPE,             // corresponds to the alternative label: #Var
    VAR_ARRAY,        // corresponds to the alternative label: #VarArray
    VAR_ARGS,         // corresponds to the alternative label: #VarArgs

    // Statement nodes
    STMT,
    ASSIGN_STMT,      // corresponds to assignment statements (#AssignStmt)
    RETURN_STMT,      // if return statements are represented as a distinct node

    // Expression nodes (from the expr rule alternatives)
    PARENTHESIZED_EXPR,   // corresponds to #ParenthesizedExpr
    ARRAY_LITERAL_EXPR,   // corresponds to #ArrayLiteralExpr
    INTEGER_LITERAL,      // corresponds to #IntegerLiteral
    BOOLEAN_TRUE,         // corresponds to #BooleanTrue
    BOOLEAN_FALSE,        // corresponds to #BooleanFalse
    VAR_REF_EXPR,         // corresponds to #VarRefExpr
    THIS_EXPR,            // corresponds to #ThisExpr
    UNARY_EXPR,           // corresponds to #UnaryExpr
    NEW_INT_ARRAY_EXPR,   // corresponds to #NewIntArrayExpr
    NEW_OBJECT_EXPR,      // corresponds to #NewObjectExpr
    POSTFIX_EXPR,         // corresponds to #PostfixExpr
    ARRAY_ACCESS_EXPR,    // corresponds to #ArrayAccessExpr
    ARRAY_LENGTH_EXPR,    // corresponds to #ArrayLengthExpr
    METHOD_CALL_EXPR,     // corresponds to #MethodCallExpr
    BINARY_EXPR;          // corresponds to all binary operations unified as #BinaryExpr

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
     * Performs a check on all kinds to test and returns false if none matches.
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
     * Performs a check on all kinds and throws if none matches. Otherwise, does nothing.
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
