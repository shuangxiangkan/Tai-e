package sa.pta.analysis.data;

import sa.pta.analysis.context.Context;
import sa.pta.element.CallSite;
import sa.pta.element.Field;
import sa.pta.element.Method;
import sa.pta.element.Obj;
import sa.pta.element.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Hash map based element manager.
 */
public class HashElementManager implements ElementManager {

    private Map<Context, Map<Variable, CSVariable>> vars = new HashMap<>();

    private Map<CSObj, Map<Field, InstanceField>> instanceFields = new HashMap<>();

    private Map<Field, StaticField> staticFields = new HashMap<>();

    private Map<Context, Map<Obj, CSObj>> objs = new HashMap<>();

    private Map<Context, Map<CallSite, CSCallSite>> callSites = new HashMap<>();

    private Map<Context, Map<Method, CSMethod>> methods = new HashMap<>();

    @Override
    public CSVariable getCSVariable(Context context, Variable var) {
        return getOrCreateCSElement(vars, context, var, CSVariable::new);
    }

    @Override
    public InstanceField getInstanceField(CSObj base, Field field) {
        return getOrCreateCSElement(instanceFields, base, field,
                InstanceField::new);
    }

    @Override
    public StaticField getStaticField(Field field) {
        return staticFields.computeIfAbsent(field, StaticField::new);
    }

    @Override
    public CSObj getCSObj(Context context, Obj obj) {
        return getOrCreateCSElement(objs, context, obj, CSObj::new);
    }

    @Override
    public CSCallSite getCSCallSite(Context context, CallSite callSite) {
        return getOrCreateCSElement(callSites, context, callSite, CSCallSite::new);
    }

    @Override
    public CSMethod getCSMethod(Context context, Method method) {
        return getOrCreateCSElement(methods, context, method, CSMethod::new);
    }

    private static <R, T, U> R getOrCreateCSElement(
            Map<T, Map<U, R>> map, T key1, U key2, BiFunction<T, U, R> creator) {
        return map.computeIfAbsent(key1, k -> new HashMap<>())
                .computeIfAbsent(key2, (k) -> creator.apply(key1, key2));
    }
}