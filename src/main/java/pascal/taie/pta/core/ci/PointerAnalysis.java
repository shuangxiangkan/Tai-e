/*
 * Tai'e - A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai'e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.pta.core.ci;

import pascal.taie.callgraph.CallGraph;
import pascal.taie.callgraph.CallKind;
import pascal.taie.callgraph.Edge;
import pascal.taie.pta.core.ProgramManager;
import pascal.taie.pta.core.heap.HeapModel;
import pascal.taie.pta.element.CallSite;
import pascal.taie.pta.element.Method;
import pascal.taie.pta.element.Obj;
import pascal.taie.pta.element.Variable;
import pascal.taie.pta.statement.Allocation;
import pascal.taie.pta.statement.Assign;
import pascal.taie.pta.statement.Call;
import pascal.taie.pta.statement.InstanceLoad;
import pascal.taie.pta.statement.InstanceStore;
import pascal.taie.pta.statement.Statement;

import java.util.Optional;
import java.util.stream.Stream;

public class PointerAnalysis {

    private ProgramManager programManager;

    private OnFlyCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private HeapModel heapModel;

    private WorkList workList;

    public ProgramManager getProgramManager() {
        return programManager;
    }

    public void setProgramManager(ProgramManager programManager) {
        this.programManager = programManager;
    }

    public void setHeapModel(HeapModel heapModel) {
        this.heapModel = heapModel;
    }

    public CallGraph<CallSite, Method> getCallGraph() {
        return callGraph;
    }

    public Stream<Var> getVariables() {
        return pointerFlowGraph.getPointers()
                .stream()
                .filter(p -> p instanceof Var)
                .map(p -> (Var) p);
    }

    public Stream<InstanceField> getInstanceFields() {
        return pointerFlowGraph.getPointers()
                .stream()
                .filter(p -> p instanceof InstanceField)
                .map(p -> (InstanceField) p);
    }

    /**
     * Runs pointer analysis algorithm.
     */
    public void solve() {
        initialize();
        analyze();
    }

    /**
     * Initializes pointer analysis.
     */
    private void initialize() {
        callGraph = new OnFlyCallGraph();
        pointerFlowGraph = new PointerFlowGraph();
        workList = new WorkList();
        Method main = programManager.getMainMethod();
        addReachable(main);
        // must be called after addReachable()
        callGraph.addEntryMethod(main);
    }

    /**
     * Processes worklist entries until the worklist is empty.
     */
    private void analyze() {
        while (!workList.isEmpty()) {
            while (workList.hasPointerEntries()) {
                WorkList.Entry entry = workList.pollPointerEntry();
                Pointer p = entry.pointer;
                PointsToSet pts = entry.pointsToSet;
                PointsToSet diff = propagate(p, pts);
                if (p instanceof Var) {
                    Var v = (Var) p;
                    processInstanceStore(v, diff);
                    processInstanceLoad(v, diff);
                    processCall(v, diff);
                }
            }
            while (workList.hasCallEdges()) {
                processCallEdge(workList.pollCallEdge());
            }
        }
    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
//         System.out.println("Propagate "
//                 + StringUtils.streamToString(pointsToSet.stream())
//                 + " to " + pointer);
        PointsToSet diff = new PointsToSet();
        for (Obj obj : pointsToSet) {
            if (pointer.getPointsToSet().addObject(obj)) {
                diff.addObject(obj);
            }
        }
        if (!diff.isEmpty()) {
            for (Pointer succ : pointerFlowGraph.getSuccessorsOf(pointer)) {
                workList.addPointerEntry(succ, diff);
            }
        }
        return diff;
    }

    /**
     * Adds an edge "from -> to" to the PFG.
     */
    private void addPFGEdge(Pointer from, Pointer to) {
        if (pointerFlowGraph.addEdge(from, to)) {
            if (!from.getPointsToSet().isEmpty()) {
                workList.addPointerEntry(to, from.getPointsToSet());
            }
        }
    }

    /**
     * Processes new reachable method.
     */
    private void addReachable(Method method) {
        if (callGraph.addNewMethod(method)) {
            processAllocations(method);
            processLocalAssign(method);
            processStaticCalls(method);
        }
    }

    /**
     * Processes allocations (new statements) in the given method.
     */
    private void processAllocations(Method method) {
        for (Statement stmt : method.getStatements()) {
            if (stmt instanceof Allocation) {
                Allocation alloc = (Allocation) stmt;
                // obtain abstract object
                Obj obj = heapModel.getObj(alloc);
                // obtain lhs variable
                Var lhs = pointerFlowGraph.getVar(alloc.getVar());
                workList.addPointerEntry(lhs, new PointsToSet(obj));
            }
        }
    }

    /**
     * Adds local assign edges of the given method to pointer flow graph.
     */
    private void processLocalAssign(Method method) {
        for (Statement stmt : method.getStatements()) {
            if (stmt instanceof Assign) {
                Assign assign = (Assign) stmt;
                Var from = pointerFlowGraph.getVar(assign.getFrom());
                Var to = pointerFlowGraph.getVar(assign.getTo());
                addPFGEdge(from, to);
            }
        }
    }

    /**
     * Processes instance stores when points-to set of the base variable changes.
     *
     * @param baseVar the base variable
     * @param pts     set of new discovered objects pointed by the variable.
     */
    private void processInstanceStore(Var baseVar, PointsToSet pts) {
        Variable var = baseVar.getVariable();
        for (InstanceStore store : var.getInstanceStores()) {
            Var from = pointerFlowGraph.getVar(store.getFrom());
            for (Obj baseObj : pts) {
                InstanceField instField = pointerFlowGraph.getInstanceField(
                        baseObj, store.getField());
                addPFGEdge(from, instField);
            }
        }
    }

    /**
     * Processes instance loads when points-to set of the base variable changes.
     *
     * @param baseVar the base variable
     * @param pts     set of new discovered objects pointed by the variable.
     */
    private void processInstanceLoad(Var baseVar, PointsToSet pts) {
        Variable var = baseVar.getVariable();
        for (InstanceLoad load : var.getInstanceLoads()) {
            Var to = pointerFlowGraph.getVar(load.getTo());
            for (Obj baseObj : pts) {
                InstanceField instField = pointerFlowGraph.getInstanceField(
                        baseObj, load.getField());
                addPFGEdge(instField, to);
            }
        }
    }

    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param recv the receiver variable
     * @param pts  set of new discovered objects pointed by the variable.
     */
    private void processCall(Var recv, PointsToSet pts) {
        Variable var = recv.getVariable();
        for (Call call : var.getCalls()) {
            CallSite callSite = call.getCallSite();
            for (Obj recvObj : pts) {
                // resolve callee
                Method callee = programManager.resolveCallee(recvObj, callSite);
                // pass receiver object to *this* variable
                Var thisVar = pointerFlowGraph.getVar(callee.getThis());
                workList.addPointerEntry(thisVar, new PointsToSet(recvObj));
                // build call edge
                workList.addCallEdge(new Edge<>(
                        callSite.getKind(), callSite, callee));
            }
        }
    }

    /**
     * Process the call edges in work list.
     */
    private void processCallEdge(Edge<CallSite, Method> edge) {
        if (!callGraph.containsEdge(edge)) {
            callGraph.addEdge(edge);
            Method callee = edge.getCallee();
            addReachable(callee);
            CallSite callSite = edge.getCallSite();
            // pass arguments to parameters
            for (int i = 0; i < callSite.getArgCount(); ++i) {
                Optional<Variable> optArg = callSite.getArg(i);
                Optional<Variable> optParam = callee.getParam(i);
                optArg.ifPresent(arg -> {
                    Var argVar = pointerFlowGraph.getVar(arg);
                    //noinspection OptionalGetWithoutIsPresent
                    Var paramVar = pointerFlowGraph.getVar(optParam.get());
                    addPFGEdge(argVar, paramVar);
                });
            }
            // pass results to LHS variable
            callSite.getCall().getLHS().ifPresent(lhs -> {
                Var lhsVar = pointerFlowGraph.getVar(lhs);
                for (Variable ret : callee.getReturnVariables()) {
                    Var retVar = pointerFlowGraph.getVar(ret);
                    addPFGEdge(retVar, lhsVar);
                }
            });
        }
    }

    /**
     * Process static calls in given method.
     */
    private void processStaticCalls(Method method) {
        for (Statement stmt : method.getStatements()) {
            if (stmt instanceof Call) {
                CallSite callSite = ((Call) stmt).getCallSite();
                if (callSite.getKind() == CallKind.STATIC) {
                    Method callee = programManager.resolveCallee(null, callSite);
                    Edge<CallSite, Method> edge =
                            new Edge<>(CallKind.STATIC, callSite, callee);
                    workList.addCallEdge(edge);
                }
            }
        }
    }
}