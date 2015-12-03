#!/bin/bash

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"
target_path="/home/jswalens/ecoop-2016-results/$date-$rev"

pars="-n 5 -r 512 -v 48"
gc_opts="-XX:+UseG1GC"

lein=$pwd/lein

echo "Installing lein..."
$lein version
echo "lein installed"

echo "Benchmarking..."

mkdir -p $result_path

cd $pwd/bayes-8

for i in {1..30}
do
  for t in 1
  do
    echo "i=$i; t=$t; parameters=$pars; gc_opts=$gc_opts"
    JVM_OPTS="$gc_opts" $lein run $pars -t $t --profile  > $result_path/t$t-i$i.txt
  done
done

echo "Copying results..."

mkdir $target_path
chmod 0777 $target_path
cp -R $result_path/* $target_path
chmod -R 0777 $target_path

echo "Benchmark done"
