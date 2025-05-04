package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.*;

import java.util.*;
import java.util.stream.Collectors;

public class RegisterAllocator {

    private final ClassUnit classUnit;
    private final int maxRegisters;

    public RegisterAllocator(ClassUnit classUnit, int maxRegisters) {
        this.classUnit = classUnit;
        this.maxRegisters = maxRegisters;
    }

    public void run() {
        classUnit.buildCFGs();

        for (Method method : classUnit.getMethods()) {
            if (method.isConstructMethod() || method.isStaticMethod()) continue;

            System.out.println("Allocating registers for method: " + method.getMethodName());

            Map<Instruction, Set<String>> in = new HashMap<>();
            Map<Instruction, Set<String>> out = new HashMap<>();
            Map<Instruction, Set<String>> def = new HashMap<>();
            Map<Instruction, Set<String>> use = new HashMap<>();

            List<Instruction> instructions = method.getInstructions();

            for (Instruction instr : instructions) {
                def.put(instr, getDef(instr));
                use.put(instr, getUse(instr));
                in.put(instr, new HashSet<>());
                out.put(instr, new HashSet<>());
            }

            boolean changed;
            do {
                changed = false;
                for (Instruction instr : instructions) {
                    Set<String> newIn = new HashSet<>(use.get(instr));
                    Set<String> outMinusDef = new HashSet<>(out.get(instr));
                    outMinusDef.removeAll(def.get(instr));
                    newIn.addAll(outMinusDef);

                    Set<String> newOut = new HashSet<>();
                    for (Node succ : instr.getSuccessors()) {
                        newOut.addAll(in.get(succ));
                    }

                    if (!newIn.equals(in.get(instr))) {
                        in.put(instr, newIn);
                        changed = true;
                    }

                    if (!newOut.equals(out.get(instr))) {
                        out.put(instr, newOut);
                        changed = true;
                    }
                }
            } while (changed);

            Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(instructions, def, out);
            Map<String, Integer> allocation = colorGraph(interferenceGraph);

            if (maxRegisters > 0 && allocation.size() > maxRegisters) {
                throw new RuntimeException("Cannot allocate registers: required " + allocation.size() + " but max is " + maxRegisters);
            }

            for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
                method.getVarTable().get(entry.getKey()).setVirtualReg(entry.getValue());
                System.out.println("Variable " + entry.getKey() + " -> r" + entry.getValue());
            }
        }
    }

    private Set<String> getDef(Instruction instr) {
        Set<String> defs = new HashSet<>();
        if (instr instanceof AssignInstruction assignInstr) {
            Element dest = assignInstr.getDest();
            if (dest instanceof Operand operand) {
                defs.add(operand.getName());
            }

        }
        return defs;
    }


    private Set<String> getUse(Instruction instr) {
        Set<String> uses = new HashSet<>();

        if (instr instanceof AssignInstruction assignInstr) {
            instr = assignInstr.getRhs();
        }

        if (instr instanceof BinaryOpInstruction binOp) {
            for (Element elem : List.of(binOp.getLeftOperand(), binOp.getRightOperand())) {
                if (elem instanceof Operand operand) {
                    uses.add(operand.getName());
                }
            }
        } else if (instr instanceof UnaryOpInstruction unaryOp) {
            Element elem = unaryOp.getOperand();
            if (elem instanceof Operand operand) {
                uses.add(operand.getName());
            }
        } else if (instr instanceof CallInstruction callInstr) {
            for (Element arg : callInstr.getOperands()) {
                if (arg instanceof Operand operand) {
                    uses.add(operand.getName());
                }
            }
        } else if (instr instanceof GetFieldInstruction getField) {
            Element objRef = getField.getOperands().get(0);
            if (objRef instanceof Operand operand) {
                uses.add(operand.getName());
            }
        } else if (instr instanceof PutFieldInstruction putField) {
            for (Element elem : List.of(putField.getOperands().get(0), putField.getOperands().get(2))) {
                if (elem instanceof Operand operand) {
                    uses.add(operand.getName());
                }
            }
        } else if (instr instanceof SingleOpInstruction singleOp) {
            Element elem = singleOp.getSingleOperand();
            if (elem instanceof Operand operand) {
                uses.add(operand.getName());
            }
        }
        else {
            System.out.println("Unhandled instruction type: " + instr.getClass().getSimpleName());
        }

        return uses;
    }


    private Map<String, Set<String>> buildInterferenceGraph(List<Instruction> instrs,
                                                            Map<Instruction, Set<String>> def,
                                                            Map<Instruction, Set<String>> out) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Instruction instr : instrs) {
            Set<String> live = new HashSet<>(out.get(instr));
            live.addAll(def.get(instr));

            for (String v1 : live) {
                graph.putIfAbsent(v1, new HashSet<>());
                for (String v2 : live) {
                    if (!v1.equals(v2)) {
                        graph.get(v1).add(v2);
                    }
                }
            }
        }
        return graph;
    }

    private Map<String, Integer> colorGraph(Map<String, Set<String>> graph) {
        Map<String, Integer> colors = new HashMap<>();
        Stack<String> stack = new Stack<>();
        Map<String, Set<String>> tempGraph = new HashMap<>();

        for (var entry : graph.entrySet()) {
            tempGraph.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        while (!tempGraph.isEmpty()) {
            Optional<String> opt = tempGraph.keySet().stream()
                    .filter(v -> tempGraph.get(v).size() < (maxRegisters == 0 ? Integer.MAX_VALUE : maxRegisters))
                    .findFirst();

            if (opt.isEmpty()) {
                throw new RuntimeException("Graph coloring failed: can't reduce graph further");
            }

            String node = opt.get();
            stack.push(node);
            for (String neighbor : tempGraph.get(node)) {
                tempGraph.get(neighbor).remove(node);
            }
            tempGraph.remove(node);
        }

        while (!stack.isEmpty()) {
            String node = stack.pop();
            Set<Integer> used = graph.get(node).stream()
                    .map(colors::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int color = 0;
            while (used.contains(color)) color++;
            colors.put(node, color);
        }

        return colors;
    }
}
