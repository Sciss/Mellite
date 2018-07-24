/*
 *  Init.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import java.io.File

import de.sciss.filecache.Limit
import de.sciss.freesound.lucre.Retrieval
import de.sciss.fscape.lucre.{FScape, Cache => FScCache}
import de.sciss.nuages.Wolkenpumpe
import de.sciss.patterns.lucre.Pattern
import de.sciss.synth.proc.{GenView, SoundProcesses, Widget}

trait Init {
  def cacheDir: File = _cacheDir

  private[this] lazy val _cacheDir = {
    val res = new File(new File(sys.props("user.home"), "mellite"), "cache")
    res.mkdirs()
    res
  }

  def initTypes(): Unit = {
    SoundProcesses.init()
    Wolkenpumpe   .init()
    FScape        .init()
    Pattern       .init()
    Retrieval     .init()
    Widget        .init()

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
      case _: com.typesafe.config.ConfigException =>
        Console.err.println(s"Mellite.init: Failed to initialize Akka.")
    }
  }
}