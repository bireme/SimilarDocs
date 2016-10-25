#!/bin/bash

HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0
#HOME=/home/heitor/Projetos/NGrams

cd $HOME

echo "Copia LILACS atualizada do serverabd2"
scp -p transfer@serverabd2.bireme.br:/home/lilacs/www/bases/lildbi/dbcertif/lilacs/LILACS.{mst,xrf} .

echo "Gera arquivo a ser indexado (S/as) a partir da LILACS"
echo "<database>|<id>|<titulo_artigo><resumo>"

./mx LILACS  "pft=if v5.1='S' then (if p(v12) then 'LILACS_Sas|',v2[1],'|',replace(v12^*,'|',''),if p(v13) then x1,replace(v13^*,'|','') fi,x1,replace(v83^*,'|','')/ fi),fi" lw=0 tell=50000 now > LILACS_Sas_TituloResumo.txt

rm LILACS.{mst,xrf}

cd -
