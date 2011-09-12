#!/bin/bash

set -o verbose #echo on
set -e

sudo apt-get install bjam libbz2-dev python-dev
mkdir -p temp
cd temp
wget -O boost_1_46_1.tar.bz2 http://sourceforge.net/projects/boost/files/boost/1.46.1/boost_1_46_1.tar.bz2/download
tar -xjf boost_1_46_1.tar.bz2
cd boost_1_46_1
bjam
sudo bjam install
sudo ldconfig

