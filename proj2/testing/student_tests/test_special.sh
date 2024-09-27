#!/bin/zsh

echo "f" >f.txt
echo "g" >g.txt
echo "This is a wug." >wug.txt
echo "This is not a wug." >notwug.txt

rm -rf ./gittest_special
mkdir ./gittest_special
cp f.txt gittest_special
cp g.txt gittest_special
cp wug.txt gittest_special
cp notwug.txt gittest_special

cd ./gittest_special || exit

gitlet init
gitlet add g.txt
gitlet add f.txt

gitlet commit "Two files"

gitlet branch b1

echo "h" >h.txt
echo "This is wug2." >wug2.txt

gitlet add h.txt
gitlet commit "Add h.txt"

gitlet branch b2

gitlet rm f.txt

gitlet commit "remove f.txt"

echo "merging b1"
gitlet merge b1

echo "checking out b2"
gitlet checkout b2

echo "merging master"
gitlet merge master
