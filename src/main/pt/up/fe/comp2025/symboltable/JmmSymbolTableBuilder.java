package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {
        reports = new ArrayList<>();

        // Instead of using root.getChild(0), filter the children to find the class declaration.
        Optional<JmmNode> maybeClassDecl = root.getChildren().stream()
                .filter(child -> Kind.CLASS_DECL.check(child))
                .findFirst();

        // If no class declaration is found, throw an error.
        SpecsCheck.checkArgument(maybeClassDecl.isPresent(),
                () -> "Expected a class declaration, but got: " + root.getChildren());
        JmmNode classDecl = maybeClassDecl.get();
        String className = classDecl.get("name");

        String extendedClass = "";
        if (classDecl.hasAttribute("extendedClass")){
            extendedClass = classDecl.get("extendedClass");
        }


        var fields = getFieldsList(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(root);


        return new JmmSymbolTable(className, extendedClass, fields, methods, returnTypes, params, locals, imports);
    }

    private boolean hasValidReturnType(JmmNode method) {
        return method.getChildren().stream()
                .anyMatch(node -> node.getKind().equals("Var")
                        || node.getKind().equals("VarArray")
                        || node.getKind().equals("VarArgs"));
    }

    private String extractMethodName(JmmNode method) {
        return hasValidReturnType(method) ? method.get("name") : "main";
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> returnTypes = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = extractMethodName(method);

            if (hasValidReturnType(method)) {
                JmmNode returnTypeNode = method.getChildren().stream()
                        .filter(node -> node.getKind().equals("Var")
                                || node.getKind().equals("VarArray")
                                || node.getKind().equals("VarArgs"))
                        .findFirst()
                        .orElseThrow(() -> new NotImplementedException("Expected a valid return type for method: " + methodName));

                // Use TypeUtils.convertType to get the method's return type.
                returnTypes.put(methodName, TypeUtils.convertType(returnTypeNode));
            } else {
                returnTypes.put(methodName, new Type("void", false));
            }
        }

        return returnTypes;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> paramsMap = new HashMap<>();
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = extractMethodName(method);
            List<Symbol> paramsList = new ArrayList<>();

            for (JmmNode param : method.getChildren("ParamExp")) {
                JmmNode typeNode = param.getChild(0);
                paramsList.add(new Symbol(TypeUtils.convertType(typeNode), param.get("name")));
            }
            paramsMap.put(methodName, paramsList);
        }
        return paramsMap;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> localsMap = new HashMap<>();
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            String methodName = extractMethodName(method);
            List<Symbol> localsList = new ArrayList<>();

            System.out.println("[buildLocals] Processing method: " + methodName);
            for (JmmNode child : method.getChildren()) {
                System.out.println("  child kind: " + child.getKind());
            }
            for (JmmNode varDecl : method.getChildren(VAR_DECL)) {
                if (varDecl.getChildren().isEmpty()) {
                    continue;
                }
                JmmNode typeNode = varDecl.getChild(0);
                localsList.add(new Symbol(TypeUtils.convertType(typeNode), varDecl.get("name")));
            }
            localsMap.put(methodName, localsList);
        }

        return localsMap;
    }

    private List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();
        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            methods.add(extractMethodName(method));
        }
        return methods;
    }

    private static List<String> buildImports(JmmNode root) {
        List<String> imports = new ArrayList<>();
        for (JmmNode child : root.getChildren()) {
            if ("ImportStmt".equals(child.getKind())) {
                StringBuilder importBuilder = new StringBuilder();
                importBuilder.append(child.get("ID"));
                for (JmmNode subNode : child.getChildren()) {
                    importBuilder.append(".").append(subNode.get("ID"));
                }
                imports.add(importBuilder.toString());
            }
        }
        return imports;
    }

    private static List<Symbol> getFieldsList(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        /*
        System.out.println("[debug] classDecl children kinds:");
        for (JmmNode child : classDecl.getChildren()) {
            System.out.println(" - " + child.getKind());
        }
        */

        for (JmmNode varDecl : classDecl.getChildren(VAR_DECL)) {
            if (varDecl.getChildren().isEmpty()) continue;
            JmmNode typeNode = varDecl.getChild(0);
            fields.add(new Symbol(TypeUtils.convertType(typeNode), varDecl.get("name")));
        }

        for (JmmNode fieldDecl : classDecl.getChildren(FIELD_DECL)) {
            if (fieldDecl.getChildren().isEmpty()) continue;
            JmmNode typeNode = fieldDecl.getChild(0);
            fields.add(new Symbol(TypeUtils.convertType(typeNode), fieldDecl.get("name")));
        }

        /*
        System.out.println("[debug] Fields extracted into symbol table:");
        for (Symbol field : fields) {
            System.out.println(" - " + field);
        }
        System.out.println("[debug] fields size: " + fields.size());
         */
        return fields;
    }
}
