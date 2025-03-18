package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.List;
import java.util.stream.Collectors;

public class MethodVerificationVisitor extends AnalysisVisitor {

    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("MethodCallExpr", this::checkMethodCall);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        currentMethod = methodDecl.get("name");
        return null;
    }

    private Void checkMethodCall(JmmNode callNode, SymbolTable table) {
        String methodName = callNode.get("method");

        // Check if the method exists in the current class; if not and no superclass, report error.
        if (!table.getMethods().contains(methodName)) {
            if (table.getSuper() == null || table.getSuper().isEmpty()) {
                addReport(newError(callNode, "Method '" + methodName + "' not declared in class and no superclass to look up."));
                return null;
            } else {
                // If there is a superclass, assume the method exists there.
                return null;
            }
        }

        List<JmmNode> args = callNode.getChildren().stream()
                .filter(child -> child.getKind().equals("Expression") || child.hasAttribute("type"))
                .collect(Collectors.toList());

        List<Symbol> methodParams = table.getParameters(methodName);

        // Check if the method has varargs (represented as int[] in the last parameter).
        boolean hasVarargs = !methodParams.isEmpty() && methodParams.get(methodParams.size() - 1).getType().getName().equals("int") && methodParams.get(methodParams.size() - 1).getType().isArray();

        if (hasVarargs) {
            // For varargs, ensure at least fixed parameters are provided.
            if (args.size() < methodParams.size() - 1) {
                addReport(newError(callNode, "Method call to '" + methodName + "' has fewer arguments than required."));
                return null;
            }

            for (int i = 0; i < methodParams.size() - 1; i++) {
                String expectedType = methodParams.get(i).getType().getName() + (methodParams.get(i).getType().isArray() ? "[]" : "");
                String givenType = args.get(i).get("type");

                if (!expectedType.equals(givenType)) {
                    addReport(newError(callNode, "Type mismatch for argument " + (i + 1) + " in method '" + methodName + "'. Expected: " + expectedType + ", got: " + givenType));
                }
            }
        } else {
            // If not varargs, check for exact argument count and type compatibility.
            if (args.size() != methodParams.size()) {
                addReport(newError(callNode, "Argument count mismatch in call to method '" + methodName + "'. Expected: " + methodParams.size() + ", got: " + args.size()));
                return null;
            }

            for (int i = 0; i < methodParams.size(); i++) {
                String expectedType = methodParams.get(i).getType().getName() + (methodParams.get(i).getType().isArray() ? "[]" : "");
                String givenType = args.get(i).get("type");

                if (!expectedType.equals(givenType)) {
                    addReport(newError(callNode, "Type mismatch for argument " + (i + 1) + " in method '" + methodName + "'. Expected: " + expectedType + ", got: " + givenType));
                }
            }
        }

        return null;
    }
}