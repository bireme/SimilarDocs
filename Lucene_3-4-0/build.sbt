lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocs"
  )

val luceneVersion = "3.4.0"

libraryDependencies ++= Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  //"org.apache.lucene" % "lucene-analyzers" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.mongodb" %% "casbah" % "3.1.1"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")
