javac = javac -Xlint -g

all: jlox

jlox: | java/Lox.class
	printf '#!/bin/sh\njava lox.java.Lox "$$@"' > jlox

java/Lox.class: $(subst .java,.class,$(shell find java -name "*.java"))

%.class: %.java
	$(javac) $<

.PHONY: run
run: java/Lox.class
	java lox.java.Lox

.PHONY: debug
debug: java/Lox.class
	@echo run \"jdb -attach 8000\" \"stop in lox.java.Parser.parseExpr\"
	java -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n lox.java.Lox

.PHONY: clean
clean:
	find -name '*.class' -delete -o -name __pycache__ -exec rm -rf {} \;
