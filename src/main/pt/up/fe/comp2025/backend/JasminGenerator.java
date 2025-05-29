package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);

    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        // TODO: When you support 'extends', this must be updated
        var fullSuperClass = "java/lang/Object";

        code.append(".super ").append(fullSuperClass).append(NL);

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        currentMethod = method;

        var code = new StringBuilder();

        // Access modifier (public, private, etc.)
        var modifier = types.getModifier(method.getMethodAccessModifier());
        var methodName = method.getMethodName();

        // Parameters and return type descriptor
        var params = method.getParams().stream()
                .map(param -> types.getJasminType(param.getType()))
                .collect(Collectors.joining());

        var returnType = types.getJasminType(method.getReturnType());

        // Generate all instructions first
        var methodCode = new StringBuilder();
        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            methodCode.append(instCode);
        }

        // Now estimate limits AFTER generating code
        int estimatedStackSize = 3;

        int estimatedLocals = method.getVarTable().values().stream()
                .mapToInt(var -> var.getVirtualReg())
                .max()
                .orElse(0) + 1;

        // Emit method header
        code.append("\n.method ").append(modifier)
                .append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        code.append(TAB).append(".limit stack ").append(estimatedStackSize).append(NL);
        code.append(TAB).append(".limit locals ").append(estimatedLocals).append(NL);

        // Add method instructions
        code.append(methodCode);

        // End method
        code.append(".end method\n");

        currentMethod = null;
        System.out.println("Generated Jasmin for method " + method.getMethodName() + ":\n" + code);

        return code.toString();
    }



    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());


        var typeCode = types.getJasminType(lhs.getType());

        String storeInst = switch (typeCode) {
            case "I", "Z" -> "istore";
            default -> "astore";
        };

        code.append(storeInst).append(" ").append(reg.getVirtualReg()).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName());
        var typeCode = types.getJasminType(operand.getType());

        String loadInst = switch (typeCode) {
            case "I", "Z" -> "iload";
            default -> "aload";
        };

        return loadInst + " " + reg.getVirtualReg() + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case AND -> "iand";
            case LTH -> {
                // Comparison result handling
                String trueLabel = "LT_TRUE_" + System.nanoTime();
                String endLabel = "LT_END_" + System.nanoTime();
                yield """
                if_icmplt %s
                iconst_0
                goto %s
                %s:
                iconst_1
                %s:
                """.formatted(trueLabel, endLabel, trueLabel, endLabel);
            }
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        if (!binaryOp.getOperation().getOpType().name().equals("LT")) {
            code.append(op).append(NL);
        } else {
            code.append(op); // already has newlines in multi-line case
        }

        return code.toString();
    }


    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (!returnInst.hasReturnValue()) {
            code.append("return").append(NL);
        } else {
            var returnValue = returnInst.getOperand().orElseThrow(() -> new IllegalStateException("Return operand expected"));
            var returnType = types.getJasminType(returnValue.getType());

            code.append(apply(returnValue));

            switch (returnType) {
                case "I", "Z" -> code.append("ireturn").append(NL);
                case "V" -> code.append("return").append(NL);
                default -> code.append("areturn").append(NL);
            }
        }

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invoke) {
        var code = new StringBuilder();

        // Load the object being called on (e.g., 'this' or some object reference)
        code.append(apply(invoke.getCaller()));

        // Load all method arguments
        for (Element arg : invoke.getArguments()) {
            code.append(apply(arg));
        }

        // Determine method name
        var methodName = ((LiteralElement) invoke.getMethodName()).getLiteral().replace("\"", "");

        // Class where method is defined — assume current class unless it's a field
        var className = ollirResult.getOllirClass().getClassName();

        // Build method descriptor
        var descriptor = new StringBuilder("(");
        for (Element arg : invoke.getArguments()) {
            descriptor.append(types.getJasminType(arg.getType()));
        }
        descriptor.append(")");
        descriptor.append(types.getJasminType(invoke.getReturnType()));

        code.append("invokevirtual ")
                .append(className).append("/")
                .append(methodName)
                .append(descriptor)
                .append(NL);

        return code.toString();
    }

    private int estimateStackDepth(Method method) {
        int maxDepth = 0;
        int currentDepth = 0;

        for (var inst : method.getInstructions()) {
            switch (inst.getInstType()) {
                case ASSIGN -> {
                    var assign = (AssignInstruction) inst;
                    var rhs = assign.getRhs();
                    String rhsType = rhs.getClass().getSimpleName();

                    switch (rhsType) {
                        case "BinaryOpInstruction" -> {
                            currentDepth += 2; // load left and right
                            currentDepth -= 1; // one result remains
                        }
                        case "UnaryOpInstruction", "SingleOpInstruction", "LiteralElement" -> {
                            currentDepth += 1;
                        }
                        case "CallInstruction" -> {
                            var call = (CallInstruction) rhs;
                            int args = call.getArguments().size();
                            if (call.getCaller() != null) args += 1;
                            currentDepth += args;

                            // if return value is kept, add it
                            if (!types.getJasminType(call.getReturnType()).equals("V"))
                                currentDepth += 1;

                            // method consumes all arguments and object
                            currentDepth -= args;
                        }
                        default -> currentDepth += 1;
                    }

                    // storing a value pops 1
                    currentDepth -= 1;
                }

                case RETURN -> currentDepth += 1;

                default -> currentDepth += 1;
            }

            maxDepth = Math.max(maxDepth, currentDepth);
        }

        return maxDepth;
    }


}