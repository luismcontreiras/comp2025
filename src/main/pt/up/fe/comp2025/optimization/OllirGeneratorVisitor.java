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

        // Check if we have enough children
        if (node.getNumChildren() < 2) {
            // This is probably an assignment without a right-hand side
            // Log or handle this case appropriately
            return "// Incomplete assignment statement\n";
        }

        var rhs = exprVisitor.visit(node.getChild(1));

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        var left = node.getChild(0);
        Type thisType = types.getExprType(left);
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = left.get("name") + typeString;

        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

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

        // Add access modifier if public
        boolean isPublic = node.getBoolean("isPublic", false);
        if (isPublic) {
            code.append("public ");
        }

        // Handle static modifier for main method
        boolean isStatic = node.getBoolean("isStatic", false);
        if (isStatic) {
            code.append("static ");
        }

        // Add method name
        String name = node.get("name");
        code.append(name);

        // Process parameters
        code.append("(");

        // Get all parameter nodes
        var paramNodes = node.getChildren(PARAM);
        if (!paramNodes.isEmpty()) {
            var paramCodes = paramNodes.stream()
                    .map(this::visit)
                    .collect(Collectors.joining(", "));
            code.append(paramCodes);
        }

        code.append(")");

        // Add return type with null check
        Type returnType;
        if (name.equals("main")) {
            returnType = new Type("void", false);
        } else {
            returnType = table.getReturnType(name);
            if (returnType == null) {
                // Default to void if return type is not found in symbol table
                returnType = new Type("void", false);
            }
        }
        code.append(ollirTypes.toOllirType(returnType));

        // Start method body
        code.append(L_BRACKET);

        // Process local variable declarations
        for (var varDecl : node.getChildren(VAR_DECL)) {
            JmmNode typeNode = varDecl.getChild(0);
            String varName = varDecl.get("name");
            Type varType = types.convertType(typeNode);
            String ollirType = ollirTypes.toOllirType(varType);

            code.append("    ").append(varName).append(ollirType).append(" :=").append(ollirType)
                    .append(" 0").append(ollirType).append(END_STMT);
        }

        // Process statements
        for (var stmt : node.getChildren()) {
            if (STMT.check(stmt) || OTHER_STMT.check(stmt) ||
                    WITH_ELSE_STMT.check(stmt) || NO_ELSE_STMT.check(stmt) ||
                    RETURN_STMT.check(stmt)) {  // Make sure we check for RETURN_STMT as well
                String stmtCode = visit(stmt);
                if (!stmtCode.isEmpty()) {
                    code.append("    ").append(stmtCode);
                }
            }
        }

        // Add return statement if not already processed
        boolean hasReturnStmt = false;
        for (var child : node.getChildren()) {
            if (RETURN_STMT.check(child)) {
                hasReturnStmt = true;
                break;
            }
        }

        // If there's no return statement and it's not main, add a default return
        if (!hasReturnStmt) {
            if (name.equals("main")) {
                code.append("    ret.V;").append(NL);
            } else {
                String returnTypeStr = ollirTypes.toOllirType(returnType);
                // Generate default return value based on type
                String defaultValue;
                if (returnType.getName().equals("int")) {
                    defaultValue = "0" + returnTypeStr;
                } else if (returnType.getName().equals("boolean")) {
                    defaultValue = "0" + returnTypeStr;
                } else if (returnType.isArray()) {
                    defaultValue = "new(array, 0)" + returnTypeStr;
                } else if (!returnType.getName().equals("void")) {
                    defaultValue = "new(" + returnType.getName() + ")" + returnTypeStr;
                } else {
                    // Void return
                    code.append("    ret.V;").append(NL);
                    defaultValue = null;
                }

                if (defaultValue != null) {
                    code.append("    ret").append(returnTypeStr)
                            .append(" ").append(defaultValue).append(";").append(NL);
                }
            }
        }

        // End method body
        code.append(R_BRACKET);
        code.append(NL);

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

        code.append(L_BRACKET);
        code.append(NL);
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

        return exprResult.getComputation();
    }
}
