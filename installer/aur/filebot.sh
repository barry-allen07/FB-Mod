#!/bin/sh
FILEBOT_HOME="/usr/share/filebot"
JAVA_HOME="/usr/lib/jvm/java-8-openjdk"

if [ -z "$HOME" ]; then
	echo '$HOME must be set'
	exit 1
fi

# select application data folder
APP_DATA="$HOME/.config/filebot"
LIBRARY_PATH="$FILEBOT_HOME/lib/$(uname -m)"

$JAVA_HOME/bin/java -Dapplication.deployment=aur -Dapplication.update=skip -Dnet.filebot.Archive.extractor=SevenZipExecutable @{java.application.options} @{linux.application.options} @{linux.desktop.application.options} $JAVA_OPTS $FILEBOT_OPTS -jar "$FILEBOT_HOME/jar/filebot.jar" "$@"
