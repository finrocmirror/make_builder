#!/bin/bash

set -o verbose #echo on

mkdir -p temp
cd temp
ARCH=`arch`
if [ $ARCH == 'x86_64' ]; then
  wget http://www.newtondynamics.com/downloads/NewtonLinux-64-2.08.tar.gz
  sudo cat NewtonLinux-64-2.08.tar.gz | sudo tar -C /usr/local/ -xz 
else
  wget http://www.newtondynamics.com/downloads/NewtonLinux-32-2.08.tar.gz
  sudo cat NewtonLinux-32-2.08.tar.gz | sudo tar -C /usr/local/ -xz 
fi
sudo ln -f -s /usr/local/newtonSDK/sdk/Newton.h /usr/local/include/Newton.h
sudo ln -f -s /usr/local/newtonSDK/sdk/libNewton.a /usr/local/lib/libNewton.a
sudo ln -f -s /usr/local/newtonSDK/sdk/libNewton.so /usr/local/lib/libNewton.so

