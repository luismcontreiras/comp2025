package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

/**
 * Combined check for array access expressions.
 * It verifies that:
 *   1. The expression being accessed is an array.
 *   2. The index expression is of type int.
 */
public class ArrayAccessCombinedCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        // Record the current method (for symbol lookup).
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        // Visit array access expressions.
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        // Save the current method name from the "name" attribute.
        currentMethod = methodDecl.get("name");
        return null;
    }

    private Void visitArrayAccessExpr(JmmNode arrayAccessExpr, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);

        // Check that the base expression (first child) is an array.
        if (arrayAccessExpr.getNumChildren() < 2) {
            // Should not occur if the grammar is correct.
            return null;
        }
        JmmNode arrayExpr = arrayAccessExpr.getChild(0);
        Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
        if (!arrayType.isArray()) {
            String message = String.format("Array access on non-array type: %s.", arrayType);
            addReport(Report.newError(Stage.SEMANTIC,
                    arrayAccessExpr.getLine(),
                    arrayAccessExpr.getColumn(),
                    message,
                    null));
        }

        // Check that the index (second child) is of type int.
        JmmNode indexExpr = arrayAccessExpr.getChild(1);
        Type indexType = typeUtils.getExprType(indexExpr, currentMethod);
        if (!"int".equals(indexType.getName()) || indexType.isArray()) {
            String message = String.format("Array index must be of type int, but found: %s.", indexType);
            addReport(Report.newError(Stage.SEMANTIC,
                    arrayAccessExpr.getLine(),
                    arrayAccessExpr.getColumn(),
                    message,
                    null));
        }
        return null;
    }
}
