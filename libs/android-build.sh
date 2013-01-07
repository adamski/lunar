#!/bin/bash

# Taken and improved from :
# http://cmumobileapps.com/2011/08/31/compiling-open-source-libraries-with-android-ndk-part-2

# The target architecture. Here we're compiling for ARM using GCC 4.7 and
# platform version 14.
TARGET_ARCH="arm"
TARGET_HOST="arm-eabi"
TARGET_TOOLCHAIN="arm-linux-androideabi"
TARGET_GCC_VERSION="4.7"
TARGET_NDK_VERSION="14"

# Add the toolchain to the path to be able to use the ARM compiler.
export PATH="$ANDROID_NDK/toolchains/$TARGET_TOOLCHAIN-$TARGET_GCC_VERSION/prebuilt/$ANDROID_NDK_HOST/bin:$PATH"

# Where to find the platform-specific files?
export SYS_ROOT="$ANDROID_NDK/platforms/android-$TARGET_NDK_VERSION/arch-$TARGET_ARCH/"

# Where to install the cross-compiled packages?
# When running `make install`, all the files (executables, libraries,
# documentation,...) will be in this directory.
export INSTALL_DIR="$(pwd)/../src/main/native"

# Executable names for the compilation toolchain
export CC="$TARGET_TOOLCHAIN-gcc --sysroot=$SYS_ROOT"
export CXX="$TARGET_TOOLCHAIN-g++ --sysroot=$SYS_ROOT"
export CPP="$TARGET_TOOLCHAIN-cpp --sysroot=$SYS_ROOT"
export LD="$TARGET_TOOLCHAIN-ld"
export AR="$TARGET_TOOLCHAIN-ar"
export RANLIB="$TARGET_TOOLCHAIN-ranlib"
export STRIP="$TARGET_TOOLCHAIN-strip"

# Add the STLPort library to the search path (needed for C++ files)
export CFLAGS="-I$INSTALL_DIR/include/"
export CXXFLAGS="-I$ANDROID_NDK/sources/cxx-stl/stlport/stlport/"
export LDFLAGS="-L$INSTALL_DIR/lib/ -L$ANDROID_NDK/sources/cxx-stl/stlport/libs/armeabi/ -lstlport_static"

# Where will pkg-config look for information about installed packages?
export PKG_CONFIG_SYSROOT_DIR="$SYS_ROOT"
export PKG_CONFIG_LIBDIR="$INSTALL_DIR/lib/pkgconfig/"

#################
# Configuration #
#################

# If it doesn't exist, create the installation directory.
if [[ ! -e "$INSTALL_DIR" ]]; then
  echo "Creating installation directory [$INSTALL_DIR]"
  mkdir -p $INSTALL_DIR || exit 3
fi

###############
# Compilation #
###############

build() {
  # Change directory to the package directory (in argument)
  echo "Building $1"
  pushd $1

  # Clean things up (optional)
  make clean 1>/dev/null 2>&1

  # Configure the package
  ./configure --host=$TARGET_HOST LIBS="-lc -lgcc" --prefix=$INSTALL_DIR || exit 1

  # Compile
  make || exit 1

  # Install in $INSTALL_DIR
  make install || exit 1

  # Return to the original directory
  popd
}

# Build all things
build libogg-1.3.0
build libvorbis-1.3.3
build flac-1.2.1
build libsndfile-1.0.25
build libsamplerate-0.1.8
build fftw-3.3.3
build aubio-0.3.2
