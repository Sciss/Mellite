![icon](icons/application.png)

# Mellite

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Sciss/Mellite?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/Sciss/Mellite.svg?branch=master)](https://travis-ci.org/Sciss/Mellite)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/mellite_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/mellite_2.11)

## statement

Mellite is a computer music environment, implemented as a graphical front end
for [SoundProcesses](http://git.iem.at/sciss/SoundProcesses). It is (C)opyright 2012&ndash;2019 by Hanns Holger Rutz.
All rights reserved. Mellite is released under the
[GNU Affero General Public License](https://git.iem.at/sciss/Mellite/raw/master/LICENSE) v3+ and comes with
absolutely no warranties. To contact the author, send an email to `contact at sciss.de`.
The website for Mellite is [www.sciss.de/mellite](https://www.sciss.de/mellite/).

The `licenses` folder contains the license headers for all dependencies and transitive dependencies. See `overview.txt`
for a dependency overview. For the binary release of Mellite, source code is not included but available via the
respective OSS project pages, as indicated in the license files, or&mdash;in compliance with GPL/LGPL&mdash;on request
via E-Mail. All source code with group-id `de.sciss` is available from
[git.iem.at](https://git.iem.at/sciss) or [github.com/Sciss](https://github.com/Sciss).

The Mellite icon is based on the file
[MELLITE Taillée Hongrie.jpg](https://de.wikipedia.org/wiki/Mellit#/media/File:MELLITE_Taill%C3%A9e_Hongrie.jpg), 
provided by Didier Descouens under CC BY 4.0 license.

<img src="site/src/paradox/assets/images/screenshot.png" alt="screenshot" width="696" height="436"/>

## download and installation

- A binary (executable) version is provided via [archive.org](https://archive.org/details/Mellite) or
  [GitHub releases](https://github.com/Sciss/Mellite/releases/latest).
  We provide a universal zip for all platforms as well as a dedicated Debian package.
- The source code can be downloaded from [git.iem.at/sciss/Mellite](https://git.iem.at/sciss/Mellite) or 
  [github.com/Sciss/Mellite](http://github.com/Sciss/Mellite).
  
In order to run the application, you must have a Java Development Kit (JDK) installed. The recommended version
is 8. You can use either OpenJDK or Oracle JDK. On the Raspberry Pi, we currently recommend Oracle JDK, as there
are stability issues with OpenJDK 8. Oracle JDK 8 is available from
[here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html). On Linux, to install
OpenJDK, use `sudo apt install openjdk-8-jdk`.

In order to use the built-in API browser, you additionally need JavaFX. It may be part of Oracle JDK. When using
OpenJDK, you should install the package `openjfx` (e.g. `sudo apt install openjfx`.)

For real-time sound reproduction, the [SuperCollider](https://supercollider.github.io/download) server is needed.
The recommended version is 3.9.0 or above (technically 3.7.0 or higher should work). 

## linking

If you want to use Mellite as a "library" in your project, you can link to a Maven artifact.

The following artifact is available from Maven Central:

    "de.sciss" %% "mellite" % v

The current version `v` is `"2.33.0"`.

## building from source

See the section 'download and installation' for requirements (JDK, JavaFX).

Mellite builds with [sbt](http://scala-sbt.org/) and Scala 2.12. The last version to support Scala 2.10 is 2.10.2.
The dependencies will be downloaded automatically from Maven Central repository, except for snapshots during
development. For convenience, the [sbt script by Paul Phillips](https://github.com/paulp/sbt-extras) has been
included, which is covered by a BSD-3-clause license. Therefore, on Linux and Mac you can just use `./sbt run` or
`./sbt universal:packageBin` to get going without having to separately install sbt. On Windows, install sbt
regularly through its website.

Dependencies not found are all available from their respective
[git repositories](https://git.iem.at/users/sciss/projects), so in case you want to build a snapshot version, you
may need to check out these projects and publish them yourself using `sbt publishLocal`.

See section 'running' for ways of building and installing standalone bundles.

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## running

The standalone jar, created via `./sbt assembly` produces `Mellite.jar` which is double-clickable and can be run via:

    $ java -jar Mellite.jar

Runnable packages can be created via `./sbt universal:packageBin` (all platforms) or `./sbt debian:packageBin` (Debian).

## documentation

Video and text tutorials, as well as API docs can be found online here:
[www.sciss.de/mellite](https://www.sciss.de/mellite/)

Note that this project is still __experimental__ in its nature, meaning that many features are incomplete or buggy,
and that the application is subject to change. For example, there is no guarantee that workspaces in an older version
of Mellite can still be fully opened in newer versions, although I try to minimise incompatibilities in the
serialization. You have been warned!

Please do not hesitate to ask on the [Gitter channel](https://gitter.im/Sciss/Mellite).
