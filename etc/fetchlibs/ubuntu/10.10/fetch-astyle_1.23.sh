#!/bin/bash

set -o verbose #echo on
set -e

mkdir -p temp
cd temp
wget http://freefr.dl.sourceforge.net/project/astyle/astyle/astyle%201.23/astyle_1.23_linux.tar.gz
tar -xzf astyle_1.23_linux.tar.gz
cd astyle/buildgcc
make
sudo make prefix=/usr/local install

