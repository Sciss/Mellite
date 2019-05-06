/*
 *  Init.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import java.io.File

import de.sciss.filecache.Limit
import de.sciss.fscape.lucre.{FScape, Cache => FScCache}
import de.sciss.lucre.swing.LucreSwing
import de.sciss.lucre.swing.graph.TimelineView
import de.sciss.mellite.gui.impl.FreesoundRetrievalObjView
import de.sciss.nuages.Wolkenpumpe
import de.sciss.patterns.lucre.Pattern
import de.sciss.synth.proc.{GenView, SoundProcesses, Widget}
import net.harawata.appdirs.AppDirsFactory

trait Init {
  def cacheDir: File = _cacheDir

  private[this] lazy val _cacheDir = {
    val appDirs = AppDirsFactory.getInstance
    val path    = appDirs.getUserCacheDir("mellite", /* version */ null, /* author */ null)
    val res     = new File(path) // new File(new File(sys.props("user.home"), "mellite"), "cache")
    res.mkdirs()
    res
  }

  def initTypes(): Unit = {
    SoundProcesses.init()
    Wolkenpumpe   .init()
    FScape        .init()
    Pattern       .init()
    FreesoundRetrievalObjView.init()  // indirect through view because it depends on scala-version
    Widget        .init()
    LucreSwing    .init()
    TimelineView  .init()

    val cacheLim = Limit(count = 8192, space = 2L << 10 << 100)  // 2 GB; XXX TODO --- through user preferences
    FScCache.init(folder = cacheDir, capacity = cacheLim)

//    val ctlConf = Control.Config()
//    ctlConf.terminateActors = false
    // we have sane default config now!

    // akka looks for stuff it can't find in IntelliJ plugin.
    // see https://github.com/Sciss/FScape-next/issues/23
    // for testing purposes, simply give up, so we
    // should be able to work with Mellite minus FScape.
    try {
      val fscapeF = FScape.genViewFactory()
      GenView.tryAddFactory(fscapeF)
    } catch {
      case ex if ex.getClass.getName.contains("com.typesafe.config.ConfigException") /* : com.typesafe.config.ConfigException */ =>
        Console.err.println(s"Mellite.init: Failed to initialize Akka.")
    }
  }
}