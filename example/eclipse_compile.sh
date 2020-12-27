#!/usr/bin/env sh
java -javaagent:../dist/lombok.jar=ECJ -cp "../lib/eclipse-oxygen/*:../dist/lombok.jar" org.eclipse.jdt.internal.compiler.batch.Main -1.8 -progress */*.java
