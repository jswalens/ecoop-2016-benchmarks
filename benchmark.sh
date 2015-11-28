#!/bin/bash

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"
target_path="/home/jswalens/ecoop-2016-results/$date-$rev"

pars="-n 4 -r 512"
#gc_opts="-XX:+UseG1GC"

lein=$pwd/lein

echo "Installing lein..."
$lein version
echo "lein installed"

echo "Benchmarking..."

mkdir -p $result_path

cd $pwd/bayes-8

for i in {1..3}
do
  for gc_opts in "" "-XX:+UseG1GC" "-XX:+UseConcMarkSweepGC -XX:-UseParNewGC" "-XX:+UseConcMarkSweepGC -XX:+UseParNewGC" "-XX:+UseParallelGC"
  do
    gc_filename="${gc_opts// /_}"
    for v in 8 16 32 64 # 128
    do
      for t in 1 2 4 8 16 32 64
      do
        echo "i=$i; v=$v; t=$t; parameters=$pars; gc_opts=$gc_opts"
        variation=""
        echo "variation=$variation"
        JVM_OPTS="$gc_opts" $lein run $pars -v $v -t $t               > $result_path/$variation-$gc_filename-v$v-t$t-i$i.txt
        variation="alternatives-parallel"
        echo "variation=$variation"
        JVM_OPTS="$gc_opts" $lein run $pars -v $v -t $t -x $variation > $result_path/$variation-$gc_filename-v$v-t$t-i$i.txt
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
