#!/bin/bash

# Base Script File (download-dependencies.sh)
# Created: Mon 07 Jan 2013 01:12:34 PM CET
# Version: 1.0
# Author: François-Xavier Thomas <fx.thomas@gmail.com>
#
# This Bash script was developped by François-Xavier Thomas.
# You are free to copy, adapt or modify it.
# If you do so, however, leave my name somewhere in the credits, I'd appreciate it ;)

wget http://aubio.org/pub/aubio-0.3.2.tar.gz
wget http://www.fftw.org/fftw-3.3.3.tar.gz
wget http://www.mega-nerd.com/libsndfile/files/libsndfile-1.0.25.tar.gz
wget http://www.mega-nerd.com/SRC/libsamplerate-0.1.8.tar.gz
wget http://downloads.sourceforge.net/project/flac/flac-src/flac-1.2.1-src/flac-1.2.1.tar.gz
wget http://downloads.xiph.org/releases/ogg/libogg-1.3.0.tar.gz
wget http://downloads.xiph.org/releases/vorbis/libvorbis-1.3.3.tar.gz

for f in *.tar.gz; do
  echo "Extracting $f..."
  tar xf "$f"
  rm "$f"
done

echo "Patching sources..."
cd libsamplerate-0.1.8/; patch -p1 < ../libsamplerate-0.1.8-android.patch; cd ..
cd libsndfile-1.0.25/; patch -p1 < ../libsndfile-1.0.25-android.patch; cd ..
