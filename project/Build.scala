import sbt._

import Keys._
import AndroidKeys._
import AndroidNdkKeys._

// Some settings for sbteclipse with sbt-android-plugin and
// AndroidProguardScala
object AndroidEclipse {

  // Represents the transformation type
  object TransformType extends Enumeration {
    type TransformType = Value
    val Append, Prepend, Replace = Value
  }

  // Import stuff
  import scala.xml.{Node,Elem,UnprefixedAttribute,Text,Null}
  import scala.xml.transform.RewriteRule
  import com.typesafe.sbteclipse.plugin.EclipsePlugin.{
    EclipseKeys,
    EclipseCreateSrc,
    EclipseTransformerFactory}
  import com.typesafe.sbteclipse.core.Validation
  import EclipseKeys._
  import TransformType._

  // Represent an Eclipse configuration object
  trait ProjectObject { def toXml: Node }

  // Represents an Eclipse nature
  case class Nature(name: String) extends ProjectObject {
    def toXml = <nature>{name}</nature>
  }

  // Represents an Eclipse builder object
  case class Builder(name: String) extends ProjectObject {
    def toXml =
      <buildCommand>
        <name>{name}</name>
        <arguments></arguments>
      </buildCommand>
  }

  case class ClasspathEntry(kind: String, path: String)
  extends ProjectObject {
    def toXml =
      <classpathentry kind={kind} path={path}/>
  }
  case class ClasspathContainer(override val path: String) extends ClasspathEntry("con", path)
  case class ClasspathSource(override val path: String) extends ClasspathEntry("src", path)

  // Automatically convert strings to project objects
  implicit def string2Nature(n: String) = Nature(n)
  implicit def string2Builder(n: String) = Builder(n)
  implicit def string2ClasspathContainer(n: String) = ClasspathContainer(n)
  implicit def string2ClasspathSource(n: String) = ClasspathSource(n)

  // Create a transformer factory to add objects to a project
  class Transformer[T <: ProjectObject](
    elem: String,        // XML parent element name
    mode: TransformType, // Should we append, prepend or replace?
    objs: Seq[T]         // Objects to append/prepend/replace
  ) extends EclipseTransformerFactory[RewriteRule] {
    import scalaz.Scalaz._

    object Rule extends RewriteRule {
      override def transform(node: Node): Seq[Node] = node match {
        // Check if this is the right parent element
        case Elem(pf, el, attrs, scope, children @ _*) if (el == elem) => {
          // If it is, then create `new_children` with the transform type
          val v = objs map { b => b.toXml }
          val new_children = mode match {
            case Prepend => v ++ children
            case Append => children ++ v
            case Replace => v
          }

          // And return a new XML element
          Elem(pf, el, attrs, scope, new_children: _*)
        }

        // If it is not, return the same element
        case other => other
      }
    }

    // Return a new transformer object
    override def createTransformer(
      ref: ProjectRef,
      state: State): Validation[RewriteRule] = {
      Rule.success
    }
  }

  class ClasspathOutputFixer (
    defaultOutput: String
  ) extends EclipseTransformerFactory[RewriteRule] {
    import scalaz.Scalaz._

    object Rule extends RewriteRule {
      override def transform(node: Node): Seq[Node] = node match {
        // Change the output to defaultOutput
        case Elem(_, "classpathentry", _, _, _*)
          if (node \ "@kind" text) == "output" =>
            (node.asInstanceOf[Elem] %
              new UnprefixedAttribute("path", Text(defaultOutput), Null))

        // Remove other output dirs
        case Elem(pf, "classpathentry", attrs, scope, children @ _*) =>
          Elem(pf, "classpathentry", attrs remove "output", scope, children:_*)

        case other => other
      }
    }

    override def createTransformer(
      ref: ProjectRef,
      state: State): Validation[RewriteRule] = {
      Rule.success
    }
  }

  // Set default settings
  lazy val settings = Seq(
    // We want managed sources in addition to the default settings
    createSrc :=
      EclipseCreateSrc.Default +
      EclipseCreateSrc.Managed,

    // ADT requires the output to be inside bin/classes
    //eclipseOutput := Some("bin/classes"),

    // Resources, assets and manifest must be at the project root directory
    mainResPath in Android <<=
      (baseDirectory, resDirectoryName in Android) (_ / _) map (x=>x),
    mainAssetsPath in Android <<=
      (baseDirectory, assetsDirectoryName in Android) (_ / _),
    manifestPath in Android <<=
      (baseDirectory, manifestName in Android) map ((s,m) => Seq(s / m)) map (x=>x),

    // Set some options inside the project
    projectTransformerFactories := Seq(
      // Add Android and AndroidProguardScala natures
      new Transformer[Nature]("natures", Append, Seq(
        "com.android.ide.eclipse.adt.AndroidNature",
        "com.restphone.androidproguardscala.Nature"
      )),

      // Add resource builder before everything else
      new Transformer[Builder]("buildSpec", Prepend, Seq(
        "com.android.ide.eclipse.adt.ResourceManagerBuilder"
      )),

      // Add proguard, pre-compiler and apk builder after everything else
      new Transformer[Builder]("buildSpec", Append, Seq(
        "com.restphone.androidproguardscala.Builder",
        "com.android.ide.eclipse.adt.PreCompilerBuilder",
        "com.android.ide.eclipse.adt.ApkBuilder"
      ))
    ),

    // Set some additional clasaspath options
    classpathTransformerFactories := Seq(
      new Transformer[ClasspathContainer]("classpath", Append, Seq(
        "com.android.ide.eclipse.adt.LIBRARIES"
      )),
      new ClasspathOutputFixer("bin/classes")
    )
  )
}

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Lunar",
    version := "0.1",
    versionCode := 0,
    scalaVersion := "2.10.0-RC3",
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
