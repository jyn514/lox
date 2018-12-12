JAVAFLAGS = -cp .:libreadline-java.jar
JAVACFLAGS = $(JAVAFLAGS) -Xlint:all -g

all: jlox

jlox: | java/Lox.class
	printf '#!/bin/sh\njava lox.java.Lox "$$@"' > jlox
	chmod +x jlox

java/Lox.class: $(subst .java,.class,$(shell find java -name "*.java"))

java/Stmt.java java/Expr.java: tools/gen_expr.py
	$^ java

java/Parser.class: java/Lexer.java java/Token.java java/LoxType.java

java/Compiler.class java/Interpreter.class java/ASTPrinter.class: java/Parser.java

%.class: %.java java/Stmt.java java/Expr.java
	javac $(JAVACFLAGS) $<

.PHONY: test
test: java/Lox.class
	test/test.sh

.PHONY: run
run: java/Lox.class
	java $(JAVAFLAGS) lox.java.Lox

.PHONY: debug
debug: java/Lox.class
	@echo run \"jdb -attach 8000\" \"stop in lox.java.Compiler.runPass\(\)\"
	java $(JAVAFLAGS) -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n lox.java.Lox

.PHONY: clean
clean:
	find -name '*.class' -delete -o -name __pycache__ -exec rm -rf {} \;

clobber: clean
	rm java/Expr.java java/Stmt.java

include dist/makefile
