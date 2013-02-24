#! /bin/sh

VERSION=$1

if [ -z $VERSION ] 
then 
  echo "$0 version"
  exit 1
fi

DIRNAME=wt1-$VERSION-src
DIR=dist/src-tarball/$DIRNAME

rm -rf $DIR
mkdir -p $DIR
git clone https://github.com/dataiku/wt1.git $DIR
cd $DIR
rm -rf .git
cd ..
tar cz $DIRNAME > ../wt1-$VERSION-src.tar.gz
