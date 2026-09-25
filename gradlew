#!/usr/bin/env bash

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS="-Xmx64m"

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn ( ) {
    echo "$*"
}

die ( ) {
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
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Split up the JVM_OPTS And GRADLE_OPTS values into an array, preserving the split tokens.
splitJvmOpts() {
    if [ -n "$1" ] ; then
        printf "%s\n" "$1"
    fi
}

# Split up the JVM_OPTS And GRADLE_OPTS values into an array, preserving the split tokens.
split() {
    if [ -n "$1" ] ; then
        IFS=$'\n' read -d "" -ra arr <<< "$1"
        for elem in "${arr[@]}" ; do
            echo "$elem"
        done
    fi
}

# Prepare the execution command.
cmd=()

# Add the JVM options.
for opt in $(splitJvmOpts "$DEFAULT_JVM_OPTS" "$JAVA_OPTS" "$GRADLE_OPTS") ; do
    cmd+=( "$opt" )
done

# Add the application JAR and the main class.
cmd+=( "-classpath" )
cmd+=( "$CLASSPATH" )
cmd+=( "org.gradle.wrapper.GradleWrapperMain" )

# Add user arguments to the end.
for a in "$@" ; do
    cmd+=( "$a" )
done

exec "$JAVACMD" "${cmd[@]}"
exit $?