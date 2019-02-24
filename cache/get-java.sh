#!/bin/sh

# @{title} for @{jdk.name} @{jdk.version}


# JDK version identifiers
JDK_ARCH=`uname -sm`

case "$JDK_ARCH" in
	"Linux armv7l")
		JDK_URL="@{jdk.linux.arm32.url}"
		JDK_SHA256="@{jdk.linux.arm32.sha256}"
	;;
	"Linux aarch64")
		JDK_URL="@{jdk.linux.arm64.url}"
		JDK_SHA256="@{jdk.linux.arm64.sha256}"
	;;
	"Linux i686")
		JDK_URL="@{jdk.linux.x86.url}"
		JDK_SHA256="@{jdk.linux.x86.sha256}"
	;;
	"Linux x86_64")
		JDK_URL="https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz"
		JDK_SHA256="99be79935354f5c0df1ad293620ea36d13f48ec3ea870c838f20c504c9668b57"
	;;
	"Darwin x86_64")
		JDK_URL="https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz"
		JDK_SHA256="f365750d4be6111be8a62feda24e265d97536712bc51783162982b8ad96a70ee"
	;;
	*)
		echo "Architecture not supported: $JDK_ARCH"
		exit 1
	;;
esac


# fetch JDK
JDK_TAR_GZ=`basename $JDK_URL`
if [ ! -f "$JDK_TAR_GZ" ]; then
	echo "Download $JDK_URL"
	curl -fsSL -o "$JDK_TAR_GZ" --retry 5 --cookie "oraclelicense=accept-securebackup-cookie" "$JDK_URL"
fi


# verify archive via SHA-256 checksum
JDK_SHA256_ACTUAL=`openssl dgst -sha256 -hex "$JDK_TAR_GZ" | egrep -o "[a-f0-9]{64}"`
echo "Expected SHA256 checksum: $JDK_SHA256"
echo "Actual SHA256 checksum: $JDK_SHA256_ACTUAL"

if [ "$JDK_SHA256" != "$JDK_SHA256_ACTUAL" ]; then
	echo "ERROR: SHA256 checksum mismatch"
	exit 1
fi


# extract and link only if explicitly requested
if [ "$1" != "install" ]; then
	echo "Download complete: $JDK_TAR_GZ"
	exit 0
fi


echo "Extract $JDK_TAR_GZ"
tar -v -zxf "$JDK_TAR_GZ"

# find java executable
JAVA_EXE=`find "$PWD" -name "java" -type f | grep -v /jre/ | sort | tail -n 1`

# link executable into /usr/local/bin/java
mkdir -p "/usr/local/bin"
ln -s -f "$JAVA_EXE" "/usr/local/bin/java"

# link java home to /usr/local/java
JAVA_BIN=`dirname $JAVA_EXE`
JAVA_HOME=`dirname $JAVA_BIN`
ln -s -f "$JAVA_HOME" "/usr/local/java"

# test
echo "Execute $JAVA_EXE -XshowSettings -version"
"$JAVA_EXE" -XshowSettings -version
