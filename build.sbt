lazy val commonSettings = Seq(
  organization := "br.bireme",
  version := "5.0.0",
  scalaVersion := "2.13.6", //"2.13.5", //"2.13.3", // "2.12.9",  // casbah congelado
  /*scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-feature",
    "-Ywarn-unused"
  )*/

  // See https://sanj.ink/posts/2019-06-14-scalac-2.13-options-and-flags.html
  Compile / scalacOptions ++= Seq(
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

val luceneVersion = "8.9.0" //"8.7.0"
val akkaVersion =  "2.6.15" //"2.6.10"
val httpClientVersion = "4.5.13" //"4.5.12"
val scalajHttpVersion = "2.4.2" //"2.4.1"
val scalaTestVersion = "3.2.9" //"3.2.3"
val mongodbDriverVersion = "4.2.3" //"4.1.1"
//val hairyfotrVersion = "0.1.17"
val h2DatabaseVersion = "1.4.200" //"1.4.199"
val gsonVersion = "2.8.7" //"2.8.6"
val playJsonVersion = "2.9.2" //"2.8.1"

libraryDependencies ++= Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
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

//addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % hairyfotrVersion)

/*assemblyMergeStrategy in assembly := {
  //case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) =>
    (xs map {_.toLowerCase}) match {
      case ("manifest.mf" :: Nil) => MergeStrategy.discard
      case _ => MergeStrategy.last
    }
  case x => MergeStrategy.first
}*/

assembly / assemblyMergeStrategy := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
