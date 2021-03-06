package de.sciss.mellite

import java.io.File

import de.sciss.lucre.store.BerkeleyDB
import de.sciss.proc.Workspace

object DocTest extends App {
  val file  = File.createTempFile("mellite", "doc")
  require(file.delete())
  val cfg   = BerkeleyDB.Config()
  cfg.allowCreate = true
  val ds    = BerkeleyDB.factory(file)
  val doc   = Workspace.Confluent.empty(file.toURI, ds)
  println("Ok.")
}