val scala3Version = "3.3.1"

lazy val game = project
  .in(file("game"))
  .settings(
    name := "backgammon",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "resources",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.14",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.14" % "test",
    libraryDependencies += "org.scalafx" %% "scalafx" % "21.0.0-R32",
    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.2.0",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.10.3"
  )

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(game)
  .settings(
    name := "backgammon-web",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      guice,
      ws,
    "com.google.firebase" % "firebase-admin" % "9.2.0",
      "org.apache.pekko" %% "pekko-stream-typed" % "1.0.3",
      // "backgammon" %% "backgammon" % "0.1.0-SNAPSHOT",
      "org.playframework"      %% "play-json"          % "3.0.6",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
      "org.apache.commons" % "commons-text" % "1.12.0",
    )
  )
