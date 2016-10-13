HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0

cd $HOME

echo "Gera indice Lucene com texto de titulo e resumo do portal org"
sbt "run-main org.bireme.sd.IndexDocs -inFile=LILACS_Sas_TituloResumo.txt -index=TitAbs -encoding=ISO-8859-1 --uniformToken"

cd -
