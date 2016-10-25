#!/bin/bash

HOME=/home/users/heitor.barbieri/SimilarDocs/Lucene_3-4-0
DECS_HOME=/bases/org.000/tabs
SD_INDEX_PATH=/home/javaapps/iahx-server/indexes/portalorg/main/data/index
OTHER_INDEX_PATH=$HOME
UPDATE_ALL_DAY=2   #1-sunday 7-saturday

cd $HOME

echo "Gera tabela dos tokens presentes no DeCS"
./mx $DECS_HOME/decs "pft=v1/v2/v3/(v50^i/),(v50^e/),(v50^p/)" lw=0 now > descritores.txt
sbt "run-main org.bireme.sd.GenerTokens descritores.txt IBM-850 decsTokens.txt"
rm ./descritores.txt

echo "Cria index com tokens do Decs e suas respectivas frequencias no portal org"
sbt "run-main org.bireme.sd.GenDecsTokenFreq3 decsTokens.txt utf-8 $SD_INDEX_PATH ti,ab $OTHER_INDEX_PATH/decs"
rm decsTokens.txt

echo "Atualiza todos os rregistros do indice Lucene 'docIndex'"
sbt "run-main package org.bireme.sd.service.UpdaterBatchService /
-sdIndexPath=$SD_INDEX_PATH -docIndexPath=$OTHER_INDEX_PATH/docIndex /
-freqIndexPath=$OTHER_INDEX_PATH/decs -updAllDay=$UPDATE_ALL_DAY"

cd -
