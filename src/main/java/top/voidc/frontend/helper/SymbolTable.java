package top.voidc.frontend.helper;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

public class SymbolTable {
    private final Stack<BlockRecord> stack = new Stack<>();
    private final Map<String, IceFunction> functionMap = new HashMap<>();

    // 块级变量
    private static class BlockRecord {
        public final BlockRecord parent;

        public String scopeName;

        private final Map<String, IceValue> table = new HashMap<>();

        private BlockRecord(BlockRecord parent, String scopeName) {
            this.parent = parent;
            this.scopeName = scopeName;
        }

        public void put(String name, IceValue value) {
            Log.should(!table.containsKey(name),
                    "Variable " + name + " already defined in scope " + scopeName);
            table.put(name, value);
        }

        public Optional<IceValue> get(String name) {
            for (var table = this; table != null; table = table.parent) {
                IceValue value = table.table.get(name);
                if (value != null) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    public SymbolTable() {}

    public void createScope(String scopeName) {
        final var table = new BlockRecord(stack.empty() ? null : stack.peek(), scopeName);
        stack.push(table);
    }

    public void put(String name, IceValue value) {
        stack.peek().put(name, value);
    }

    public Optional<IceValue> get(String name) {
        return stack.peek().get(name);
    }

    public void exitScope() {
        stack.pop();
    }

    public void putFunction(String name, IceFunction function) {
        Log.should(!functionMap.containsKey(name),
                "Function" + name + " already defined");
        functionMap.put(name, function);
    }

    public IceFunction getFunction(String name) {
        return functionMap.get(name);
    }
}
