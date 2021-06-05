![icon](icons/application.png)

# Mellite

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Sciss/Mellite?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://github.com/Sciss/Mellite/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/Sciss/Mellite/actions?query=workflow%3A%22Scala+CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/mellite-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/mellite-core_2.13)
<a href="https://liberapay.com/sciss"><img alt="Donate using Liberapay" src="https://liberapay.com/assets/widgets/donate.svg" height="24"></a>

## statement

Mellite is a computer music environment, implemented as a graphical front end
for [SoundProcesses](http://github.com/Sciss/SoundProcesses). It is (C)opyright 2012&ndash;2021 by Hanns Holger Rutz.
All rights reserved. Mellite is released under the
[GNU Affero General Public License](https://github.com/Sciss/Mellite/raw/main/LICENSE) v3+ and comes with
absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`.
The website for Mellite is [www.sciss.de/mellite](https://www.sciss.de/mellite/).

Please consider supporting this project through Liberapay (see badge above) – thank you!

The file [mellite-app-licenses.csv](app/src/main/resources/de/sciss/mellite/mellite-app-licenses.csv) contains 
the license data for all  dependencies and transitive dependencies. For the binary release of Mellite, source code
is not included but  available via the respective OSS project pages, as indicated in the license files, or&mdash;in
compliance with  GPL/LGPL&mdash;on request  via E-Mail. All source code with group-id `de.sciss` is available from
[github.com/Sciss](https://github.com/Sciss).

The Mellite icon is based on the file
[MELLITE Taillée Hongrie.jpg](https://de.wikipedia.org/wiki/Mellit#/media/File:MELLITE_Taill%C3%A9e_Hongrie.jpg), 
provided by Didier Descouens under CC BY 4.0 license.

<img src="https://raw.githubusercontent.com/Sciss/Mellite-website/main/src/main/paradox/assets/images/screenshot.png" alt="screenshot" width="696" height="436"/>

## download and installation

- A binary (executable) version is provided via [archive.org](https://archive.org/details/Mellite) or
  [GitHub releases](https://github.com/Sciss/Mellite/releases/latest).
  We provide a universal zip for all platforms as well as a dedicated Debian package. These need an
  additional JDK installation (see below). Even better, for the common Intel 64-bit architecture, you can use the 
  "full" packages that bundle the JDK, available for Linux, Windows, macOS (10.10 or newer).
- The source code can be downloaded from [github.com/Sciss/Mellite](https://github.com/Sciss/Mellite) or 
  [github.com/Sciss/Mellite](http://github.com/Sciss/Mellite).
  
In order to run the application when not using the "full" bundle, you must have a Java Development Kit (JDK) installed. 
The recommended version is __JDK 11.__
On Linux, to install OpenJDK, use `sudo apt install openjdk-11-jdk`. In general, you find good binary builds
for all platforms on [adoptopenjdk.net](https://adoptopenjdk.net/).

For real-time sound reproduction, the [SuperCollider](https://supercollider.github.io/download) server is needed.
The recommended version is 3.10.0 or above (technically 3.7.0 or higher should work).

### issues

When using JDK 8 under Debian and GNOME, there is a __bug in the assistive technology (Atk)__ which results in
performance degradation over time, as some parts of the UI are used. To solve this problem, create a plain text file
`~/.accessibility.properties` (that is, in your home directory) and put the following contents inside:

    javax.accessibility.assistive_technologies=

Mellite has now also been tested with __JDK 11.__ You will see some warnings/errors when starting, including
"An illegal reflective access operation has occurred" and
"ERROR com.alee.utils.ProprietaryUtils - java.lang.NoSuchFieldException: AA_TEXT_PROPERTY_KEY". These are related
to the Web Look-and-Feel, and can be ignored. However, JavaFX is not available as a system-wide package for JDK 11,
so the API browser currently does not work under JDK 11.

## running

The standalone jar, created via `./sbt mellite-app/assembly` produces `app/Mellite.jar` which is double-clickable and can be run via:

    $ java -jar app/Mellite.jar

Runnable packages can be created via `./sbt mellite-app/universal:packageBin` (all platforms) or `./sbt mellite-app/debian:packageBin` (Debian).

## documentation

Video and text tutorials, as well as API docs can be found online here:
[www.sciss.de/mellite](https://www.sciss.de/mellite/)

Note that this project is still __experimental__ in its nature, meaning that many features are incomplete or buggy,
and that the application is subject to change. For example, there is no guarantee that workspaces in an older version
of Mellite can still be fully opened in newer versions, although I try to minimise incompatibilities in the
serialization. You have been warned!

Please do not hesitate to ask on the [Gitter channel](https://gitter.im/Sciss/Mellite).

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

---------

## linking

If you want to use Mellite as a "library" in your project, you can link to a Maven artifact.

The following artifact is available from Maven Central:

    "de.sciss" %% "mellite-core" % "3.5.3"
    "de.sciss" %% "mellite-app"  % "3.5.3"

## building from source

See the section 'download and installation' for requirements (JDK, JavaFX).

Mellite builds with [sbt](http://scala-sbt.org/) and Scala 2.13, 2.12.
The last version to support Scala 2.11 is 2.38.1.
The last version to support Scala 2.10 is 2.10.2.
The default target and the binary distribution of the application are currently based on Scala 2.13. Scala 2.12 was used up to Mellite v2.43.0.

The dependencies will be downloaded automatically from Maven Central repository, except for snapshots during
development. 

Dependencies not found are all available from their respective
[git repositories](https://github.com/Sciss?tab=repositories), so in case you want to build a snapshot version, you
may need to check out these projects and publish them yourself using `sbt publishLocal`.

See section 'running' for ways of building and installing standalone bundles.

## building with bundled JDK

We are currently experimenting with a build variant that bundles the JDK using the JLink plugin for sbt-native-packager.
In order to build this version, run `sbt mellite-full/universal:packageBin`. This must be done on a host JDK 11.
The produced installation is _platform dependent_, so will create a version that only works on the OS you are building from.

Note that should probably specify an explicit java-home, otherwise the bundled package might be unreasonably large:

    sbt ++2.13.3 -java-home ~/Downloads/OpenJDK11U-jdk_x64_linux_hotspot_11.0.9_11/jdk-11.0.9+11 clean update mellite-full/debian:packageBin

---------

## creating new releases

This section is an aide-mémoire for me in releasing stable versions.

1. check that no `SNAPSHOT` versions of libraries are used: `cat build.sbt | grep SNAPSHOT`.
   Change `commonVersion` and `appVersion` appropriately.
2. if releasing a new minor version, make sure it is binary compatible: `sbt mimaReportBinaryIssues`
3. check that libraries are up-to-date, and that there are no binary conflicts:
   `sbt mellite-core/dependencyUpdates mellite-core/evicted`
4. if the version bump is minor and Negatum and Freesound are not involved, skip to step 8
5. otherwise, there is a "ping-pong" process, and the need to publish (locally) a new version of `mellite-core`,
   then of `negatum-core` and `negatum-views`. The core library should be published using JDK 8
   (I use script `java-use-8` which calls `update-java-alternatives`). Then:
   `sbt +mellite-core/clean +mellite-core/update +mellite-core/publishLocal`.
6. Check Negatum:
   `sbt negatum-core/dependencyUpdates negatum-core/evicted`. Publish locally:
   `sbt +negatum-core/clean  +negatum-core/update  +negatum-core/publishLocal` and
   `sbt +negatum-views/clean +negatum-views/update +negatum-views/publishLocal`
7. Check ScalaFreesound:
   `sbt dependencyUpdates evicted`. Publish locally:
   `sbt +clean  +update +test +publishLocal`
8. now for app: `sbt mellite-app/dependencyUpdates mellite-app/evicted`
9. License information is updated by running
   `sbt mellite-app/dumpLicenseReport` via [sbt-license-report](https://github.com/sbt/sbt-license-report).
   Output is found in `app/src/main/resources/de/sciss/mellite/mellite-app-licenses.csv`.
10. Make sure the XFree desktop file version is set:
   `vim app/src/debian/Mellite.desktop`
11. Update the release versions in `README.md`
12. Update the release notes in `pomExtra` of `build.sbt`
13. Test the app building: `sbt +mellite-app/clean +mellite-app/update +mellite-app/test mellite-app/assembly`

### publish to Maven Central

`scripts/publishSignedApp.sh` will release the Scala 2.13 build of the `app` module. If the `core` module
has been updated, it must be published separately and explicitly with `scripts/publishSignedCore.sh`.

### fully bundled standalone

We're currently publishing the following artifacts:

 - `mellite_<version>_all.zip`
 - `mellite-full_<version>_linux_x64.zip`
 - `mellite-full_<version>_amd64.deb`
 - `mellite-full_<version>_win_x64.zip`
 - `mellite-full_<version>_mac_x64.zip`

To build for Linux:

 1. `java-use-8`
 2. `sbt mellite-app/universal:packageBin`
 3. `java-use-11`
 4. `sbt -java-home '/home/hhrutz/Downloads/OpenJDK11U-jdk_x64_linux_hotspot_11.0.9_11/jdk-11.0.9+11' mellite-full/universal:packageBin mellite-full/debian:packageBin`
 
Copy the artifacts to a safe location now.
To build for Mac and Windows, we need to publish all libraries now to Maven Central (use JDK 8 again!).
Then Windows can be built on Linux using wine:
 
 1. `rm -rf full/target` (otherwise Jlink fails)
 2. `wine cmd.exe` and
 `Z:\home\hhrutz\Downloads\OpenJDK11U-jdk_x64_windows_hotspot_11.0.9_11\jdk-11.0.9+11\bin\java.exe -jar Z:\home\hhrutz\Downloads\sbt-1.4.2\sbt\bin\sbt-launch.jar` then in sbt console:
 `project mellite-full` and `universal:packageBin`
 
N.B.: sbt 1.4.x seems no longer to work under wine due to coursier updates; for now we can still build by setting the version in `build.properties` to 1.3.13 temporarily!

For Mac, we need a bloody fruit company machine:

 1. `git fetch; git merge origin/work`
 2. `./sbt -java-home /Users/naya/Downloads/jdk-11.0.9+11/Contents/Home clean update mellite-full/universal:packageBin`
 3. We need to set the execution bits on Linux after copying the zip to the Linux machine, and unpacking it:
 `rm mellite-full_<version>_mac_x64/bin/mellite.bat` then
 `rm mellite-full_<version>_mac_x64.zip` then
 `chmod a+x mellite-full_<version>_mac_x64/bin/mellite` then
 `chmod a+x mellite-full_<version>_mac_x64/jre/bin/*`
 4. Repackage: `zip -y -r -9 mellite-full_<version>_mac_x64.zip mellite-full_<version>_mac_x64`
