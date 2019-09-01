package de.sciss.mellite

import java.io.File

import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.synth.proc.Workspace

object DocTest extends App {
  val file  = File.createTempFile("mellite", "doc")
  require(file.delete())
  val cfg   = BerkeleyDB.Config()
  cfg.allowCreate = true
  val ds    = BerkeleyDB.factory(file)
  val doc   = Workspace.Confluent.empty(file, ds)
  println("Ok.")
}