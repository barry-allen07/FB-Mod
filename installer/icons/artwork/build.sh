#!/bin/sh

LOGO='artwork_4096x4096.png'

for SIZE in 71x71 150x150 192x192 300x300 1440x2160 2160x2160; do
	convert -verbose $LOGO -resize $SIZE -gravity center -background transparent -extent $SIZE "artwork_$SIZE.png"
done
