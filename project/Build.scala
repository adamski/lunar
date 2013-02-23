import sbt._

import Keys._
import AndroidKeys._
import AndroidNdkKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Lunar",
    version := "0.1",
    versionCode := 0,
    scalaVersion := "2.10.0",
    platformName in Android := "android-16"
  )

  lazy val proguardSettings = inConfig(Android) (Seq (
    useProguard := true,
    proguardOptimizations ++= Seq(
      """-keep class com.github.fxthomas.lunar.** {
           void set*(***);
           *** get*();
      }""",
      "-keepattributes EnclosingMethod",
      "-keep class scala.collection.SeqLike { public protected *; }"
    )
  ))

  lazy val ndkSettings = inConfig(Android) (Seq(
    jniClasses += "com.github.fxthomas.lunar.Song$",
    javahOutputFile := Some(new File("native.h"))
  ))

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    AndroidNdk.settings ++
    TypedResources.settings ++
    proguardSettings ++
    ndkSettings ++
    AndroidEclipse.settings ++
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
