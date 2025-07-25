#
# Originally this file compiled all the compiler versions. However,
# this created problems when the compilers started to get more error
# checking and balked at the fact they did not get a boot classpath.
# so this is not incremental. The class files are actually stored in
# git now
#
# So for a new compiler, just create a new directory and compile it 
# by hand. 
# Attention: make sure you adjust the package 
# when copying the ClassRef.java from an existing folder
# otherwise you may encounter a "Classes found in the wrong directory" error.
#
# Then build the jar:
#
#  bnd buildx compilerversions.bnd
#
# Make sure to add the class files and the compilerversions.jar to git! 
#

# Example: 
# ~/jdks/jdk-25/Contents/Home/bin/javac --release 25 -cp src ~/git/bnd/biz.aQute.bndlib.tests/compilerversions/src/jdk_25/*.java
# cd ~/git/bnd/biz.aQute.bndlib.tests/compilerversions
# bnd buildx compilerversions.bnd
# or
# java -jar ../../biz.aQute.bnd/generated/biz.aQute.bnd.jar buildx compilerversions.bnd  

# javac -target 1.1 -source 1.2 -cp src src/sun_1_1/*.java
# javac -target 1.2 -source 1.2 -cp src src/sun_1_2/*.java
# javac -target 1.3 -source 1.3 -cp src src/sun_1_3/*.java
# javac -target 1.4 -source 1.4 -cp src src/sun_1_4/*.java
# javac -target 1.5 -source 1.5 -cp src src/sun_1_5/*.java
# javac -target 1.6 -source 1.6 -cp src src/sun_1_6/*.java
# javac -target jsr14 -source 1.5 -cp src src/sun_jsr14/*.java
# javac -target 1.7 -source 1.7 -cp src src/sun_1_7/*.java
# javac -target 1.8 -source 1.8 -cp src src/sun_1_8/*.java
# javac -target 9 -source 9 -cp src src/jdk_9_0/*.java
# javac --release 10 -cp src src/jdk_10_0/*.java
# javac --release 11 -cp src src/jdk_11_0/*.java
# javac --release 12 -cp src src/jdk_12_0/*.java
# javac --release 13 -cp src src/jdk_13_0/*.java
# javac --release 14 -cp src src/jdk_14_0/*.java
# javac --release 15 -cp src src/jdk_15/*.java
# javac --release 16 -cp src src/jdk_16/*.java
# javac --release 17 -cp src src/jdk_17/*.java
# javac --release 18 -cp src src/jdk_18/*.java
# javac --release 19 -cp src src/jdk_19/*.java
# javac --release 20 -cp src src/jdk_20/*.java
# javac --release 21 -cp src src/jdk_21/*.java
# javac --release 22 -cp src src/jdk_22/*.java
# javac --release 23 -cp src src/jdk_23/*.java
# javac --release 24 -cp src src/jdk_24/*.java
# javac --release 25 -cp src src/jdk_25/*.java

# java -jar ../jar/ecj_3.2.2.jar -target 1.1 -source 1.3 -cp src src/eclipse_1_1/*.java
# java -jar ../jar/ecj_3.2.2.jar -target 1.2 -source 1.3 -cp src src/eclipse_1_2/*.java
# java -jar ../jar/ecj_3.2.2.jar -target 1.3 -source 1.3 -cp src src/eclipse_1_3/*.java
# java -jar ../jar/ecj_3.2.2.jar -target 1.4 -source 1.4 -cp src src/eclipse_1_4/*.java
# java -jar ../jar/ecj_3.2.2.jar -target 1.5 -source 1.5 -cp src src/eclipse_1_5/*.java
# java -jar ../jar/ecj_3.2.2.jar -target 1.6 -source 1.6 -cp src src/eclipse_1_6/*.java
# java -jar ../jar/ecj_3.2.2.jar -target jsr14 -source 1.5 -cp src src/eclipse_jsr14/*.java
# java -jar ../jar/ecj_4.2.2.jar -target 1.7 -source 1.7 -cp src src/eclipse_1_7/*.java
# java -jar ../jar/ecj-4.7.1.jar -target 1.8 -source 1.8 -cp src src/eclipse_1_8/*.java
# java -jar ../jar/ecj-4.7.1.50.jar -9 -cp src src/eclipse_9_0/*.java
# java -jar ../jar/ecj-4.7.3a.jar -10 -cp src src/eclipse_10_0/*.java
# java -jar ../jar/ecj-4.10.jar --release 11 -cp src src/eclipse_11_0/*.java
# java -jar ../jar/ecj-4.13.jar --release 12 -cp src src/eclipse_12_0/*.java
# java -jar ../jar/ecj-4.14.jar --release 13 -cp src src/eclipse_13_0/*.java
# java -jar ../jar/ecj-4.16.jar --release 14 -cp src src/eclipse_14_0/*.java


