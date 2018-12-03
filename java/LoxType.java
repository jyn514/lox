package lox.java;

import java.util.Map;
import java.util.HashMap;

enum LoxType {
    BOOL, INT, DOUBLE, STRING, UNDEFINED, VOID, ERROR;

    public static LoxType get(Token.Type type) {
        return types.get(type);
    }

    private static final Map<Token.Type, LoxType> types = new HashMap<>();
    static {
        types.put(Token.Type.BOOL, BOOL);
        types.put(Token.Type.INT, INT);
        types.put(Token.Type.DOUBLE, DOUBLE);
        types.put(Token.Type.STRING, STRING);
        types.put(Token.Type.VOID, VOID);
        types.put(null, UNDEFINED);
    }
}
