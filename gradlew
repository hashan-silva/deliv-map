#!/bin/sh

##############################################################################
# Gradle start up script for POSIX environments.
##############################################################################

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "$(dirname "$0")"; pwd -P)

DEFAULT_JVM_OPTS="-Dfile.encoding=UTF-8"

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
  echo "$*"
}

die () {
  echo
  echo "$*"
  echo
  exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
  CYGWIN* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MINGW* | MSYS* | MSYS_NT* | MINGW64* ) msys=true ;;
  NONSTOP* ) nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
  if [ ! -x "$JAVACMD" ] ; then
    die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
  fi
else
  JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] ; then
  die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Increase the maximum file descriptors if we can.
if ! $cygwin && ! $darwin && ! $nonstop ; then
  MAX_FD_LIMIT=$(ulimit -H -n 2>/dev/null)
  if [ $? -eq 0 ]; then
    if [ "$MAX_FD" = "maximum" ] || [ "$MAX_FD" = "max" ]; then
      MAX_FD="$MAX_FD_LIMIT"
    fi
    ulimit -n $MAX_FD 2>/dev/null
  else
    warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
  fi
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if $cygwin || $msys ; then
  APP_HOME=$(cygpath --unix "$APP_HOME")
  CLASSPATH=$(cygpath --path --unix "$CLASSPATH")
  JAVACMD=$(cygpath --unix "$JAVACMD")
fi

# Collect all arguments for the java command.
GRADLE_OPTS="$DEFAULT_JVM_OPTS $GRADLE_OPTS"

exec "$JAVACMD" $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
