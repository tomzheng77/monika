name := "monika"
version := "0.1"
scalaVersion := "2.12.8"

mainClass in assembly := Some("monika.Monika")
assemblyJarName in assembly := "monika.jar"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.12.8"
libraryDependencies += "org.littleshoot" % "littleproxy" % "1.1.2"
libraryDependencies += "net.lightbody.bmp" % "mitm" % "2.1.4"
libraryDependencies += "com.github.ganskef" % "littleproxy-mitm" % "1.1.0"
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.6.2"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.3"
libraryDependencies += "com.sparkjava" % "spark-core" % "2.8.0"
libraryDependencies += "org.apache.commons" % "commons-exec" % "1.3"
libraryDependencies += "net.openhft" % "chronicle-map" % "3.17.0" exclude("xpp3", "xpp3_min")
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.0-M26"
libraryDependencies += "org.scalaz" %% "scalaz-effect" % "7.3.0-M26"
libraryDependencies += "co.fs2" %% "fs2-core" % "1.0.2"
libraryDependencies += "com.mashape.unirest" % "unirest-java" % "1.4.9" exclude("commons-logging", "commons-logging")
libraryDependencies += "com.storm-enroute" %% "scalameter-core" % "0.10.1"
libraryDependencies += "org.bitcoinj" % "bitcoinj-core" % "0.14.7"
libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.3"
libraryDependencies += "org.reflections" % "reflections" % "0.9.11"
libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.8.0-beta2"
libraryDependencies += "com.fifesoft" % "rsyntaxtextarea" % "3.0.2"
libraryDependencies += "info.picocli" % "picocli" % "3.9.5"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.0-SNAP10" % Test

autoCompilerPlugins := true
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.0-M4")
