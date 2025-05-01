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

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
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

        String op = node.get("op");
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(op).append(resOllirType).append(SPACE)
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
                .append("new(array, ").append(sizeExpr.getCode()).append(")").append(ollirType)
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
                .append("new(").append(className).append(")").append(ollirType).append(END_STMT);
        computation.append("invokespecial(").append(code).append(", \"<init>\").V").append(END_STMT);

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

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(arrayExpr.getCode()).append("[")
                .append(indexExpr.getCode()).append("]").append(ollirType)
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
                .append("arraylength(").append(arrayExpr.getCode()).append(")").append(ollirType)
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        var callerExpr = visit(node.getChild(0));
        String methodName = node.get("method");

        StringBuilder computation = new StringBuilder();
        computation.append(callerExpr.getComputation());

        // Handle argument expressions
        StringBuilder args = new StringBuilder();
        for (int i = 1; i < node.getNumChildren(); i++) {
            var argExpr = visit(node.getChild(i));
            computation.append(argExpr.getComputation());

            if (i > 1) args.append(", ");
            args.append(argExpr.getCode());
        }

        // Determine return type of the method
        Type returnType;
        try {
            returnType = table.getReturnType(methodName);
        } catch (Exception e) {
            // If method not in symbol table, assume int return type
            returnType = TypeUtils.newIntType();
        }
        String ollirReturnType = ollirTypes.toOllirType(returnType);

        String code = ollirTypes.nextTemp() + ollirReturnType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(ollirReturnType).append(SPACE)
                .append("invokevirtual(").append(callerExpr.getCode()).append(", \"")
                .append(methodName).append("\"");

        // Add arguments if any
        if (args.length() > 0) {
            computation.append(", ").append(args);
        }

        computation.append(")").append(ollirReturnType).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitPostfixExpr(JmmNode node, Void unused) {
        String varName = node.get("value");
        String op = node.get("op");

        Type type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        StringBuilder computation = new StringBuilder();

        // Create temporary with current value
        String tempVar = ollirTypes.nextTemp() + ollirType;
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(END_STMT);

        // Update original variable
        computation.append(varName).append(ollirType).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append(varName).append(ollirType).append(SPACE);

        if (op.equals("++")) {
            computation.append("+").append(ollirType).append(SPACE).append("1").append(ollirType);
        } else { // "--"
            computation.append("-").append(ollirType).append(SPACE).append("1").append(ollirType);
        }
        computation.append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitArrayLiteralExpr(JmmNode node, Void unused) {
        // This is a more complex case as arrays need to be created and initialized
        if (node.getNumChildren() == 0) {
            // Empty array case
            Type arrayType = new Type("int", true);
            String ollirType = ollirTypes.toOllirType(arrayType);
            String code = ollirTypes.nextTemp() + ollirType;

            StringBuilder computation = new StringBuilder();
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE)
                    .append("new(array, 0)").append(ollirType)
                    .append(END_STMT);

            return new OllirExprResult(code, computation);
        }

        // Create array with right size
        int size = node.getNumChildren();
        Type arrayType = new Type("int", true);  // Assuming int array as default
        String ollirType = ollirTypes.toOllirType(arrayType);
        String arrayVar = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();
        computation.append(arrayVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(array, ").append(size).append(ollirType).append(")")
                .append(ollirType).append(END_STMT);

        // Initialize array elements
        for (int i = 0; i < size; i++) {
            var elemExpr = visit(node.getChild(i));
            computation.append(elemExpr.getComputation());

            // Determine element type from first element
            Type elemType = types.getExprType(node.getChild(i));
            String elemOllirType = ollirTypes.toOllirType(elemType);

            computation.append(arrayVar).append("[").append(i).append(ollirType).append("]")
                    .append(elemOllirType).append(SPACE)
                    .append(ASSIGN).append(elemOllirType).append(SPACE)
                    .append(elemExpr.getCode()).append(END_STMT);
        }

        return new OllirExprResult(arrayVar, computation);
    }
}
