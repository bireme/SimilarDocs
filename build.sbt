lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "5.5.0",
  scalaVersion := "2.13.13" //"2.13.6"
)

// See https://sanj.ink/posts/2019-06-14-scalac-2.13-options-and-flags.html
scalacOptions ++= Seq(
    "-Wdead-code",
    "-Wextra-implicit",
    "-Wnumeric-widen",
    "-Woctal-literal",
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
    "-Xlint:implicit-recursion",
    "-Xlint:constant",  // Constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",  // Selecting member of DelayedInit.
    "-Xlint:doc-detached",  // A detached Scaladoc comment.
    "-Xlint:inaccessible",  // Inaccessible types in method signatures.
    "-Xlint:infer-any",  // A type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",  // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit",  // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",  // Option.apply used implicit view.
    "-Xlint:package-object-classes",  // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",  // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",  // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",  // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-deprecation", // Warning and location for usages of deprecated APIs.
    "-encoding", "utf-8" // Specify character encoding used by source files.
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "SimilarDocs"
  )

val jakartaServletApiVersion = "6.0.0"
val luceneVersion = "9.10.0" //"9.5.0"
val akkaVersion =  "2.8.5" //"2.8.0"
val httpClientVersion = "4.5.14" //"4.5.13"
val scalajHttpVersion = "2.4.2"
val scalaTestVersion = "3.2.18" //"3.2.15"
val mongodbDriverVersion = "5.0.1" //"4.9.1"
val h2DatabaseVersion = "2.2.224" //"2.1.214"
val gsonVersion = "2.10.1" //"2.8.7"
val playJsonVersion = "2.10.4" //"2.9.4"

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
  "org.scalaj" %% "scalaj-http" % scalajHttpVersion,
  "org.scalactic" %% "scalactic" % scalaTestVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "org.mongodb.scala" %% "mongo-scala-driver" % mongodbDriverVersion,
  "com.h2database" % "h2" % h2DatabaseVersion,
  "com.google.code.gson" % "gson" % gsonVersion,
  "com.typesafe.play" %% "play-json" % playJsonVersion
)

assembly / test := {}

Test / logBuffered := false
trapExit :=  false  // To allow System.exit() without an exception (TestIndex.scala)

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

enablePlugins(JettyPlugin)