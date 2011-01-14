#!/bin/bash

set -o verbose #echo on
set -e

mkdir -p temp
cd temp
wget http://www.peak-system.com/fileadmin/media/linux/files/peak-linux-driver-6.24.tar.gz
tar -xzf peak-linux-driver-6.24.tar.gz
cd peak-linux-driver-6.24
sudo apt-get install build-essential libpopt-dev
make clean
make NET=NO

sudo make install

