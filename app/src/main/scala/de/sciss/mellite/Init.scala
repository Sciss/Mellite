/*
 *  Init.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.fscape.lucre.{FScape, Cache => FScCache}
import de.sciss.lucre
import de.sciss.lucre.swing.LucreSwing
import de.sciss.mellite.impl.code.{CodeFrameImpl, CodeViewImpl}
import de.sciss.mellite.impl.document.{FolderEditorViewImpl, FolderViewImpl}
import de.sciss.mellite.impl.fscape.{FScapeObjView, FScapeOutputObjView}
import de.sciss.mellite.impl.grapheme.{GraphemeToolImpl, GraphemeToolsImpl, GraphemeViewImpl}
import de.sciss.mellite.impl.markdown.{MarkdownEditorViewImpl, MarkdownFrameImpl, MarkdownObjView, MarkdownRenderViewImpl}
import de.sciss.mellite.impl.objview.{ActionObjView, ArtifactLocationObjViewImpl, ArtifactObjView, AudioCueObjViewImpl, BooleanObjView, CodeObjView, ColorObjView, ControlObjView, DoubleObjView, DoubleVectorObjView, EnvSegmentObjView, FadeSpecObjView, FolderObjView, GraphemeObjView, IntObjView, IntVectorObjView, LongObjView, NuagesObjView, ParamSpecObjView, StringObjView, TimelineObjView}
import de.sciss.mellite.impl.patterns.{PatternObjView, StreamObjView}
import de.sciss.mellite.impl.proc.{OutputObjView, ProcObjView}
import de.sciss.mellite.impl.timeline.{GlobalProcsViewImpl, TimelineToolImpl, TimelineToolsImpl, TimelineViewImpl}
import de.sciss.mellite.impl.widget.WidgetObjView
import de.sciss.negatum.Negatum
import de.sciss.negatum.gui.NegatumObjView
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

  private[this] lazy val _initObjViews: Unit = {
    val obj = List(
      ActionObjView,
//      ActionRawObjView,
      ArtifactLocationObjView,
      ArtifactObjView,
      AudioCueObjView,
      BooleanObjView,
      ColorObjView,
      CodeObjView,
      ControlObjView,
      DoubleObjView,
      DoubleVectorObjView,
//      EnsembleObjView,
      EnvSegmentObjView,
      FadeSpecObjView,
      FolderObjView,
      RetrievalObjView,
      FScapeObjView,
      FScapeOutputObjView,
      GraphemeObjView,
      IntObjView,
      IntVectorObjView,
      LongObjView,
      MarkdownObjView,
      NuagesObjView,
      NegatumObjView,
      OutputObjView,
      ParamSpecObjView,
      PatternObjView,
      ProcObjView,
      StreamObjView,
      StringObjView,
      TimelineObjView,
      WidgetObjView,
    )
    obj.foreach(ObjListView.addFactory)

    val gr = List(
      DoubleObjView,
      DoubleVectorObjView,
      EnvSegmentObjView,
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
    ArtifactLocationObjViewImpl .install()
    AudioCueObjViewImpl         .install()
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