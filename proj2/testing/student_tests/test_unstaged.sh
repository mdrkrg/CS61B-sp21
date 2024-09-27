#!/bin/zsh

rm -rf ./gittest/
mkdir ./gittest/
cd ./gittest/ || exit

echo "a" >a.txt
echo "b" >b.txt

gitlet init
gitlet add a.txt
gitlet add b.txt
gitlet commit "two files"

gitlet branch other
gitlet checkout other

echo "new a" >a.txt

gitlet add a.txt
gitlet commit "other"

echo "new stuff" >>b.txt
# b.txt should have unstaged changes right now

gitlet checkout master

diff a.txt <(printf "new a\n")
diff b.txt <(printf "b\nnew stuff\n")
