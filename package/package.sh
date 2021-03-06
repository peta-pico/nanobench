#!/usr/bin/env bash

set -e

cd -- "$(dirname "$0")"
cd ..
VERSION=$(cat pom.xml | grep "<version>" | head -1 | sed -r 's/[^.0-9]//g')
mvn clean install org.apache.tomcat.maven:tomcat7-maven-plugin:2.1:exec-war-only
cp target/nanobench-$VERSION*.jar package/nanobench.jar
cd package
rm -f nanobench-$VERSION.zip
zip -r nanobench-$VERSION.zip nanobench.jar run run-under-windows.bat update update-under-windows.bat
