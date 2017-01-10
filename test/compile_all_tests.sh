#!/bin/sh

exec find . -name code.s -exec ./compile_test.sh {} \;
