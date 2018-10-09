#!/bin/sh

sbt "run-main org.bireme.sd.LuceneIndexAkka indexes/sdIndex /bases/iahx/xml-inbox/regional -storedFields=au -decs=/usr/local/bireme/tabs/decs -encoding=ISO-8859-1"
