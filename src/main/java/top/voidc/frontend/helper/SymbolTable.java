package top.voidc.frontend.helper;

import top.voidc.ir.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class SymbolTable {
    public static Stack<SymbolTable> stack = new Stack<>();
    private static Map<String, IceFunction> functionMap = new HashMap<>();

    private SymbolTable parent;

    public String scopeName;

    private final Map<String, IceValue> table = new HashMap<>();

    private SymbolTable(SymbolTable parent, String scopeName) {
        this.parent = parent;
        this.scopeName = scopeName;
    }

    public static SymbolTable createScope(String scopeName) {
        SymbolTable table = new SymbolTable(stack.empty() ? null : stack.peek(), scopeName);
        stack.push(table);
        return table;
    }

    public void put(String name, IceValue value) {
        Log.should(!table.containsKey(name),
                "Variable " + name + " already defined in scope " + scopeName);
        table.put(name, value);
    }

    public IceValue get(String name) {
        for (SymbolTable table = this; table != null; table = table.parent) {
            IceValue value = table.table.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static SymbolTable current() {
        return stack.peek();
    }

    public static void exitScope() {
        stack.pop();
    }

    public static void putFunction(String name, IceFunction function) {
        Log.should(!functionMap.containsKey(name),
                "Function" + name + " already defined");
        functionMap.put(name, function);
    }

    public static IceFunction getFunction(String name) {
        return functionMap.get(name);
    }
}
