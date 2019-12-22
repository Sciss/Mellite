addSbtPlugin("com.eed3si9n"     % "sbt-assembly"        % "0.14.9")   // cross-platform standalone
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"       % "0.9.0")    // meta-data such as project and Scala version
addSbtPlugin("com.typesafe"     % "sbt-mima-plugin"     % "0.6.1")    // binary compatibility testing
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.4.1")    // release standalone binaries
addSbtPlugin("com.typesafe.sbt" % "sbt-license-report"  % "1.2.0")    // generates list of dependencies licenses

// sbt-mima was killed
resolvers += Resolver.url(
  "typesafe sbt-plugins",
  url("https://dl.bintray.com/typesafe/sbt-plugins")
)(Resolver.ivyStylePatterns)

