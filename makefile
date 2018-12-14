CLASSPATH = libreadline-java.jar$(shell ./sep.sh)$(BUILD)
BUILD = build
JAVAFLAGS = -cp $(CLASSPATH)
JAVACFLAGS = $(JAVAFLAGS) -Xlint:all -g -target 9 -source 9 -d $(BUILD)
MAIN = $(BUILD)/lox/java/Lox.class
MAINJ = lox.java.Lox
GENSRC = lox/java/Stmt.java lox/java/Expr.java

.PHONY: all
all: jlox

jlox: | $(MAIN)
	printf '#!/bin/sh\nCLASSPATH=$(CLASSPATH) java $(MAINJ) "$$@"' > jlox
	chmod +x jlox

$(MAIN): lox/java/*.java $(GENSRC) | $(BUILD)
	javac $(JAVACFLAGS) $^

$(GENSRC): tools/gen_expr.py
	chmod +x $<
	$^ lox/java

.PHONY: test
test: jlox
	test/test.sh

.PHONY: run
run: jlox
	./jlox

.PHONY: debug
debug: $(MAIN)
	@echo run \"jdb -attach 8000\" \"stop in lox.java.Compiler.runPass\(\)\"
	java $(JAVAFLAGS) -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n $(MAINJ)

.PHONY: clean
clean:
	$(RM) -r $(BUILD)

.PHONY: clobber
clobber: clean
	rm -f $(GENSRC) jlox tools/__pycache__

$(BUILD):
	mkdir -p $@

include dist/makefile
