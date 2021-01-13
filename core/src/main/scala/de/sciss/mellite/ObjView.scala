/*
 *  ElementView.scala
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

import de.sciss.desktop
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, Obj, Observable, Source, Txn => LTxn}
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.proc.{Color, Universe}

import java.awt.datatransfer.Transferable
import javax.swing.Icon
import scala.util.Try

object ObjView {
  /** Standard `AttrMap` key whose value is of type `Color.Obj`. */
  final val attrColor         = "color"

  final val categPrimitives   = "Primitives"
  final val categResources    = "Resources"
  final val categComposition  = "Composition"
  // final val categSound        = "Sound"
  final val categOrganization = "Organization"
  final val categMisc         = "Miscellaneous"

  final val Unnamed = "<unnamed>"

  final case class Drag[T <: LTxn[T]](universe: Universe[T], view: ObjView[T],
                                      context: Set[Context[T]] /*= Set.empty*/)

  object Context {
    final case class AttrKey[T <: LTxn[T]](s: String) extends Context[T]
  }
  sealed trait Context[T <: LTxn[T]]

  // Document not serializable -- local JVM only DnD -- cf. stackoverflow #10484344
  val Flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  trait Factory {
    def prefix    : String
    def humanName : String
    def icon      : Icon
    def tpe       : Obj.Type

    type Config[T <: LTxn[T]]

    type MakeResult[T <: LTxn[T]] = Try[Config[T]]

    type E[~ <: LTxn[~]] <: Obj[~]

    /** Whether it is possible to create an instance of the object via
      * `initMakeDialog`, `initMakeCmdLine`, or `makeObj`. If this answers `false`, expect
      * `initMakeDialog` and `initMakeCmdLine` to throw an exception, and `makeObj` to simply return `Nil`.
      */
    def canMakeObj: Boolean

    // Note: we use a callback `done` instead of returning a `Future[Config[T]]` because the
    // latter means a lot of boiler plate (executionContext) and `Future { }` does not
    // guarantee execution on the EDT, so it's a mismatch.

    /** Provides an optional initial configuration for the make-new-instance dialog.
      * If the user aborts the dialog, the `done` call-back should be invoked nevertheless,
      * using the value of `Processor.Aborted` to indicate so. If only a message should be
      * shown instead of a full exception, use `MessageException`.
      */
    def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                   (implicit universe: Universe[T]): Unit

    /** Tries to create a make-configuration from a command line string. */
    def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T]

    def category: String

    /** Creates an object from a configuration.
      * The reason that the result type is not `Obj.T[T, E]` is
      * that we allow the returning of a number of auxiliary other objects as well.
      */
    def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]]
  }

  trait Update[T <: LTxn[T]] {
    def view: ObjView[T]
  }
  final case class Repaint[T <: LTxn[T]](view: ObjView[T]) extends Update[T]
}
trait ObjView[T <: LTxn[T]]
  extends Disposable[T]
  with Observable[T, ObjView.Update[T]] /* Model[ObjView.Update[T]] */ {

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

  type Repr <: Obj[T]

  def objH: Source[T, Repr]

  /** The view must store a handle to its underlying model. */
  def obj(implicit tx: T): Repr

  /** Whether a dedicated view/editor window exists for this type of object. */
  def isViewable: Boolean

  def createTransferable(): Option[Transferable] = None

  /** If the object is viewable, this method is invoked when the user pressed the eye button.
    * The method should return an appropriate view for this object, or `None` if no editor or viewer window
    * can be produced.
    */
  def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]]
}