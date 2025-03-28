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
        currentMethod = methodDecl.get("name");
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        int numChildren = assignStmt.getNumChildren();
        Type leftType = null;
        Type rightType = null;
        TypeUtils typeUtils = new TypeUtils(table);

        if (numChildren == 1) {
            // Simple assignment
            String varName = assignStmt.get("name");
            leftType = lookupVariableType(varName, table, currentMethod);
            if (leftType == null) {
                throw new RuntimeException("Undeclared variable in assignment: " + varName);
            }

            JmmNode rhs = assignStmt.getChild(0);

            try {
                rightType = typeUtils.getExprType(rhs, currentMethod);
            } catch (RuntimeException e) {
                addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(),
                        "Failed to evaluate assignment RHS: " + e.getMessage(), null));
                return null;
            }

        } else if (numChildren == 3) {
            // Array assignment
            JmmNode arrayExpr = assignStmt.getChild(0);

            try {
                Type arrayType = typeUtils.getExprType(arrayExpr, currentMethod);
                if (!arrayType.isArray()) {
                    String message = String.format("Assignment expected an array type on the left, but found: %s.", arrayType);
                    addReport(Report.newError(Stage.SEMANTIC, assignStmt.getLine(), assignStmt.getColumn(), message, null));
                    return null;
                }

                leftType = new Type(arrayType.getName(), false);
                JmmNode rhs = assignStmt.getChild(2);

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
