package de.wayofquality.blended.mill.modules

import coursier.Dependency
import coursier.core.Organization
import mill.scalalib._

trait BlendedDependencies { deps =>

  def blendedOrg : String = "de.wayofquality.blended"

  // Versions
  def activeMqVersion = "5.15.6"

  def akkaVersion = "2.6.6"
  def akkaHttpVersion = "10.1.12"

  def dominoVersion = "1.1.5"
  def jettyVersion = "9.4.28.v20200408"
  def jolokiaVersion = "1.6.2"
  def microJsonVersion = "1.6"
  def parboiledVersion = "1.1.6"
  def prickleVersion = "1.1.16"
  def scalaJsVersion = "1.1.0"
  def scalaVersion = "2.13.2"
  def scalaBinVersion(scalaVersion: String) = scalaVersion.split("[.]").take(2).mkString(".")
  def scalatestVersion = "3.2.0"
  def scoverageVersion = "1.4.1"
  def slf4jVersion = "1.7.25"
  def sprayVersion = "1.3.5"
  def springVersion = "4.3.12.RELEASE_1"

  def blendedDep(version : String)(module : String) = ivy"$blendedOrg::blended.$module:$version"

  def activationApi = ivy"org.apache.servicemix.specs:org.apache.servicemix.specs.activation-api-1.1:2.2.0"
  def aopAlliance = ivy"org.apache.servicemix.bundles:org.apache.servicemix.bundles.aopalliance:1.0_6"

  def activeMqOsgi = ivy"org.apache.activemq:activemq-osgi:$activeMqVersion"

  def activeMqBroker = ivy"org.apache.activemq:activemq-broker:${activeMqVersion}"
  def activeMqClient = ivy"org.apache.activemq:activemq-client:${activeMqVersion}"
  def activeMqKahadbStore = ivy"org.apache.activemq:activemq-kahadb-store:${activeMqVersion}"
  def activeMqSpring = ivy"org.apache.activemq:activemq-spring:${activeMqVersion}"

  def akka(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaVersion}"
  def akkaHttpModule(m: String) = ivy"com.typesafe.akka::akka-${m}:${akkaHttpVersion}"

  // Convenient method to derive the coordinates for a given Akka or Akka Http jar that has been
  // wrapped by https://github.com/woq-blended/akka-osgi
  def toAkkaBundle(d : Dep)(akkaBundleRevision : String) : Dep = {
    val newMod = d.dep.module.withOrganization(Organization(blendedOrg))
    val newDep : Dependency = d.dep.withVersion(d.dep.version + "." + akkaBundleRevision).withModule(newMod)
    d.copy(dep = newDep)
  }

  def akkaActor : String => Dep = toAkkaBundle(akka("actor"))
  def akkaHttp : String => Dep = toAkkaBundle(akkaHttpModule("http"))
  def akkaHttpCore : String => Dep = toAkkaBundle(akkaHttpModule("http-core"))
  def akkaParsing : String => Dep = toAkkaBundle(akkaHttpModule("parsing"))
  def akkaProtobuf : String => Dep = toAkkaBundle(akka("protobuf"))
  def akkaProtobufV3 : String => Dep = toAkkaBundle(akka("protobuf-v3"))
  def akkaStream : String => Dep = toAkkaBundle(akka("stream"))
  def akkaSlf4j : String => Dep = toAkkaBundle(akka("slf4j"))

  def akkaHttpTestkit = akkaHttpModule("http-testkit")
  def akkaStreamTestkit = akka("stream-testkit")
  def akkaTestkit = akka("testkit")

  def ariesBlueprintApi = ivy"org.apache.aries.blueprint:org.apache.aries.blueprint.api:1.0.1"
  def ariesBlueprintCore = "org.apache.aries.blueprint:org.apache.aries.blueprint.core:1.4.3"
  def ariesJmxApi = ivy"org.apache.aries.jmx:org.apache.aries.jmx.api:1.1.1"
  def ariesJmxCore = ivy"org.apache.aries.jmx:org.apache.aries.jmx.core:1.1.1"
  def ariesProxyApi = ivy"org.apache.aries.proxy:org.apache.aries.proxy.api:1.0.1"
  def ariesUtil = ivy"org.apache.aries:org.apache.aries.util:1.1.0"

  def asciiRender = ivy"com.indvd00m.ascii.render:ascii-render:1.2.3"
  def asmAll = ivy"org.ow2.asm:asm-all:4.1"

  def bouncyCastleBcprov = ivy"org.bouncycastle:bcprov-jdk15on:1.60"
  def bouncyCastlePkix = ivy"org.bouncycastle:bcpkix-jdk15on:1.60"

  def cmdOption = ivy"de.tototec:de.tototec.cmdoption:0.6.0"
  def commonsBeanUtils = ivy"commons-beanutils:commons-beanutils:1.9.3"
  def commonsCodec = ivy"commons-codec:commons-codec:1.11"
  def commonsCollections = ivy"org.apache.commons:com.springsource.org.apache.commons.collections:3.2.1"
  def commonsCompress = ivy"org.apache.commons:commons-compress:1.13"
  def commonsDaemon = ivy"commons-daemon:commons-daemon:1.0.15"
  def commonsDiscovery = ivy"org.apache.commons:com.springsource.org.apache.commons.discovery:0.4.0"
  def commonsExec = ivy"org.apache.commons:commons-exec:1.3"
  def commonsHttpclient = ivy"org.apache.commons:com.springsource.org.apache.commons.httpclient:3.1.0"
  def commonsIo = ivy"commons-io:commons-io:2.6"
  def commonsLang2 = ivy"commons-lang:commons-lang:2.6"
  def commonsLang3 = ivy"org.apache.commons:commons-lang3:3.7"
  def commonsNet = ivy"commons-net:commons-net:3.3"
  def commonsPool2 = ivy"org.apache.commons:commons-pool2:2.6.0"

  def concurrentLinkedHashMapLru = ivy"com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2"

  def dockerJava = ivy"com.github.docker-java:docker-java:3.0.13"
  def domino = ivy"com.github.domino-osgi::domino:${dominoVersion}"

  def eclipseEquinoxConsole = ivy"org.eclipse.platform:org.eclipse.equinox.console:1.4.0"
  def eclipseOsgi = ivy"org.eclipse.platform:org.eclipse.osgi:3.12.50"
  def equinoxServlet = ivy"org.eclipse.platform:org.eclipse.equinox.http.servlet:1.4.0"

  def felixConnect = ivy"org.apache.felix:org.apache.felix.connect:0.1.0"
  def felixConfigAdmin = ivy"org.apache.felix:org.apache.felix.configadmin:1.8.6"
  def felixEventAdmin = ivy"org.apache.felix:org.apache.felix.eventadmin:1.3.2"
  def felixFileinstall = ivy"org.apache.felix:org.apache.felix.fileinstall:3.4.2"
  def felixFramework = ivy"org.apache.felix:org.apache.felix.framework:6.0.2"
  def felixGogoCommand = ivy"org.apache.felix:org.apache.felix.gogo.command:1.1.0"
  def felixGogoJline = ivy"org.apache.felix:org.apache.felix.gogo.jline:1.1.4"
  def felixGogoShell = ivy"org.apache.felix:org.apache.felix.gogo.shell:1.1.2"
  def felixGogoRuntime = ivy"org.apache.felix:org.apache.felix.gogo.runtime:1.1.2"
  def felixHttpApi = ivy"org.apache.felix:org.apache.felix.http.api:3.0.0"
  def felixMetatype = ivy"org.apache.felix:org.apache.felix.metatype:1.0.12"
  def felixShellRemote = ivy"org.apache.felix:org.apache.felix.shell.remote:1.2.0"

  def geronimoJ2eeMgmtSpec = ivy"org.apache.geronimo.specs:geronimo-j2ee-management_1.1_spec:1.0.1"
  def geronimoJms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"
  def geronimoAnnotation = ivy"org.apache.geronimo.specs:geronimo-annotation_1.1_spec:1.0.1"
  def geronimoJaspic = ivy"org.apache.geronimo.specs:geronimo-jaspic_1.0_spec:1.1"

  def hawtioWeb = {
    val dep = ivy"io.hawt:hawtio-web:1.5.11"
    val publ = dep.dep.publication.withType(coursier.core.Type("war"))
    dep.copy(dep = dep.dep.withPublication(publ))
  }

  def h2 = ivy"com.h2database:h2:1.4.197"
  def hikaricp = ivy"com.zaxxer:HikariCP:3.1.0"
  def hsqldb = ivy"hsqldb:hsqldb:1.8.0.7"

  protected def jetty(n: String) = ivy"org.eclipse.jetty:jetty-${n}:$jettyVersion"
  protected def jettyOsgi(n : String) = ivy"org.eclipse.jetty.osgi:jetty-${n}:$jettyVersion"
  def jettyDeploy = jetty("deploy")
  def jettyHttp = jetty("http")
  def jettyHttpService = jettyOsgi("httpservice")
  def jettyIo = jetty("io")
  def jettyJmx = jetty("jmx")
  def jettySecurity = jetty("security")
  def jettyServlet = jetty("servlet")
  def jettyServer = jetty("server")
  def jettyUtil = jetty("util")
  def jettyWebapp = jetty("webapp")
  def jettyXml = jetty("xml")

  def jacksonCore = ivy"com.fasterxml.jackson.core:jackson-core:2.9.3".withDottyCompat(scalaVersion)
  def jacksonBind = ivy"com.fasterxml.jackson.core:jackson-databind:2.9.3"
  def jacksonAnnotations = ivy"com.fasterxml.jackson.core:jackson-annotations:2.9.3"

  def javaxMail = ivy"javax.mail:mail:1.4.5"
  def javaxServlet31 = ivy"org.everit.osgi.bundles:org.everit.osgi.bundles.javax.servlet.api:3.1.0"

  def jaxb = ivy"org.glassfish.jaxb:jaxb-runtime:2.3.1"
  def jcip = ivy"net.jcip:jcip-annotations:1.0"
  def jclOverSlf4j = ivy"org.slf4j:jcl-over-slf4j:${slf4jVersion}"
  def jettyOsgiBoot = jettyOsgi("osgi-boot")
  def jjwt = ivy"io.jsonwebtoken:jjwt:0.7.0"
  def jline = ivy"org.jline:jline:3.9.0"
  def jlineBuiltins = ivy"org.jline:jline-builtins:3.9.0"
  def jms11Spec = ivy"org.apache.geronimo.specs:geronimo-jms_1.1_spec:1.1.1"
  def jolokiaJvm = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion}"
  def jolokiaJvmAgent = ivy"org.jolokia:jolokia-jvm:${jolokiaVersion};classifier=agent"
  def jolokiaOsgi = ivy"org.jolokia:jolokia-osgi:$jolokiaVersion"
  def jscep = ivy"com.google.code.jscep:jscep:2.5.0"
  def jsonLenses = ivy"net.virtual-void::json-lenses:0.6.2"
  def jsr305 = ivy"com.google.code.findbugs:jsr305:3.0.1"
  def julToSlf4j = ivy"org.slf4j:jul-to-slf4j:${slf4jVersion}"
  def junit = ivy"junit:junit:4.13"
  val junitInterface = ivy"com.novocode:junit-interface:0.11"

  def lambdaTest = ivy"de.tototec:de.tobiasroeser.lambdatest:0.6.2"
  def levelDbJava = ivy"org.iq80.leveldb:leveldb:0.9"
  def levelDbJni = ivy"org.fusesource.leveldbjni:leveldbjni-all:1.8"
  def lihaoyiPprint = ivy"com.lihaoyi::pprint:0.5.9"
  def liquibase = ivy"org.liquibase:liquibase-core:3.6.1"
  def logbackCore = ivy"ch.qos.logback:logback-core:1.2.3"
  def logbackClassic = ivy"ch.qos.logback:logback-classic:1.2.3"

  def microjson = ivy"com.github.benhutchison::microjson:${microJsonVersion}"
  def mimepull = ivy"org.jvnet.mimepull:mimepull:1.9.5"
  def mockitoAll = ivy"org.mockito:mockito-all:1.10.19"

  def orgOsgi = ivy"org.osgi:org.osgi.core:6.0.0"
  def orgOsgiCompendium = ivy"org.osgi:org.osgi.compendium:5.0.0"
  def osLib = ivy"com.lihaoyi::os-lib:0.6.3"

  def parboiledCore = ivy"org.parboiled:parboiled-core:${parboiledVersion}"
  def parboiledScala = ivy"org.parboiled::parboiled-scala:${parboiledVersion}"
  def prickle = ivy"com.github.benhutchison::prickle:${prickleVersion}"
  def reactiveStreams = ivy"org.reactivestreams:reactive-streams:1.0.0.final"

  def servicemixStaxApi = ivy"org.apache.servicemix.specs:org.apache.servicemix.specs.stax-api-1.0:2.4.0"

  // SCALA
  def scalaLibrary(scalaVersion: String) = ivy"org.scala-lang:scala-library:${scalaVersion}"
  def scalaReflect(scalaVersion: String) = ivy"org.scala-lang:scala-reflect:${scalaVersion}"
  def scalaCompatJava8 = ivy"org.scala-lang.modules::scala-java8-compat:0.9.1"
  def scalaParser = ivy"org.scala-lang.modules::scala-parser-combinators:1.1.2"
  def scalaXml = ivy"org.scala-lang.modules::scala-xml:1.3.0"

  def scalacheck = ivy"org.scalacheck::scalacheck:1.14.3"
  def scalatest = ivy"org.scalatest::scalatest:${scalatestVersion}"
  def scalatestCore = ivy"org.scalatest::scalatest-core:${scalatestVersion}"
  def scalactic = ivy"org.scalactic::scalactic:${scalatestVersion}"
  def scalatestplusScalacheck = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  def scalatestplusMockito = ivy"org.scalatestplus::mockito-1-10:3.1.0.0"
  def shapeless = ivy"com.chuusai::shapeless:1.2.4"
  def slf4j = ivy"org.slf4j:slf4j-api:${slf4jVersion}"
  def slf4jLog4j12 = ivy"org.slf4j:slf4j-log4j12:${slf4jVersion}"
  def snakeyaml = ivy"org.yaml:snakeyaml:1.18"
  def sprayJson = ivy"io.spray::spray-json:${sprayVersion}"

  //  protected def spring(n: String) = ivy"org.springframework" % s"spring-${n}" % springVersion
  protected def spring(n: String) = ivy"org.apache.servicemix.bundles:org.apache.servicemix.bundles.spring-${n}:${springVersion}"

  def springBeans = spring("beans")
  def springAop = spring("aop")
  def springContext = spring("context")
  def springContextSupport = spring("context-support")
  def springExpression = spring("expression")
  def springCore = spring("core")
  def springJdbc = spring("jdbc")
  def springJms = spring("jms")
  def springTx = spring("tx")

  def sttp = ivy"com.softwaremill.sttp.client::core:2.0.6"
  def sttpAkka = ivy"com.softwaremill.sttp.client::akka-http-backend:2.0.6"

  def typesafeConfig = ivy"com.typesafe:config:1.4.0"
  def typesafeSslConfigCore = ivy"com.typesafe::ssl-config-core:0.4.2"

  // libs for splunk support via HEC
  def splunkjava = ivy"com.splunk.logging:splunk-library-javalogging:1.7.3"
  def httpCore = ivy"org.apache.httpcomponents:httpcore:4.4.9"
  def httpCoreNio = ivy"org.apache.httpcomponents:httpcore:4.4.6"
  def httpComponents = ivy"org.apache.httpcomponents:httpclient:4.5.5"
  def httpAsync = ivy"org.apache.httpcomponents:httpasyncclient:4.1.3"
  def commonsLogging = ivy"commons-logging:commons-logging:1.2"
  def jsonSimple = ivy"com.googlecode.json-simple:json-simple:1.1.1"

  def toJs(dep: Dep) = {
    val base = dep.dep
    ivy"${base.module.organization.value}::${base.module.name.value}::${base.version}"
  }

  object js {
    /** Convert a scala dependency into a scala.js dependency */
    def prickle = toJs(deps.prickle)
    def scalatest = toJs(deps.scalatest)
    def scalacheck = toJs(deps.scalacheck)
    def scalatestplusScalacheck = toJs(deps.scalatestplusScalacheck)
  }

}

object BlendedDependencies {
  def scalaVersions: Map[String, BlendedDependencies] = Seq(Deps_2_13).map(d => d.scalaVersion -> d).toMap
  object Deps_2_13 extends BlendedDependencies
}
