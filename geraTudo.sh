#!/bin/sh

# A partir do diretório do Iahx, pega todos os xmls e cria indice Lucene para a
# busca de documentos similares.
sbt "run-main org.bireme.sd.LuceneIndexAkka indexes/sdIndex /bases/iahx/xml-inbox/regional -fields=ti,ti_pt,ti_ru,ti_fr,ti_de,ti_it,ti_en,ti_es,ti_eng,ti_Pt,ti_Ru,ti_Fr,ti_De,ti_It,ti_En,ti_Es,ab_en,ab_es,ab_Es,ab_de,ab_De,ab_pt,ab_fr,ab_french -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1"

# Atualiza todos os perfis do serviço personalizado para apontarem para documentos
# atualizados no índice criado no comando anterior.
sbt "run-main org.bireme.sd.service.UpdaterBatchService -sdIndexPath=/home/javaapps/sbt-projects/SimilarDocs/indexes/sdIndex -docIndexPath=/home/javaapps/sbt-projects/SimilarDocs/indexes/docIndex -updAllDay=0
