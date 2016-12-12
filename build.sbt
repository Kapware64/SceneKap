name := """Scene-Kap"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "com.h2database" % "h2" % "1.4.190",
  specs2 % Test,
  ws
)

libraryDependencies += "org.jsoup" % "jsoup" % "1.8.3"
libraryDependencies += "com.syncthemall" % "boilerpipe" % "1.2.1"
libraryDependencies += "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.16"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "1.2.1"

fork in run := true