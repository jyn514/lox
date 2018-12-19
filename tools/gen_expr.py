#!/usr/bin/env python3

import os.path
from re import sub

ALL_TYPES = [
    # expressions
    ("Expr", {
        "Unary": ["final Token operator", "final Expr right"],
        "Binary": ["final Expr left", "final Token operator", "final Expr right"],
        "Logical": ["final Expr left", "final Token operator", "final Expr right"],
        "Grouping": ["final Expr expression"],
        "Literal": ["final Object value"],
        "Symbol": ["final Token name", "int arity"],
        "Assign": ["Expr.Symbol lvalue", "final Token equal", "final Expr rvalue"],
        "Call": ["Expr.Symbol callee", "final Token paren", "final List<Expr> arguments"],
    }, ["LoxType type"]),
    # statements
    ("Stmt", {
        "Expression": ["final Expr expression"],
        "Print": ["final Expr expression"],
        "Var": ["Expr.Symbol identifier", "final Expr.Assign equals"],
        "Block": ["final List<Stmt> statements"],
        "If": ["final Expr condition", "final Stmt then", "final Stmt otherwise"],
        "While": ["final Expr condition", "final Stmt body"],
        "LoopControl": ["final Token keyword"],
        "Function": ["Expr.Symbol identifier", "final List<Expr.Symbol> arguments", "final Stmt.Block body"],
        "Return": ["final Token keyword", "final Expr value"]
    }, [])
]


def generate_visitors(basename, types):
    return ("\n  interface Visitor<T> {\n" +
            "\n".join("    T visit{1}({0} {2});\n".format(
                typename, basename, basename.lower()) for typename in types) +
            "\n  }")


def generate_constructor(classname, fields):
    return """
    {0}({1}) {{
{2}
    }}
""".format(classname, ', '.join(sub('^final ', '', f) for f in fields),
           '\n'.join('      this.' + s.split()[-1] + ' = ' + s.split()[-1] + ';'
                     for s in fields))


def generate_class(basename, classname, fields, abstract_fields):
    return """\n  static class {1} extends {0} {{
{2}
{3}
    public <T> T accept(Visitor<T> visitor) {{
      return visitor.visit{0}(this);
    }}
    public String toString() {{
      return printer.print(this);
    }}
  }}\n""".format(basename, classname,
                 '\n'.join("    " + s + ";" for s in fields),
                 generate_constructor(classname, fields + abstract_fields))


def generate(directory, basename, types, abstract_fields):
    with open(os.path.join(directory, basename + '.java'), 'w') as output:
        base = """package lox.java;

import java.util.List;

abstract class {0} {{
  private static final ASTPrinter printer = new ASTPrinter();
{1}
  abstract <T> T accept(Visitor<T> visitor);
""".format(basename, '\n'.join('  ' + field + ';' for field in abstract_fields))
        output.write(base)
        for classname, fields in types.items():
            output.write(generate_class(basename, classname, fields, abstract_fields))
        output.write(generate_visitors(basename, types))
        output.write('\n}')


def main():
    from argparse import ArgumentParser
    parser = ArgumentParser()
    parser.add_argument('directory')
    args = parser.parse_args()
    for types in ALL_TYPES:
        generate(args.directory, *types)


if __name__ == '__main__':
    main()
