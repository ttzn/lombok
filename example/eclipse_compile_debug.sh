#!/usr/bin/env sh

# ../lib/eclipse-oxygen/org.eclipse.jdt-org.eclipse.jdt.core.jar 
# java -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -javaagent:../dist/lombok.jar=ECJ -jar  ecj.jar -7 -cp ../dist/lombok.jar */*.java
java -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -javaagent:../dist/lombok.jar=ECJ -cp "../lib/eclipse-oxygen/*:../dist/lombok.jar" org.eclipse.jdt.internal.compiler.batch.Main -1.8 -progress */*.java
# java -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -javaagent:../dist/lombok.jar=ECJ -cp "../lib/ecj8/*:../dist/lombok.jar" org.eclipse.jdt.internal.compiler.batch.Main -7 */*.java

