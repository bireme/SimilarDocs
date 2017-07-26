#!/bin/sh

# Shell script para gerar índice de documentos relacionados, mover índice
# atual para diretória de backup e transferir o novo índice para o servidor
# de produção.

JAVA_HOME=/usr/local/oracle-8-jdk
J2SDKDIR=$JAVA_HOME
J2REDIR=$JAVA_HOME/jre
PATH=$JAVA_HOME/bin:$PATH

SIM_DOCS_SERVER=http://basalto01.bireme.br:8180/SDService/SDService

# Vai para diretório da aplicação SimilarDcs
cd /home/javaapps/sbt-projects/SimilarDocs

# Cria diretórios de trabalho de versão nova e de versão anterior
mkdir -p work/new work/old

# Apaga todos os arquivos do diretório de versão nova
rm -r work/new/*

# A partir do diretório do Iahx, pega todos os xmls e cria índice Lucene para a
# busca de documentos similares no diretório de versão nova.
sbt 'run-main org.bireme.sd.LuceneIndex work/new/sdIndex /bases/iahx/xml-inbox/regional "-xmlFileFilter=(lil_regional.xml|mdl\\d\\d_regional.xml)" -indexedFields=ti,ti_pt,ti_ru,ti_fr,ti_de,ti_it,ti_en,ti_es,ti_eng,ti_Pt,ti_Ru,ti_Fr,ti_De,ti_It,ti_En,ti_Es,ab,ab_en,ab_es,ab_Es,ab_de,ab_De,ab_pt,ab_fr,ab_french -storedFields=au,la -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1'
if [ "$?" -ne 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Index creation ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na criação do índice" -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
  exit 1
fi

# Checa índice recém criado buscando todos documentos contendo o ngram 'deng'
sbt "test:runMain org.bireme.sd.IndexTest work/new/sdIndex ab deng"
hits="$?"
if [ "$hits" -eq 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Index checking ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro na checagem do índice" -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
  exit 1
fi

# (cd myPath/; sbt "run arg1")

# Apaga todos os arquivos do diretório de versão anterior
rm -r work/old/*

# Move diretório 'sdIndex' do diretório oficial 'indexes' para o diretório de versão anterior
mv indexes/sdIndex work/old

# Move diretório 'sdIndex' do diretório de versão nova para o diretório oficial
mv work/new/sdIndex indexes

# Cria arquivo flag para avisar processo que move diretório 'sdIndex' do
# diretório oficial para o servidor de produção
echo $hits > work/flag.txt

# Trava o servidor para atualizações
sbt "run-main org.bireme.sd.service.MaintenanceMode $SIM_DOCS_SERVER set"
hits="$?"
if [ "$hits" -eq 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Service shutdown ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro no bloqueio do serviço" -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
  exit 1
fi

# Espera 120 secondos
sleep 120s

# Destrava o servidor para atualizações
sbt "run-main org.bireme.sd.service.MaintenanceMode $SIM_DOCS_SERVER reset"
hits="$?"
if [ "$hits" -eq 0 ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Service restart ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Erro no desbloqueio" -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
  exit 1
fi

cd -
