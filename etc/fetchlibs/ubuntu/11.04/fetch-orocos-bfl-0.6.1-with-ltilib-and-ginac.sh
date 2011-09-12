#!/bin/bash

set -o verbose #echo on
set -e

mkdir -p temp
cd temp
sudo apt-get install build-essential libginac-dev libx11-dev cmake libxt-dev automake gfortran libf2c2-dev libv4l-dev

wget http://downloads.sourceforge.net/project/ltilib/LTI-Lib-1/1.9.16/100410_ltilib-1.9.16.tar.bz2?use_mirror=kent
tar -xjf 100410_ltilib-1.9.16.tar.bz2?use_mirror=kent
cd ltilib
find -type f | xargs sed -i 's%include <linux/videodev.h>%include <libv4l1-videodev.h>%'
cd linux
make -f Makefile.cvs
./configure --disable-debug --without-gtk --disable-gtk 
make -j2 EXTRAINCLUDEPATH=-I/usr/src/linux-headers-`uname -r`/include/media
sudo make install
cd ../..

wget http://people.mech.kuleuven.be/~tdelaet/bfl_tar/orocos-bfl-0.6.1-src.tar.bz2
tar -xjf orocos-bfl-0.6.1-src.tar.bz2
cd orocos-bfl-0.6.1-src/config
sed -i 's/OFF/ON/g' FindGINAC.cmake
sed -i 's/MATRIX_INSTALL \/usr/MATRIX_INSTALL \/usr\/local/g' FindMATRIX.cmake
sed -i 's/RNG_INSTALL \/usr/RNG_INSTALL \/usr\/local/g' FindRNG.cmake
cd ..
CXXFLAGS=' -O2 ' ./configure
make -j2
sudo make install
