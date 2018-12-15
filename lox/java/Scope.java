package lox.java;

import java.util.Map;
import java.util.HashMap;

class Scope<T> {
  private final Map<String, T> map = new HashMap<>();
  private final Scope<T> previous;

  public Scope() {
    this(null);
  }

  public Scope(Scope<T> previous) {
    this.previous = previous;
  }

  public T getImmediate(String key) {
    return map.get(key);
  }

  public T get(String key) {
    T result = null;
    for (Scope<T> current = this; current != null; current = current.previous) {
      if ((result = current.getImmediate(key)) != null) {
        break;
      }
    }
    return result;
  }

  public void put(String key, T value) {
    map.put(key, value);
  }
}
