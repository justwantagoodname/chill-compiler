package top.voidc.misc;

import java.util.HashMap;
import java.util.Map;

public class Flag {
    public static class Option<T> {
        private final String name;
        private final Class<T> type;
        private T value;

        public Option(String name, Class<T> type, T defaultValue) {
            this.name = name;
            this.type = type;
            this.value = defaultValue;
        }

        public String getName() {
            return name;
        }

        public Class<T> getType() {
            return type;
        }

        public T getValue() {
            return value;
        }

        public void setValue(String value) {
            if (type == String.class) {
                this.value = type.cast(value);
            } else if (type == Integer.class) {
                this.value = type.cast(Integer.parseInt(value));
            } else if (type == Boolean.class) {
                this.value = type.cast(Boolean.parseBoolean(value));
            } else {
                throw new IllegalArgumentException("Unsupported type: " + type);
            }
        }
    }

    private static Flag instance = null;
    private final Map<String, Option<?>> options = new HashMap<>();

    private Flag() {
    }

    public static Flag getInstance() {
        if (instance == null) {
            instance = new Flag();
        }
        return instance;
    }

    public void registerOption(Option<?> option) {
        options.put(option.getName(), option);
    }

    public static void init(String[] args) {
        Flag flagInstance = getInstance();

        flagInstance.registerOption(new Option<>("source", String.class, null));
        flagInstance.registerOption(new Option<>("-o", String.class, "a.out"));

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                Option<?> option = flagInstance.options.get(args[i]);
                if (option != null && i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    option.setValue(args[i + 1]);
                    i++;
                }
            } else {
                Option<String> sourceOption = (Option<String>) flagInstance.options.get("source");
                if (sourceOption != null) {
                    sourceOption.setValue(args[i]);
                }
            }
        }
    }

    static public <T> T get(String key) {
        Option<?> option = getInstance().options.get(key);
        if (option == null) {
            return null;
        }
        try {
            return (T) option.getValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Cannot cast the value to the desired type.", e);
        }
    }
}