/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.analysis.pta;

import org.junit.Test;
import pascal.taie.analysis.Tests;

public class ReflectionTest {

    private static final String DIR = "reflection";

    @Test
    public void testStringConstant() {
        Tests.testPTA(DIR, "GetMember");
    }

    @Test
    public void testReflectionLog() {
        Tests.testPTA(DIR, "ReflectiveAction",
                "reflection-log:src/test/resources/pta/reflection/ReflectiveAction.log");
    }
}