import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName                   = "Mellite"
lazy val baseNameL                  = baseName.toLowerCase
lazy val appDescription             = "A computer music application based on SoundProcesses"
lazy val projectVersion             = "2.20.1-SNAPSHOT"
lazy val mimaVersion                = "2.20.0"

lazy val loggingEnabled             = true

lazy val authorName                 = "Hanns Holger Rutz"
lazy val authorEMail                = "contact@sciss.de"

// ---- dependencies ----

lazy val deps = new {
  val main = new {
    val audioWidgets        = "1.11.2"
    val desktop             = "0.8.0"
    val equal               = "0.1.2"
    val fileUtil            = "1.1.3"
    val freesound           = "1.6.0"
    val fscape              = "2.11.1"
    val interpreterPane     = "1.8.1"
    val jline               = "2.14.5"
    val lucre               = "3.5.0"
    val lucreSwing          = "1.7.0"
    val model               = "0.3.4"
    val pdflitz             = "1.2.2"
    val pegDown             = "1.6.0"
    val playJSON            = "0.4.0"
    val raphaelIcons        = "1.0.4"
    val scalaCollider       = "1.23.0"
    val scalaColliderSwing  = "1.35.0"
    val scalaColliderUGen   = "1.17.1"
    val sonogram            = "1.9.1"
    val soundProcesses      = "3.16.1"
    val span                = "1.3.3"
    val submin              = "0.2.2"
    val swingPlus           = "0.2.4"
    val webLaF              = "2.1.3"
    val wolkenpumpe         = "2.21.2"
    val patterns            = "0.1.0-SNAPSHOT"
  }
}

lazy val bdb = "bdb" // either "bdb" or "bdb6"

// ---- app packaging ----

lazy val appMainClass               = Some("de.sciss.mellite.Mellite")
def appName                         = baseName
def appNameL                        = baseNameL

// ---- common ----

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  homepage           := Some(url(s"https://sciss.github.io/$baseName")),
  licenses           := Seq("GNU General Public License v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalaVersion       := "2.12.4",
  crossScalaVersions := Seq("2.12.4", "2.11.12"),
  scalacOptions ++= {
    val xs = Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint:-stars-align,_")
    if (loggingEnabled || isSnapshot.value) xs else xs ++ Seq("-Xelide-below", "INFO")
  },
  javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
  resolvers += "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/", // https://stackoverflow.com/questions/23979577
  // resolvers += "Typesafe Simple Repository" at "http://repo.typesafe.com/typesafe/simple/maven-releases/", // https://stackoverflow.com/questions/20497271
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
    <url>git@github.com:Sciss/{n}.git</url>
    <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
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

lazy val root = Project(id = baseName, base = file("."))
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
      "de.sciss"    %% "soundprocesses-views"           % deps.main.soundProcesses,      // computer-music framework
      "de.sciss"    %% "soundprocesses-compiler"        % deps.main.soundProcesses,      // computer-music framework
      "jline"       %  "jline"                          % deps.main.jline,               // must match scala-compiler
      "de.sciss"    %% "scalainterpreterpane"           % deps.main.interpreterPane,     // REPL
      "de.sciss"    %% "scalacollider"                  % deps.main.scalaCollider,
      "de.sciss"    %  "scalacolliderugens-spec"        % deps.main.scalaColliderUGen,   // meta data
      "de.sciss"    %% "lucre-core"                     % deps.main.lucre,
      "de.sciss"    %% s"lucre-$bdb"                    % deps.main.lucre,               // database backend
      "de.sciss"    %% "lucre-expr"                     % deps.main.lucre,
      "de.sciss"    %% "equal"                          % deps.main.equal,               // type-safe equals
      "de.sciss"    %% "span"                           % deps.main.span,                // (sbt bug)
      "de.sciss"    %% "fileutil"                       % deps.main.fileUtil,            // (sbt bug)
      "de.sciss"    %% "wolkenpumpe"                    % deps.main.wolkenpumpe,         // live improv
      "de.sciss"    %% "scalacolliderswing-core"        % deps.main.scalaColliderSwing,  // (sbt bug)
      "de.sciss"    %% "scalacolliderswing-interpreter" % deps.main.scalaColliderSwing,  // REPL view
      "de.sciss"    %% "lucreswing"                     % deps.main.lucreSwing,          // reactive Swing components
      "de.sciss"    %% "audiowidgets-swing"             % deps.main.audioWidgets,        // audio application widgets
      "de.sciss"    %% "audiowidgets-app"               % deps.main.audioWidgets,        // audio application widgets
      "de.sciss"    %% "swingplus"                      % deps.main.swingPlus,           // Swing extensions
      "de.sciss"    %% "desktop"                        % deps.main.desktop,
      "de.sciss"    %% "sonogramoverview"               % deps.main.sonogram,            // sonogram component
      "de.sciss"    %% "raphael-icons"                  % deps.main.raphaelIcons,        // icon set
      "de.sciss"    %% "model"                          % deps.main.model,               // non-txn MVC
      "de.sciss"    %% "fscape"                         % deps.main.fscape,              // offline audio rendering
      "de.sciss"    %% "patterns-lucre"                 % deps.main.patterns,            // pattern sequences
      "de.sciss"    %% "scalafreesound"                 % deps.main.freesound,           // Freesound support
      "org.pegdown" %  "pegdown"                        % deps.main.pegDown,             // Markdown renderer
      "de.sciss"    %% "pdflitz"                        % deps.main.pdflitz,             // PDF export
      "de.sciss"    %  "weblaf"                         % deps.main.webLaF,              // look and feel
      "de.sciss"    %  "submin"                         % deps.main.submin               // dark skin
    ),
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
