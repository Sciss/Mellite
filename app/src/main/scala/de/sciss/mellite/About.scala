/*
 *  About.scala
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

import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.function.ToLongFunction

import de.sciss.desktop.{Desktop, OptionPane}
import de.sciss.lucre.swing.LucreSwing.defer
import de.sciss.lucre.synth.Server
import de.sciss.mellite.Mellite.applyAudioPreferences
import de.sciss.synth.{Client, Server => SServer}
import javax.swing.SwingUtilities

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.swing.Table.IntervalMode
import scala.swing.event.TableRowsSelected
import scala.swing.{Action, BorderPanel, Button, Component, Label, Orientation, ScrollPane, SplitPane, Swing, Table, TextArea}
import scala.util.{Failure, Success}

object About {
  def show(): Unit = {
    val url       = Mellite.homepage
//    val addr      = url // url.substring(math.min(url.length, url.indexOf("//") + 2))
    val cacheDir  = Mellite.cacheDir
    var scVersion = "..."
    var cacheSize = "..."

    def html(): String =
      s"""<html><center>
         |<font size=+1><b>${Application.name}</b></font><p>
         |Version ${Mellite.version}<p>
         |<p>
         |Copyright (c) 2012&ndash;2019 Hanns Holger Rutz. All rights reserved.<p>
         |This software is published under the ${Mellite.license}
         |<p>&nbsp;<p><i>
         |Scala v${de.sciss.mellite.BuildInfo.scalaVersion}<br>
         |Java v${sys.props.getOrElse("java.version", "?")}<br>
         |SuperCollider server $scVersion<br>
         |Cache directory: $cacheDir $cacheSize
         |</i>
         |<p>&nbsp;<p>
         |""".stripMargin

    val box = new Label(html())
//    {
//      // cf. http://stackoverflow.com/questions/527719/how-to-add-hyperlink-in-jlabel
//      // There is no way to directly register a HyperlinkListener, despite hyper links
//      // being rendered... A simple solution is to accept any mouse click on the label
//      // to open the corresponding website.
//      cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
//      listenTo(mouse.clicks)
//      reactions += {
//        case MouseClicked(_, _, _, 1, false) => Desktop.browseURI(new URL(url).toURI)
//      }
//    }

    def spawn[A](gen: => A)(success: A => Unit)(failure: => Unit): Unit = {
      import ExecutionContext.Implicits.global
      val fut = Future(blocking(gen))
      fut.onComplete { tr =>
        defer {
          tr match {
            case Success(v) => success(v)
            case Failure(_) => failure
          }
          box.text = html()
        }
      }
    }

    val serverCfg = Server.Config()
    // must be on EDT:
    applyAudioPreferences(serverCfg, Client.Config(), useDevice = false, pickPort = false)

    spawn {
      SServer.version(serverCfg).get
    } { case (v, b) =>
      val bs = if (b.isEmpty) b else s" ($b)"
      scVersion = s"v$v$bs"
    } {
      scVersion = "?"
    }

    spawn {
      import java.nio.file.Files
      //      val size: Long = Files.walk(cacheDir.toPath).mapToLong((p: Path) => p.toFile.length).sum
      val size: Long = Files.walk(cacheDir.toPath).mapToLong(new ToLongFunction[Path] {
        def applyAsLong(p: Path): Long = p.toFile.length
      }).sum
      size
    } { sz =>
      cacheSize = s"(using ${(sz + 500000)/1000000} MB)"
    } {
      cacheSize = "(unknown size)"
    }

//    OptionPane.message(message = lb.peer, icon = Logo.icon(128)).show(None, title = "About")
    val entries = List(
      Button("  Ok  "         )(dispose(box)),
      Button("Visit Website…" )(Desktop.browseURI(new URL(url).toURI)),
      Button("License Details")(showLicenses())
    )
    val pane = OptionPane(
      message     = box.peer,
      optionType  = OptionPane.Options.OkCancel,  // irrelevant
      messageType = OptionPane.Message.Info,
      icon        = Logo.icon(128),
      entries     = entries
    )
    pane.showNonModal(title = "About")
  }

  private def dispose(c: Component): Unit =
    Option(SwingUtilities.windowForComponent(c.peer)).foreach(_.dispose())

  def showLicenses(): Unit = {
    val lic       = readMelliteLicenseText()
    val libs      = readLibraryInfo()

    val ggLic     = new TextArea(lic, 16, 0) {
      editable = false
    }
    val scrollLic = new ScrollPane(ggLic)

    val rowData   = libs.map(_.asRow)
    val tabLibs   = new Table(rowData, List("Group", "Artifact", "Version", "license", "type")) {
      autoCreateRowSorter     = true
      selection.intervalMode  = IntervalMode.Single
    }
    val scrollLibs = new ScrollPane(tabLibs)

    val lbMellite = new Label(s"${Application.name} is published under the ${Mellite.license} shown below:")
    val lbLibs    = new Label("It includes the following libraries:")

    def border(label: Component, body: Component): Component =
      new BorderPanel {
        layout(label) = BorderPanel.Position.North
        layout(body ) = BorderPanel.Position.Center
        layoutManager.setVgap(8)
        border        = Swing.EmptyBorder(8, 0, 8, 0)
      }

    val box = new SplitPane(Orientation.Horizontal,
      border(lbMellite, scrollLic ),
      border(lbLibs   , scrollLibs)
    )
//    box.dividerLocation_=(0.5)
    // XXX TODO -- what the f*** is this again. Can't we set 50%?
//    box.dividerLocation = 440 // box.preferredSize.height / 2

//    val box = new BoxPanel(Orientation.Vertical) {
//      contents ++= List(lbMellite, scrollLic, lbLibs, scrollLibs)
//    }
    scrollLic.minimumSize = {
      val d = scrollLic.minimumSize
      d.height = math.max(d.height, 360)
      d
    }

    def withSelection(body: Info => Unit): Unit =
      tabLibs.selection.rows.headOption.foreach { view =>
        val row   = tabLibs.viewToModelRow(view)
        val info  = libs(row)
        body(info)
      }

    val actViewLic = Action("View Library Licence…") {
      withSelection { info =>
        info.licenseURL.foreach { url =>
          Desktop.browseURI(new URL(url).toURI)
        }
      }
    }

    val actViewArt = Action("Download Library Sources…") {
      withSelection { info =>
        val url = s"https://search.maven.org/artifact/${info.groupId}/${info.artifactId}/${info.version}/jar"
        Desktop.browseURI(new URL(url).toURI)
      }
    }

    def updateSelection(): Unit = {
      val b = tabLibs.selection.rows.nonEmpty
      actViewLic.enabled = b
      actViewArt.enabled = b
    }

    tabLibs.listenTo(tabLibs.selection)
    tabLibs.reactions += {
      case _: TableRowsSelected => updateSelection()
    }
    tabLibs.sort(1)  // by artifact

    updateSelection()

    val entries = List(
      Button("  Ok  ")(dispose(box)),
      new Button(actViewLic),
      new Button(actViewArt),
    )
    val pane = OptionPane(message = box.peer, entries = entries)
    pane.showNonModal(title = "License Information")
//    box.dividerLocation_=(0.5)
  }

  private def readAllText(in: InputStream): String = {
    val sz  = in.available()
    val b   = new Array[Byte](sz)
    var off = 0
    try {
      while (in.available() > 0) {
        off += in.read(b, off, in.available())
      }
      new String(b, 0, off, "UTF-8")
    } finally {
      in.close()
    }
  }

  private case class Info(category: String, licenseName: String, licenseURL: Option[String],
                          groupId: String, artifactId: String, version: String) {
    def asRow: Array[Any] =
      Array(groupId, artifactId, version, licenseName, category)
  }

  private def readMelliteLicenseText(): String = {
    val in = getClass.getResourceAsStream("LICENSE")
    try {
      readAllText(in)
    } finally {
      in.close()
    }
  }

  private def readLibraryInfo(): Array[Info] = {
    val in = getClass.getResourceAsStream("mellite-app-licenses.csv")
//    val in = new FileInputStream("app/target/license-reports/mellite-app-licenses.csv")
    val raw = try {
      readCSV(in)
    } finally {
      in.close()
    }
    raw.tail.iterator.map {
      case category :: lic :: art :: _ =>
        val i       = lic.indexOf("(")
        val j       = lic.indexOf(")", i + 1)
        val licName = if (i < 0) lic else lic.substring(0, i).trim
        val licURL  = if (j < 0) None else {
          val sub = lic.substring(i + 1, j)
          if (sub.startsWith("http")) Some(sub) else None
        }
        val artArr  = art.split("#").map(_.trim)
        if (artArr.length < 3) println(s"Oh noes: '$art'")
        val groupId = artArr(0)
        val artId   = artArr(1)
        val version = artArr(2)
        Info(
          category    = category,
          licenseName = licName,
          licenseURL  = licURL,
          groupId     = groupId,
          artifactId  = artId,
          version     = version
        )
    } .toArray
  }

  private def readCSV(in: InputStream): List[List[String]] = {
    val text = readAllText(in)
    text.split("\n").iterator.map { s =>
      @tailrec
      def loop(ci: Int, row: List[String]): List[String] = {
        if (ci >= s.length) row.reverse
        else {
          val c0 = s.charAt(ci)
          // when a cell contains a comma, it is "escaped" by
          // surrounding it with quotes " ". quotes
          // themselves are then escaped by duplicating them.
          val quotes = c0 == '"'
          if (quotes) {
            val cj0 = s.indexOf("\",", ci + 1)
            val cj  = if (cj0 < 0) s.length - 1 else cj0
            val c   = s.substring(ci + 1, cj)
            loop(cj + 2, c :: row)
          } else {
            val cj0 = s.indexOf(',', ci)
            val cj  = if (cj0 < 0) s.length else cj0
            val c   = s.substring(ci, cj)
            loop(cj + 1, c :: row)
          }
        }
      }

      loop(0, Nil)
    } .filter(_.nonEmpty).toList
  }
}
