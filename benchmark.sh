#!/bin/sh

pwd=`pwd`
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y-%m-%dT%H:%M:%S%Z"`
result_path="$pwd/results/$date-$rev"
target_path="/home/jswalens/ecoop-2016-results/$date-$rev"

tests="random-x64-y64-z3-n32"

lein=$pwd/lein

echo "Installing lein..."
$lein version
echo "lein installed"

echo "Benchmarking..."

mkdir -p $result_path

for i in 1 2 3
do
  for test in $tests
  do
  	for t in 1 2 4 8 16
  	do
      echo "i=$i; test=$test; t=$t"
      version="llcc-2.3"
      cd $pwd/$version
  		$lein run -i $pwd/$test.txt -t $t > $result_path/$test-$version-t$t-i$i.txt
      version="pbcc-3"
      cd $pwd/$version
      for a in 1 2 4 8 16
      do
        $lein run -i $pwd/$test.txt -t $t -a $a > $result_path/$test-$version-t$t-a$a-i$i.txt
      done
  	done
  done
done

mkdir $target_path
chmod 0777 $target_path
cp -R $result_path/ $target_path
chmod -R 0777 $target_path

echo "Benchmark done"
