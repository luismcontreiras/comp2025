package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_TRUE, this::visitBoolean);
        addVisit(BOOLEAN_FALSE, this::visitBoolean);
        addVisit(PARENTHESIZED_EXPR, this::visitParenthesizedExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(NEW_INT_ARRAY_EXPR, this::visitNewIntArrayExpr);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObjectExpr);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(POSTFIX_EXPR, this::visitPostfixExpr);
        addVisit(ARRAY_LITERAL_EXPR, this::visitArrayLiteralExpr);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // Add code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // Get operator and result type
        String op = node.get("op");
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        // Generate the binary operation
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE)
                .append(op).append(resOllirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("value");
        String methodName = node.getAncestor(METHOD_DECL.getNodeName()).map(m -> m.get("name")).orElse("main");
        Type type = types.getExprType(node, methodName);
        String ollirType = ollirTypes.toOllirType(type);

        boolean isParam = table.getParameters(methodName).stream().anyMatch(s -> s.getName().equals(id));
        boolean isLocalVar = table.getLocalVariables(methodName).stream().anyMatch(s -> s.getName().equals(id));
        boolean isField = table.getFields().stream().anyMatch(f -> f.getName().equals(id));

        /*
        System.out.println("[visitVarRef] id = " + id);
        System.out.println("[visitVarRef] method = " + methodName);
        System.out.println("[visitVarRef] params: " + table.getParameters(methodName));
        System.out.println("[visitVarRef] locals: " + table.getLocalVariables(methodName));
        System.out.println("[visitVarRef] fields: " + table.getFields());
        */

        if (!isParam && !isLocalVar && isField) {
            String className = table.getClassName();
            String code = "getfield(this." + className + ", " + id + ollirType + ")" + ollirType;
            System.out.println("[visitVarRef] emitting getfield for field: " + id);
            return new OllirExprResult(code);
        }

        return new OllirExprResult(id + ollirType);
    }




    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        Type boolType = new Type("boolean", false);
        String ollirBoolType = ollirTypes.toOllirType(boolType);
        String value = node.getKind().equals(BOOLEAN_TRUE.getNodeName()) ? "1" : "0";
        String code = value + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenthesizedExpr(JmmNode node, Void unused) {
        // For parenthesized expressions, just return the inner expression
        return visit(node.getChild(0));
    }

    private OllirExprResult visitThisExpr(JmmNode node, Void unused) {
        Type classType = new Type(table.getClassName(), false);
        String ollirType = ollirTypes.toOllirType(classType);
        String code = "this" + ollirType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var operand = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(operand.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        // Only '!' operator is supported in the grammar
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!").append(resOllirType).append(SPACE)
                .append(operand.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewIntArrayExpr(JmmNode node, Void unused) {
        var sizeExpr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(sizeExpr.getComputation());

        Type arrayType = new Type("int", true);
        String ollirType = ollirTypes.toOllirType(arrayType);
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(sizeExpr.getCode()).append(")")
                .append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        String className = node.get("value");
        Type objType = new Type(className, false);
        String ollirType = ollirTypes.toOllirType(objType);
        String code = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(").append(className).append(")")
                .append(ollirType).append(END_STMT);

        // Add constructor call
        computation.append("invokespecial(").append(code)
                .append(", \"<init>\").V").append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));
        var indexExpr = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());

        Type elemType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(elemType);
        String code = ollirTypes.nextTemp() + ollirType;

        // Get the element type
        Type intType = TypeUtils.newIntType();
        String intOllirType = ollirTypes.toOllirType(intType);

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(arrayExpr.getCode()).append("[")
                .append(indexExpr.getCode()).append("]")
                .append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());

        Type intType = TypeUtils.newIntType();
        String ollirType = ollirTypes.toOllirType(intType);
        String code = ollirTypes.nextTemp() + ollirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("arraylength(").append(arrayExpr.getCode()).append(")")
                .append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        var callerExpr = visit(node.getChild(0));
        String methodName = node.get("method");

        StringBuilder computation = new StringBuilder();
        computation.append(callerExpr.getComputation());

        // Process all arguments
        StringBuilder args = new StringBuilder();
        for (int i = 1; i < node.getNumChildren(); i++) {
            var argExpr = visit(node.getChild(i));
            computation.append(argExpr.getComputation());

            if (i > 1) args.append(", ");
            args.append(argExpr.getCode());
        }

        // Determine the return type of the method
        Type returnType;
        try {
            // Check if caller is "this" or a class instance
            JmmNode caller = node.getChild(0);
            if (caller.getKind().equals(THIS_EXPR.getNodeName()) ||
                    (caller.getKind().equals(VAR_REF_EXPR.getNodeName()) &&
                            types.getExprType(caller).getName().equals(table.getClassName()))) {
                returnType = table.getReturnType(methodName);
            } else {
                // For external methods, try to get from symbol table or default to int
                returnType = table.getReturnType(methodName);
            }
        } catch (Exception e) {
            // If method is not found in symbol table, assume int return type
            returnType = TypeUtils.newIntType();
        }

        String ollirReturnType = ollirTypes.toOllirType(returnType);
        String code = ollirTypes.nextTemp() + ollirReturnType;

        // Generate the method call
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirReturnType).append(SPACE);

        String invokeKind;

        // If caller is from an import (like 'io'), use invokestatic
        String callerId = node.getChild(0).get("value");
        boolean isStaticCall = table.getImports().stream().anyMatch(imp -> imp.endsWith(callerId));

        if (isStaticCall) {
            invokeKind = "invokestatic";
            computation.append(invokeKind).append("(").append(callerId);
        } else {
            invokeKind = "invokevirtual";
            computation.append(invokeKind).append("(").append(callerExpr.getCode());
        }

        computation.append(", \"").append(methodName).append("\"");

        // Add arguments if any
        if (args.length() > 0) {
            computation.append(", ").append(args);
        }

        computation.append(")").append(ollirReturnType).append(END_STMT);

        System.out.println("[MethodCallExpr] caller = " + callerExpr.getCode());
        System.out.println("[MethodCallExpr] method = " + methodName);
        System.out.println("[MethodCallExpr] isStaticCall = " + isStaticCall);
        System.out.println("[MethodCallExpr] imports = " + table.getImports());

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitPostfixExpr(JmmNode node, Void unused) {
        String varName = node.get("value");
        String op = node.get("op");

        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        StringBuilder computation = new StringBuilder();

        // Store the original value in a temporary
        String tempVar = ollirTypes.nextTemp() + ollirType;
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(END_STMT);

        // Update the original variable
        computation.append(varName).append(ollirType).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(SPACE);

        if (op.equals("++")) {
            computation.append("+").append(ollirType).append(SPACE)
                    .append("1").append(ollirType);
        } else { // "--"
            computation.append("-").append(ollirType).append(SPACE)
                    .append("1").append(ollirType);
        }
        computation.append(END_STMT);

        // Return the original value (before increment/decrement)
        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArrayLiteralExpr(JmmNode node, Void unused) {
        if (node.getNumChildren() == 0) {
            // Handle empty array case
            Type arrayType = new Type("int", true);
            String ollirType = ollirTypes.toOllirType(arrayType);
            String code = ollirTypes.nextTemp() + ollirType;

            StringBuilder computation = new StringBuilder();
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE)
                    .append("new(array, 0)")
                    .append(ollirType)
                    .append(END_STMT);

            return new OllirExprResult(code, computation);
        }

        // For non-empty arrays, create array of correct size and initialize elements
        int size = node.getNumChildren();

        // Determine element type from first element
        Type elemType = types.getExprType(node.getChild(0));
        Type arrayType = new Type(elemType.getName(), true);
        String ollirType = ollirTypes.toOllirType(arrayType);
        String elemOllirType = ollirTypes.toOllirType(elemType);

        String arrayVar = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();

        // Create array with the needed size
        computation.append(arrayVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(size).append(".i32)")
                .append(ollirType).append(END_STMT);

        // Initialize all array elements
        Type intType = TypeUtils.newIntType();
        String intOllirType = ollirTypes.toOllirType(intType);

        for (int i = 0; i < size; i++) {
            var elemExpr = visit(node.getChild(i));
            computation.append(elemExpr.getComputation());

            // Set array[i] = element
            computation.append(arrayVar).append("[").append(i).append(intOllirType).append("]")
                    .append(elemOllirType).append(SPACE)
                    .append(ASSIGN).append(elemOllirType).append(SPACE)
                    .append(elemExpr.getCode()).append(END_STMT);
        }

        return new OllirExprResult(arrayVar, computation);
    }

    /**
     * Default visitor. Visits every child node and returns an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return OllirExprResult.EMPTY;
    }
}