name := "Server"

version := "0.1"

scalaVersion := "2.11.8"


libraryDependencies ++= Seq(
   "com.typesafe.akka" %% "akka-http" % "10.0.1",
   "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.1",
   "com.typesafe.akka" %% "akka-actor" % "2.4.0",
   "com.typesafe.akka" %% "akka-http-core" % "10.0.1"
)
