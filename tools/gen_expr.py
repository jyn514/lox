#!/usr/bin/env python3

import os.path

ALL_TYPES = [
    # expressions
    ("Expr", {
        "Unary": ["Token operator", "Expr right"],
        "Binary": ["Expr left", "Token operator", "Expr right"],
        "Logical": ["Expr left", "Token operator", "Expr right"],
        "Grouping": ["Expr expression"],
        "Literal": ["Object value"],
        "Symbol": ["Token name"],
        "Assign": ["Expr lvalue", "Token equal", "Expr rvalue"],
        "Call": ["Expr callee", "Token paren", "List<Expr> arguments"],
    }, ["LoxType type"]),
    # statements
    ("Stmt", {
        "Expression": ["Expr expression"],
        "Print": ["Expr expression"],
        "Var": ["Expr.Symbol identifier", "Expr expression"],
        "Block": ["List<Stmt> statements"],
        "If": ["Expr condition", "Stmt then", "Stmt otherwise"],
        "While": ["Expr condition", "Stmt body"],
        "LoopControl": ["Token keyword"],
        "Function": ["Expr.Symbol identifier", "List<Expr.Symbol> arguments", "Stmt.Block body"],
    }, [])
]

def generate_visitors(basename, types):
    return ("\n    interface Visitor<T> {\n" +
            "\n".join("        T visit{1}({0} {2});".format(
                typename, basename, basename.lower()) for typename in types) +
            "\n    }")

def generate_constructor(classname, fields):
    ret = """
        {0}({1}) {{
{2}
        }}
""".format(classname, ', '.join(fields),
           '\n'.join('            this.' + s.split()[1] + ' = ' + s.split()[1] + ';'
              for s in fields))
    for i in range(1, len(fields)):
        ret += """
        {0}({1}) {{
            this({2});
        }}
""".format(classname, ', '.join(fields[:i]),
            ', '.join(s.split()[1] for s in fields[:i] + ["null null"]))
    return ret

def generate_class(basename, classname, fields, abstract_fields):
    return """\n    static class {1} extends {0} {{
{2}
{3}
        public <T> T accept(Visitor<T> visitor) {{
            return visitor.visit{0}(this);
        }}
    }}""".format(basename, classname,
                '\n'.join("        final " + s + ";" for s in fields),
                generate_constructor(classname, fields + abstract_fields))

def generate(directory, basename, types, abstract_fields):
    with open(os.path.join(directory, basename + '.java'), 'w') as output:
        base = """package lox.java;

import java.util.List;

abstract class {0} {{
    {1}
    abstract <T> T accept(Visitor<T> visitor);
""".format(basename, '\n'.join(field + ';' for field in abstract_fields))
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
