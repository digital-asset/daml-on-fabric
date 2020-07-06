.PHONY: compile cleanCompile format-check format-all test package it

compile:
	sbt compile

cleanCompile:
	sbt clean cleanFiles
	sbt compile

format-check: compile
	sbt scalafmtCheckAll

format-all:
	sbt scalafmtAll

test: compile
	sbt test

package: compile
	sbt assembly

it: package; ./it.sh
