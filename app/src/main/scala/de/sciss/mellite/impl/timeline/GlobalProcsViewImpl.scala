/*
 *  GlobalProcsViewImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{Menu, OptionPane, UndoManager}
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{IntObj, Obj, Source, SpanLikeObj, TxnLike, Txn => LTxn}
import de.sciss.mellite.edit.{EditAttrMap, EditTimelineInsertObj, Edits}
import de.sciss.mellite.impl.objview.IntObjView
import de.sciss.mellite.impl.proc.{ProcGUIActions, ProcObjView}
import de.sciss.mellite.impl.state.TableViewState
import de.sciss.mellite.{AttrMapFrame, DragAndDrop, GUI, GlobalProcsView, ObjTimelineView, ObjView, ProcActions, SelectionModel, ViewState}
import de.sciss.proc.{Proc, Timeline, Universe}
import de.sciss.span.Span
import de.sciss.swingplus.{ComboBox, GroupPanel}
import de.sciss.{desktop, equal}

import java.awt.datatransfer.Transferable
import javax.swing.TransferHandler.TransferSupport
import javax.swing.table.{AbstractTableModel, TableColumnModel}
import javax.swing.undo.UndoableEdit
import javax.swing.{DropMode, JComponent, SwingUtilities, TransferHandler}
import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.TxnExecutor
import scala.swing.event.{MouseButtonEvent, MouseEvent, SelectionChanged, TableRowsSelected}
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, FlowPanel, Label, Orientation, ScrollPane, Swing, Table, TextField}
import scala.util.Try

object GlobalProcsViewImpl extends GlobalProcsView.Companion {
  def install(): Unit =
    GlobalProcsView.peer = this

  def apply[T <: Txn[T]](group: Timeline[T], selectionModel: SelectionModel[T, ObjTimelineView[T]])
                        (implicit tx: T, universe: Universe[T],
                         undo: UndoManager): GlobalProcsView[T] = {

    // import ProcGroup.Modifiable.serializer
    val groupHOpt = group.modifiableOption.map(gm => tx.newHandle(gm))
    val view      = new Impl[T](/* tx.newHandle(group), */ groupHOpt, selectionModel)
    view.init(group)
  }

  private final val Key_ColWidths  = "global-col-widths"
  private final val Key_RowSort    = "global-row-sort"
  private final val Key_ColOrder   = "global-col-order"

  private final class Impl[T <: Txn[T]](// groupH: Source[T, Timeline[T]],
                                        groupHOpt: Option[Source[T, Timeline.Modifiable[T]]],
                                        tlSelModel: SelectionModel[T, ObjTimelineView[T]])
                                       (implicit val universe: Universe[T],
                                        val undoManager: UndoManager)
    extends GlobalProcsView[T] with ComponentHolder[Component] {

    type C = Component

    private val stateTable = new TableViewState[T](
      keyColWidths = Key_ColWidths,
      keyRowSort   = Key_RowSort,
      keyColOrder  = Key_ColOrder
    )

    override def viewState: Set[ViewState] = stateTable.entries()

    //    private[this] var procSeq = Vec.empty[ProcObjView.Timeline[T]]
    private[this] var procSeq = Vec.empty[ProcObjView.Timeline[T]]

    private def atomic[A](block: T => A): A = cursor.step(block)

    private[this] var table: Table = _

    def tableComponent: Table = table

//    val selectionModel: SelectionModel[T, ProcObjView.Timeline[T]] = SelectionModel.apply
    val selectionModel: SelectionModel[T, ObjView[T]] = SelectionModel.apply

    private[this] val tlSelListener: SelectionModel.Listener[T, ObjTimelineView[T]] = {
      case SelectionModel.Update(_, _) =>
        val items: Set[ProcObjView.Timeline[T]] = TxnExecutor.defaultAtomic { implicit itx =>
          implicit val tx: TxnLike = LTxn.wrap(itx)
          tlSelModel.iterator.flatMap {
            case pv: ProcObjView.Timeline[T] =>
              pv.targets.flatMap { link =>
                val tgt = link.attr.parent
                if (tgt.isGlobal) Some(tgt) else None
              }

            case _ => Nil
          } .toSet
        }

        val indices   = items.map(procSeq.indexOf(_))
        val rows      = table.selection.rows
        val toAdd     = indices.diff(rows)
        val toRemove  = rows   .diff(indices)

        if (toRemove.nonEmpty) rows --= toRemove
        if (toAdd   .nonEmpty) rows ++= toAdd
    }

    // columns: name, gain, muted, bus
    private val tm = new AbstractTableModel {
      def getRowCount   : Int = procSeq.size
      def getColumnCount: Int = 4

      def getValueAt(row: Int, col: Int): AnyRef = {
        val pv  = procSeq(row)
        val res = (col: @switch) match {
          case 0 => pv.name
          case 1 => pv.gain
          case 2 => pv.muted
          case 3 => pv.busOption.getOrElse(0)
        }
        res.asInstanceOf[AnyRef]
      }

      override def isCellEditable(row: Int, col: Int): Boolean = true

      override def setValueAt(value: Any, row: Int, col: Int): Unit = {
        val pv = procSeq(row)
        (col, value) match {
          case (0, name: String) =>
            atomic { implicit tx =>
              ProcActions.rename(pv.obj, if (name.isEmpty) None else Some(name))
            }
          case (1, gainS: String) =>  // XXX TODO: should use spinner for editing
            Try(gainS.toDouble).foreach { gain =>
              atomic { implicit tx =>
                ProcActions.setGain(pv.obj, gain)
              }
            }

          case (2, _ /* muted */: Boolean) =>
            atomic { implicit tx =>
              ProcActions.toggleMute(pv.obj)
            }

          case (3, busS: String) =>   // XXX TODO: should use spinner for editing
            Try(busS.toInt).foreach { bus =>
              atomic { implicit tx =>
                ProcActions.setBus(pv.obj :: Nil, IntObj.newConst[T](bus))
              }
            }

          case _ =>
        }
      }

      override def getColumnName(col: Int): String = (col: @switch) match {
        case 0 => "Name"
        case 1 => "Gain"
        case 2 => "M" // short because column only uses checkbox
        case 3 => "Bus"
        // case other => super.getColumnName(col)
      }

      override def getColumnClass(col: Int): Class[_] = (col: @switch) match {
        case 0 => classOf[String]
        case 1 => classOf[Double]
        case 2 => classOf[Boolean]
        case 3 => classOf[Int]
      }
    }

    private def addItemWithDialog(): Unit =
      groupHOpt.foreach { groupH =>
        val lbName    = new Label("Name:")
        val ggName    = new TextField("Bus", 12)
        val lbPreset  = new Label("Preset:")
        val ggPreset  = new ComboBox(GlobalProcPreset.all) {
          listenTo(selection)
        }
        val flow      = new BoxPanel(Orientation.Vertical)
        val op        = OptionPane(flow, OptionPane.Options.OkCancel, focus = Some(ggName))

        var presetCtl: GlobalProcPreset.Controls = null

        def updatePreset(): Unit = {
          val preset = ggPreset.selection.item
          if (flow.contents.size > 2) flow.contents.remove(1)
          presetCtl = preset.mkControls()
          flow.contents.insert(1, presetCtl.component)
          Option(SwingUtilities.getWindowAncestor(op.peer)).foreach(_.pack())
        }

        ggPreset.reactions += {
          case SelectionChanged(_) => updatePreset()
        }
        val pane      = new GroupPanel {
          horizontal  = Seq(Par(lbName, lbPreset), Par(ggName, ggPreset))
          vertical    = Seq(Par(Baseline)(lbName, ggName), Par(Baseline)(lbPreset, ggPreset))
        }

        flow.contents += pane
        flow.contents += Swing.VStrut(4)

        updatePreset()

        val objType   = "Global Proc"
        op.title      = s"Add $objType"
        val opRes     = op.show(None)
        import equal.Implicits._
        if (opRes === OptionPane.Result.Ok) {
          val name = ggName.text
          val edit = atomic { implicit tx =>
//            ProcActions.insertGlobalRegion(groupH(), name, bus = None)
            import de.sciss.proc.Implicits._
            val obj   = presetCtl.make[T]()
            obj.name  = name
            val group = groupH()
            EditTimelineInsertObj[T](objType, group, Span.All, obj)
          }
          undoManager.add(edit)
        }
      }

    private def removeProcs(pvs: Iterable[ProcObjView.Timeline[T]]): Unit =
      if (pvs.nonEmpty) groupHOpt.foreach { groupH =>
        val editOpt = atomic { implicit tx =>
          ProcGUIActions.removeProcs(groupH(), pvs.iterator)
        }
        editOpt.foreach(undoManager.add)
      }

    private def setColumnWidth(tcm: TableColumnModel, idx: Int, w: Int): Unit = {
      val tc = tcm.getColumn(idx)
      tc.setPreferredWidth(w)
      // tc.setMaxWidth      (w)
    }

//    private def getColumnWidth(tcm: TableColumnModel, idx: Int): Int = {
//      val tc = tcm.getColumn(idx)
//      tc.getPreferredWidth
//    }

    def init(timeline: Timeline[T])(implicit tx: T): this.type = {
      for {
        tAttr <- ViewState.map(timeline)
      } {
        stateTable.init(tAttr)
      }
      deferTx(initGUI())
      this
    }

    private def initGUI(): Unit = {
      table             = new Table(tm)

      // XXX TODO: enable the following - but we're loosing default boolean rendering
      //        // Table default has idiotic renderer/editor handling
      //        override lazy val peer: JTable = new JTable /* with Table.JTableMixin */ with SuperMixin
      //      }
      // table.background  = Color.darkGray
      val jt            = table.peer
      jt.setAutoCreateRowSorter(true)
      // jt.putClientProperty("JComponent.sizeVariant", "small")
      // jt.getRowSorter.setSortKeys(...)
      //      val tcm = new DefaultTableColumnModel {
      //
      //      }
      val tcm = jt.getColumnModel
      setColumnWidth(tcm, 0, 55)
      setColumnWidth(tcm, 1, 47)
      setColumnWidth(tcm, 2, 29)
      setColumnWidth(tcm, 3, 43)

      stateTable.initGUI_J(jt)
//      val tabW = 3 +
//        getColumnWidth(tcm, 0) +
//        getColumnWidth(tcm, 1) +
//        getColumnWidth(tcm, 2) +
//        getColumnWidth(tcm, 3)
//      println(s"tabW = $tabW")
//      jt.setPreferredScrollableViewportSize(new Dimension(tabW, 100))

      // ---- drag and drop ----
      jt.setDropMode(DropMode.ON)
      jt.setDragEnabled(true)
      jt.setTransferHandler(new TransferHandler {
        override def getSourceActions(c: JComponent): Int = TransferHandler.LINK

        override def createTransferable(c: JComponent): Transferable = {
          val selRows = table.selection.rows
          selRows.headOption.map { row =>
            val pv = procSeq(row)
            DragAndDrop.Transferable(DnD.flavor)(DnD.GlobalProcDrag(universe, pv.objH))
          } .orNull
        }

        // ---- import ----
        override def canImport(support: TransferSupport): Boolean =
          support.isDataFlavorSupported(ObjView.Flavor)

        override def importData(support: TransferSupport): Boolean =
          support.isDataFlavorSupported(ObjView.Flavor) && {
            Option(jt.getDropLocation).fold(false) { dl =>
              val pv    = procSeq(dl.getRow)
              val drag  = support.getTransferable.getTransferData(ObjView.Flavor)
                .asInstanceOf[ObjView.Drag[T]]
              import de.sciss.equal.Implicits._
              drag.universe === universe && {
                drag.view match {
                  case iv: IntObjView[T] =>
                    atomic { implicit tx =>
                      val objT = iv.obj
                      val intExpr = objT
                      ProcActions.setBus(pv.obj :: Nil, intExpr)
                      true
                    }

//                  case iv: CodeObjView[T] =>
//                    atomic { implicit tx =>
//                      val objT = iv.obj
//                      import Mellite.compiler
//                      ProcActions.setSynthGraph(pv.obj :: Nil, objT)
//                      true
//                    }

                  case _ => false
                }
              }
            }
          }
      })

      val scroll    = new ScrollPane(table)
      scroll.peer.putClientProperty("styleId", "undecorated")
      scroll.border = null

      val actionAdd = Action(null)(addItemWithDialog())
      val ggAdd: Button = GUI.addButton(actionAdd, "Add Global Process")

      val actionDelete = Action(null) {
        val pvs = table.selection.rows.map(procSeq)
        removeProcs(pvs)
      }
      val ggDelete: Button = GUI.toolButton(actionDelete, raphael.Shapes.Minus, "Delete Global Process")
      actionDelete.enabled = false

      val actionAttr: Action = Action(null) {
        if (selectionModel.nonEmpty) cursor.step { implicit tx =>
          selectionModel.iterator.foreach { view =>
            AttrMapFrame(view.obj)
          }
        }
      }

      val actionEdit: Action = Action(null) {
        if (selectionModel.nonEmpty) cursor.step { implicit tx =>
          selectionModel.iterator.foreach { view =>
            if (view.isViewable) view.openView(None)  //  /// XXX TODO - find window
          }
        }
      }

      val ggAttr = GUI.toolButton(actionAttr, raphael.Shapes.Wrench, "Attributes Editor")
      actionAttr.enabled = false

      val ggEdit = GUI.toolButton(actionEdit, raphael.Shapes.View, "Proc Editor")
      actionEdit.enabled = false

      table.listenTo(table.selection, table.mouse.clicks)
      table.reactions += {
        case TableRowsSelected(_, _, _) =>
          val range   = table.selection.rows
          val hasSel  = range.nonEmpty
          actionDelete.enabled = hasSel
          actionAttr  .enabled = hasSel
          actionEdit  .enabled = hasSel
          // println(s"Table range = $range")
          val newSel = range.map(procSeq(_): ObjView[T])
          selectionModel.iterator.foreach { v =>
            if (!newSel.contains(v)) {
              // println(s"selectionModel -= $v")
              selectionModel -= v
            }
          }
          newSel.foreach { v =>
            if (!selectionModel.contains(v)) {
              // println(s"selectionModel += $v")
              selectionModel += v
            }
          }

        case e: MouseButtonEvent if e.triggersPopup => showPopup(e)
      }

      tlSelModel addListener tlSelListener

      val pBottom = new BoxPanel(Orientation.Vertical)
      if (groupHOpt.isDefined) {
        // only add buttons if group is modifiable
        pBottom.contents += new FlowPanel(ggAdd, ggDelete)
      }
      pBottom.contents += new FlowPanel(ggAttr, ggEdit)

      component = new BorderPanel {
        add(scroll , BorderPanel.Position.Center)
        add(pBottom, BorderPanel.Position.South )
      }
    }

    private def showPopup(e: MouseEvent): Unit = desktop.Window.find(component).foreach { w =>
      val hasGlobal = selectionModel.nonEmpty
      val hasTL     = tlSelModel    .nonEmpty
      if (hasGlobal) {
        import Menu._
        // val itSelect      = Item("select"        )("Select Connected Regions")(selectRegions())
        val itDup         = Item("duplicate"     )("Duplicate"                       )(duplicate(connect = false))
        val itDupC        = Item("duplicate-con" )("Duplicate with Connections"      )(duplicate(connect = true))
        val itConnect     = Item("connect"       )("Connect to Selected Regions"     )(connectToSelectedRegions())
        val itDisconnect  = Item("disconnect"    )("Disconnect from Selected Regions")(disconnectFromSelectedRegions())
        val itDisconnectA = Item("disconnect-all")("Disconnect from All Regions"     )(disconnectFromAllRegions())
        if (groupHOpt.isEmpty) {
          itDup .disable()
          itDupC.disable()
        }
        if (!hasTL) {
          itConnect   .disable()
          itDisconnect.disable()
        }

        val pop = Popup().add(itDup).add(itDupC).add(Line).add(itConnect).add(itDisconnect).add(itDisconnectA)
        pop.create(w).show(component, e.point.x, e.point.y)
      }
    }

//    private def selectRegions(): Unit = {
//      val itGlob = selectionModel.iterator
//      cursor.step { implicit tx =>
//        val scans = itGlob.flatMap { inView =>
//          inView.obj.scans.get("in")
//        }
//        tlSelModel.
//      }
//    }

    private def duplicate(connect: Boolean): Unit = groupHOpt.foreach { groupH =>
      val itGlob = selectionModel.iterator
      val edits = cursor.step { implicit tx =>
        val tl = groupH()
        val it = itGlob.map { inView =>
          val inObj   = inView.obj
//          val span    = inView.span   // not necessary to copy
          val span    = SpanLikeObj.newConst[T](Span.all)
          val outObj  = ProcActions.copy[T](inObj, connectInput = connect)
          EditTimelineInsertObj("Insert Global Proc", tl, span, outObj)
        }
        it.toList   // tricky, need to unwind transactional iterator
      }
      val editOpt = CompoundEdit(edits, "Duplicate Global Procs")
      editOpt.foreach(undoManager.add)
    }

    private def connectToSelectedRegions(): Unit = {
      val seqGlob = selectionModel.iterator.toSeq
      val seqTL   = tlSelModel    .iterator.toSeq
      val plGlob  = seqGlob.size > 1
      val plTL    = seqTL  .size > 1
      val edits   = cursor.step { implicit tx =>
        val it = for {
          outView <- seqTL
          inView  <- seqGlob
          in      <- inView .obj match { case p: Proc[T] => Some(p); case _ => None }
          out     <- outView.obj match { case p: Proc[T] => Some(p); case _ => None } // Proc.unapply(outView.obj)
          source  <- out.outputs.get(Proc.mainOut)
          if Edits.findLink(out = out, in = in).isEmpty
        } yield Edits.addLink(source = source, sink = in, key = Proc.mainIn)
        it.toList   // tricky, need to unwind transactional iterator
      }
      val editOpt = CompoundEdit(edits,
        s"Connect Global ${if (plGlob) "Procs" else "Proc"} to Selected ${if (plTL) "Regions" else "Region"}")
      editOpt.foreach(undoManager.add)
    }

    private def disconnectFromSelectedRegions(): Unit = {
      val seqGlob = selectionModel.iterator.toSeq
      val seqTL   = tlSelModel    .iterator.toSeq
      val plGlob  = seqGlob.size > 1
      val plTL    = seqTL  .size > 1
      val edits   = cursor.step { implicit tx =>
        val it = for {
          outView <- seqTL
          inView  <- seqGlob
          in      <- inView .obj match { case p: Proc[T] => Some(p); case _ => None } // Proc.unapply(outView.obj)
          out     <- outView.obj match { case p: Proc[T] => Some(p); case _ => None } // Proc.unapply(outView.obj)
          link    <- Edits.findLink(out = out, in = in)
        } yield Edits.removeLink(link)
        it.toList   // tricky, need to unwind transactional iterator
      }
      val editOpt = CompoundEdit(edits,
        s"Disconnect Global ${if (plGlob) "Procs" else "Proc"} from Selected ${if (plTL) "Regions" else "Region"}")
      editOpt.foreach(undoManager.add)
    }

    private def removeInputs(in: Obj[T])(implicit tx: T): Option[UndoableEdit] =
      if (!in.attr.contains(Proc.mainIn)) None else {
        val edit = EditAttrMap.remove(name = "Input", obj = in, key = Proc.mainIn)
        Some(edit)
      }

    private def disconnectFromAllRegions(): Unit = {
      val seqGlob = selectionModel.iterator.toList
      val plGlob  = seqGlob.size > 1
      val edits   = cursor.step { implicit tx =>
        seqGlob.flatMap { inView =>
          val in = inView.obj
          removeInputs(in)
        }
      }
      val editOpt = CompoundEdit(edits,
        s"Disconnect Global ${if (plGlob) "Procs" else "Proc"} from All Regions")
      editOpt.foreach(undoManager.add)
    }

    def dispose()(implicit tx: T): Unit = deferTx {
      tlSelModel removeListener tlSelListener
    }

    def add(proc: ObjView[T]): Unit = proc match {
      case pv: ProcObjView.Timeline[T] =>
        val row   = procSeq.size
        procSeq :+= pv
        tm.fireTableRowsInserted(row, row)

      case _ =>
    }

    def remove(proc: ObjView[T]): Unit = {
      val row = procSeq.indexOf(proc)
      if (row >= 0) {
        procSeq = procSeq.patch(row, Vec.empty, 1)
        tm.fireTableRowsDeleted(row, row)
      }
    }

    def iterator: Iterator[ProcObjView.Timeline[T]] = procSeq.iterator

    def updated(proc: ObjView[T]): Unit = {
      val row = procSeq.indexOf(proc)
      if (row >= 0) {
        tm.fireTableRowsUpdated(row, row)
      }
    }
  }
}