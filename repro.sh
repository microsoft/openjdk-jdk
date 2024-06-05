#!/bin/bash

BASE="/Users/cesar/wf/"

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

for i in `seq 1 100` ; do
	echo "Execution $i"

#	# EXECUTE ONLY THE TEST
#	make test CONF=release TEST=test/langtools/tools/javac/annotations/typeAnnotations/api/ZZZArrayCreationTree.java JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
#	cp ${BASE}/jdk2/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tools_javac_annotations_typeAnnotations_api_ZZZArrayCreationTree_java/tools/javac/annotations/typeAnnotations/api/ZZZArrayCreationTree.jtr "${BASE}/logs/ZZZArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"


 	# EXECUTE ONLY A SUBSET OF TESTS
 	# make test CONF=release TEST=test/langtools/tools/javac/annotations/ JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
 	# cp ${BASE}/jdk2/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tools_javac_annotations_typeAnnotations_api/tools/javac/annotations/typeAnnotations/api/ZZZArrayCreationTree.jtr "${BASE}/logs/ZZZArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"

 	# EXECUTE ONLY A SUBSET OF TESTS
 	make test CONF=release TEST=test/langtools/:repro JTREG="OPTIONS=--max-pool-size=5;JOBS=5;TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
 	cp ${BASE}/jdk2/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tools_javac_repro/ZZZArrayCreationTree.jtr "${BASE}/logs/ZZZArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"


	# EXECUTE WHOLE GROUP
#	make test CONF=release TEST=test/langtools/:tier1 JTREG="TIMEOUT_FACTOR=100;JAVA_OPTIONS=${OPTS}"
#	cp ${BASE}/jdk2/build/macosx-aarch64-server-release/test-support/jtreg_test_langtools_tier1/tools/javac/annotations/typeAnnotations/api/ZZZArrayCreationTree.jtr "${BASE}/logs/ZZZArrayCreationTree.jfr-$(date +'%Y%m%d-%H%M%S').txt"

done
