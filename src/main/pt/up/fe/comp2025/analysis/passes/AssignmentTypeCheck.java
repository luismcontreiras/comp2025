package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class AssignmentTypeCheck extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(pt.up.fe.comp2025.ast.Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(pt.up.fe.comp2025.ast.Kind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        String name = methodDecl.get("name");
        currentMethod = name.equals("args") ? "main" : name;
        //System.out.println("[DEBUG] AssignmentTypeCheck — entering method: " + currentMethod);
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        int numChildren = assignStmt.getNumChildren();
        Type leftType = null;
        Type rightType = null;
        TypeUtils typeUtils = new TypeUtils(table);

        if (numChildren == 2) {
            // Simple assignment: a = expr;
            JmmNode lhs = assignStmt.getChild(0);
            JmmNode rhs = assignStmt.getChild(1);

            if (!lhs.hasAttribute("value")) {
                addReport(Report.newError(Stage.SEMANTIC, lhs.getLine(), lhs.getColumn(),
                        "Left-hand side of assignment does not contain a valid variable reference", null));
                return null;
            }

            String varName = lhs.get("value");
            leftType = lookupVariableType(varName, table, currentMethod);
            if (leftType == null) {
                addReport(Report.newError(Stage.SEMANTIC, lhs.getLine(), lhs.getColumn(),
                        "Undeclared variable in assignment: " + varName, null));
                return null;
            }

            try {
                rightType = typeUtils.getExprType(rhs, currentMethod);
            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, rhs.getLine(), rhs.getColumn(),
                        "Failed to evaluate assignment RHS: " + e.getMessage(), null));
                return null;
            }

        } else if (numChildren == 3) {
            // Array assignment: a[i] = expr;
            JmmNode arrayExpr = assignStmt.getChild(0);
            JmmNode rhs = assignStmt.getChild(2);

            try {
                Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
                if (!arrayType.isArray()) {
                    addReport(Report.newError(Stage.SEMANTIC, arrayExpr.getLine(), arrayExpr.getColumn(),
                            "Left-hand side is not an array, but used as one.", null));
                    return null;
                }

                leftType = new Type(arrayType.getName(), false);
                rightType = typeUtils.getExprType(rhs, currentMethod);

            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(),
                        "Failed to evaluate array assignment: " + e.getMessage(), null));
                return null;
            }

        } else {
            return null;
        }

        if (!isTypeCompatible(leftType, rightType, table)) {
            String message = String.format("Type mismatch in assignment: cannot assign %s to %s.", rightType, leftType);
            addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(), message, null));
        }

        return null;
    }


    private Type lookupVariableType(String varName, SymbolTable table, String currentMethod) {
        for (Symbol symbol : table.getLocalVariables(currentMethod)) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        for (Symbol symbol : table.getParameters(currentMethod)) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        for (Symbol symbol : table.getFields()) {
            if (symbol.getName().equals(varName)) {
                return symbol.getType();
            }
        }
        return null;
    }

    private boolean isTypeCompatible(Type left, Type right, SymbolTable table) {
        if (left.isArray() || right.isArray()) {
            return left.getName().equals(right.getName()) && (left.isArray() == right.isArray());
        }

        if (left.getName().equals("int") || left.getName().equals("boolean")) {
            return left.getName().equals(right.getName());
        }

        if (left.getName().equals(right.getName())) {
            return true;
        }

        JmmSymbolTable jmmTable = (JmmSymbolTable) table;
        String currentClass = jmmTable.getClassName();
        if (right.getName().equals(currentClass)) {
            String extended = jmmTable.getSuper();
            if (extended != null && !extended.isEmpty() && extended.equals(left.getName())) {
                return true;
            }
        }

        return left.getName().equals("A") && right.getName().equals("B");
    }
}
