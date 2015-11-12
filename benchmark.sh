#!/bin/sh

pwd="`pwd`"
rev=`git rev-parse HEAD | cut -c1-8`
date=`date "+%Y%m%dT%H%M"`
result_path="$pwd/results/$date-$rev"
target_path="/home/jswalens/ecoop-2016-results/$date-$rev"

tests="random-x64-y64-z3-n32"

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

for i in 1 2 3 4 5 # five now
do
  for test in $tests
  do
    for opts in "" "-Xmx8192m"
    do
      for gc_opts in "" "-XX:+UseSerialGC" "-XX:+UseParallelGC -XX:-UseParallelOldGC" "-XX:+UseParallelGC -XX:+UseParallelOldGC" "-XX:+UseConcMarkSweepGC -XX:+UseParNewGC" "-XX:+UseG1GC"
      do
        for t in 1 2 4 8 # 16
        do
          echo "i=$i; test=$test; t=$t; opts=$opts; gc_opts=$gc_opts"
          version="llcc-2.3"
          cd $pwd/$version
          echo "version=$version"
          java $opts $gc_opts -cp resources/clojure-1.6.0-transactional-futures-2.3.jar:target/uberjar/labyrinth-0.1.0-SNAPSHOT-standalone.jar labyrinth.main -i $pwd/$test.txt -t $t > $result_path/$test-$opts-$gc_opts-$version-t$t-i$i.txt
          #$lein run -i $pwd/$test.txt -t $t > $result_path/$test-$version-t$t-i$i.txt
          version="pbcc-3"
          cd $pwd/$version
          for a in 1 2 4 8 # 16
          do
            echo "version=$version; a=$a"
            java $opts $gc_opts -cp resources/clojure-1.6.0-transactional-futures-2.3.jar:target/uberjar/labyrinth-0.1.0-SNAPSHOT-standalone.jar labyrinth.main -i $pwd/$test.txt -t $t -a $a > $result_path/$test-$opts-$gc_opts-$version-t$t-a$a-i$i.txt
            #$lein run -i $pwd/$test.txt -t $t -a $a > $result_path/$test-$version-t$t-a$a-i$i.txt
          done
        done
      done
    done
  done
done

mkdir $target_path
chmod 0777 $target_path
cp -R $result_path/ $target_path
chmod -R 0777 $target_path

echo "Benchmark done"
