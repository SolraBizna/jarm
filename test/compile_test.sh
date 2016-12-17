#!/bin/sh

if [ "$1" = "" ]; then
    echo "Usage: compile_test path/to/sourcefile.s"
    exit 1
fi

set -e

DIR=`dirname $1`
FILE=`basename -s .s $1`
RESULT_FILE=$DIR/$FILE.elf
OBJECT_FILE=$DIR/$FILE.o
SOURCE_FILE=$DIR/$FILE.s
SCRIPT_FILE=linkerscript.ld

if [ `echo $FILE | cut -b 1-3` = "LE." ]; then
    CROSSPREFIX=/opt/occross/arm-oc_arm-eabi/bin/arm-oc_arm-eabi
    BE8=
else
    CROSSPREFIX=/opt/occross/armeb-oc_arm-eabi/bin/armeb-oc_arm-eabi
    BE8=--be8
fi

AS="$CROSSPREFIX-as -mfpu=vfpv4"
LD="$CROSSPREFIX-ld -z max-page-size=4 -nostartfiles $BE8"

if [ ! -f $RESULT_FILE -o $SOURCE_FILE -nt $RESULT_FILE ]; then
    echo Assemble: $DIR
    $AS -c -o $OBJECT_FILE $SOURCE_FILE
    $LD -o $RESULT_FILE -T $SCRIPT_FILE $OBJECT_FILE
    rm -f $OBJECT_FILE
fi
