package de.wayofquality.blended.mill.modules

import com.goyeau.mill.scalafix.ScalafixModule
import mill.{Agg, PathRef, T}
import mill.api.{Loose, Result}
import mill.define.{Command, Sources, Target}
import mill.scalalib.{Dep, JavaModule, Lib, SbtModule, TestRunner}
import mill.scalalib.api.CompilationResult
import sbt.testing.{Fingerprint, Framework}
import mill.contrib.scoverage.ScoverageModule

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

trait BlendedBaseModule
  extends SbtModule
  with ScoverageModule
  with ScalafixModule { blendedModuleBase =>

  type ProjectDeps <: BlendedDependencies
  def deps : ProjectDeps
  def baseDir : os.Path

  /** The blended module name. */
  def blendedModule: String = millModuleSegments.parts.filterNot(_ == deps.scalaVersion).mkString(".")
  override def artifactName: T[String] = blendedModule

  def scalaBinVersion = T { scalaVersion().split("[.]").take(2).mkString(".") }

  def exportPackages: Seq[String] = Seq(blendedModule)
  def essentialImportPackage: Seq[String] = Seq()

  override def millSourcePath: os.Path = baseDir / blendedModule

  override def resources = T.sources { super.resources() ++ Seq(
    PathRef(millSourcePath / "src" / "main" / "binaryResources")
  )}

  override def scalacOptions = Seq(
    "--deprecation",
    "--target:8",
    "-Werror",
    "--feature",
    Seq(
      "adapted-args",
      "constant",
      "deprecation",
      "doc-detached",
      "inaccessible",
      "infer-any",
      "missing-interpolator",
      "nullary-override",
      "nullary-unit",
      "option-implicit",
      "poly-implicit-overload",
      "stars-align",
      // Compiler doesn't know it but suggests it: "Recompile with -Xlint:unchecked for details."
      // "unchecked",
      "unused",
    ).mkString("-Xlint:", ",", ""),
    //    "--unchecked"
  )

  override def javacOptions = Seq(
    "-Xlint:unchecked"
  )

  override def scalaDocOptions: Target[Seq[String]] = T {
    scalacOptions().filter(_ != "-Werror")
  }

  override def scoverageVersion = deps.scoverageVersion

  def mapToScoverageModule(m: JavaModule) = m match {
    case module: ScoverageModule =>
      // instead of depending on the base module, we depend on it's inner scoverage module
      module.scoverage
    case module => module
  }

  override val scoverage: BlendedScoverageData = new BlendedScoverageData(implicitly)
  class BlendedScoverageData(ctx0: mill.define.Ctx) extends super.ScoverageData(ctx0) with JavaModule {
//    // we ensure, out scoverage enhancer is also a valid OSGi module to drop-in replace it in all tests
//    override def bundleSymbolicName: T[String] = T{ blendedModuleBase.bundleSymbolicName() }
//    override def bundleVersion: T[String] = T{ blendedModuleBase.bundleVersion() }
//    override def osgiHeaders: T[OsgiHeaders] = T{ blendedModuleBase.osgiHeaders() }
//    override def reproducibleBundle: T[Boolean] = T{ blendedModuleBase.reproducibleBundle() }
//    override def embeddedJars: T[Seq[PathRef]] = T{ blendedModuleBase.embeddedJars() }
//    override def explodedJars: T[Seq[PathRef]] = T{ blendedModuleBase.explodedJars() }
    override def moduleDeps: Seq[JavaModule] = blendedModuleBase.moduleDeps.map(mapToScoverageModule)
    override def recursiveModuleDeps: Seq[JavaModule] = blendedModuleBase.recursiveModuleDeps.map(mapToScoverageModule)
  }

  trait BlendedTests extends super.Tests with super.ScoverageTests {
    /** Ensure we don't include the non-scoverage-enhanced classes. */
    //    override def moduleDeps: Seq[JavaModule] = super.moduleDeps.filterNot(_ == blendedModuleBase)
    override def moduleDeps: Seq[JavaModule] = super.moduleDeps.map(mapToScoverageModule)
    override def recursiveModuleDeps: Seq[JavaModule] = super.recursiveModuleDeps.map(mapToScoverageModule)
    override def transitiveLocalClasspath: T[Loose.Agg[PathRef]] = T { T.traverse(recursiveModuleDeps)(m => m.jar)() }
    override def ivyDeps = T{ super.ivyDeps() ++ Agg(
      deps.scalatest
    )}
    override def testFrameworks = Seq("org.scalatest.tools.Framework")
    /** Empty, we use [[testResources]] instead to model sbt behavior. */
    override def runIvyDeps: Target[Loose.Agg[Dep]] = T{ super.runIvyDeps() ++ Agg(
      deps.logbackClassic,
      deps.jclOverSlf4j
    )}
    override def resources = T.sources { Seq.empty[PathRef] }

    def testResources: Sources = T.sources(
      millSourcePath / "src" / "test" / "resources",
      millSourcePath / "src" / "test" / "binaryResources"
    )
    // TODO: set projectTestOutput property to resources directory
    /** Used by all tests, e.g. for logback config. */
    def logResources = T {
      val moduleSpec = toString()
      val dest = T.ctx().dest
      val logConfig =
        s"""<configuration>
           |
           |  <appender name="FILE" class="ch.qos.logback.core.FileAppender">
           |    <file>${baseDir.toString()}/out/testlog-${System.getProperty("java.version")}-${scalaBinVersion()}/test-${moduleSpec}.log</file>
           |
           |    <encoder>
           |      <pattern>%d{yyyy-MM-dd-HH:mm.ss.SSS} | %8.8r | %-5level [%t] %logger : %msg%n</pattern>
           |    </encoder>
           |  </appender>
           |
           |  <logger name="blended" level="debug" />
           |  <logger name="domino" level="debug" />
           |  <logger name="App" level="debug" />
           |
           |  <root level="INFO">
           |    <appender-ref ref="FILE" />
           |  </root>
           |
           |</configuration>
           |""".stripMargin
      os.write(dest / "logback-test.xml", logConfig)
      PathRef(dest)
    }
    /** A T.input, because this needs to run always to be always fresh, as we intend to write into that dir when executing tests.
     * This is in migration from sbt-like setup.
     */
    def copiedResources: T[PathRef] = T.input {
      val dest = T.dest
      testResources().foreach { p =>
        if(os.exists(p.path)) {
          if(os.isDir(p.path)) {
            os.list(p.path).foreach { p1 =>
              os.copy.into(p1, dest)
            }
          }
          else {
            os.copy.over(p.path, dest)
          }
        }
      }
      PathRef(dest)
    }
    override def runClasspath: Target[Seq[PathRef]] = T{ super.runClasspath() ++ Seq(logResources(), copiedResources()) }
    override def forkArgs: Target[Seq[String]] = T{ super.forkArgs() ++ Seq(
      s"-DprojectTestOutput=${copiedResources().path.toString()}"
    )}
  }

  val otherTestGroup = "other"
  /** A Map of groups with their belonging test suites.
   * The groups name  [[otherTestGroup]] is reserved for all tests that don't need to run in an extra JVM. */
  def testGroups: Map[String, Set[String]] = Map()
  /** Test group names, derived from [[testGroups]]. */
  def crossTestGroups: Seq[String] = (Set(otherTestGroup) ++ testGroups.keySet).toSeq

  /** A test module that only executed the tests from the configured [[ForkedTest#testGroup]]. */
  trait BlendedForkedTests extends BlendedTests {

    def testGroup: String = otherTestGroup

    def detectTestGroups: T[Map[String, Set[String]]] = T {
      if(testFrameworks() != Seq("org.scalatest.tools.Framework")) {
        Result.Failure("Unsupported test framework set")
      } else {
        val testClasspath = runClasspath().map(_.path)
        val cl = new URLClassLoader(testClasspath.map(_.toNIO.toUri().toURL()), getClass().getClassLoader())
        val framework: Framework = TestRunner.frameworks(testFrameworks())(cl).head
        val testClasses: Loose.Agg[(Class[_], Fingerprint)] = Lib.discoverTests(cl, framework, Agg(compile().classes.path))
        val groups: Map[String, List[(Class[_], Fingerprint)]] = testClasses.iterator.to(List).groupBy { case (cl, fp) =>
          cl.getAnnotations().map{ anno =>
            //            println(s"anno: ${anno}, name: ${anno.getClass().getName()}")
          }
          val isFork = cl.getAnnotations().exists(a => a.toString().contains("blended.testsupport.RequiresForkedJVM") || a.getClass().getName().contains("blended.testsupport.RequiresForkedJVM"))
          if(isFork) cl.getName()
          else otherTestGroup
        }
        val groupNames: Map[String, Set[String]] = groups.view.mapValues(_.map(_._1.getName()).toSet).toMap
        cl.close()
        Result.Success(groupNames)
      }
    }

    /** redirect to "other"-compile */
    override def compile: T[CompilationResult] = if(testGroup == otherTestGroup) super.compile else otherModule.compile
    /** Override this to define the target which compiles the test sources */
    def otherModule: BlendedForkedTests

    def checkTestGroups(): Command[Unit] = T.command {
      if(testGroup == otherTestGroup) T{
        // only check in default cross instance "other"

        // First we check if we have detected any other groups than the default group
        val anyGroupsDetected : Boolean = detectTestGroups().keySet != Set(otherTestGroup)

        // Then we check if we have any groups defined in the build.sc
        val anyGroupsDefined = testGroups.nonEmpty

        if (anyGroupsDetected || anyGroupsDefined) {
          val relevantGroups: PartialFunction[(String, Set[String]), Set[String]] = {
            case (name, tests) if name != otherTestGroup => tests
          }

          val definedGroupedTests : Set[Set[String]] = testGroups.collect(relevantGroups).toSet
          val detectedGroupedTests : Set[Set[String]] = detectTestGroups().collect(relevantGroups).toSet

          val definedUndetected : Set[Set[String]] = definedGroupedTests.diff(detectedGroupedTests)
          val detectedUndefined : Set[Set[String]] = detectedGroupedTests.diff(definedGroupedTests)

          if (detectedUndefined.nonEmpty) {
            T.log.error(s"The following test groups are detected, but do not occurr in the build file:\n${detectedUndefined.mkString("\n")}")
          }
          if (definedUndetected.nonEmpty) {
            T.log.error(s"The following test groups are defined, but are missing the RequiredForkedJVM annotation:\n${definedUndetected.mkString("\n")}")
          }
        }
      } else T{
        // depend on the other check
        otherModule.checkTestGroups()()
      }
    }

    override def test(args: String*): Command[(String, Seq[TestRunner.Result])] =
      if(args.isEmpty) T.command{
        super.testTask(testCachedArgs)()
      }
      else super.test(args: _*)

    override def testCachedArgs: T[Seq[String]] = T{
      checkTestGroups()()
      val tests = if(testGroup == otherTestGroup && testGroups.get(otherTestGroup).isEmpty) {
        val allTests = detectTestGroups().values.toSet.flatten
        val groupTests = testGroups.values.toSet.flatten
        allTests -- groupTests
      } else {
        testGroups(testGroup)
      }
      T.log.debug(s"tests: ${tests}")
      if(tests.isEmpty) {
        Seq("-w", "NON_TESTS")
      } else {
        tests.toSeq.flatMap(tc => Seq("-w", tc))
      }
    }
  }
}
