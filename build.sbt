lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "0.1.0",
  scalaVersion := "2.12.0"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocsTrig"
  )

val luceneVersion = "6.3.0"
//val luceneVersion = "6.2.1"
val akkaVersion = "2.4.14"

libraryDependencies ++= Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion
  //"com.typesafe.akka" %% "akka-slf4j" % akkaVersion
)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
