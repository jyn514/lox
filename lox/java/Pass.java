package lox.java;

abstract class Pass<Accept, Return> {
  protected final Accept input;

  Pass(Accept input) {
    this.input = input;
  }

  abstract Return runPass();
}
