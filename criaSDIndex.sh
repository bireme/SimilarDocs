#!/bin/sh

sbt "run-main org.bireme.sd.LuceneIndexAkka indexes/sdIndex /bases/iahx/xml-inbox/regional -storedFields=au -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1"
