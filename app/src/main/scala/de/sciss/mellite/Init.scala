/*
 *  Init.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.freesound.lucre.{Retrieval, RetrievalObjView}
import de.sciss.fscape.lucre.{Cache => FScCache}
import de.sciss.proc.FScape
import de.sciss.lucre
import de.sciss.lucre.swing.{FScapeViews, LucreSwing}
import de.sciss.mellite.impl.code.{CodeFrameImpl, CodeViewImpl}
import de.sciss.mellite.impl.document.{FolderEditorViewImpl, FolderViewImpl}
import de.sciss.mellite.impl.fscape.{FScapeObjView, FScapeOutputObjView}
import de.sciss.mellite.impl.grapheme.{GraphemeToolImpl, GraphemeToolsImpl, GraphemeViewImpl}
import de.sciss.mellite.impl.markdown.{MarkdownEditorViewImpl, MarkdownFrameImpl, MarkdownObjView, MarkdownRenderViewImpl}
import de.sciss.mellite.impl.objview
import de.sciss.mellite.impl.patterns.{PatternObjView, StreamObjView}
import de.sciss.mellite.impl.proc.{ProcObjView, ProcOutputObjView}
import de.sciss.mellite.impl.timeline.{GlobalProcsViewImpl, TimelineToolImpl, TimelineToolsImpl, TimelineViewImpl}
import de.sciss.mellite.impl.widget.WidgetObjView
import de.sciss.negatum.Negatum
import de.sciss.negatum.gui.NegatumObjView
import de.sciss.nuages.Wolkenpumpe
import de.sciss.proc.Pattern
import de.sciss.proc.{GenView, SoundProcesses, Widget}
import de.sciss.synth.ThirdPartyUGens
import net.harawata.appdirs.AppDirsFactory

trait Init {
  def cacheDir: File = _cacheDir

  private[this] lazy val _cacheDir = {
    val appDirs = AppDirsFactory.getInstance
    val path    = appDirs.getUserCacheDir("mellite", /* version */ null, /* author */ "de.sciss")
    val res     = new File(path) // new File(new File(sys.props("user.home"), "mellite"), "cache")
    res.mkdirs()
    res
  }

  private[this] lazy val _initObjViews: Unit = {
    val obj = List(
      objview.ActionObjView,
      objview.ArtifactObjView,
      objview.BooleanObjView,
      objview.ColorObjView,
      objview.CodeObjView,
      objview.ControlObjView,
      objview.DoubleObjView,
      objview.DoubleVectorObjView,
      objview.EnvSegmentObjView,
      objview.FadeSpecObjView,
      objview.FolderObjView,
      objview.GraphemeObjView,
      objview.IntObjView,
      objview.IntVectorObjView,
      objview.LongObjView,
      objview.NuagesObjView,
      objview.ParamSpecObjView,
      objview.StringObjView,
      objview.TagObjView,
      objview.TimelineObjView,
      ArtifactLocationObjView,
      AudioCueObjView,
      FScapeObjView,
      FScapeOutputObjView,
      MarkdownObjView,
      NegatumObjView,
      PatternObjView,
      ProcObjView,
      ProcOutputObjView,
      RetrievalObjView,
      StreamObjView,
      WidgetObjView,
    )
    obj.foreach(ObjListView.addFactory)

    val gr = List(
      objview.DoubleObjView,
      objview.DoubleVectorObjView,
      objview.EnvSegmentObjView,
    )
    gr.foreach(ObjGraphemeView.addFactory)

    val tl = List(
      ProcObjView,
//      ActionRawObjView,
      PatternObjView,
      StreamObjView,
    )
    tl.foreach(ObjTimelineView.addFactory)
  }

  private[this] lazy val _initCompanionFactories: Unit = {
    objview.ArtifactLocationObjViewImpl .install()
    objview.AudioCueObjViewImpl         .install()
    CodeFrameImpl               .install()
    CodeViewImpl                .install()
    FolderEditorViewImpl        .install()
    FolderViewImpl              .install()
    GlobalProcsViewImpl         .install()
    GraphemeToolImpl            .install()
    GraphemeToolsImpl           .install()
    GraphemeViewImpl            .install()
    MarkdownEditorViewImpl      .install()
    MarkdownFrameImpl           .install()
    MarkdownRenderViewImpl      .install()
    TimelineToolImpl            .install()
    TimelineToolsImpl           .install()
    TimelineViewImpl            .install()
  }

  def initTypes(): Unit = {
    FScape        .init()
    FScapeViews   .init()
    LucreSwing    .init()
    Negatum       .init()
    Pattern       .init()
    Retrieval     .init()
    SoundProcesses.init()
    Widget        .init()
    Wolkenpumpe   .init()

    lucre.swing.graph.TimelineView  .init()

    _initObjViews
    _initCompanionFactories

    ThirdPartyUGens.init()

    // ---- FScape ----

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