package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

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

        // Get caller type
        Type callerType = new TypeUtils(table).getExprType(callNode.getChild(0), currentMethod);

        String callerName = callNode.getChild(0).get("value");
        if (table.getImports().stream().anyMatch(imp -> imp.equals(callerName)) ||
                !callerType.getName().equals(table.getClassName()) &&
                        !callerType.getName().equals("this") &&
                        !callerType.getName().equals("unknown")) {
            // Skip method verification if from import or external
            return null;
        }

        // If the method does not exist locally
        if (!table.getMethods().contains(methodName)) {
            if (table.getSuper() == null || table.getSuper().isEmpty()) {
                addReport(newError(callNode, "Method '" + methodName + "' not declared in class and no superclass to look up."));
                return null;
            } else {
                return null; // Assume exists in superclass
            }
        }

        List<JmmNode> args = callNode.getChildren().stream()
                .filter(child -> child.getKind().equals("Expression") || child.hasAttribute("type"))
                .collect(Collectors.toList());

        List<Symbol> methodParams = table.getParameters(methodName);

        boolean hasVarargs = !methodParams.isEmpty() && methodParams.get(methodParams.size() - 1).getType().getName().equals("int") && methodParams.get(methodParams.size() - 1).getType().isArray();

        if (hasVarargs) {
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