#!/bin/sh
dir=$(dirname $0)
cd $dir/..
for f in "$dir"/input/*; do
	echo $(basename "$f")
	output="$(./jlox "$f" 2>&1; echo $?)"
	if [ $(echo "$output" | tail -1) -ne 0 ]; then
		echo "$output" | head --lines=-2 | tail --lines=+2
		nl $(echo "$output" | head -1 | cut -d ' ' -f 5)
	fi
done
