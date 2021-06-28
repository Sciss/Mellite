/*
 *  CollectionViewImpl.scala
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

package de.sciss.mellite.impl.component

import java.util.Locale
import de.sciss.desktop
import de.sciss.desktop.{KeyStrokes, OptionPane, Util}
import de.sciss.equal.Implicits._
import de.sciss.lucre.Obj
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{Application, AttrMapFrame, GUI, MessageException, ObjListView, ObjView, UniverseObjView}
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.PopupMenu

import javax.swing.undo.UndoableEdit
import scala.annotation.tailrec
import scala.swing.event.{EditDone, FocusLost, Key, KeyPressed}
import scala.swing.{Action, Alignment, BorderPanel, Button, Component, Dialog, FlowPanel, SequentialContainer, Swing, TextField}
import scala.tools.cmd.CommandLineParser
import scala.util.{Failure, Success}

trait CollectionViewImpl[T <: Txn[T]]
  extends UniverseObjView[T]
  with View.Editable[T]
  with ComponentHolder[Component] {

  impl =>

  type C = Component

  // ---- abstract ----

  protected def peer: View.Editable[T]

  protected def actionDelete: Action

  protected def selectedObjects: List[ObjView[T]]

  /** Called after the main GUI has been initialized. */
  protected def initGUI2(): Unit

  protected type InsertConfig

  /** Prepare new object insertion by generating an 'insert-config', possibly opening a dialog */
  protected def prepareInsertDialog(f: ObjView.Factory): Option[InsertConfig]

  /** Prepare new object insertion by generating an 'insert-config' from a given argument list,
    * returning the config along with the possibly filtered list.
    */
  protected def prepareInsertCmdLine(args: List[String]): Option[(InsertConfig, List[String])]

  protected def editInsert(f: ObjView.Factory, xs: List[Obj[T]], config: InsertConfig)(implicit tx: T): Option[UndoableEdit]

  // ---- implemented ----

  lazy final protected val actionAttr: Action = Action(null) {
    val sel = selectedObjects
    val sz  = sel.size
    if (sz > 0) GUI.step[T](nameAttr, s"Opening ${if (sz == 1) "window" else "windows"}") { implicit tx =>
      sel.foreach(n => AttrMapFrame(n.obj))
    }
  }

  lazy final protected val actionView: Action = Action(null) {
    val sel = selectedObjects.filter(_.isViewable)
    val sz  = sel.size
    if (sz > 0) {
      val windowOption = Window.find(this)
      GUI.step[T](nameView, s"Opening ${if (sz == 1) "window" else "windows"}")  { implicit tx =>
        sel.foreach(_.openView(windowOption))
      }
    }
  }

  protected def selectionChanged(sel: List[ObjView[T]]): Unit = {
    val nonEmpty  = sel.nonEmpty
    actionAdd   .enabled = sel.size < 2
    actionDelete.enabled = nonEmpty
    actionView  .enabled = nonEmpty && sel.exists(_.isViewable)
    actionAttr  .enabled = nonEmpty
  }

  final protected var ggAdd   : Button = _
  final protected var ggDelete: Button = _
  final protected var ggView  : Button = _
  final protected var ggAttr  : Button = _

  final def init()(implicit tx: T): this.type = {
    deferTx(initGUI())
    this
  }

  final protected lazy val actionAdd: Action = Action(null) {
    val bp = ggAdd
    addPopup.show(bp, (bp.size.width - addPopup.size.width) >> 1, bp.size.height - 4)
  }

  final def bottomComponent: Component with SequentialContainer = {
    requireEDT()
    if (_bottomComponent == null) throw new IllegalStateException("Called component before GUI was initialized")
    _bottomComponent
  }

  // ---- private ----

  private final class AddAction(f: ObjView.Factory) extends Action(f.humanName) {
    icon = f.icon

    def apply(): Unit = {
      val winOpt = desktop.Window.find(component)
      f.initMakeDialog[T](/* workspace, */ /* parentH, */ winOpt) {
        case Success(conf) =>
          val confOpt2  = prepareInsertDialog(f)
          confOpt2.foreach { insConf =>
            val editOpt = cursor.step { implicit tx =>
              val xs = f.makeObj(conf)
              editInsert(f, xs, insConf)
            }
            editOpt.foreach(undoManager.add)
          }

        case Failure(Aborted()) =>

        case Failure(e) =>
          val text = e match {
            case Aborted() => ""
            case MessageException(m) => m
            case _ => Util.formatException(e)
          }
          if (text.nonEmpty) {
            val optUnable = OptionPane.message(
              message     = s"Unable to create object of type ${f.humanName} \n\n$text",
              messageType = OptionPane.Message.Error
            )
            optUnable.title = title
            optUnable.show()
          }
      }
    }
  }

  private[this] lazy val addPopup: PopupMenu = {
    import de.sciss.desktop.Menu._
    val pop     = Popup()
    val tlP     = Application.topLevelObjects
    val flt     = Application.objectFilter
    val f0      = ObjListView.factories.filter(f => f.canMakeObj && flt(f.prefix)).toSeq.sortBy(_.humanName)
    val (top0, sub) = f0.partition(f => tlP.contains(f.prefix))
    val top     = tlP.flatMap(prefix => top0.find(_.prefix == prefix))
    top.foreach { f =>
      pop.add(Item(f.prefix, new AddAction(f)))
    }
    val subMap = sub.groupBy(_.category)
    subMap.keys.toSeq.sorted.foreach { category =>
      val group = Group(category.toLowerCase, category)
      subMap.getOrElse(category, Nil).foreach { f =>
        group.add(Item(f.prefix, new AddAction(f)))
      }
      pop.add(group)
    }

    val window = desktop.Window.find(component).getOrElse(sys.error(s"No window for $impl"))
    val res = pop.create(window)
    res.peer.pack() // so we can read `size` correctly
    res
  }

  private[this] lazy val ggNewType = new TextField(16)

  private def newTypeDialog(): Unit = {
    //      // cf. https://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
    //      val regex = "[^\\s\"']+|\"([^\"]*)\"|'([^']*)'".r
    //
    //      def splitArgs(cmd: String): List[String] =
    //        regex.findAllIn(cmd).map { s =>
    //          val nm    = s.length - 1
    //          val head  = s.charAt(0)
    //          val last  = s.charAt(nm)
    //          if ((head == '\'' && last == '\'') || (head == '\"' && last == '\"'))
    //            s.substring(1, nm)
    //          else
    //            s
    //        } .toList

    val winOpt  = desktop.Window.find(component)
    val win     = winOpt.map(_.component) match {
      case Some(w: scala.swing.Window) => w
      case _ => null
    }
    val dialog  = new Dialog(win)
    dialog.peer.setUndecorated(true)
    dialog.contents = ggNewType
    dialog.pack()
    GUI.setLocationRelativeTo(dialog, ggAdd, hAlign = Alignment.Right)
    ggNewType.listenTo(ggNewType)
    ggNewType.listenTo(ggNewType.keys)
    // note: focus-lost is followed by edit-done as well.
    // when user hits return, only edit-done is sent.
    var handled = false

    def handle(body: => Unit): Unit = if (!handled) {
      handled = true
      dialog.dispose()
      body
    }

    def clearText(): Unit = ggNewType.text = ""

    ggNewType.reactions += {
      case KeyPressed(_, Key.Escape, _, _)  => handle {}
      case FocusLost(_, _, _)               => handle {}

      case EditDone(_) => handle {
        var tokenOk   = true
        val argString = ggNewType.text
        val args0     = CommandLineParser.tokenize(argString, errorFn = { err => println(err); tokenOk = false })
        val prepOpt   = if (tokenOk) prepareInsertCmdLine(args0) else None
        prepOpt match {
          case Some((insConf, cmd :: rest)) =>
            val nameL     = cmd.toLowerCase(Locale.US)

            @tailrec
            def checkOpts(factOpts: Seq[ObjListView.Factory]): Unit =
              factOpts match {
                case f :: Nil =>
                  if (f.canMakeObj) {
                    val res = f.initMakeCmdLine[T](rest)
                    res match {
                      case Success(conf) =>
                        val editOpt = cursor.step { implicit tx =>
                          val xs = f.makeObj(conf)
                          editInsert(f, xs, insConf)
                        }
                        editOpt.foreach(undoManager.add)
                        clearText()

                      case Failure(Aborted()) =>

                      case Failure(MessageException(msg)) =>
                        println()
                        println(msg)

                      case Failure(ex) =>
                        val msg = Util.formatException(ex)
                        println(msg)
                    }

                  } else {
                    println(s"Object type '$cmd' does not support command line instantiation.")
                    clearText()
                  }

                case Nil =>
                  val pre     = s"Unknown object type '$cmd'. Available:\n"
                  val avail   = ObjListView.factories.iterator.filter(_.canMakeObj).map(_.prefix).toList.sorted
                  val availS  = GUI.formatTextTable(avail, columns = 3)
                  println(pre)
                  println(availS)

                case _ /*multiple*/ =>
                  // make more attempts first by selecting only those
                  // supporting command line; then choosing the shorter one
                  val factOpt1 = factOpts.filter(_.canMakeObj)
                  if (factOpt1 !== factOpts) {
                    checkOpts(factOpt1)
                  } else {
                    val choice = factOpts.minBy(_.prefix)
                    checkOpts(choice :: Nil)

//                    val factOps2 = factOpts.filter(_.prefix.toLowerCase(Locale.US) === nameL)
//                    if (factOps2.nonEmpty && (factOps2 !== factOpts)) {
//                      checkOpts(factOps2)
//                    } else {
//                      val pre     = s"Multiple object types start with '$cmd':\n"
//                      val avail   = multiple.map(_.prefix).sorted
//                      val availS  = GUI.formatTextTable(avail, columns = 3)
//                      println(pre)
//                      println(availS)
//                    }
                  }
              }

            val factOpts0 = ObjListView.factories.filter(_.prefix.toLowerCase(Locale.US).startsWith(nameL)).toList
            checkOpts(factOpts0)

          case _ =>
        }
      }
    }
    dialog.open()
  }

  // XXX TODO DRY with TimelineViewBaseImpl
  private def nameAttr = "Attributes Editor"
  private def nameView = "View Selected Element"

  private[this] var _bottomComponent: FlowPanel = _

  private def initGUI(): Unit = {
    ggAdd    = GUI.addButton   (actionAdd   , "Add Element")
    ggDelete = GUI.removeButton(actionDelete, "Remove Selected Element")
    ggAttr   = GUI.attrButton  (actionAttr  , nameAttr)
    ggView   = GUI.viewButton  (actionView  , nameView)

    _bottomComponent = new FlowPanel(ggAdd, ggDelete, ggAttr, Swing.HStrut(32), ggView)

    component = new BorderPanel {
      add(impl.peer.component, BorderPanel.Position.Center)
      add(_bottomComponent, BorderPanel.Position.South)
    }

    Util.addGlobalAction(ggAdd, "type-new", KeyStrokes.menu1 + Key.Key1) {
      newTypeDialog()
    }

    initGUI2()
    selectionChanged(selectedObjects)
  }
}