package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        var config = semanticsResult.getConfig();

        // Check if optimizations are enabled
        boolean optimizeEnabled = ConfigOptions.getOptimize(config);

        // Debug output
        System.out.println("Optimization flag (-o) enabled: " + optimizeEnabled);
        System.out.println("Config: " + config);

        if (!optimizeEnabled) {
            return semanticsResult;
        }

        var root = semanticsResult.getRootNode();
        var table = semanticsResult.getSymbolTable();

        boolean changed;
        int iterations = 0;
        do {
            changed = false;
            iterations++;

            // Apply constant folding
            ConstantFoldingVisitor folder = new ConstantFoldingVisitor(table);
            folder.visit(root);
            changed = folder.didChange();

            System.out.println("Iteration " + iterations + ", changed: " + changed);

        } while (changed && iterations < 10);

        return semanticsResult;
    }


    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }


}
