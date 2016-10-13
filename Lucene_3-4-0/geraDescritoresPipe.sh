HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0
DECS_HOME=/home/org.000/tabs/

cd $HOME

echo "Gera tabela dos tokens presentes no DeCS"

./mx $DECS_HOME/decs "pft=v1/v2/v3/(v50^i/),(v50^e/),(v50^p/)" lw=0 now > descritores.txt
sbt "run-main org.bireme.sd.GenerTokens descritores.txt ISO-8859-1 decsTokens.txt"

rm ./descritores.txt

cd -
