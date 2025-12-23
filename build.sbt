lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "6.0",
  scalaVersion := "3.3.7" //"2.13.18" //"2.13.6"
)

// See https://sanj.ink/posts/2019-06-14-scalac-2.13-options-and-flags.html
scalacOptions ++= Seq(
    "-Wunused:imports",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:locals",
    "-Wunused:explicits",
    "-Wunused:implicits",
    "-Wunused:params",
    "-Wunused:linted",
    "-Wvalue-discard",
    "-explaintypes",
    "-Xlint:private-shadow",  // A private field (or class parameter) shadows a superclass field.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-deprecation", // Warning and location for usages of deprecated APIs.
    "-encoding", "utf-8" // Specify character encoding used by source files.
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocs"
  )

val jakartaServletApiVersion = "6.1.0"
val luceneVersion = "10.3.2" //"9.5.0"
val akkaVersion =  "2.8.8" //"2.8.0"
val httpClientVersion = "4.5.14" //"4.5.13"
//val scalajHttpVersion = "2.4.2"
val sttpClient4Version = "4.0.13"
val scalaTestVersion = "3.2.19" //"3.2.15"
val mongodbDriverVersion = "5.6.2" //"4.9.1"
val h2DatabaseVersion = "2.4.240" //"2.1.214"
val gsonVersion = "2.13.2" //"2.8.7"
val playJsonVersion = "2.10.8" //"2.9.4"

libraryDependencies ++= Seq(
  "jakarta.servlet" % "jakarta.servlet-api" % jakartaServletApiVersion % "provided",
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  //"org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
  "org.apache.lucene" % "lucene-analysis-common" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.apache.lucene" % "lucene-backward-codecs" % luceneVersion,
  "org.apache.lucene" % "lucene-codecs" % luceneVersion % Test,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.apache.httpcomponents" % "httpclient" % httpClientVersion,
  //"org.scalaj" %% "scalaj-http" % scalajHttpVersion,
  "com.softwaremill.sttp.client4" %% "core" % sttpClient4Version,
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "org.mongodb" % "mongodb-driver-sync" % mongodbDriverVersion,
  // Gson (já usado no código)
  //"com.google.code.gson" % "gson" % "2.10.1"
  "com.h2database" % "h2" % h2DatabaseVersion,
  "com.google.code.gson" % "gson" % gsonVersion,
  "com.typesafe.play" %% "play-json" % playJsonVersion
)

assembly / test := {}

Test / logBuffered := false
trapExit :=  false  // To allow System.exit() without an exception (TestIndex.scala)

//enablePlugins(JettyPlugin)
enablePlugins(SbtWar)

/*assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}*/

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}

// Forca versao do jckson que e incluido no play-json
val jacksonCoreV = "2.20.1"
val jacksonAnnV  = "2.20"     // <- não existe 2.20.1

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonCoreV,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonCoreV,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonAnnV,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonCoreV,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonCoreV
)