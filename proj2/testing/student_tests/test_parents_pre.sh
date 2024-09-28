#!/bin/zsh

rm -rf ./gittest/
mkdir gittest
cd gittest

gitlet init
gitlet branch B1
gitlet branch B2
gitlet checkout B1

echo "this is a wug" >h.txt
gitlet add h.txt
gitlet commit "Add h.txt"

gitlet checkout B2
echo "this is a wug" >f.txt
gitlet add f.txt
gitlet commit "f.txt added"

gitlet branch C1

echo "this is not a wug" >g.txt
gitlet add g.txt
gitlet rm f.txt
gitlet commit "g.txt added, f.txt removed"

gitlet checkout B1
gitlet merge C1

# Pretest ends
