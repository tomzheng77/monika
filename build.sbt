name := "monika"
version := "0.1"
scalaVersion := "2.12.8"

mainClass in assembly := Some("monika.Monika")
assemblyJarName in assembly := "monika.jar"

libraryDependencies += "org.littleshoot" % "littleproxy" % "1.1.2"
libraryDependencies += "net.lightbody.bmp" % "mitm" % "2.1.4"
libraryDependencies += "com.github.ganskef" % "littleproxy-mitm" % "1.1.0"
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.6.2"
libraryDependencies += "com.sparkjava" % "spark-core" % "2.8.0"
libraryDependencies += "org.apache.commons" % "commons-exec" % "1.3"
libraryDependencies += "io.swaydb" %% "core" % "0.6"
libraryDependencies += "net.openhft" % "chronicle-map" % "3.17.0"
libraryDependencies += "org.mapdb" % "mapdb" % "3.0.7"
