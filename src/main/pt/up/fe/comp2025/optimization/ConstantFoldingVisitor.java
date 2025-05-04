package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

import java.util.List;

public class ConstantFoldingVisitor extends PostorderJmmVisitor<String, Boolean> {

    private final SymbolTable table;
    private boolean changed;

    public ConstantFoldingVisitor(SymbolTable table) {
        this.table = table;
        this.changed = false;
        setDefaultVisit(this::defaultVisit);
        addVisit("BinaryExpr", this::visitBinaryExpr);
    }

    public boolean didChange() {
        return changed;
    }

    private Boolean visitBinaryExpr(JmmNode node, String dummy) {
        var left = node.getChild(0);
        var right = node.getChild(1);
        var op = node.get("op");

        System.out.println("Visiting BinaryExpr: " + op);

        // Only fold if both sides are integer literals
        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
            int lhs = Integer.parseInt(left.get("value"));
            int rhs = Integer.parseInt(right.get("value"));

            // Compute the result of the operation
            Integer result = switch (op) {
                case "+" -> lhs + rhs;
                case "-" -> lhs - rhs;
                case "*" -> lhs * rhs;
                case "/" -> (rhs != 0) ? lhs / rhs : null;
                default -> null;
            };

            if (result == null) return false;

            System.out.printf("Folding: %d %s %d = %d%n", lhs, op, rhs, result);

            // Replace the BinaryExpr node inside AssignStmt
            var parent = node.getParent();
            if (parent != null && parent.getKind().equals("AssignStmt")) {
                var replacement = left.copy(List.copyOf(left.getAttributes()));
                replacement.put("value", Integer.toString(result));

                List<JmmNode> children = parent.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    if (children.get(i) == node) {
                        parent.setChild(replacement, i);
                        break;
                    }
                }

                changed = true;
            }
        }

        return false;
    }


    private Boolean defaultVisit(JmmNode node, String dummy) {
        return false;
    }

    @Override
    protected void buildVisitor() {

    }
}
