/*
 *  ElementView.scala
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

import java.awt.datatransfer.Transferable

import de.sciss.desktop
import de.sciss.lucre.event.Observable
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.synth.proc.{Color, Universe}
import javax.swing.Icon

import scala.language.higherKinds
import scala.util.Try

object ObjView {
  /** Standard `AttrMap` key whose value is of type `Color.Obj`. */
  final val attrColor         = "color"

  final val categPrimitives   = "Primitives"
  final val categResources    = "Resources"
  final val categComposition  = "Composition"
  // final val categSound        = "Sound"
  final val categOrganisation = "Organisation"
  final val categMisc         = "Miscellaneous"

  final val Unnamed = "<unnamed>"

  final case class Drag[S <: stm.Sys[S]](universe: Universe[S], view: ObjView[S])

  // Document not serializable -- local JVM only DnD -- cf. stackoverflow #10484344
  val Flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  trait Factory {
    def prefix    : String
    def humanName : String
    def icon      : Icon
    def tpe       : Obj.Type

    type Config[S <: stm.Sys[S]]

    type MakeResult[S <: stm.Sys[S]] = Try[Config[S]]

    type E[~ <: stm.Sys[~]] <: Obj[~]

    /** Whether it is possible to create an instance of the object via
      * `initMakeDialog`, `initMakeCmdLine`, or `makeObj`. If this answers `false`, expect
      * `initMakeDialog` and `initMakeCmdLine` to throw an exception, and `makeObj` to simply return `Nil`.
      */
    def canMakeObj: Boolean

    // Note: we use a callback `done` instead of returning a `Future[Config[S]]` because the
    // latter means a lot of boiler plate (executionContext) and `Future { }` does not
    // guarantee execution on the EDT, so it's a mismatch.

    /** Provides an optional initial configuration for the make-new-instance dialog.
      * If the user aborts the dialog, the `done` call-back should be invoked nevertheless,
      * using the value of `Processor.Aborted` to indicate so. If only a message should be
      * shown instead of a full exception, use `MessageException`.
      */
    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                   (implicit universe: Universe[S]): Unit

    /** Tries to create a make-configuration from a command line string. */
    def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S]

    def category: String

    /** Creates an object from a configuration.
      * The reason that the result type is not `Obj.T[S, E]` is
      * that we allow the returning of a number of auxiliary other objects as well.
      */
    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]]
  }

  trait Update[S <: stm.Sys[S]] {
    def view: ObjView[S]
  }
  final case class Repaint[S <: stm.Sys[S]](view: ObjView[S]) extends Update[S]
}
trait ObjView[S <: stm.Sys[S]]
  extends Disposable[S#Tx]
  with Observable[S#Tx, ObjView.Update[S]] /* Model[ObjView.Update[S]] */ {

  def factory: ObjView.Factory

  def humanName: String

  /** The contents of the `"name"` attribute of the object. This is directly
    * set by the table tree view. The object view itself must only make sure that
    * an initial value is provided.
    */
  def nameOption: Option[String]

  def colorOption: Option[Color]

  /** Convenience method that returns an "unnamed" string if no name is set. */
  def name: String = nameOption.getOrElse(ObjView.Unnamed)

  /** A view must provide an icon for the user interface. It should have a dimension of 32 x 32 and ideally
    * be drawn as vector graphics in order to look good when applying scaling.
    */
  def icon: Icon

  type Repr <: Obj[S]

  def objH: stm.Source[S#Tx, Repr]

  /** The view must store a handle to its underlying model. */
  def obj(implicit tx: S#Tx): Repr

  /** Whether a dedicated view/editor window exists for this type of object. */
  def isViewable: Boolean

  def createTransferable(): Option[Transferable] = None

  /** If the object is viewable, this method is invoked when the user pressed the eye button.
    * The method should return an appropriate view for this object, or `None` if no editor or viewer window
    * can be produced.
    */
  def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]]
}