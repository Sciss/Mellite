/*
 *  DragAndDrop.scala
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

package de.sciss
package mellite
package gui

import java.awt.datatransfer.{DataFlavor, Transferable, UnsupportedFlavorException}

import de.sciss.file.File

import scala.collection.JavaConverters._

object DragAndDrop {
  sealed trait Flavor[A] extends DataFlavor

  def internalFlavor[A](implicit ct: reflect.ClassTag[A]): Flavor[A] =
    new DataFlavor(s"""${DataFlavor.javaJVMLocalObjectMimeType};class="${ct.runtimeClass.getName}"""") with Flavor[A]

  object Transferable {
    /** Creates a transferable for one particular flavor. */
    def apply[A](flavor: Flavor[A])(data: A): Transferable = new Transferable {
      override def toString = s"Transferable($data)"

      // private val flavor = internalFlavor[A]
      // println(s"My flavor is $flavor")
      def getTransferDataFlavors: Array[DataFlavor] = Array(flavor) // flavors.toArray
      def isDataFlavorSupported(_flavor: DataFlavor): Boolean = {
        import equal.Implicits._
        _flavor === flavor
      }
      def getTransferData(_flavor: DataFlavor): AnyRef  = {
        if (!isDataFlavorSupported(_flavor)) throw new UnsupportedFlavorException(_flavor)
        data  /* .getOrElse(throw new IOException()) */ .asInstanceOf[AnyRef]
      }
    }

    def files(f: File*): Transferable = new Transferable {
      private[this] val data: java.util.List[File] = f.asJava

      def getTransferDataFlavors: Array[DataFlavor] = Array(DataFlavor.javaFileListFlavor)

      def isDataFlavorSupported (flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor
      def getTransferData       (flavor: DataFlavor): AnyRef  = data
    }

    /** Creates a transferable by wrapping a sequence of existing transferables. */
    def seq(xs: Transferable*): Transferable = new Transferable {
      def getTransferDataFlavors: Array[DataFlavor] = xs.iterator.flatMap(_.getTransferDataFlavors).toArray
      def isDataFlavorSupported(_flavor: DataFlavor): Boolean = xs.exists(_.isDataFlavorSupported(_flavor))
      def getTransferData(_flavor: DataFlavor): AnyRef = {
        val peer = xs.find(_.isDataFlavorSupported(_flavor)).getOrElse(throw new UnsupportedFlavorException(_flavor))
        peer.getTransferData(_flavor)
      }
    }
  }
}