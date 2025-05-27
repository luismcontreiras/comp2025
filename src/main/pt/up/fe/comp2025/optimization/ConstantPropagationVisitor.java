package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements constant propagation optimization.
 * FIXED: Never replaces variables on the LEFT side of assignments.
 */
public class ConstantPropagationVisitor extends PreorderJmmVisitor<String, Void> {

    private final SymbolTable table;
    private boolean changed;
    private String currentMethod;

    // Map from variable name to its constant value (if any)
    private Map<String, ConstantValue> constantMap;

    public ConstantPropagationVisitor(SymbolTable table) {
        this.table = table;
        this.changed = false;
        this.constantMap = new HashMap<>();
        setDefaultVisit(this::defaultVisit);
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("AssignStmt", this::visitAssignStmt);
        addVisit("VarRefExpr", this::visitVarRefExpr);
    }

    public boolean didChange() {
        return changed;
    }

    private Void visitMethodDecl(JmmNode methodDecl, String dummy) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;

        // Clear constant map for each method (local scope)
        constantMap.clear();

        System.out.println("Starting constant propagation for method: " + currentMethod);

        // Continue visiting children
        for (var child : methodDecl.getChildren()) {
            visit(child);
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, String dummy) {
        if (assignStmt.getNumChildren() != 2) {
            return defaultVisit(assignStmt, dummy);
        }

        JmmNode lhs = assignStmt.getChild(0);
        JmmNode rhs = assignStmt.getChild(1);

        // Only handle simple variable assignments (not array assignments)
        if (!lhs.getKind().equals("VarRefExpr")) {
            // Visit RHS for other types of assignments
            visit(rhs);
            return null;
        }

        String varName = lhs.get("value");

        // ❌ CRITICAL: Do NOT visit LHS - it's the target of assignment
        // ✅ ONLY visit RHS to apply propagations there
        visit(rhs);

        // Check if RHS is a constant after visiting it
        ConstantValue constantValue = getConstantValue(rhs);

        if (constantValue != null) {
            // This variable now has a constant value
            constantMap.put(varName, constantValue);
            System.out.println("Variable '" + varName + "' assigned constant: " + constantValue);
        } else {
            // This variable no longer has a constant value
            constantMap.remove(varName);
            System.out.println("Variable '" + varName + "' no longer constant");
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, String dummy) {
        String varName = varRefExpr.get("value");

        // ❌ CRITICAL: Check if this is the LHS of an assignment
        // If parent is AssignStmt and this is the first child, DON'T replace
        JmmNode parent = varRefExpr.getParent();
        if (parent != null && parent.getKind().equals("AssignStmt")) {
            if (parent.getChild(0) == varRefExpr) {
                // This is the LHS of assignment, don't replace!
                System.out.println("Skipping propagation for LHS variable: " + varName);
                return null;
            }
        }

        // Check if this variable has a known constant value
        ConstantValue constantValue = constantMap.get(varName);

        if (constantValue != null) {
            // Replace variable reference with constant
            JmmNode replacement = createConstantNode(constantValue);

            System.out.println("Propagating constant: " + varName + " -> " + constantValue.value);

            varRefExpr.replace(replacement);
            changed = true;
        }

        return null;
    }

    private Void defaultVisit(JmmNode node, String dummy) {
        // Visit all children
        for (var child : node.getChildren()) {
            visit(child);
        }
        return null;
    }

    /**
     * Determines if a node represents a constant value
     */
    private ConstantValue getConstantValue(JmmNode node) {
        switch (node.getKind()) {
            case "IntegerLiteral":
                return new ConstantValue(ConstantType.INTEGER, node.get("value"));
            case "BooleanTrue":
                return new ConstantValue(ConstantType.BOOLEAN, "true");
            case "BooleanFalse":
                return new ConstantValue(ConstantType.BOOLEAN, "false");
            case "VarRefExpr":
                // Check if this variable reference is itself a constant
                String varName = node.get("value");
                return constantMap.get(varName);
            default:
                return null;
        }
    }

    /**
     * Creates a new constant node based on the constant value
     */
    private JmmNode createConstantNode(ConstantValue constantValue) {
        JmmNode node;

        switch (constantValue.type) {
            case INTEGER:
                node = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
                node.put("value", constantValue.value);
                break;
            case BOOLEAN:
                if ("true".equals(constantValue.value)) {
                    node = new JmmNodeImpl(Collections.singletonList("BooleanTrue"));
                    node.put("value", "true");
                } else {
                    node = new JmmNodeImpl(Collections.singletonList("BooleanFalse"));
                    node.put("value", "false");
                }
                break;
            default:
                throw new RuntimeException("Unsupported constant type: " + constantValue.type);
        }

        return node;
    }

    @Override
    protected void buildVisitor() {
        // Already configured in constructor
    }

    /**
     * Represents a constant value with its type
     */
    private static class ConstantValue {
        final ConstantType type;
        final String value;

        ConstantValue(ConstantType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            return type + "(" + value + ")";
        }
    }

    private enum ConstantType {
        INTEGER, BOOLEAN
    }
}