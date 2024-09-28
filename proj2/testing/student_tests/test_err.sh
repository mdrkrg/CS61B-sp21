#!/bin/zsh

rm -rf ./gittest/
mkdir ./gittest/
cd ./gittest/ || exit

echo "wug" >f.txt
echo "not wug" >g.txt

gitlet init
gitlet add f.txt
gitlet add g.txt
gitlet commit "two files"

gitlet branch other

echo "wug2" >h.txt

gitlet add h.txt
gitlet rm g.txt
gitlet commit "Add h.txt and remove g.txt"

echo "checkout other"
gitlet checkout other
ls

gitlet merge other | diff - <(printf "Cannot merge a branch with itself.\n")

gitlet rm f.txt

echo "wug3" >k.txt

gitlet add k.txt

gitlet commit "Add k.txt and remove f.txt"

gitlet checkout master
gitlet merge foobar | diff - <(printf "A branch with that name does not exist.\n")

echo "wug" >k.txt

gitlet merge other | diff - <(printf "There is an untracked file in the way; delete it, or add and commit it first.\n")

rm -f k.txt

gitlet status

echo "wug" >k.txt

gitlet add k.txt
gitlet merge other | diff - <(printf "You have uncommitted changes.\n")

gitlet rm k.txt
rm -f k.txt

gitlet status
