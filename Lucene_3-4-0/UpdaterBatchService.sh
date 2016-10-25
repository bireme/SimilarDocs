HOME=/home/heitor/sbt-projects/SimilarDocs/Lucene_3-4-0
SD_INDEX_PATH=
OTHER_INDEX_PATH=
UPDATE_ALL_DAY=2   #1-sunday 7-saturday

cd $HOME

echo "Update all records from 'docIndex' Lucene index"

sbt "run-main package org.bireme.sd.service.UpdaterBatchService /
-sdIndexPath=$SD_INDEX_PATH -docIndexPath=$OTHER_INDEX_PATH/docIndex /
-freqIndexPath=$OTHER_INDEX_PATH/decs -updAllDay=$UPDATE_ALL_DAY"

cd -
