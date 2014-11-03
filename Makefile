.PHONY: all
.NOTPARALLEL:

all : etc/libdb.txt dist/build.jar

etc/libdb.txt : etc/libdb.raw scripts/updatelibdb
	scripts/updatelibdb

# using "find" here may break systems that have no "find", but it should be way more robust than before
JAVA_SOURCES := $(shell find src/makebuilder/ -name "*.java")

dist/build.jar : Makefile $(JAVA_SOURCES)
	@echo Invoking ant to do the Java build 
	@#Building incrementally sometimes causes build errors after updates. Workaround: Always call 'ant clean'.
	@ant clean
	@ant
	@# yeah ... if ant does not touch it, we have to do it; TODO: obsolete after adding 'ant clean'?
	@touch dist/build.jar
	@echo Clearing make_builder cache ... 
	@rm -f ../.makeBuilderCache


