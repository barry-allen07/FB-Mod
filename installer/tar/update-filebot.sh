#!/bin/sh
PRG="$0"

# resolve relative symlinks
while [ -h "$PRG" ]; do
	ls=`ls -ld "$PRG"`
	link=`expr "$ls" : '.*-> \(.*\)$'`
	if expr "$link" : '/.*' > /dev/null; then
		PRG="$link"
	else
		PRG="`dirname "$PRG"`/$link"
	fi
done

# make it fully qualified
PRG_DIR=`dirname "$PRG"`
FILEBOT_HOME=`cd "$PRG_DIR" && pwd`


# update core application files
PACKAGE_NAME="CHANGES.tar.xz"
PACKAGE_FILE="$FILEBOT_HOME/data/$PACKAGE_NAME"
PACKAGE_URL="@{link.release.index}/HEAD/$PACKAGE_NAME"

SIGNATURE_FILE="$PACKAGE_FILE.asc"
SIGNATURE_URL="$PACKAGE_URL.asc"


# use *.asc file to check for updates
echo "Update $PACKAGE_FILE"
HTTP_CODE=`curl -L -o "$SIGNATURE_FILE" -z "$SIGNATURE_FILE" --retry 5 "$SIGNATURE_URL" -w "%{http_code}"`

if [ $HTTP_CODE -ne 200 ]; then
	echo "$HTTP_CODE NO UPDATE"
	exit 1
fi

curl -L -o "$PACKAGE_FILE" -z "$PACKAGE_FILE" --retry 5 "$PACKAGE_URL"


# verify signature and extract tar
if gpgv --homedir "$FILEBOT_HOME" --keyring "maintainer.gpg" "$SIGNATURE_FILE" "$PACKAGE_FILE"; then
	tar -xvf "$PACKAGE_FILE"
fi
