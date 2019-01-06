import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName                   = "Mellite"
lazy val baseNameL                  = baseName.toLowerCase
lazy val appDescription             = "A computer music application based on SoundProcesses"
lazy val projectVersion             = "2.31.0"
lazy val mimaVersion                = "2.31.0"

lazy val loggingEnabled             = true

lazy val authorName                 = "Hanns Holger Rutz"
lazy val authorEMail                = "contact@sciss.de"

// ---- dependencies ----

lazy val deps = new {
  val main = new {
    val audioFile           = "1.5.1"
    val audioWidgets        = "1.14.0"
    val desktop             = "0.10.0"
    val equal               = "0.1.3"
    val fileCache           = "0.5.0"
    val fileUtil            = "1.1.3"
    val fingerTree          = "1.5.4"
    val freesound           = "1.13.0"
    val fscape              = "2.20.0"
    val interpreterPane     = "1.10.0"
    val jline               = "2.14.6"
    val jump3r              = "1.0.5"
    val kollFlitz           = "0.2.3"
    val lucre               = "3.11.0"
    val lucreSwing          = "1.14.0"
    val model               = "0.3.4"
    val numbers             = "0.2.0"
    val patterns            = "0.7.0"
    val pdflitz             = "1.4.0"
    val pegDown             = "1.6.0"
    val playJSON            = "0.4.0"
    val processor           = "0.4.2"
    val raphaelIcons        = "1.0.5"
    val scalaCollider       = "1.28.0"
    val scalaColliderSwing  = "1.41.0"
    val scalaColliderUGens  = "1.19.2"
    val scalaOSC            = "1.2.0"
    val scalaSTM            = "0.9"
    val scalaSwing          = "2.1.0"
    val scissDSP            = "1.3.1"
    val scopt               = "3.7.1"
    val serial              = "1.1.1"
    val sonogram            = "1.11.0"
    val soundProcesses      = "3.24.0"
    val span                = "1.4.2"
    val submin              = "0.2.4"
    val swingPlus           = "0.4.0"
    val syntaxPane          = "1.2.0"
    val treeTable           = "1.5.0"
    val topology            = "1.1.1"
    val webLaF              = "2.1.4"
    val wolkenpumpe         = "2.29.0"
  }
}

lazy val bdb = "bdb" // one of "bdb" (Java 6+, GPL 2+), "bdb6" (Java 7+, AGPL 3+), "bdb7" (Java 8+, Apache)

// ---- app packaging ----

lazy val appMainClass               = Some("de.sciss.mellite.Mellite")
def appName                         = baseName
def appNameL                        = baseNameL

// ---- common ----

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  homepage           := Some(url(s"https://sciss.de/$baseNameL")),
  licenses           := Seq("GNU Affero General Public License v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  scalaVersion       := "2.12.8",
  crossScalaVersions := Seq("2.12.8", "2.11.12", "2.13.0-M5"),
  scalacOptions ++= {
    val xs = Seq(
      "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint:-stars-align,_", "-Xsource:2.13"
    )
    if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
  },
  javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
  resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/", // https://stackoverflow.com/questions/23979577
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
  javaOptions in Universal ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m",
    // others will be added as app parameters
    "-Djavax.accessibility.assistive_technologies=",  // work around for #70
  ),
  // Since our class path is very very long,
  // we use instead the wild-card, supported
  // by Java 6+. In the packaged script this
  // results in something like `java -cp "../lib/*" ...`.
  // NOTE: `in Universal` does not work. It therefore
  // also affects debian package building :-/
  // We need this settings for Windows.
  scriptClasspath /* in Universal */ := Seq("*")
)

//////////////// debian installer
lazy val pkgDebianSettings = Seq(
  name                      in Debian := appName,
  packageName               in Debian := appNameL,
  name                      in Linux  := appName,
  packageName               in Linux  := appNameL,
  packageSummary            in Debian := appDescription,
  mainClass                 in Debian := appMainClass,
  maintainer                in Debian := s"$authorName <$authorEMail>",
  debianPackageDependencies in Debian += "java8-runtime",
  packageDescription        in Debian :=
    """Mellite is a computer music environment,
      | a desktop application based on SoundProcesses.
      | It manages workspaces of musical objects, including
      | sound processes, timelines, code fragments, or
      | live improvisation sets.
      |""".stripMargin,
  // include all files in src/debian in the installed base directory
  linuxPackageMappings      in Debian ++= {
    val n     = (name            in Debian).value.toLowerCase
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

// ---- projects ----

lazy val assemblySettings = Seq(
    mainClass             in assembly := appMainClass,
    target                in assembly := baseDirectory.value,
    assemblyJarName       in assembly := s"$baseName.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("org", "xmlpull", _ @ _*) => MergeStrategy.first
      case PathList("org", "w3c", "dom", "events", _ @ _*) => MergeStrategy.first // bloody Apache Batik
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(pkgUniversalSettings)
  .settings(pkgDebianSettings)
  .settings(useNativeZip) // cf. https://github.com/sbt/sbt-native-packager/issues/334
  .settings(assemblySettings)
  .settings(
    name        := baseName,
    description := appDescription,
    resolvers += "Oracle Repository" at "http://download.oracle.com/maven", // required for sleepycat
    libraryDependencies ++= Seq(
      "com.github.scopt"  %% "scopt"                          % deps.main.scopt,              // command line option parsing
      "de.sciss"          %% "audiofile"                      % deps.main.audioFile,          // reading/writing audio files
      "de.sciss"          %% "audiowidgets-app"               % deps.main.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-core"              % deps.main.audioWidgets,       // audio application widgets
      "de.sciss"          %% "audiowidgets-swing"             % deps.main.audioWidgets,       // audio application widgets
      "de.sciss"          %% "desktop"                        % deps.main.desktop,
      "de.sciss"          %% "equal"                          % deps.main.equal,              // type-safe equals
      "de.sciss"          %% "filecache-common"               % deps.main.fileCache,          // caching data to disk
      "de.sciss"          %% "fileutil"                       % deps.main.fileUtil,           // extension methods for files
      "de.sciss"          %% "fingertree"                     % deps.main.fingerTree,         // data structures
      "de.sciss"          %% "fscape"                         % deps.main.fscape,             // offline audio rendering
      "de.sciss"          %  "jump3r"                         % deps.main.jump3r,             // mp3 export
      "de.sciss"          %% "kollflitz"                      % deps.main.kollFlitz,          // more collections methods
      "de.sciss"          %% "lucre-base"                     % deps.main.lucre,              // object system
      "de.sciss"          %% s"lucre-$bdb"                    % deps.main.lucre,              // object system (database backend)
      "de.sciss"          %% "lucre-confluent"                % deps.main.lucre,              // object system
      "de.sciss"          %% "lucre-core"                     % deps.main.lucre,              // object system
      "de.sciss"          %% "lucre-expr"                     % deps.main.lucre,              // object system
      "de.sciss"          %% "lucreswing"                     % deps.main.lucreSwing,         // reactive Swing components
      "de.sciss"          %% "model"                          % deps.main.model,              // non-txn MVC
      "de.sciss"          %% "numbers"                        % deps.main.numbers,            // extension methods for numbers
      "de.sciss"          %% "patterns"                       % deps.main.patterns,           // pattern sequences
      "de.sciss"          %% "processor"                      % deps.main.processor,          // futures with progress and cancel
      "de.sciss"          %% "pdflitz"                        % deps.main.pdflitz,            // PDF export
      "de.sciss"          %% "raphael-icons"                  % deps.main.raphaelIcons,       // icon set
      "de.sciss"          %% "scalacollider"                  % deps.main.scalaCollider,      // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-api"         % deps.main.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacolliderugens-core"        % deps.main.scalaColliderUGens, // realtime sound synthesis
      "de.sciss"          %% "scalacolliderswing-core"        % deps.main.scalaColliderSwing, // UI methods for scala-collider
      "de.sciss"          %% "scalainterpreterpane"           % deps.main.interpreterPane,    // REPL
      "de.sciss"          %% "scalaosc"                       % deps.main.scalaOSC,           // open sound control
      "de.sciss"          %% "scissdsp"                       % deps.main.scissDSP,           // offline signal processing
      "de.sciss"          %% "serial"                         % deps.main.serial,             // serialization
      "de.sciss"          %% "sonogramoverview"               % deps.main.sonogram,           // sonogram component
      "de.sciss"          %% "soundprocesses"                 % deps.main.soundProcesses,     // computer-music framework
      "de.sciss"          %% "span"                           % deps.main.span,               // time spans
      "de.sciss"          %  "submin"                         % deps.main.submin,             // dark skin
      "de.sciss"          %  "syntaxpane"                     % deps.main.syntaxPane,         // code editor
      "de.sciss"          %% "swingplus"                      % deps.main.swingPlus,          // Swing extensions
      "de.sciss"          %% "topology"                       % deps.main.topology,           // graph sorting
      "de.sciss"          %  "treetable-java"                 % deps.main.treeTable,          // widget
      "de.sciss"          %% "treetable-scala"                % deps.main.treeTable,          // widget
      "de.sciss"          %% "wolkenpumpe"                    % deps.main.wolkenpumpe,        // live improv
      "org.pegdown"       %  "pegdown"                        % deps.main.pegDown,            // Markdown renderer
      "org.scala-lang.modules" %% "scala-swing"               % deps.main.scalaSwing,         // desktop UI kit
      "org.scala-stm"     %% "scala-stm"                      % deps.main.scalaSTM,           // software transactional memory
    ),
    libraryDependencies ++= {
      // currently disabled until Scala 2.13 version is available
      if (scalaVersion.value == "2.13.0-M5") Nil else Seq(
        "de.sciss"        %% "scalafreesound"                 % deps.main.freesound           // Freesound support
      )
    },
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    mainClass in (Compile,run) := appMainClass,
    initialCommands in console :=
      """import de.sciss.mellite._""".stripMargin,
    fork in run := true,  // required for shutdown hook, and also the scheduled thread pool, it seems
    // ---- build-info ----
    buildInfoKeys := Seq("name" -> baseName /* name */, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.mellite"
  )
