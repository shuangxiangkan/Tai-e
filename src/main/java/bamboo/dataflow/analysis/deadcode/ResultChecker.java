/*
 * Bamboo - A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Bamboo is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package bamboo.dataflow.analysis.deadcode;

import bamboo.dataflow.analysis.constprop.ConstantPropagation;
import bamboo.dataflow.analysis.livevar.LiveVariableAnalysis;
import bamboo.util.SootUtils;
import soot.Body;
import soot.BriefUnitPrinter;
import soot.G;
import soot.Unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Boosts dead code elimination and checks whether the analysis result
 * is correct by comparing it with prepared expected result.
 */
public class ResultChecker {

    // ---------- static members ----------
    /**
     * The current result checker
     */
    private static ResultChecker checker;

    private static void setChecker(ResultChecker checker) {
        ResultChecker.checker = checker;
    }

    public static boolean isAvailable() {
        return checker != null;
    }

    public static ResultChecker get() {
        return checker;
    }

    /**
     * The entry function of whole checking mechanism.
     * @param args the arguments for running Soot
     * @param path the path string of the expected result file
     * @return the mismatched information in form of set of strings
     */
    public static Set<String> check(String[] args, String path) {
        ResultChecker checker = new ResultChecker(Paths.get(path));
        setChecker(checker);
        ConstantPropagation.setOutput(false);
        LiveVariableAnalysis.setOutput(false);
        DeadCodeDetection.setOutput(false);

        G.reset(); // reset the whole Soot environment
        Main.main(args);
        return checker.getMismatches();
    }

    // ---------- instance members ----------
    private Map<String, Set<String>> expectedResult;

    private Set<String> mismatches = new TreeSet<>();

    ResultChecker(Path filePath) {
        readExpectedResult(filePath);
    }

    Set<String> getMismatches() {
        return mismatches;
    }

    /**
     * Compares the analysis result with expected result, and stores
     * any found mismatches.
     */
    public void compare(Body body, Set<Unit> analysisResult) {
        String method = body.getMethod().getSignature();
        Set<String> expectedDeadCode = expectedResult.get(method);
        if (expectedDeadCode != null) {
            BriefUnitPrinter up = new BriefUnitPrinter(body);
            body.getUnits().forEach(u -> {
                String given = SootUtils.unitToString(up, u);
                if (analysisResult.contains(u)
                        && !expectedDeadCode.contains(given)) {
                    mismatches.add("\n" + given + " should NOT be dead code");
                } if (!analysisResult.contains(u)
                        && expectedDeadCode.contains(given)) {
                    mismatches.add("\n" + given + " should be dead code");
                }
            });
        }
    }

    /**
     * Reads expected result from given file path.
     */
    private void readExpectedResult(Path filePath) {
        expectedResult = new TreeMap<>();
        String currentMethod = null;  // method signature
        try {
            for (String line : Files.readAllLines(filePath)) {
                if (isMethodSignature(line)) {
                    currentMethod = line;
                    expectedResult.put(currentMethod, new TreeSet<>());
                } else if (!isEmpty(line)) {
                    expectedResult.get(currentMethod).add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath
                    + " caused by " + e);
        }
    }

    private boolean isMethodSignature(String line) {
        return line.startsWith("<");
    }

    private boolean isEmpty(String line) {
        return line.trim().equals("");
    }
}
