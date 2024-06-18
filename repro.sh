#!/bin/bash

OPTS="\
-XX:+UnlockDiagnosticVMOptions \
-XX:-UseCompressedOops \
-Xbatch \
-XX:-TraceReduceAllocationMerges \
-XX:CompileCommand=inline,*JavacParser*::*foldIfNeeded* \
-XX:CompileCommand=inline,*JavacParser*::*merge* \
-XX:CompileCommand=inline,*ListBuffer*::*prepend* \
-XX:CompileCommand=inline,*ListBuffer*::*clear* \
-XX:CompileCommand=inline,*ListBuffer*::*size* \
-XX:CompileCommand=inline,*ListBuffer*::*first* \
-XX:CompileCommand=inline,*::*last* \
-XX:CompileCommand=inline,*ListBuffer*::*isEmpty* \
-XX:CompileCommand=inline,*::*stream* \
-XX:CompileCommand=inline,*::*getTag* \
-XX:CompileCommand=inline,*::*fromCharacteristics* \
-XX:CompileCommand=inline,*ListBuffer*::*init* \
-XX:CompileCommand=inline,*::*map* \
-XX:CompileCommand=inline,*::*collect* \
-XX:CompileCommand=inline,*::*isParallel* \
-XX:CompileCommand=inline,*::*isOrdered* \
-XX:CompileCommand=inline,*::*sourceSpliterator* \
-XX:CompileCommand=inline,*::*evaluate* \
-XX:CompileCommand=inline,*::getStartPosition* \
-XX:CompileCommand=inline,*TreeInfo*::* \
-XX:CompileCommand=inline,*::*spliterator* \
-XX:CompileCommand=CompileThresholdScaling,*::foldIfNeeded*,0.10 \
-XX:CompileCommand=CompileThresholdScaling,*::foldStrings*,0.10 \
-XX:CompileCommand=TraceEscapeAnalysis,*::foldStrings* \
-XX:CompileCommand=option,*::fold*,PrintMethodData \
-XX:+PrintCompilation \
-XX:+CountCompiledCalls \
-XX:+PrintInlining"

# EXECUTE ONLY THE TEST
  make test CONF=release TEST=test/langtools/tools/javac/annotations/typeAnnotations/ZAPI/ZZZArrayCreationTree.java JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"

# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/tools/javac/annotations/ JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"

# EXECUTE ONLY A SUBSET OF TESTS
# make test CONF=release TEST=test/langtools/:repro JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"

# EXECUTE WHOLE GROUP
#  make test CONF=release TEST=test/langtools/:tier1 JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS};REPEAT_COUNT=50"

cp /tmp/graph* .
cp build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tools_javac_annotations_typeAnnotations_ZAPI_ZZZArrayCreationTree_java/tools/javac/annotations/typeAnnotations/ZAPI/ZZZArrayCreationTree.jtr .
cp /build/macosx-aarch64-server-release/jdk/modules/jdk.compiler/com/sun/tools/javac/parser/JavacParser.class .