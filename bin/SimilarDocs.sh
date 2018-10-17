#!/bin/bash

# shell script para gerar índice de documentos relacionados, mover índice
# atual para diretória de backup e transferir e checar o novo índice para
# o servido de produção.

FULL_INDEXING=0

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

SERVER=basalto01.bireme.br
SIM_DOCS_SERVER=http://$SERVER:8180/SDService/SDService
SIM_DOCS_DIR=/home/javaapps/sbt-projects/SimilarDocs

DECS_PATH=/usr/local/bireme/tabs

# Vai para diretório da aplicação SimilarDcs
cd $SIM_DOCS_DIR

# Apaga diretórios 'indexes' e 'work' se a indexação for total
if [ "$FULL_INDEXING" -eq 1 ]; then
  rm -fr indexes
  rm -fr work
fi

#Cria diretório indexes se ele não existir
[ ! -d indexes ] && mkdir -p indexes

# Cria diretórios de trabalho de versão nova e de versão anterior
mkdir -p work/new work/old

# Apaga todos os arquivos do diretório de versão nova
if [ -e "work/new" ]; then
 rm -fr work/new
 mkdir -p work/new
fi

# Copia a base decs
sbt 'runMain bruma.tools.ImportMaster id='${DECS_PATH}'/decs.id  ISO-8859-1 work/new/decs'
if [ "$?" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Decs database creation ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na criação da base de dados decs" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Cria o índice decs
sbt 'runMain org.bireme.sd.OneWordDecsCreate  work/new/decs work/new/decsIndex' > logs/decsIndex.txt
if [ "$?" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Decs index creation ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na criação do índice decs" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa índice recém criado buscando documento contendo 'atencao plena'
sbt 'test:runMain org.bireme.sd.IndexTest work/new/decsIndex descriptor "atencao plena"'
decsHits="$?"
if [ "$decsHits" -ne 1 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Decs index checking ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na checagem do índice decs" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

if [ "$FULL_INDEXING" -eq 0 ]; then
  # Copia índice 'sdIndex_isNew' do diretorio oficial para o work/new para adicionar
  # identificadores novos e o índice 'sdIndex' também para ser atualizado
  cp -r indexes/sdIndex_isNew work/new/
  cp -r indexes/sdIndex work/new/
fi

# A partir do diretório do Iahx, pega todos os xmls e cria índice Lucene para a
# busca de documentos similares no diretório de versão nova.
if [ "$FULL_INDEXING" -eq 0 ]; then
  sbt 'runMain org.bireme.sd.LuceneIndexAkka work/new/sdIndex work/new/decsIndex /bases/iahx/xml-inbox/regional "-xmlFileFilter=[^\\.]+\\.xml" -storedFields=au,la,entry_date -decs=work/new/decs -encoding=ISO-8859-1' > logs/sdIndex.txt
else
  sbt 'runMain org.bireme.sd.LuceneIndexAkka work/new/sdIndex work/new/decsIndex /bases/iahx/xml-inbox/regional "-xmlFileFilter=[^\\.]+\\.xml" -storedFields=au,la,entry_date -decs=work/new/decs -encoding=ISO-8859-1 --fullIndexing' > logs/sdIndex.txt
fi
if [ "$?" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Index creation ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na criação do índice" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

#Apaga base Isis do decs
if [ -e "work/new/decs.mst" ]; then
 rm -r work/new/decs.mst
fi
if [ -e "work/new/decs.xrf" ]; then
 rm -r work/new/decs.xrf
fi

# Checa índice 'sdIndex' recém criado buscando todos documentos contendo o ngram 'abdom' (max value = 255)
sbt "test:runMain org.bireme.sd.IndexTest work/new/sdIndex ti abdom"
hits="$?"
if [ "$hits" -ne 255 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Index checking ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na checagem do índice 'sdIndex'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Checa índice 'sdIndex_isNew' recém criado buscando todos documentos contendo o termo 'mdl-29053231'
sbt "test:runMain org.bireme.sd.IndexTest work/new/sdIndex_isNew id mdl-29053231"
if [ "$?" -ne 1 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Index checking ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na checagem do índice 'sdIndex_isNew'" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Apaga todos os arquivos do diretório de versão anterior
rm -r work/old/*

# Move diretórios 'decsIndex', 'sdIndex' e 'sdIndex_isNew' do diretório oficial 'indexes' para o diretório de versão anterior
mv indexes/decsIndex work/old/
mv indexes/sdIndex work/old/
mv indexes/sdIndex_isNew work/old/

# Move diretórios 'decsIndex', 'sdIndex' e 'sdIndex_isNew' do diretório de versão nova para o diretório oficial
mv work/new/decsIndex indexes/
mv work/new/sdIndex indexes/
mv work/new/sdIndex_isNew indexes/

# Copia diretório 'decsIndex' para servidor de produção
$MISC/sendFiles.sh indexes/decsIndex $SERVER:$SIM_DOCS_DIR/indexes/
result="$?"
if [ "$result" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Directory 'decsIndex' transfer ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na transferência do diretório decsIndex para basalto01:$SIM_DOCS_DIR/indexes/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Copia diretório 'sdIndex' para servidor de produção
$MISC/sendFiles.sh indexes/sdIndex $SERVER:$SIM_DOCS_DIR/indexes/
result="$?"
if [ "$result" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Directory 'sdIndex' transfer ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na transferência do diretório sdIndex para basalto01:$SIM_DOCS_DIR/indexes/" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

# Verifica se o site está no ar
sbt "test:runMain org.bireme.sd.SiteOk $SIM_DOCS_SERVER"
siteIsOn="$?"

if [ "$siteIsOn" -eq 1 ]; then
  # Trava o servidor para atualizações
  sbt "runMain org.bireme.sd.service.MaintenanceMode $SIM_DOCS_SERVER set"
  result="$?"
  if [ "$result" -ne 0 ]; then
    sendemail -f appofi@bireme.org -u "Similar Documents Service - Service shutdown ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro no bloqueio do serviço" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
    exit 1
  fi

  # Espera 120 segundos
  sleep 120s
fi

# Faz a rotação do índice 'decsIndex'
ssh $TRANSFER@$SERVER "if [ -e '$SIM_DOCS_DIR/indexes/decsIndex.bad' ]; then rm -r $SIM_DOCS_DIR/indexes/decsIndex.bad; fi"
ssh $TRANSFER@$SERVER "if [ -e '$SIM_DOCS_DIR/indexes/decsIndex.old' ]; then rm -r $SIM_DOCS_DIR/indexes/decsIndex.old; fi"
ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/decsIndex $SIM_DOCS_DIR/indexes/decsIndex.old"
ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/decsIndex.new $SIM_DOCS_DIR/indexes/decsIndex"

# Faz a rotação do índice 'sdIndex'
ssh $TRANSFER@$SERVER "if [ -e '$SIM_DOCS_DIR/indexes/sdIndex.bad' ]; then rm -r $SIM_DOCS_DIR/indexes/sdIndex.bad; fi"
ssh $TRANSFER@$SERVER "if [ -e '$SIM_DOCS_DIR/indexes/sdIndex.old' ]; then rm -r $SIM_DOCS_DIR/indexes/sdIndex.old; fi"
ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/sdIndex $SIM_DOCS_DIR/indexes/sdIndex.old"
ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/sdIndex.new $SIM_DOCS_DIR/indexes/sdIndex"

# Checa qualidade dos índices 'decsIndex', 'sdIndex' e 'sdIndex_isNew'
#ssh $TRANSFER@$SERVER "(cd $SIM_DOCS_DIR/; sbt 'test:runMain org.bireme.sd.IndexTest indexes/sdIndex ab deng')"
ssh $TRANSFER@$SERVER $SIM_DOCS_DIR/checkIndex.sh decsIndex descriptor "atencao plena"
decsHitsRemoto="$?"
ssh $TRANSFER@$SERVER $SIM_DOCS_DIR/checkIndex.sh sdIndex ti abdom
hitsRemoto="$?"

if [ "$decsHitsRemoto" -ne "$decsHits" ] || [ "$hitsRemoto" -ne "$hits" ]; then  # Índice apresenta problemas, faz rollback
  ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/decsIndex $SIM_DOCS_DIR/indexes/decsIndex.bad"
  ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/decsIndex.old $SIM_DOCS_DIR/indexes/decsIndex"

  ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/sdIndex $SIM_DOCS_DIR/indexes/sdIndex.bad"
  ssh $TRANSFER@$SERVER "mv $SIM_DOCS_DIR/indexes/sdIndex.old $SIM_DOCS_DIR/indexes/sdIndex"

  sendemail -f appofi@bireme.org -u "Similar Documents Service - Directory index check ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na checagem da qualidade do(s) índice(s) 'decsIndex' e/ou 'sdIndex' no servidor de produção" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
  exit 1
fi

if [ "$siteIsOn" -eq 1 ]; then
  # Destrava o servidor para atualizações e abre nova versão do índice
  sbt "runMain org.bireme.sd.service.MaintenanceMode $SIM_DOCS_SERVER reset"
  result="$?"
  if [ "$result" -ne 0 ]; then
    sendemail -f appofi@bireme.org -u "Similar Documents Service - Service restart ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro no desbloqueio" -t barbieri@paho.org -cc mourawil@paho.org ofi@bireme.org -s esmeralda.bireme.br
    exit 1
  fi
fi

# Email avisando que o processamento finalizou corretamente
sendemail -f appofi@bireme.org -u "Similar Documents Service - Processing finished - `date '+%Y%m%d'`" -m "Similar Documents Service - Fim do processamento" -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br

cd -
