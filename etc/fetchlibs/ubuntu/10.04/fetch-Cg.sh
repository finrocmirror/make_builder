#!/bin/bash

set -o verbose #echo on

mkdir -p temp
cd temp
wget http://developer.download.nvidia.com/cg/Cg_2.2/Cg-2.2_February2010_x86.tgz
sudo cat Cg-2.2_February2010_x86.tgz | sudo tar --strip-components=3 -C /usr/local/bin -xz ./usr/bin
sudo cat Cg-2.2_February2010_x86.tgz | sudo tar --strip-components=3 -C /usr/local/include -xz ./usr/include
sudo cat Cg-2.2_February2010_x86.tgz | sudo tar --strip-components=3 -C /usr/local/lib -xz ./usr/lib
sudo cat Cg-2.2_February2010_x86.tgz | sudo tar --strip-components=3 -C /usr/local/ -xz ./usr/local
sudo cat Cg-2.2_February2010_x86.tgz | sudo tar --strip-components=3 -C /usr/local/share -xz ./usr/share

