java/Lox.class: $(shell find java -name "*.java")
	javac -g -Xlint lox/java/Lox.java

.PHONY: run
run: java/Lox.class
	java lox.java.Lox

.PHONY: debug
debug: java/Lox.class
	@echo run "jdb -attach 8000" "stop in lox.java.lexer.Lexer.scanToken" to debug
	java -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n lox.java.Lox
