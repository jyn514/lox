JAR = dist/lox.jar

.PHONY: jar
jar: $(JAR)

.PHONY: run-jar
run-jar: $(JAR)
	java -jar $(JAR)

dist/Manifest:
	echo Main-Class: $(MAINJ) > $@

$(JAR): dist/Manifest $(MAIN) libreadline-java.jar | $(BUILD)
	jar -cvfm $(JAR) dist/Manifest
	jar -uvf $(JAR) -C $(BUILD) lox
	unzip -d $(BUILD) -uo libreadline-java.jar
	jar -uvf $(JAR) -C $(BUILD) org

distclean: clobber
	rm -rf $(BUILD) dist/Manifest $(JAR)
