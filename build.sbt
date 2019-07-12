lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "0.1.0",
  scalaVersion := /* "2.13.0," */ "2.12.8",
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


val luceneVersion = "7.5.0" //"8.0.0"
val akkaVersion =  "2.5.23" //"2.5.21"
val httpClientVersion = "4.5.9" //"4.5.7"
val scalaTestVersion = "3.0.8" // "3.0.7"
val casbahVersion = "3.1.1"
val playVersion = "2.7.4" //"2.7.2"
val hairyfotrVersion = "0.1.17"
val h2DatabaseVersion = "1.4.199"

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
  "com.typesafe.play" %% "play-json" % playVersion,
  "com.h2database" % "h2" % h2DatabaseVersion
)

test in assembly := {}

logBuffered in Test := false
trapExit :=  false  // To allow System.exit() without an exception (TestIndex.scala)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % hairyfotrVersion)
