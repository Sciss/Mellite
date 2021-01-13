/*
 *  Help.scala
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

import de.sciss.lucre.InMemory
import de.sciss.proc.Implicits._
import de.sciss.proc.Markdown

object Help {
  private type                  S = InMemory
  private type                  T = InMemory.Txn
  private implicit val system:  S = InMemory()

  def shortcuts(): Unit = {
    system.step { implicit tx =>
      val md = markdownResource("shortcuts.md", "Keyboard Shortcuts")
      MarkdownFrame.basic(md)
    }
  }

  private def markdownResource(name: String, title: String)(implicit tx: T): Markdown[T] = {
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
    val md = Markdown.newConst[T](mdValue)
    md.name = title
    md
  }
}
