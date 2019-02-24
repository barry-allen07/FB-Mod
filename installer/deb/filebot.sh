#!/bin/sh
FILEBOT_HOME="/usr/share/filebot"

if [ -z "$HOME" ]; then
	echo '$HOME must be set'
	exit 1
fi

# select application data folder
APP_DATA="$HOME/.filebot"
LIBRARY_PATH="$FILEBOT_HOME/lib"
MODULE_PATH="$FILEBOT_HOME/mod"

java -Dapplication.deployment=deb --module-path "$MODULE_PATH" --add-modules ALL-MODULE-PATH -Dnet.filebot.AcoustID.fpcalc="$LIBRARY_PATH/fpcalc" @{java.application.options} @{linux.application.options} @{linux.desktop.application.options} $JAVA_OPTS $FILEBOT_OPTS -jar "$FILEBOT_HOME/jar/filebot.jar" "$@"
