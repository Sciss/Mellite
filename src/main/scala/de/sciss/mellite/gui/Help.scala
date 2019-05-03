/*
 *  Help.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre.stm.InMemory
import de.sciss.mellite.Mellite
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Markdown

object Help {
  private type                  S = InMemory
  private implicit val system:  S = InMemory()

  def shortcuts(): Unit = {
    system.step { implicit tx =>
      val md = markdownResource("shortcuts.md", "Keyboard Shortcuts")
      MarkdownRenderFrame.basic(md)
    }
  }

  private def markdownResource(name: String, title: String)(implicit tx: S#Tx): Markdown[S] = {
    val mdValue = Option(Mellite.getClass.getResourceAsStream(name)).fold[String](
      s"__Could not find resource '$name'!__"
    ) { is =>
      try {
        val arr = new Array[Byte](is.available())
        is.read(arr)
        new String(arr, "UTF-8")
      } finally {
        is.close()
      }
    }
    val md = Markdown.newConst[S](mdValue)
    md.name = title
    md
  }
}
