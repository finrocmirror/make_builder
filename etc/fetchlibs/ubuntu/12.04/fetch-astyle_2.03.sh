#!/bin/bash

set -o verbose #echo on
set -e

mkdir -p temp
cd temp
wget http://freefr.dl.sourceforge.net/project/astyle/astyle/astyle%202.03/astyle_2.03_linux.tar.gz
tar -xzf astyle_2.03_linux.tar.gz
cd astyle/build/gcc
make
sudo make prefix=/usr/local install

