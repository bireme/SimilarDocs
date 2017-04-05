#!/bin/sh

export JAVA_HOME=/usr/local/oracle-8-jdk
export J2SDKDIR=$JAVA_HOME
export J2REDIR=$JAVA_HOME/jre
export PATH=$JAVA_HOME/bin:$PATH

# Vai para diretório da aplicação SimilarDcs
cd /home/javaapps/sbt-projects/SimilarDocs

# Apaga todos os arquivos do diretório de trabalho temporario
rm -r indexes/tmp/sdIndex/*

# A partir do diretório do Iahx, pega todos os xmls e cria indice Lucene para a
# busca de documentos similares no diretório temporário.
#sbt "run-main org.bireme.sd.LuceneIndexAkka indexes/tmp/sdIndex /bases/iahx/xml-inbox/regional -indexedFields=ti,ti_pt,ti_ru,ti_fr,ti_de,ti_it,ti_en,ti_es,ti_eng,ti_Pt,ti_Ru,ti_Fr,ti_De,ti_It,ti_En,ti_Es,ab,ab_en,ab_es,ab_Es,ab_de,ab_De,ab_pt,ab_fr,ab_french -storedFields=au,la -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1"
sbt 'run-main org.bireme.sd.LuceneIndexAkka indexes/tmp/sdIndex /bases/iahx/xml-inbox/regional "-xmlFileFilter=(lil_regional.xml|mdl\\d\\d_regional.xml)" -indexedFields=ti,ti_pt,ti_ru,ti_fr,ti_de,ti_it,ti_en,ti_es,ti_eng,ti_Pt,ti_Ru,ti_Fr,ti_De,ti_It,ti_En,ti_Es,ab,ab_en,ab_es,ab_Es,ab_de,ab_De,ab_pt,ab_fr,ab_french -storedFields=au,la -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1'

# Trava o servidor para atualizações
sbt "run-main org.bireme.sd.service.MaintenanceMode http://serverofi5.bireme.br:8080/SDService/SDService set"

# Espera 30 secondos
sleep 30s

# Apaga o diretório do índice dos documentos similares
rm -fr indexes/sdIndex

# Copia o índice recém gerado do diretório temporário para o de trabalho
mv indexes/tmp/sdIndex indexes

# Atualiza todos os perfis do serviço personalizado para apontarem para documentos
# atualizados no índice criado no comando anterior.
sbt "run-main org.bireme.sd.service.UpdaterBatchService -sdIndexPath=/home/javaapps/sbt-projects/SimilarDocs/indexes/sdIndex -topIndexPath=/home/javaapps/sbt-projects/SimilarDocs/indexes/topIndex -docIndexPath=/home/javaapps/sbt-projects/SimilarDocs/indexes/docIndex -updAllDay=0"

# Apaga arquivos 'write.lock' dos índices
rm indexes/docIndex/write.lock
rm indexes/topIndex/write.lock
rm indexes/sdIndex/write.lock

# Destrava o servidor para atualizações
sbt "run-main org.bireme.sd.service.MaintenanceMode http://serverofi5.bireme.br:8080/SDService/SDService reset"

cd -
