package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.LiteralElement;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import org.specs.comp.ollir.OperationType;
import org.specs.comp.ollir.inst.AssignInstruction;
import org.specs.comp.ollir.inst.BinaryOpInstruction;
import org.specs.comp.ollir.inst.ReturnInstruction;
import org.specs.comp.ollir.inst.SingleOpInstruction;
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

        var modifier = types.getModifier(method.getMethodAccessModifier());

        var methodName = method.getMethodName();

        // Dynamic generation of params and return types
        var params = method.getParams().stream()
                .map(param -> types.getJasminType(param.getType()))
                .collect(Collectors.joining());

        var returnType = types.getJasminType(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        // Add initial stack/local limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        currentMethod = null;
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




}