package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(WITH_ELSE_STMT, this::visitWithElseStmt);
        addVisit(NO_ELSE_STMT, this::visitNoElseStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(FOR_STMT, this::visitForStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);

        setDefaultVisit(this::defaultVisit);
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        System.out.println("[visitAssignStmt] Processing node: " + node);
        System.out.println("[visitAssignStmt] Number of children: " + node.getNumChildren());

        if (node.getNumChildren() != 1) {
            System.out.println("[visitAssignStmt] Unexpected child count in AssignStmt: " + node.getNumChildren());
            return "// Unexpected assign format\n";
        }

        // Extract LHS from attribute, RHS from only child
        String lhsName = node.get("name");
        JmmNode rhsNode = node.getChild(0);

        System.out.println("[visitAssignStmt] LHS name: " + lhsName);
        System.out.println("[visitAssignStmt] RHS node: " + rhsNode);

        var rhs = exprVisitor.visit(rhsNode);
        code.append(rhs.getComputation());

        // Get context for scope detection
        String methodName = node.getAncestor("MethodDecl").map(m -> m.get("name")).orElse("main");

        // Infer type from RHS
        Type lhsType = types.getExprType(rhsNode, methodName);
        String ollirType = ollirTypes.toOllirType(lhsType);

        boolean isLocalOrParam =
                table.getLocalVariables(methodName).stream().anyMatch(s -> s.getName().equals(lhsName)) ||
                        table.getParameters(methodName).stream().anyMatch(s -> s.getName().equals(lhsName));

        boolean isField = table.getFields().stream().anyMatch(f -> f.getName().equals(lhsName));

        System.out.printf("[visitAssignStmt] lhsName=%s, method=%s, type=%s, isLocalOrParam=%b, isField=%b\n",
                lhsName, methodName, ollirType, isLocalOrParam, isField);

        if (!isLocalOrParam && isField) {
            // Field assignment
            code.append("putfield(this.").append(table.getClassName())
                    .append(", ").append(lhsName).append(ollirType)
                    .append(", ").append(rhs.getCode()).append(")")
                    .append(ollirType).append(";\n");
            System.out.println("[visitAssignStmt] Emitted putfield for field: " + lhsName);
        } else {
            // Local or parameter assignment
            code.append(lhsName).append(ollirType).append(" ")
                    .append(ASSIGN).append(ollirType).append(" ")
                    .append(rhs.getCode()).append(";\n");
            System.out.println("[visitAssignStmt] Emitted local/param assignment for: " + lhsName);
        }

        return code.toString();
    }









    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the method name from the ancestors
        JmmNode methodNode = node;
        while (methodNode != null && !methodNode.getKind().equals(METHOD_DECL.getNodeName())) {
            methodNode = methodNode.getParent();
        }

        if (methodNode == null) {
            throw new RuntimeException("Return statement outside method context");
        }

        String methodName = methodNode.get("name");

        // Get the return type of the method
        Type retType = table.getReturnType(methodName);

        // Process the expression if it exists
        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        // Add computation code
        code.append(expr.getComputation());

        // Add the return instruction with the correct type
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        // Add the expression code if present
        if (node.getNumChildren() > 0) {
            code.append(expr.getCode());
        }

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        // Add access modifiers
        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) code.append("public ");
        boolean isStatic = node.getBoolean("isStatic", false);
        if (isStatic) code.append("static ");

        // Method name
        String name = node.get("name");
        code.append(name).append("(");

        // Parameters
        var paramNodes = node.getChildren(PARAM);
        if (!paramNodes.isEmpty()) {
            var paramCodes = paramNodes.stream().map(this::visit).collect(Collectors.joining(", "));
            code.append(paramCodes);
        }

        code.append(")");

        // Return type
        Type returnType = name.equals("main") ? new Type("void", false) : table.getReturnType(name);
        if (returnType == null) returnType = new Type("void", false);
        code.append(ollirTypes.toOllirType(returnType));

        code.append(" {\n");

        // Local variables
        for (var varDecl : node.getChildren(VAR_DECL)) {
            JmmNode typeNode = varDecl.getChild(0);
            String varName = varDecl.get("name");
            Type varType = types.convertType(typeNode);
            String ollirType = ollirTypes.toOllirType(varType);
            code.append("    ").append(varName).append(ollirType).append(" :=").append(ollirType)
                    .append(" 0").append(ollirType).append(";\n");
        }

        // Process statements and expressions
        for (var child : node.getChildren()) {
            String kind = child.getKind();

            if (kind.endsWith("Stmt") || kind.contains("Stmt")) {
                String stmtCode = visit(child);
                if (!stmtCode.isEmpty()) {
                    code.append("    ").append(stmtCode);
                }
            } else if (kind.equals(VAR_REF_EXPR.getNodeName()) || kind.endsWith("Expr")) {
                var exprResult = exprVisitor.visit(child);

                if (!exprResult.getComputation().isBlank()) {
                    code.append("    ").append(exprResult.getComputation());
                }

                // Emit a return using the evaluated expression
                if (!exprResult.getCode().isBlank()) {
                    String tempVar = ollirTypes.nextTemp("retVal");
                    String retType = ollirTypes.toOllirType(returnType);

                    // Assign result to temp
                    code.append("    ").append(tempVar).append(retType)
                            .append(" :=").append(retType)
                            .append(" ").append(exprResult.getCode()).append(";\n");

                    // Return the temp
                    code.append("    ret").append(retType).append(" ").append(tempVar).append(retType).append(";\n");
                }
            }
        }

        // Return fallback if no return statement exists
        boolean hasReturn = node.getChildren().stream().anyMatch(child -> child.getKind().equals("ReturnStmt"));
        if (!hasReturn) {
            if (returnType.getName().equals("void")) {
                code.append("    ret.V;\n");
            } else {
                String defaultVal = returnType.getName().equals("boolean") || returnType.getName().equals("int")
                        ? "0" + ollirTypes.toOllirType(returnType)
                        : "null" + ollirTypes.toOllirType(returnType);
                code.append("    ret").append(ollirTypes.toOllirType(returnType))
                        .append(" ").append(defaultVal).append(";\n");
            }
        }

        code.append("}\n\n");
        return code.toString();
    }




    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        // Add extends clause if there is a superclass
        String superClassName = table.getSuper();
        if (superClassName != null && !superClassName.isEmpty()) {
            code.append(" extends ").append(superClassName);
        }

        code.append(SPACE).append(L_BRACKET).append(NL).append(NL);

        for (var field : table.getFields()) {
            Type fieldType = field.getType();
            String ollirType = ollirTypes.toOllirType(fieldType);
            String fieldName = field.getName();

            code.append(".field public ").append(fieldName).append(ollirType).append(";").append(NL);
        }

        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);
        return code.toString();
    }


    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }

    private String visitWithElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Create a unique label for the else branch and the end
        String elseLabel = ollirTypes.nextTemp("else");
        String endLabel = ollirTypes.nextTemp("endif");

        // If condition is false, jump to else
        code.append("if (").append(condExpr.getCode()).append(") goto ").append(elseLabel).append(END_STMT);

        // Then branch - visit the 'then' statement (child 1)
        code.append(visit(node.getChild(1)));

        // Jump to end after executing 'then'
        code.append("goto ").append(endLabel).append(END_STMT);

        // Else label and branch - visit the 'else' statement (child 2)
        code.append(elseLabel).append(":").append(NL);
        code.append(visit(node.getChild(2)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitNoElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // Create a unique label for the end
        String endLabel = ollirTypes.nextTemp("endif");

        // If condition is false, jump to end
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Visit the 'then' statement (child 1)
        code.append(visit(node.getChild(1)));

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Create unique labels for loop
        String loopLabel = ollirTypes.nextTemp("loop");
        String endLabel = ollirTypes.nextTemp("endloop");

        // Loop label
        code.append(loopLabel).append(":").append(NL);

        // Get the condition expression
        var condExpr = exprVisitor.visit(node.getChild(0));
        code.append(condExpr.getComputation());

        // If condition is false, exit loop
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Loop body - visit the body statement (child 1)
        code.append(visit(node.getChild(1)));

        // Jump back to start of loop
        code.append("goto ").append(loopLabel).append(END_STMT);

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Visit all statements in the block
        for (JmmNode stmt : node.getChildren()) {
            code.append(visit(stmt));
        }

        return code.toString();
    }

    private String visitForStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Create unique labels for loop
        String loopLabel = ollirTypes.nextTemp("forloop");
        String endLabel = ollirTypes.nextTemp("endforloop");

        // Initialize loop variable (first child)
        code.append(visit(node.getChild(0)));

        // Loop label
        code.append(loopLabel).append(":").append(NL);

        // Get the condition expression (second child)
        var condExpr = exprVisitor.visit(node.getChild(1));
        code.append(condExpr.getComputation());

        // If condition is false, exit loop
        code.append("if (!").append(condExpr.getCode()).append(") goto ").append(endLabel).append(END_STMT);

        // Loop body - visit the body statement (fourth child)
        code.append(visit(node.getChild(3)));

        // Update expression (third child)
        var updateExpr = exprVisitor.visit(node.getChild(2));
        code.append(updateExpr.getComputation());

        // Jump back to start of loop
        code.append("goto ").append(loopLabel).append(END_STMT);

        // End label
        code.append(endLabel).append(":").append(NL);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        // Expression statement - just evaluate the expression
        var exprResult = exprVisitor.visit(node.getChild(0));
        if (exprResult.getComputation().isBlank()) {
            // If it's a simple method call like `io.println(a);`, emit it explicitly
            String code = exprResult.getCode() + ";\n";
            System.out.println("[visitExprStmt] emitted simple expr: " + code.trim());
            return code;
        }

        System.out.println("[visitExprStmt] emitted expr computation: " + exprResult.getComputation());

        return exprResult.getComputation();
    }
}
