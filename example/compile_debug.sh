#!/usr/bin/env sh
javac -J-Xdebug -J-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -cp ../dist/lombok.jar */*.java
