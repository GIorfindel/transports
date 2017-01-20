name := "Scala.js Tutorial"

version := "0.1"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.0"

libraryDependencies ++= Seq(
   "com.typesafe.akka" %% "akka-http" % "10.0.1",
   "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.1",
   "com.typesafe.akka" %% "akka-actor" % "2.4.0"
)
