/#!/usr/bin/env bash
# Wrapper around IntelliJ's bundled Maven.
# Usage: ./build.sh [maven goals]   e.g.  ./build.sh compile
#                                         ./build.sh clean package -DskipTests
MVN="/d/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn.cmd"
REPO="C:/Users/Tharwani/.m2/repository"

"$MVN" -f "$(dirname "$0")/pom.xml" -Dmaven.repo.local="$REPO" "$@"
