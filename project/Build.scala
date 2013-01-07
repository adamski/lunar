import sbt._

import Keys._
import AndroidKeys._
import AndroidNdkKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Lunar",
    version := "0.1",
    versionCode := 0,
    scalaVersion := "2.10.0-RC3",
    platformName in Android := "android-16"
  )

  val proguardSettings = Seq (
    useProguard in Android := true,
    proguardOptimizations in Android ++= Seq(
      """-keep class com.github.fxthomas.lunar.** {
           void set*(***);
           *** get*();
      }""",
      "-keepattributes EnclosingMethod",
      "-keep class scala.collection.SeqLike { public protected *; }"
    )
  )

  val ndkSettings = Seq(
    jniClasses in Android += "com.github.fxthomas.lunar.Song",
    javahOutputFile in Android := Some(new File("native.h"))
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    AndroidNdk.settings ++
    TypedResources.settings ++
    proguardSettings ++
    ndkSettings ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "change-me",
      libraryDependencies += "org.scalatest" % "scalatest_2.10.0-RC3" % "1.8-B1" % "test"
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "Lunar",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++
               AndroidTest.androidSettings ++
               General.proguardSettings ++ Seq (
      name := "LunarTests"
    )
  ) dependsOn main
}
