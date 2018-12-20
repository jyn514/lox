CLASSPATH = libreadline-java.jar$(shell ./sep.sh)$(BUILD)
JAVAFLAGS = -cp $(CLASSPATH)
JAVACFLAGS = $(JAVAFLAGS) -Xlint:all -g -target 9 -source 9 -d $(BUILD)
BUILD = build
MAIN = $(BUILD)/lox/java/Lox.class
MAINJ = lox.java.Lox
GENSRC = lox/java/Stmt.java lox/java/Expr.java

.PHONY: all
all: jlox

jlox: | $(MAIN)
	printf 'cd "$$(dirname "$$0")" && CLASSPATH=libreadline-java.jar:build java lox.java.Lox "$$@"' > jlox
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
	$(RM) -r $(BUILD) a.out

.PHONY: clobber
clobber: clean
	rm -f $(GENSRC) jlox tools/__pycache__

$(BUILD):
	mkdir -p $@

include dist/makefile
