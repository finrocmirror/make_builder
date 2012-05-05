#!/bin/bash

set -o verbose #echo on

mkdir -p temp
cd temp
ARCH=`arch`
wget http://newton-dynamics.googlecode.com/files/newton-dynamics-2.35.rar
sudo apt-get install unrar
unrar x newton-dynamics-2.35.rar newton-dynamics-2.35/coreLibrary_200
mkdir -p newton-dynamics-2.35/packages/linux32
mkdir -p newton-dynamics-2.35/packages/linux64
cd newton-dynamics-2.35/coreLibrary_200/projets
if [ $ARCH == 'x86_64' ]; then
  cd linux64
else
  cd linux32
fi
make
sudo cp libNewton.so /usr/local/lib
sudo cp libNewton.a /usr/local/lib
sudo cp ../../source/newton/Newton.h /usr/local/include


