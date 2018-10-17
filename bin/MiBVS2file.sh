#!/bin/sh

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

HOST=cristal40.bireme.br
DBASE=servicesplatform
COLL=logs
OUTFILEDIR=logs

SIM_DOCS_DIR=/home/javaapps/sbt-projects/SimilarDocs

# Vai para diretório da aplicação SimilarDcs
cd $SIM_DOCS_DIR

# Exporta documentos gerados ontem para arquivo no diretório logs
sbt "runMain org.bireme.sd.MongoDbExport -host=$HOST -dbase=$DBASE -coll=$COLL -outFileDir=$OUTFILEDIR"
if [ $? -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - MongoDB to file ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Exporting MongoDB documents to file ERROR" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

cd -
