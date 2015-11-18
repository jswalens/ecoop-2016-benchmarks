#!/bin/bash

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"
target_path="/home/jswalens/ecoop-2016-results/$date-$rev"

tests="random-x64-y64-z3-n4 random-x64-y64-z3-n32"

lein=$pwd/lein

echo "Installing lein..."
$lein version
echo "lein installed"

echo "Making uberjars"
cd $pwd/llcc-2.3
$lein uberjar
cd $pwd/pbcc-3
$lein uberjar
echo "Uberjars made"

echo "Benchmarking..."

mkdir -p $result_path

for i in {1..30}
do
  for test in $tests
  do
    for gc_opts in "" "-XX:+UseG1GC"
    do
      for t in 1 2 4 8 16
      do
        echo "i=$i; test=$test; t=$t; gc_opts=$gc_opts"
        version="llcc-2.3"
        cd $pwd/$version
        echo "version=$version"
        JVM_OPTS="$gc_opts" $lein run -i $pwd/$test.txt -t $t > $result_path/$test-$gc_opts-$version-t$t-i$i.txt
        version="pbcc-3"
        cd $pwd/$version
        for a in 1 2 4 8 16
        do
          echo "version=$version; a=$a"
          JVM_OPTS="$gc_opts" $lein run -i $pwd/$test.txt -t $t -a $a > $result_path/$test-$gc_opts-$version-t$t-a$a-i$i.txt
        done
      done
    done
  done
done

echo "Copying results..."

mkdir $target_path
chmod 0777 $target_path
cp -R $result_path/* $target_path
chmod -R 0777 $target_path

echo "Benchmark done"
