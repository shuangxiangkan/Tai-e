/*
 * Tai-e: A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai-e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.pta.env.nativemodel;

import pascal.taie.callgraph.CallKind;
import pascal.taie.java.ClassHierarchy;
import pascal.taie.java.classes.JMethod;
import pascal.taie.java.types.Type;
import pascal.taie.pta.env.EnvObj;
import pascal.taie.pta.ir.Allocation;
import pascal.taie.pta.ir.Call;
import pascal.taie.pta.ir.CallSite;
import pascal.taie.pta.ir.IR;
import pascal.taie.pta.ir.Obj;
import pascal.taie.pta.ir.Variable;

import java.util.Collections;

/**
 * Convenient methods for creating native models.
 */
class Utils {

    /**
     * Create allocation site and the corresponding constructor call site.
     * This method only supports non-argument constructor.
     * @param hierarchy the class hierarchy
     * @param containerIR IR of the containing method of the allocation site
     * @param type type of the allocated object
     * @param name name of the allocated object
     * @param recv variable holds the allocated object and
     *            acts as the receiver variable for the constructor call
     * @param ctorSig signature of the constructor
     * @param callId ID of the mock constructor call site
     */
    static void modelAllocation(
            ClassHierarchy hierarchy, IR containerIR,
            Type type, String name, Variable recv,
            String ctorSig, String callId) {
        JMethod container = containerIR.getMethod();
        Obj obj = new EnvObj(name, type, container);
        containerIR.addStatement(new Allocation(recv, obj));
        JMethod ctor = hierarchy.getJREMethod(ctorSig);
        MockCallSite initCallSite = new MockCallSite(
                CallKind.SPECIAL, ctor.getRef(),
                recv, Collections.emptyList(),
                container, callId);
        Call initCall = new Call(initCallSite, null);
        containerIR.addStatement(initCall);
    }

    /**
     * Model the side effects of a static native call r = T.foo(o, ...)
     * by mocking a virtual call r = o.m().
     */
    static void modelStaticToVirtualCall(
            ClassHierarchy hierarchy, IR containerIR, Call call,
            String calleeSig, String callId) {
        CallSite origin = call.getCallSite();
        JMethod callee = hierarchy.getJREMethod(calleeSig);
        MockCallSite callSite = new MockCallSite(CallKind.VIRTUAL,
                callee.getRef(),
                origin.getArg(0), Collections.emptyList(),
                containerIR.getMethod(), callId);
        Call mockCall = new Call(callSite, call.getLHS().orElse(null));
        containerIR.addStatement(mockCall);
    }
}
