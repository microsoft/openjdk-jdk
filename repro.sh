#!/bin/bash

OPTS="-XX:+UnlockDiagnosticVMOptions \
-Xbatch \
-server \
-Xmx8g \
-XX:MaxInlineSize=1000 \
-XX:InlineSmallCode=300000 \
-XX:-TieredCompilation \
-XX:CompileCommand=compileonly,*javac*::* \
-XX:CompileCommand=compileonly,*foldStrings*::* \
-XX:CompileCommand=dontinline,*::*foldStrings* \
-XX:CompileCommand=inline,*::clear \
-XX:-UseCompressedOops \
-XX:-UseCompressedClassPointers \
-XX:+TraceReduceAllocationMerges \
-XX:+PrintCompilation \
-XX:+PrintInlining"

OPTS="-XX:-UseCompressedOops -XX:+TraceReduceAllocationMerges"

# EXECUTE ONLY THE TEST
# make test CONF=release TEST=test/langtools/tools/javac/annotations/typeAnnotations/api/ZZZArrayCreationTree.java JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"


# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/tools/javac/annotations/ JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"

# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/:repro JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"


# EXECUTE WHOLE GROUP
  make test CONF=release TEST=test/langtools/:tier1 JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"
