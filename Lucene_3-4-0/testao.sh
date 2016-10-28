rm -r topIndex docIndex
sbt "run-main org.bireme.sd.service.TopIndexTestService -sdIndexPath=TitAbs -freqIndexPath=decs -otherIndexPath=. -psId=um \"-addProfile=esporte=corrida caminhada natação\""
sbt "run-main org.bireme.sd.service.TopIndexTestService -sdIndexPath=TitAbs -freqIndexPath=decs -otherIndexPath=. -psId=dois \"-addProfile=doenças=zika e dengue hemorragica matam\""
sbt "run-main org.bireme.sd.service.TopIndexTestService -sdIndexPath=TitAbs -freqIndexPath=decs -otherIndexPath=. -psId=dois \"-addProfile=academia=nem que a vaca tussa\""
sbt "run-main org.bireme.sd.service.TopIndexTestService -sdIndexPath=TitAbs -freqIndexPath=decs -otherIndexPath=. -psId=dois \"-addProfile=academia=corrida caminhada natação\""
sbt "run-main org.bireme.sd.Tools topIndex"
sbt "run-main org.bireme.sd.Tools docIndex"
sbt "run-main org.bireme.sd.service.TopIndexTestService -sdIndexPath=TitAbs -freqIndexPath=decs -otherIndexPath=. -psId=dois -deleteProfile=academia"
sbt "run-main org.bireme.sd.Tools topIndex"
sbt "run-main org.bireme.sd.Tools docIndex"

