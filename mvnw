#!/usr/bin/env bash
export MAVEN_OPTS=""
BASEDIR="$(cd "$(dirname "$0")" && pwd)"
export MAVEN_HOME="${MAVEN_HOME:-}"
if [ -z "$MAVEN_HOME" ]; then
  MAVEN_HOME="$(cd "$BASEDIR/../.mvn/wrapper" && pwd)"
fi
exec "$MAVEN_HOME/bin/maven" "$@"