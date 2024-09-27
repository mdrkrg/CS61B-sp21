#!/bin/zsh
echo "same" >same_modify.txt
echo "same delete" >same_delete.txt
echo "a" >a.txt
echo "b" >b.txt
echo "c" >c.txt
echo "d" >d.txt

echo "rm_in_current" >rm_in_current.txt
echo "rm_in_other" >rm_in_other.txt

rm -rf ./gittest/
mkdir ./gittest/
cd ./gittest/ || exit
rm -rf ./.gitlet
gitlet init
cp ../a.txt .
cp ../b.txt .
cp ../same_modify.txt .
cp ../same_delete.txt .
cp ../rm_in_current.txt .
cp ../rm_in_other.txt .

gitlet add a.txt
gitlet add b.txt
gitlet add same_modify.txt
gitlet add same_delete.txt
gitlet add rm_in_current.txt
gitlet add rm_in_other.txt
gitlet status
gitlet commit "a, b"
gitlet branch other

cp ../c.txt .
echo "s" >b.txt
echo "modify same way" >same_modify.txt
gitlet add c.txt
gitlet add b.txt
gitlet add same_modify.txt
gitlet rm same_delete.txt
gitlet rm rm_in_other.txt
gitlet status
gitlet commit "add c, mod b, mod same_modify, rm same_delete, rm rm_in_other"

gitlet switch other
gitlet status
echo "afaf" >>a.txt
echo "modify same way" >same_modify.txt
cp ../d.txt .
gitlet add a.txt
gitlet add d.txt
gitlet add same_modify.txt
gitlet rm same_delete.txt
gitlet rm rm_in_current.txt
gitlet status
gitlet commit "add d, mod a, rm rm_in_current, rm same_delete, mod same_modify"

gitlet log
git init
git add .
git commit -m 'checkpoint'
