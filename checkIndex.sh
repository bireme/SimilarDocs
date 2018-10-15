#!/bin/sh

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

SIM_DOCS_DIR=/home/javaapps/sbt-projects/SimilarDocs

cd $SIM_DOCS_DIR

sbt "test:runMain org.bireme.sd.IndexTest indexes/$1 $2 $3"
hits="$?"

cd -

exit $hits
