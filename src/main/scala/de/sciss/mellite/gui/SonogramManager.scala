/*
 *  SonogramManager.scala
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
package gui

import java.io.File

import de.sciss.dsp.{ConstQ, Threading}
import de.sciss.sonogram

import scala.concurrent.ExecutionContext

object SonogramManager {
  private lazy val _instance = {
    val cfg               = sonogram.OverviewManager.Config()
    val folder            = new File(new File(sys.props("user.home"), "mellite"), "cache")
    folder.mkdirs()
    val sizeLimit         = 2L << 10 << 10 << 100  // 20 GB
    cfg.caching           = Some(sonogram.OverviewManager.Caching(folder, sizeLimit))
    // currently a problem with JTransforms
    // cfg.executionContext  = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
    // cfg.executionContext  = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    sonogram.OverviewManager(cfg)
  }

  def instance: sonogram.OverviewManager = _instance

  def acquire(file: File): sonogram.Overview = {
    val cq    = ConstQ.Config()
    cq.bandsPerOct  = 18
    cq.maxTimeRes   = 18
//    cq.bandsPerOct  = 18 * 4
//    cq.maxTimeRes   = 18 / 4.0f
    cq.threading    = Threading.Single
    val job   = sonogram.OverviewManager.Job(file, cq)
    _instance.acquire(job)
  }
  def release(overview: sonogram.Overview): Unit = _instance.release(overview)

  implicit def executionContext: ExecutionContext = _instance.config.executionContext
}