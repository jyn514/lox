package lox.java;

import java.util.Map;
import java.util.HashMap;

enum LoxType {
  BOOL, INT, DOUBLE, STRING, UNDEFINED, NULL, ERROR;

  private static final int END_OF_NUMBERS = STRING.ordinal();

  public static LoxType get(Token.Type type) {
    return types.get(type);
  }

  public static LoxType assertPromotable(LoxType left, LoxType right, LoxType max)
      throws TypeError {
      LoxType result = compareTo(left, right);
      if (max != null)
        result = compareTo(result, max);
      return result;
  }

  /*
   * Comparing ERROR or UNDEFINED will throw a type error.
   * Comparing incompatible types (e.g. STRING, INT) will throw a type error.
   * Comparing types which can be upcast (e.g. BOOL, INT)
   *   will return the least class compatible with both (in this case, INT)
   * Comparing types which can be downcast (e.g. DOUBLE, INT) will throw a type error.
   */
  public static LoxType compareTo(LoxType left, LoxType right) throws TypeError {
    if (left == null || left == ERROR || left == UNDEFINED) throw new TypeError();
    if (left == right) return left;

    if (left.ordinal() < END_OF_NUMBERS && right.ordinal() < END_OF_NUMBERS) {
      int diff = left.compareTo(right);
      return diff >= 0 ? left : LoxType.values()[left.ordinal() - diff];
    }

    throw new TypeError();
  }

  private static final Map<Token.Type, LoxType> types = new HashMap<>();
  static {
    types.put(Token.Type.BOOL, BOOL);
    types.put(Token.Type.INT, INT);
    types.put(Token.Type.DOUBLE, DOUBLE);
    types.put(Token.Type.STRING, STRING);
    types.put(Token.Type.VOID, NULL);
  }

  @SuppressWarnings("serial")
  public static class TypeError extends Exception {}
}
