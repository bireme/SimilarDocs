HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0
PORTAL_ORG=/home/javaapps/iahx-server/indexes/portalorg/main/data/index

cd $HOME

echo "Cria index com tokens do Decs e suas respectivas frequencias no portal org"
sbt "run-main org.bireme.sd.GenDecsTokenFreq3 decsTokens.txt utf-8 $PORTAL_ORG ti,ab decs"

cd -
