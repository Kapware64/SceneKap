name := """Scene-Kap"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  specs2 % Test,
  ws
)

libraryDependencies += "org.jsoup" % "jsoup" % "1.8.3"
libraryDependencies += "com.syncthemall" % "boilerpipe" % "1.2.1"
libraryDependencies += "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.16"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "1.2.1"

resolvers += "lightshed-maven" at "http://dl.bintray.com/content/lightshed/maven"
libraryDependencies += "ch.lightshed" %% "courier" % "0.1.4"

fork in run := true