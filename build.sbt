import com.typesafe.sbt.license.{DepLicense, DepModuleInfo, LicenseInfo}
import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName                   = "Mellite"
lazy val baseNameL                  = baseName.toLowerCase
lazy val appDescription             = "A computer music application based on SoundProcesses"
lazy val commonVersion              = "3.2.3-SNAPSHOT"
lazy val mimaCommonVersion          = "3.2.1"
lazy val appVersion                 = "3.2.3-SNAPSHOT"
lazy val mimaAppVersion             = "3.2.1"

lazy val loggingEnabled             = true

lazy val authorName                 = "Hanns Holger Rutz"
lazy val authorEMail                = "contact@sciss.de"

// ---- dependencies ----

lazy val deps = new {
  val common = new {
    val asyncFile           = "0.1.2"
    val audioFile           = "2.3.1"
    val audioWidgets        = "2.3.0"
    val desktop             = "0.11.3"
    val equal               = "0.1.6"
    val fileUtil            = "1.1.5"
    val lucre               = "4.2.0"
    val lucreSwing          = "2.4.1"
    val model               = "0.3.5"
    val numbers             = "0.2.1"
    val processor           = "0.5.0"
    val raphaelIcons        = "1.0.7"
    val scalaCollider       = "2.4.0"
    val scalaColliderUGens  = "1.20.1"
    val scalaOSC            = "1.2.3"
    val scalaSTM            = "0.11.0"
    val scalaSwing          = "3.0.0"
    val scallop             = "3.5.1"
    val serial              = "2.0.0"
    val sonogram            = "2.2.0"
    val soundProcesses      = "4.4.1"
    val span                = "2.0.0"
    val swingPlus           = "0.5.0"
  }
  val app = new {
    val akka                = "2.6.10"
    val appDirs             = "1.2.0"
    val dejaVuFonts         = "2.37"    // directly included
    val dotterweide         = "0.4.0"
    val fileCache           = "1.1.0"
    val fingerTree          = "1.5.5"
    val freesound           = "2.2.0"
    val fscape              = "3.3.1"
    val interpreterPane     = "1.11.0"
//    val jline               = "2.14.6"
    val jump3r              = "1.0.5"
    val kollFlitz           = "0.2.4"
    val linKernighan        = "0.1.3"
    val lucrePi             = "1.2.0"
    val negatum             = "1.2.0"
    val patterns            = "1.2.0"
    val pdflitz             = "1.5.0"
    val pegDown             = "1.6.0"
//    val playJSON            = "0.4.0"
//    val plexMono            = "4.0.2"   // directly included
    val scalaColliderSwing  = "2.4.0"
    val scissDSP            = "2.2.0"
    val slf4j               = "1.7.30"
    val submin              = "0.3.4"
    val syntaxPane          = "1.2.0"
    val treeTable           = "1.6.1"
    val topology            = "1.1.3"
    // val webLaF              = "2.2.1"
    val webLaF              = "1.2.11"
    val wolkenpumpe         = "3.2.0"
  }
}

lazy val bdb = "bdb"

// ---- app packaging ----

lazy val appMainClass               = Some("de.sciss.mellite.Mellite")
def appName                         = baseName
def appNameL                        = baseNameL

// ---- common ----

lazy val commonSettings = Seq(
  organization       := "de.sciss",
  homepage           := Some(url(s"https://sciss.de/$baseNameL")),
  licenses           := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  scalaVersion       := "2.13.4",
  crossScalaVersions := Seq("2.13.4", "2.12.12"),  // N.B. nsc API has breakage in minor versions (2.13.0 Dotterweide fails on 2.13.1)
  scalacOptions ++= {
    val xs = Seq(
      "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint:-stars-align,_", "-Xsource:2.13"
    )
    if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
  },
  scalacOptions /* in (Compile, compile) */ ++= {
    val sq0 = if (scala.util.Properties.isJavaAtLeast("9")) Seq("-release", "8") else Nil // JDK >8 breaks API; skip scala-doc
    val sq1 = if (VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector(">=2.13"))) Seq("-Wconf:cat=deprecation&msg=Widening conversion:s") else Nil // nanny state defaults :-E
    sq1
  },
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  // resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/", // https://stackoverflow.com/questions/23979577
  // resolvers += "Typesafe Simple Repository" at "http://repo.typesafe.com/typesafe/simple/maven-releases/", // https://stackoverflow.com/questions/20497271
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  aggregate in assembly := false,
  // ---- publishing ----
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
  <scm>
    <url>git@git.iem.at:sciss/{n}.git</url>
    <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
  </scm>
    <developers>
      <developer>
        <id>sciss</id>
        <name>Hanns Holger Rutz</name>
        <url>http://www.sciss.de</url>
      </developer>
    </developers>
  }
)

// ---- packaging ----

//////////////// universal (directory) installer
lazy val pkgUniversalSettings = Seq(
  executableScriptName /* in Universal */ := appNameL,
  // NOTE: doesn't work on Windows, where we have to
  // provide manual file `MELLITE_config.txt` instead!
// NOTE: This workaround for #70 is incompatible with Java 11...
// instead recommend to users of Linux with JDK 8 to create
// `~/.accessibility.properties`
//
//  javaOptions in Universal ++= Seq(
//    // -J params will be added as jvm parameters
//    // "-J-Xmx1024m",
//    // others will be added as app parameters
//    "-Djavax.accessibility.assistive_technologies=",  // work around for #70
//  ),
  // Since our class path is very very long,
  // we use instead the wild-card, supported
  // by Java 6+. In the packaged script this
  // results in something like `java -cp "../lib/*" ...`.
  // NOTE: `in Universal` does not work. It therefore
  // also affects debian package building :-/
  // We need this settings for Windows.
  scriptClasspath /* in Universal */ := Seq("*"),
  name                      in Linux     := appName,
  packageName               in Linux     := appNameL, // XXX TODO -- what was this for?
  mainClass                 in Universal := appMainClass,
  maintainer                in Universal := s"$authorName <$authorEMail>",
  target      in Universal := (target in Compile).value,
)

//////////////// debian installer
lazy val pkgDebianSettings = Seq(
  packageName               in Debian := appNameL,  // this is the installed package (e.g. in `apt remove <name>`).
  packageSummary            in Debian := appDescription,
  mainClass                 in Debian := appMainClass,
  maintainer                in Debian := s"$authorName <$authorEMail>",
  packageDescription        in Debian :=
    """Mellite is a computer music environment,
      | a desktop application based on SoundProcesses.
      | It manages workspaces of musical objects, including
      | sound processes, timelines, code fragments, or
      | live improvisation sets.
      |""".stripMargin,
  // include all files in src/debian in the installed base directory
  linuxPackageMappings      in Debian ++= {
    val n     = appNameL // (name in Debian).value.toLowerCase
    val dir   = (sourceDirectory in Debian).value / "debian"
    val f1    = (dir * "*").filter(_.isFile).get  // direct child files inside `debian` folder
    val f2    = ((dir / "doc") * "*").get
    //
    def readOnly(in: LinuxPackageMapping) =
      in.withUser ("root")
        .withGroup("root")
        .withPerms("0644")  // http://help.unc.edu/help/how-to-use-unix-and-linux-file-permissions/
    //
    val aux   = f1.map { fIn => packageMapping(fIn -> s"/usr/share/$n/${fIn.name}") }
    val doc   = f2.map { fIn => packageMapping(fIn -> s"/usr/share/doc/$n/${fIn.name}") }
    (aux ++ doc).map(readOnly)
  }
)

//////////////// fat-jar assembly
lazy val assemblySettings = Seq(
    mainClass             in assembly := appMainClass,
    target                in assembly := baseDirectory.value,
    assemblyJarName       in assembly := s"$baseName.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("org", "xmlpull", _ @ _*) => MergeStrategy.first
      case PathList("org", "w3c", "dom", "events", _ @ _*) => MergeStrategy.first // bloody Apache Batik
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

// ---- projects ----

lazy val root = project.withId(baseNameL).in(file("."))
  .aggregate(core, app, full)
  .dependsOn(core, app)
  .settings(commonSettings)
  .settings(
    name    := baseName,
    version := appVersion,
    publishArtifact in(Compile, packageBin) := false, // there are no binaries
    publishArtifact in(Compile, packageDoc) := false, // there are no javadocs
    publishArtifact in(Compile, packageSrc) := false, // there are no sources
    // packagedArtifacts := Map.empty
    autoScalaLibrary := false
  )

lazy val core = project.withId(s"$baseNameL-core").in(file("core"))
  .settings(commonSettings)
  .settings(
    name        := s"$baseName-core",
    version     := commonVersion,
    description := s"$baseName - core library",
    libraryDependencies ++= Seq(
      "de.sciss"          %% "asyncfile"                      % deps.common.asyncFile,          // file I/O
      "de.sciss"          %% "audiofile"                      % deps.common.audioFile,          // audio file I/O
      "de.sciss"          %% "audiowidgets-app"               % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-core"              % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-swing"             % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "desktop-core"                   % deps.common.desktop,            // support for desktop applications 
      "de.sciss"          %% "equal"                          % deps.common.equal,              // type-safe equals operator
      "de.sciss"          %% "fileutil"                       % deps.common.fileUtil,           // utility methods for file paths
      "de.sciss"          %% "lucre-base"                     % deps.common.lucre,              // transactional objects
      "de.sciss"          %% "lucre-confluent"                % deps.common.lucre,              // transactional objects
      "de.sciss"          %% "lucre-core"                     % deps.common.lucre,              // transactional objects
      "de.sciss"          %% "lucre-expr"                     % deps.common.lucre,              // transactional objects
      "de.sciss"          %% "lucre-swing"                    % deps.common.lucreSwing,         // reactive Swing components
      "de.sciss"          %% "lucre-synth"                    % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "model"                          % deps.common.model,              // publisher-subscriber library
      "de.sciss"          %% "numbers"                        % deps.common.numbers,            // extension methods for numbers
      "de.sciss"          %% "processor"                      % deps.common.processor,          // futures with progress and cancel
      "de.sciss"          %% "raphael-icons"                  % deps.common.raphaelIcons,       // icon set
      "de.sciss"          %% "scalaosc"                       % deps.common.scalaOSC,           // open sound control
      "de.sciss"          %% "scalacollider"                  % deps.common.scalaCollider,      // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-api"         % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "serial"                         % deps.common.serial,             // serialization
      "de.sciss"          %% "sonogramoverview"               % deps.common.sonogram,           // sonogram component
      "de.sciss"          %% "soundprocesses-core"            % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "soundprocesses-views"           % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "span"                           % deps.common.span,               // time spans
      "de.sciss"          %% "swingplus"                      % deps.common.swingPlus,          // Swing extensions
      "org.rogach"        %% "scallop"                        % deps.common.scallop,            // command line option parsing
      "org.scala-lang.modules" %% "scala-swing"               % deps.common.scalaSwing,         // desktop UI kit
      "org.scala-stm"     %% "scala-stm"                      % deps.common.scalaSTM,           // software transactional memory
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-core" % mimaCommonVersion),
  )

lazy val appSettings = Seq(
  description := appDescription,
  mainClass in Compile := appMainClass,
)

lazy val app = project.withId(s"$baseNameL-app").in(file("app"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(pkgUniversalSettings)
  .settings(pkgDebianSettings)
  .settings(useNativeZip) // cf. https://github.com/sbt/sbt-native-packager/issues/334
  .settings(assemblySettings)
  .settings(appSettings)
  .settings(
    name    := s"$baseName-app",
    version := appVersion,
    // resolvers += "Oracle Repository" at "http://download.oracle.com/maven", // required for sleepycat
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %%  "akka-stream"         % deps.app.akka,
      "com.typesafe.akka" %%  "akka-stream-testkit" % deps.app.akka,
      "de.sciss"          %% "audiofile"                      % deps.common.audioFile,          // reading/writing audio files
      "de.sciss"          %% "audiowidgets-app"               % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-core"              % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-swing"             % deps.common.audioWidgets,       // audio application widgets
      "de.sciss"          %% "desktop-core"                   % deps.common.desktop,            // support for desktop applications 
      "de.sciss"          %% "desktop-linux"                  % deps.common.desktop,            // support for desktop applications 
      "de.sciss"          %% "dotterweide-core"               % deps.app.dotterweide,           // Code editor
      "de.sciss"          %% "dotterweide-doc-browser"        % deps.app.dotterweide,           // Code editor
      "de.sciss"          %% "dotterweide-scala"              % deps.app.dotterweide,           // Code editor
      "de.sciss"          %% "dotterweide-ui"                 % deps.app.dotterweide,           // Code editor
      "de.sciss"          %% "desktop-mac"                    % deps.common.desktop,            // support for desktop applications 
      "de.sciss"          %% "equal"                          % deps.common.equal,              // type-safe equals
      "de.sciss"          %% "filecache-common"               % deps.app.fileCache,             // caching data to disk
      "de.sciss"          %% "fileutil"                       % deps.common.fileUtil,           // extension methods for files
      "de.sciss"          %% "fingertree"                     % deps.app.fingerTree,            // data structures
      "de.sciss"          %% "fscape-core"                    % deps.app.fscape,                // offline audio rendering
      "de.sciss"          %% "fscape-lucre"                   % deps.app.fscape,                // offline audio rendering
      "de.sciss"          %% "fscape-views"                   % deps.app.fscape,                // offline audio rendering
      "de.sciss"          %  "jump3r"                         % deps.app.jump3r,                // mp3 export
      "de.sciss"          %% "kollflitz"                      % deps.app.kollFlitz,             // more collections methods
      "de.sciss"          %% "linkernighantsp"                % deps.app.linKernighan,          // used by FScape
      "de.sciss"          %% "lucre-adjunct"                  % deps.common.lucre,              // object system
      "de.sciss"          %% "lucre-base"                     % deps.common.lucre,              // object system
      "de.sciss"          %% s"lucre-$bdb"                    % deps.common.lucre,              // object system (database backend)
      "de.sciss"          %% "lucre-confluent"                % deps.common.lucre,              // object system
      "de.sciss"          %% "lucre-core"                     % deps.common.lucre,              // object system
      "de.sciss"          %% "lucre-expr"                     % deps.common.lucre,              // object system
      "de.sciss"          %% "lucre-pi"                       % deps.app.lucrePi,               // Raspberry Pi support
      "de.sciss"          %% "lucre-swing"                    % deps.common.lucreSwing,         // reactive Swing components
      "de.sciss"          %% "lucre-synth"                    % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "model"                          % deps.common.model,              // non-txn MVC
      "de.sciss"          %% "negatum-core"                   % deps.app.negatum,               // genetic programming of sounds
      "de.sciss"          %% "negatum-views"                  % deps.app.negatum,               // genetic programming of sounds
      "de.sciss"          %% "numbers"                        % deps.common.numbers,            // extension methods for numbers
      "de.sciss"          %% "patterns-core"                  % deps.app.patterns,              // pattern sequences
      "de.sciss"          %% "patterns-lucre"                 % deps.app.patterns,              // pattern sequences
      "de.sciss"          %% "processor"                      % deps.common.processor,          // futures with progress and cancel
      "de.sciss"          %% "pdflitz"                        % deps.app.pdflitz,               // PDF export
      "de.sciss"          %% "scalacollider"                  % deps.common.scalaCollider,      // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-api"         % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-core"        % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-plugins"     % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %  "scalacolliderugens-spec"        % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacolliderswing-core"        % deps.app.scalaColliderSwing,    // UI methods for scala-collider
      "de.sciss"          %% "scalainterpreterpane"           % deps.app.interpreterPane,       // REPL
      "de.sciss"          %% "scalafreesound-lucre"           % deps.app.freesound,             // Freesound support
      "de.sciss"          %% "scalafreesound-views"           % deps.app.freesound,             // Freesound support
      "de.sciss"          %% "scalaosc"                       % deps.common.scalaOSC,           // open sound control
      "de.sciss"          %% "scissdsp"                       % deps.app.scissDSP,              // offline signal processing
      "de.sciss"          %% "serial"                         % deps.common.serial,             // serialization
      "de.sciss"          %% "sonogramoverview"               % deps.common.sonogram,           // sonogram component
      "de.sciss"          %% "soundprocesses-compiler"        % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "soundprocesses-core"            % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "soundprocesses-views"           % deps.common.soundProcesses,     // computer-music framework
      "de.sciss"          %% "span"                           % deps.common.span,               // time spans
      "de.sciss"          %  "submin"                         % deps.app.submin,                // dark skin
      "de.sciss"          %  "syntaxpane"                     % deps.app.syntaxPane,            // code editor
      "de.sciss"          %% "swingplus"                      % deps.common.swingPlus,          // Swing extensions
      "de.sciss"          %% "topology"                       % deps.app.topology,              // graph sorting
      "de.sciss"          %  "treetable-java"                 % deps.app.treeTable,             // widget
      "de.sciss"          %% "treetable-scala"                % deps.app.treeTable,             // widget
//      "de.sciss"          %  "weblaf-core"                    % deps.app.webLaF,                // look-and-feel
//      "de.sciss"          %  "weblaf-ui"                      % deps.app.webLaF,                // look-and-feel
      "com.weblookandfeel" % "weblaf-core"     % deps.app.webLaF,
      "com.weblookandfeel" % "weblaf-ui"       % deps.app.webLaF,
      "de.sciss"          %% "wolkenpumpe-basic"              % deps.app.wolkenpumpe,           // live improvisation
      "de.sciss"          %% "wolkenpumpe-core"               % deps.app.wolkenpumpe,           // live improvisation
      "net.harawata"      %  "appdirs"                        % deps.app.appDirs,               // finding cache directory
      "org.pegdown"       %  "pegdown"                        % deps.app.pegDown,               // Markdown renderer
      "org.rogach"        %% "scallop"                        % deps.common.scallop,            // command line option parsing
      "org.scala-lang"    %  "scala-compiler"                 % scalaVersion.value,             // embedded compiler
      "org.scala-lang.modules" %% "scala-swing"               % deps.common.scalaSwing,         // desktop UI kit
      "org.scala-stm"     %% "scala-stm"                      % deps.common.scalaSTM,           // software transactional memory
      "org.slf4j"         %  "slf4j-api"                      % deps.app.slf4j,                 // logging (used by weblaf)
      "org.slf4j"         %  "slf4j-simple"                   % deps.app.slf4j,                 // logging (used by weblaf)
    ),
    mimaPreviousArtifacts := Set("de.sciss" %% s"$baseNameL-app" % mimaAppVersion),
    initialCommands in console :=
      """import de.sciss.mellite._""".stripMargin,
    fork in run := true,  // required for shutdown hook, and also the scheduled thread pool, it seems
    // ---- build-info ----
    buildInfoKeys := Seq("name" -> baseName /* name */, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.mellite",
    // ---- license report ----
    licenseReportTypes    := Seq(Csv),
    licenseConfigurations := Set(Compile.name),
    updateLicenses := {
      val regular = updateLicenses.value
      val fontsLic = DepLicense(
        DepModuleInfo("io.github.dejavu-fonts", "dejavu-fonts", deps.app.dejaVuFonts),
        LicenseInfo(LicenseCategory("OFL"), name = "DejaVu Fonts License",
          url = "https://dejavu-fonts.github.io/License.html"
        ),
        configs = Set(Compile.name)
      )
      regular.copy(licenses = regular.licenses :+ fontsLic)
    },
    licenseReportDir := (sourceDirectory in Compile).value / "resources" / "de" / "sciss" / "mellite",
    // ---- packaging ----
    packageName in Universal := s"${appNameL}_${version.value}_all",
    name                      in Debian := appNameL,  // this is used for .deb file-name; NOT appName,
    debianPackageDependencies in Debian ++= Seq("java11-runtime"),
    debianPackageRecommends   in Debian ++= Seq("openjfx"), // you could run without, just the API browser won't work
  )

// Determine OS version of JavaFX binaries
lazy val jfxClassifier = sys.props("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}

def jfxDep(name: String): ModuleID =
  "org.openjfx" % s"javafx-$name" % "11.0.2" classifier jfxClassifier

def archSuffix: String =
  sys.props("os.arch") match {
    case "i386"  | "x86_32" => "x32"
    case "amd64" | "x86_64" => "x64"
    case other              => other
  }

lazy val full = project.withId(s"$baseNameL-full").in(file("full"))
  .dependsOn(app)
  .enablePlugins(JavaAppPackaging, DebianPlugin, JlinkPlugin)
  .settings(commonSettings)
  .settings(pkgUniversalSettings)
  .settings(pkgDebianSettings)
  // disabled so we don't need to install zip.exe on wine:
  // .settings(useNativeZip) // cf. https://github.com/sbt/sbt-native-packager/issues/334
  .settings(assemblySettings) // do we need this here?
  .settings(appSettings)
  .settings(
    name := s"$baseName-full",
    version := appVersion,
    jlinkIgnoreMissingDependency := JlinkIgnore.everything, // temporary for testing
    jlinkModules += "jdk.unsupported", // needed for Akka
    libraryDependencies ++= Seq("base", "swing", "controls", "graphics", "media", "web").map(jfxDep),
    packageName in Universal := s"$appNameL-full_${version.value}_${jfxClassifier}_$archSuffix",
    name                in Debian := s"$appNameL-full",  // this is used for .deb file-name; NOT appName,
    packageArchitecture in Debian := sys.props("os.arch"), // archSuffix,
  )

