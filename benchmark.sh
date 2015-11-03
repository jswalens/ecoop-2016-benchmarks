#!/bin/sh

echo "Benchmarking..."

REV=`git rev-parse HEAD | cut -c1-8`
TARGET_PATH=/home/jswalens/ecoop-2016-results/$REV/
mkdir $TARGET_PATH
#cp results $TARGET_PATH

echo "Benchmark done"
