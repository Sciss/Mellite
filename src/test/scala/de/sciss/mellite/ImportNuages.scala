package de.sciss.mellite

import de.sciss.file._
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.lucre.stm.{Copy, Txn}
import de.sciss.nuages.Nuages
import de.sciss.nuages.Nuages.Surface
import de.sciss.synth.proc.{Durable, Workspace}

// quick hack to copy a nuages-only database into a regular mellite session
object ImportNuages extends App {
  val fIn     = args.headOption.map(file).getOrElse(
    // userHome/"Documents"/"projects"/"Anemone"/"sessions"/"session_160527_164131"
    userHome/"Music"/"renibday"/"sessions"/"session_160616_160647"
  )
  val fOut    = userHome/"mellite"/"sessions"/fIn.replaceExt(".mllt").name
  require(!fOut.exists())

  Mellite.initTypes()

  val factIn  = BerkeleyDB.factory(fIn, createIfNecessary = false)
  type In     = Durable
  type Out    = Durable

  val sysIn   = Durable(factIn)
  try {
    val nInH    = sysIn.root[Nuages[In]] { _ => sys.error("Expecting existing Nuages file") }
    val dsc     = BerkeleyDB.Config()
    dsc.allowCreate = true
    val ds      = BerkeleyDB.factory(fOut, dsc)
    val wOut    = Workspace.Durable.empty(fOut, ds)
    try {
      Txn.copy[In, Out, Unit] { (txIn: In#Tx, tx: Out#Tx) =>
        val cpy   = Copy[In, Out](txIn, tx)
        val nIn   = nInH()(txIn)
        val infoIn = nIn.surface match {
          case Surface.Timeline(tl) => s"Timeline: ${tl.iterator(txIn).size}"
          case Surface.Folder  (f)  => s"Folder: ${f.size(txIn)}"
        }
        println(s"IN:  $infoIn")
        val nOut = cpy(nIn)
        cpy.finish()
        val infoOut = nOut.surface match {
          case Surface.Timeline(tl) => s"Timeline: ${tl.iterator(tx).size}"
          case Surface.Folder  (f)  => s"Folder: ${f.size(tx)}"
        }
        println(s"OUT: $infoOut")
        val foldOut = wOut.root(tx)
        foldOut.addLast(nOut)(tx)

      } (sysIn, wOut.cursor)

    } finally {
      wOut.cursor.step { implicit tx =>
        wOut.dispose()
      }
    }

  } finally {
    sysIn.close()
  }

  println("Import done.")
  sys.exit()
}