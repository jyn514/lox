.PHONY: jar
jar: dist/lox.jar

dist/Manifest:
	echo Main-Class: lox.java.Lox > $@

sources = Lox Token Lexer Parser Expr Stmt Annotate Compiler Assembler
dist/lox.jar: dist/Manifest $(addprefix java/,$(addsuffix .class,$(sources))) libreadline-java.jar
	mkdir -p dist/tmp
	cd dist/tmp && unzip -uo ../../libreadline-java.jar
	cd dist/tmp && jar -cvfm ../lox.jar ../Manifest $$(find ../../lox/java -name *.class) org
