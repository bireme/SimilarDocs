lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "0.1.0",
  scalaVersion := "2.12.7",
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-feature",
    "-Ywarn-unused"
  )
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocs"
  )

lazy val SDService = (project in file("./SDService")).
  settings(commonSettings: _*).
  settings(
    name := "SDService"
  )


val luceneVersion = "7.5.0" //"7.3.1"
val akkaVersion =  "2.5.16" //"2.5.13"
val httpClientVersion = "4.5.6" //"4.5.5"
val scalaTestVersion = "3.0.5"
val casbahVersion = "3.1.1"
val playVersion = "2.6.10" //"2.6.9"
val hairyfotrVersion = "0.1.17"

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
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "org.mongodb" %% "casbah" % casbahVersion,
  "com.typesafe.play" %% "play-json" % playVersion
)

logBuffered in Test := false
trapExit :=  false  // To allow System.exit() without an exception (TestIndex.scala)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % hairyfotrVersion)
