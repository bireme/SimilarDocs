#!/bin/sh

sbt "run-main org.bireme.sd.LuceneIndexAkka indexes/sdIndex /bases/iahx/xml-inbox/regional -indexedFields=ti,ti_pt,ti_ru,ti_fr,ti_de,ti_it,ti_en,ti_es,ti_eng,ti_Pt,ti_Ru,ti_Fr,ti_De,ti_It,ti_En,ti_Es,ab_en,ab_es,ab_Es,ab_de,ab_De,ab_pt,ab_fr,ab_french -storedFields=au -decs=/bases/dec.000/dec.dec/decs -encoding=ISO-8859-1"
