#!/bin/sh
FILEBOT_HOME="/Applications/FileBot.app/Contents"
JAVA_HOME="$FILEBOT_HOME/PlugIns/jre-@{jre.version}.jre/Contents/Home"

# select application data folder
APP_DATA="$HOME/.filebot"
LIBRARY_PATH="$FILEBOT_HOME/MacOS"

# start filebot
"$JAVA_HOME/bin/java" -Dapplication.deployment=pkg -Dapple.awt.UIElement=true -Dnet.filebot.AcoustID.fpcalc="$LIBRARY_PATH/fpcalc" @{java.application.options} @{linux.application.options} $JAVA_OPTS $FILEBOT_OPTS -jar "$FILEBOT_HOME/Java/filebot.jar" "$@"
