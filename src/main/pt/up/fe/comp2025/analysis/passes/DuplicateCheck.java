package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;

import java.util.*;

public class DuplicateCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        // Check duplicates at the root level
        addVisit("Program", this::checkDuplicates);
    }

    private Void checkDuplicates(JmmNode node, SymbolTable table) {
        // Check for duplicate methods
        checkDuplicateMethods(table);

        // Check for duplicate fields
        checkDuplicateFields(table);

        // Check for duplicate imports
        checkDuplicateImports(table);

        // Check duplicates within each method
        for (String methodName : table.getMethods()) {
            checkDuplicateParameters(methodName, table);
            checkDuplicateLocals(methodName, table);
            checkParameterLocalConflict(methodName, table);
        }

        return null;
    }

    private void checkDuplicateMethods(SymbolTable table) {
        List<String> methods = table.getMethods();
        Set<String> seen = new HashSet<>();

        for (String method : methods) {
            if (!seen.add(method)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Duplicate method declaration: '" + method + "'",
                        null
                ));
            }
        }
    }

    private void checkDuplicateFields(SymbolTable table) {
        List<Symbol> fields = table.getFields();
        Set<String> seen = new HashSet<>();

        for (Symbol field : fields) {
            if (!seen.add(field.getName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Duplicate field declaration: '" + field.getName() + "'",
                        null
                ));
            }
        }
    }

    private void checkDuplicateImports(SymbolTable table) {
        List<String> imports = table.getImports();
        Map<String, String> seenSimpleNames = new HashMap<>();

        for (String importStr : imports) {
            String simpleName = getSimpleName(importStr);

            if (seenSimpleNames.containsKey(simpleName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Duplicate import: '" + importStr + "' conflicts with '" + seenSimpleNames.get(simpleName) + "' (both have simple name '" + simpleName + "')",
                        null
                ));
            } else {
                seenSimpleNames.put(simpleName, importStr);
            }
        }
    }

    private void checkDuplicateParameters(String methodName, SymbolTable table) {
        List<Symbol> parameters = table.getParameters(methodName);
        Set<String> seen = new HashSet<>();

        for (Symbol param : parameters) {
            if (!seen.add(param.getName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Duplicate parameter '" + param.getName() + "' in method '" + methodName + "'",
                        null
                ));
            }
        }
    }

    private void checkDuplicateLocals(String methodName, SymbolTable table) {
        List<Symbol> locals = table.getLocalVariables(methodName);
        Set<String> seen = new HashSet<>();

        for (Symbol local : locals) {
            if (!seen.add(local.getName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Duplicate local variable '" + local.getName() + "' in method '" + methodName + "'",
                        null
                ));
            }
        }
    }

    private void checkParameterLocalConflict(String methodName, SymbolTable table) {
        List<Symbol> parameters = table.getParameters(methodName);
        List<Symbol> locals = table.getLocalVariables(methodName);

        Set<String> paramNames = new HashSet<>();
        for (Symbol param : parameters) {
            paramNames.add(param.getName());
        }

        for (Symbol local : locals) {
            if (paramNames.contains(local.getName())) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        -1,
                        -1,
                        "Local variable '" + local.getName() + "' conflicts with parameter in method '" + methodName + "'",
                        null
                ));
            }
        }
    }

    private String getSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}