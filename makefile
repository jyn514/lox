export CLASSPATH = .$(shell ./sep.sh)libreadline-java.jar
JAVACFLAGS = -Xlint:all -g

all: jlox

jlox: | lox/java/Lox.class
	printf '#!/bin/sh\nCLASSPATH=$(CLASSPATH) java lox.java.Lox "$$@"' > jlox
	chmod +x jlox

lox/java/Lox.class: lox/java/*.java
	javac $(JAVACFLAGS) $^

lox/java/Stmt.java lox/java/Expr.java: tools/gen_expr.py
	chmod +x $<
	$^ lox/java

lox/java/Parser.class: lox/java/Lexer.java lox/java/Token.java lox/java/LoxType.java

lox/java/Compiler.class lox/java/Interpreter.class lox/java/ASTPrinter.class: lox/java/Parser.java

%.class: %.java lox/java/Stmt.java lox/java/Expr.java
	javac $(JAVACFLAGS) $<

.PHONY: test
test: lox/java/Lox.class
	test/test.sh

.PHONY: run
run: lox/java/Lox.class
	./jlox

.PHONY: debug
debug: lox/java/Lox.class
	@echo run \"jdb -attach 8000\" \"stop in lox.java.Compiler.runPass\(\)\"
	java -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n lox.java.Lox

.PHONY: clean
clean:
	find -name '*.class' -delete -o -name __pycache__ -exec rm -rf {} \;

clobber: clean
	rm lox/java/Expr.java lox/java/Stmt.java

include dist/makefile
