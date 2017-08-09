lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "0.1.0",
  scalaVersion := "2.12.2" //"2.12.1"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocs"
  )

val luceneVersion = "6.6.0" //"6.5.1"
val akkaVersion = "2.5.3" //"2.5.2" //"2.5.1" //"2.4.14"
val httpClientVersion = "4.5.3" //"4.5.2"
val scalaTestVersion = "3.0.3"

libraryDependencies ++= Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  //"com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.apache.httpcomponents" % "httpclient" % httpClientVersion,
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
)

logBuffered in Test := false
trapExit :=  false  // To allow System.exit() without an exception (TestIndex.scala)

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused")
