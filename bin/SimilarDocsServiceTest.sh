#!/bin/bash

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

SIM_DOCS_DIR=/home/javaapps/sbt-projects/SimilarDocs

cd $SIM_DOCS_DIR

CheckFileName=`date '+%Y%m%d'.chk`
sbt "testOnly org.bireme.sd.SimilarDocsServiceTest" &> ./$CheckFileName

Errors=`grep -c "*** FAILED ***" ./$CheckFileName`
if [ "$Errors" != "0" ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Check ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Check ERROR - See attached file." -a $CheckFileName -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
  rm ./$CheckFileName
  exit 1
fi

rm ./$CheckFileName

cd -
