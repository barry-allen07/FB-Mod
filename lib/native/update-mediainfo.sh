#!/bin/sh -xu

MEDIAINFO_VERSION="18.05"
ZEN_VERSION="0.4.37"


# Download and extract archives
mkdir -p "Staging" && cd "Staging"

curl -O "https://mediaarea.net/download/binary/libmediainfo0/${MEDIAINFO_VERSION}/MediaInfo_DLL_${MEDIAINFO_VERSION}_Mac_i386+x86_64.tar.bz2"
curl -O "https://mediaarea.net/download/binary/libmediainfo0/${MEDIAINFO_VERSION}/MediaInfo_DLL_${MEDIAINFO_VERSION}_Windows_x64_WithoutInstaller.7z"
curl -O "https://mediaarea.net/download/binary/libmediainfo0/${MEDIAINFO_VERSION}/MediaInfo_DLL_${MEDIAINFO_VERSION}_Windows_i386_WithoutInstaller.7z"
curl -O "https://mediaarea.net/download/binary/libmediainfo0/${MEDIAINFO_VERSION}/libmediainfo0_${MEDIAINFO_VERSION}-1_amd64.Debian_8.0.deb"
curl -O "https://mediaarea.net/download/binary/libmediainfo0/${MEDIAINFO_VERSION}/libmediainfo0_${MEDIAINFO_VERSION}-1_i386.Debian_8.0.deb"
curl -O "https://mediaarea.net/download/binary/libzen0/${ZEN_VERSION}/libzen0_${ZEN_VERSION}-1_amd64.Debian_8.0.deb"
curl -O "https://mediaarea.net/download/binary/libzen0/${ZEN_VERSION}/libzen0_${ZEN_VERSION}-1_i386.Debian_8.0.deb"

for FILE in *.tar.* *.deb *.7z
	do mkdir -p "${FILE%.*}" && 7z x "$FILE" -aoa -o"${FILE%.*}"
done

for FILE in */*.tar
	do mkdir -p "${FILE%.*}" && 7z x "$FILE" -aoa -o"${FILE%.*}"
done


# Copy native libraries into repository
cd ..

cp Staging/*Windows*x64*/MediaInfo.dll win32-x64/MediaInfo.dll
cp Staging/*Windows*i386*/MediaInfo.dll win32-x86/MediaInfo.dll
cp Staging/*/data/usr/lib/x86_64-linux-gnu/libmediainfo.so.0.0.0 linux-amd64/libmediainfo.so 
cp Staging/*/data/usr/lib/i386-linux-gnu/libmediainfo.so.0.0.0 linux-i686/libmediainfo.so
cp Staging/*/data/usr/lib/x86_64-linux-gnu/libzen.so.0.0.0 linux-amd64/libzen.so
cp Staging/*/data/usr/lib/i386-linux-gnu/libzen.so.0.0.0 linux-i686/libzen.so

# Strip x86 and PPC native code from universal library
ditto --arch x86_64 Staging/*Mac*x86_64*/*/*/libmediainfo.0.dylib mac-x86_64/libmediainfo.dylib

rm -r Staging
