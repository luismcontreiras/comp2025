package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ConditionCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        // Register visit rules for if and while nodes.
        // Adjust these names if your AST uses different kinds.
        //addVisit("IfStmt", this::visitIfStmt);
        addVisit("WhileStmt", this::visitWhileStmt);
    }


    /**
     * Checks that the condition expression of an if-statement evaluates to a boolean.
     * Assumes that the first child of the if node is the condition.
     */
    /*
    private Void visitIfStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() < 3) {
            // Typically, an if-statement has at least three children: condition, then, else.
            // If the structure is not as expected, we skip checking.
            return null;
        }
        JmmNode condition = node.getChild(0);
        Type condType;
        try {
            // Retrieve type of condition expression.
            // Using "main" as fallback for method context if not provided.
            String methodContext = node.get("method") != null ? node.get("method") : "main";
            condType = new TypeUtils(table).getExprType(condition, methodContext);
        } catch (RuntimeException e) {
            addReport(Report.newError(Stage.SEMANTIC, condition.getLine(), condition.getColumn(),
                    "Failed to evaluate condition type: " + e.getMessage(), null));
            return null;
        }
        if (!"boolean".equals(condType.getName()) || condType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, condition.getLine(), condition.getColumn(),
                    "Condition in 'if' must be boolean, but found: " + condType.getName(), null));
        }
        return null;
    }
    */

    /**
     * Checks that the condition expression of a while-statement evaluates to a boolean.
     * Assumes that the first child of the while node is the condition.
     */
    private Void visitWhileStmt(JmmNode node, SymbolTable table) {
        if (node.getNumChildren() < 2) {
            // A while statement should have at least two children: condition and body.
            return null;
        }
        JmmNode condition = node.getChild(0);
        Type condType;
        try {
            String methodContext = node.get("method") != null ? node.get("method") : "main";
            condType = new TypeUtils(table).getExprType(condition, methodContext);
        } catch (RuntimeException e) {
            addReport(Report.newError(Stage.SEMANTIC, condition.getLine(), condition.getColumn(),
                    "Failed to evaluate condition type: " + e.getMessage(), null));
            return null;
        }
        if (!"boolean".equals(condType.getName()) || condType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, condition.getLine(), condition.getColumn(),
                    "Condition in 'while' must be boolean, but found: " + condType.getName(), null));
        }
        return null;
    }
}

