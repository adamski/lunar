// Import SBT
import sbt._
import Keys._

// Import Android plugin
import AndroidKeys._

// Import Eclipse plugin
import com.typesafe.sbteclipse.plugin.EclipsePlugin.{
  EclipseKeys,
  EclipseCreateSrc,
  EclipseTransformerFactory}
import com.typesafe.sbteclipse.core.Validation
import EclipseKeys._

// Import Scala XML
import scala.xml.{Node,Elem,UnprefixedAttribute,Text,Null}
import scala.xml.transform.RewriteRule

// Some settings for sbteclipse with sbt-android-plugin and
// AndroidProguardScala.
object AndroidEclipse {

  // Represents the transformation type
  object TransformType extends Enumeration {
    type TransformType = Value
    val Append, Prepend, Replace = Value
  }
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

    // We need Scalaz to use Validation
    import scalaz.Scalaz._

    // Rewrite rule that either prepends, replaces or appends children to any
    // parent element having the tag `elem`.
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

  // This fixes the output directory.
  //
  // SBTEclipse adds a target output directory to each classpath entry inside
  // the .classpath file, but this doesn't play nice with the Android plugin.
  //
  // This Transformer will :
  //   * Remove output="..." from each classpathentry
  //   * Set output="defaultOutput" to the default entry
  class ClasspathOutputFixer (
    defaultOutput: String    // Default output directory
  ) extends EclipseTransformerFactory[RewriteRule] {

    // We need Scalaz
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

  // Output settings that play well with Eclipse and ADT :
  //   * Add managed sources to the Eclipse classpath
  //   * Fix Eclipse output
  //   * Put the resources, manifest and assets to the root dir
  lazy val outputSettings = Seq(
    // We want managed sources in addition to the default settings
    createSrc :=
      EclipseCreateSrc.Default +
      EclipseCreateSrc.Managed,

    // Initialize Eclipse Output to None (output will default to bin/classes)
    eclipseOutput := None,

    // Fix output directories
    classpathTransformerFactories <+= (eclipseOutput) {
      d => d match {
        case Some(s) => new ClasspathOutputFixer(s)
        case None => new ClasspathOutputFixer("bin/classes")
      }
    },

    // Resources, assets and manifest must be at the project root directory
    mainResPath in Android <<=
      (baseDirectory, resDirectoryName in Android) (_ / _) map (x=>x),
    mainAssetsPath in Android <<=
      (baseDirectory, assetsDirectoryName in Android) (_ / _),
    manifestPath in Android <<=
      (baseDirectory, manifestName in Android) map ((b,m) => Seq(b / m)) map (x=>x)
  )

  lazy val naturesSettings = Seq(
    // Set some options inside the project
    projectTransformerFactories ++= Seq(
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

    // Add the Android lib/ folder to the classpath
    classpathTransformerFactories ++= Seq(
      new Transformer[ClasspathContainer]("classpath", Append, Seq(
        "com.android.ide.eclipse.adt.LIBRARIES"
      ))
    )
  )

  // Set default settings
  lazy val settings = Seq (
    classpathTransformerFactories := Seq(),
    projectTransformerFactories := Seq()
  ) ++ outputSettings ++ naturesSettings
}
