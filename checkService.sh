
#!/bin/bash

cd /home/javaapps/sbt-projects/SimilarDocs

CheckFileName=`date '+%Y%m%d'.chk`
Errors=`sbt "testOnly org.bireme.sd.SDServiceTest -f $CheckFileName" | grep -c "*** FAILED ***"`

if [ "$Errors" != "0" ]; then
  sendemail -f appofi@bireme.org -u "Similar Documents Service - Check ERROR - `date '+%Y%m%d'`" -m "Similar Documents Service - Check ERROR - See attached file." -a $CheckFileName -t barbieri@paho.org -cc mourawil@paho.org -s esmeralda.bireme.br -xu serverofi -xp bir@2012#
fi

rm $CheckFileName

cd -
