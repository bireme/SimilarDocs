HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0

cd $HOME

echo "Cria index com tokens do Decs e suas respectivas frequencias no portal org"
sbt "run-main org.bireme.sd.GenDecsTokenFreq3 decsTokens.txt utf-8 /home/javaapps/iahx-server/indexes/solr5/tst-main/data/index ti,ab decs"

cd -
