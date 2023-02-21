#!/usr/bin/env bash

PRJ_DIR="$( cd "$( dirname "$0" )" && pwd -P )"
RELEASE_DIR="$PRJ_DIR/target/releases"
TMP_DIR="$RELEASE_DIR/tmp"
OUT_FILE=elasticsearch-analysis-baikal-8.5.2.zip

echo "RELEASE_DIR " $RELEASE_DIR

echo  mkdir $TMP_DIR
mkdir $TMP_DIR

echo unzip to $TMP_DIR
unzip $RELEASE_DIR/$OUT_FILE -d $TMP_DIR
rm $RELEASE_DIR/$OUT_FILE

cd $TMP_DIR

echo zip to $RELEASE_DIR/$OUT_FILE
zip $RELEASE_DIR/$OUT_FILE *

cd $PRJ_DIR

echo rm -r $TMP_DIR
rm -r $TMP_DIR

unzip -l $RELEASE_DIR/$OUT_FILE