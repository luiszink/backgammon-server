val scala3Version = "3.7.3"

lazy val game = (project in file("game"))
  .settings(
    scalaVersion := scala3Version
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
      "org.apache.pekko" %% "pekko-stream-typed" % "1.0.3",
      "org.playframework"      %% "play-json"          % "3.0.6",
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test,
      "org.apache.commons" % "commons-text" % "1.12.0",
    )
  )
