import com.typesafe.sbt.license.{DepLicense, DepModuleInfo, LicenseInfo}
import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName                   = "Mellite"
lazy val baseNameL                  = baseName.toLowerCase
lazy val appDescription             = "A computer music application based on SoundProcesses"
lazy val commonVersion              = "3.6.0"
lazy val mimaCommonVersion          = "3.6.0"
lazy val appVersion                 = "3.6.0"
lazy val mimaAppVersion             = "3.6.0"

lazy val loggingEnabled             = true

lazy val authorName                 = "Hanns Holger Rutz"
lazy val authorEMail                = "contact@sciss.de"

// ---- changes ----

lazy val changeLog = Seq(
  "Allow type changes in aural attributes provided by Control programs (SoundProcesses #109)",
)

// ---- dependencies ----

lazy val deps = new {
  val common = new {
    val asyncFile           = "0.1.4"
    val audioFile           = "2.3.3"
    val audioWidgets        = "2.3.2"
    val desktop             = "0.11.3"
    val equal               = "0.1.6"
    val fileUtil            = "1.1.5"
    val lucre               = "4.4.5"
    val lucreSwing          = "2.6.3"
    val model               = "0.3.5"
    val numbers             = "0.2.1"
    val processor           = "0.5.0"
    val raphaelIcons        = "1.0.7"
    val scalaCollider       = "2.6.4"
    val scalaColliderUGens  = "1.21.1"
    val scalaColliderIf     = "1.7.6"
    val scalaOSC            = "1.3.1"
    val scalaSTM            = "0.11.1"
    val scalaSwing          = "3.0.0"
    val scallop             = "4.0.3"
    val serial              = "2.0.1"
    val sonogram            = "2.2.1"
    val soundProcesses      = "4.8.0"
    val span                = "2.0.2"
    val swingPlus           = "0.5.0"
  }
  val app = new {
    val akka                = "2.6.15"  // note -- should correspond to FScape always
    val appDirs             = "1.2.1"
    val dejaVuFonts         = "2.37"    // directly included
    val dotterweide         = "0.4.0"
    val fileCache           = "1.1.1"
    val fingerTree          = "1.5.5"
    val freesound           = "2.6.0"
    val fscape              = "3.7.0"
    val interpreterPane     = "1.11.0"
//    val jline               = "2.14.6"
    val jump3r              = "1.0.5"
    val kollFlitz           = "0.2.4"
    val linKernighan        = "0.1.3"
    val lucrePi             = "1.5.0"
    val negatum             = "1.6.0"
    val patterns            = "1.5.0"
    val pdflitz             = "1.5.0"
    val pegDown             = "1.6.0"
//    val playJSON            = "0.4.0"
//    val plexMono            = "4.0.2"   // directly included
    val scalaColliderSwing  = "2.6.4"
    val scissDSP            = "2.2.2"
    val slf4j               = "1.7.31"
    val submin              = "0.3.4"
    val syntaxPane          = "1.2.0"
    val treeTable           = "1.6.1"
    val topology            = "1.1.4"
    // val webLaF              = "2.2.1"
    val webLaF              = "1.2.11"
    val wolkenpumpe         = "3.5.0"
  }
}

lazy val bdb = "bdb"

// ---- app packaging ----

lazy val appMainClass = Some("de.sciss.mellite.Mellite")
def appName           = baseName
def appNameL          = baseNameL

// ---- common ----

ThisBuild / organization  := "de.sciss"
ThisBuild / versionScheme := Some("pvp")

lazy val commonSettings = Seq(
  homepage           := Some(url(s"https://sciss.de/$baseNameL")),
  // note: license _name_ is printed in 'about' dialog
  licenses           := Seq("GNU Affero General Public License v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  scalaVersion       := "2.13.6",
  crossScalaVersions := Seq(/* "3.0.0", */ "2.13.6", "2.12.14"),
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8"),
  scalacOptions ++= {
    // if (isDotty.value) Nil else 
    Seq("-Xlint:-stars-align,_", "-Xsource:2.13")
  },
  scalacOptions ++= {
    if (loggingEnabled || isSnapshot.value) Nil else Seq("-Xelide-below", "INFO")
  },
  scalacOptions /* in (Compile, compile) */ ++= {
    val sq0 = if (scala.util.Properties.isJavaAtLeast("9")) List("-release", "8") else Nil // JDK >8 breaks API; skip scala-doc
    val sq1 = if (VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector(">=2.13"))) "-Wconf:cat=deprecation&msg=Widening conversion:s" :: sq0 else sq0 // nanny state defaults :-E
    sq1
  },
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  // resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/", // https://stackoverflow.com/questions/23979577
  // resolvers += "Typesafe Simple Repository" at "http://repo.typesafe.com/typesafe/simple/maven-releases/", // https://stackoverflow.com/questions/20497271
  updateOptions := updateOptions.value.withLatestSnapshots(false),
  assembly / aggregate := false,
  // ---- publishing ----
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "github.com"
    val a = s"Sciss/$baseName"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
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
//  Universal / javaOptions ++= Seq(
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
  Linux / name        := appName,
  Linux / packageName := appNameL, // XXX TODO -- what was this for?
  // Universal / mainClass := appMainClass,
  Universal / maintainer := s"$authorName <$authorEMail>",
  Universal / target := (Compile / target).value,
)

//////////////// debian installer
lazy val pkgDebianSettings = Seq(
  Debian / packageName        := appNameL,  // this is the installed package (e.g. in `apt remove <name>`).
  Debian / packageSummary     := appDescription,
  // Debian / mainClass          := appMainClass,
  Debian / maintainer         := s"$authorName <$authorEMail>",
  Debian / packageDescription :=
    """Mellite is a computer music environment,
      | a desktop application based on SoundProcesses.
      | It manages workspaces of musical objects, including
      | sound processes, timelines, code fragments, or
      | live improvisation sets.
      |""".stripMargin,
  // include all files in src/debian in the installed base directory
  Debian / linuxPackageMappings ++= {
    val n     = appNameL // (Debian / name).value.toLowerCase
    val dir   = (Debian / sourceDirectory).value / "debian"
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
    assembly / mainClass             := appMainClass,
    assembly / target                := baseDirectory.value,
    assembly / assemblyJarName       := s"$baseName.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("org", "xmlpull", _ @ _*)                   => MergeStrategy.first
      case PathList("org", "w3c", "dom", "events", _ @ _*)      => MergeStrategy.first // bloody Apache Batik
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
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
    Compile / packageBin / publishArtifact := false, // there are no binaries
    Compile / packageDoc / publishArtifact := false, // there are no javadocs
    Compile / packageSrc / publishArtifact := false, // there are no sources
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
      "de.sciss"          %% "scalacolliderugens-core"        % deps.common.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacollider-if"               % deps.common.scalaColliderIf,    // realtime sound synthesis
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
  description         := appDescription,
  Compile / mainClass := appMainClass,
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
      "com.typesafe.akka" %%  "akka-stream"                   % deps.app.akka,
      "com.typesafe.akka" %%  "akka-stream-testkit"           % deps.app.akka,
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
//      "de.sciss"          % mainClassmainClassaf-ui"                      % deps.app.webLaF,                // look-and-feel
      "com.weblookandfeel" % "weblaf-core"                    % deps.app.webLaF,
      "com.weblookandfeel" % "weblaf-ui"                      % deps.app.webLaF,
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
    console / initialCommands :=
      """import de.sciss.mellite._""".stripMargin,
    run / fork := true,  // required for shutdown hook, and also the scheduled thread pool, it seems
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
    licenseReportDir := (Compile / sourceDirectory).value / "resources" / "de" / "sciss" / "mellite",
    // ---- packaging ----
    Universal / packageName := s"${appNameL}_${version.value}_all",
    Debian / name                      := appNameL,  // this is used for .deb file-name; NOT appName,
    Debian / debianPackageDependencies ++= Seq("java11-runtime"),
    Debian / debianPackageRecommends   ++= Seq("openjfx"), // you could run without, just the API browser won't work
    // ---- publishing ----
    pomPostProcess := addChanges,
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

lazy val addChanges: xml.Node => xml.Node = {
  case root: xml.Elem =>
    val changeNodes: Seq[xml.Node] = changeLog.map(t => <mllt.change>{t}</mllt.change>)
    val newChildren = root.child.map {
      case e: xml.Elem if e.label == "properties" => e.copy(child = e.child ++ changeNodes)
      case other => other
    }
    root.copy(child = newChildren)
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
    Universal / packageName := s"$appNameL-full_${version.value}_${jfxClassifier}_$archSuffix",
    Debian / name                := s"$appNameL-full",  // this is used for .deb file-name; NOT appName,
    Debian / packageArchitecture := sys.props("os.arch"), // archSuffix,
  )

