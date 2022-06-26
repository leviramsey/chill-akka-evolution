scalaVersion := "2.13.8"

val akkaVersion = "2.6.19"

libraryDependencies ++= Seq(
	"com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
	"com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
	"org.iq80.leveldb" % "leveldb" % "0.7",
	"org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
	"com.twitter" %% "chill-akka" % "0.10.0"
)
