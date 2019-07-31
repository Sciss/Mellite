/*
 *  GlobalProcPreset.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.lucre.stm.Sys
import de.sciss.swingplus.Spinner
import de.sciss.synth
import de.sciss.synth.proc.{Code, Proc, SynthGraphObj}
import de.sciss.synth.{GE, SynthGraph}
import javax.swing.SpinnerNumberModel

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.{Component, FlowPanel, Label, Swing}

object GlobalProcPreset {
  final val all: ISeq[GlobalProcPreset] = ISeq(Empty, OneToN, NToN)

  trait Controls {
    def component: Component

    def make[S <: Sys[S]]()(implicit tx: S#Tx): Proc[S]
  }

  private trait Impl extends GlobalProcPreset {
    // ---- abstract ----

    type Ctl <: Controls

    protected def source(controls: Ctl): Option[String]
    protected def graph[S <: Sys[S]](controls: Ctl)(implicit tx: S#Tx): SynthGraphObj[S]
    protected def hasOutput: Boolean

    protected def configure[S <: Sys[S]](proc: Proc[S], controls: Ctl)(implicit tx: S#Tx): Unit

    // ---- impl ----

    override def toString: String = name

    final def make[S <: Sys[S]](controls: Ctl)(implicit tx: S#Tx): Proc[S] = {
      val p = Proc[S]
      p.graph() = graph(controls)
      source(controls).foreach(s => p.attr.put(Proc.attrSource, Code.Obj.newVar(Code.SynthGraph(s))))
      if (hasOutput) p.outputs.add(Proc.mainOut)
      configure(p, controls)
      p
    }
  }

  private object Empty extends Impl { self =>
    def name = "Empty"

    protected def source(controls: Ctl): Option[String] = None

    protected def graph[S <: Sys[S]](controls: Ctl)(implicit tx: S#Tx): SynthGraphObj[S] = SynthGraphObj.empty[S]

    protected def hasOutput = false

    final class Ctl extends Controls {
      val component: Component = Swing.HGlue

      def make[S <: Sys[S]]()(implicit tx: S#Tx): Proc[S] = self.make(this)
    }

    def mkControls() = new Ctl

    protected def configure[S <: Sys[S]](proc: Proc[S], controls: Ctl)(implicit tx: S#Tx): Unit = ()
  }

  private trait MToN extends Impl { self =>
    // ---- abstract ----

    protected def numInputChannels (controls: Ctl): Int
    protected def numOutputChannels(controls: Ctl): Int

    // ---- impl ----

    // XXX TODO --- should have a macro to produces both source and graph at the same time
    final protected def source(controls: Ctl): Option[String] = {
      val numInChannels  = numInputChannels (controls)
      val numOutChannels = numOutputChannels(controls)
      Some(
        s"""val in    = ScanInFix($numInChannels)
           |val gain  = "gain".kr(1f)
           |val mute  = "mute".kr(0f)
           |val bus   = "bus" .kr(0f)
           |val amp   = gain * (1 - mute)
           |val mul   = in * amp
           |val sig   = ${if (numInChannels == numOutChannels) "mul" else s"Seq.tabulate($numOutChannels)(i => mul.out(i % $numInChannels)): GE"}
           |Out.ar(bus, sig)
           |""".stripMargin
      )
    }

    final protected def graph[S <: Sys[S]](controls: Ctl)(implicit tx: S#Tx): SynthGraphObj[S] = {
      val numInChannels  = numInputChannels (controls)
      val numOutChannels = numOutputChannels(controls)
      // Note: be careful when you change this to take care of the source code string above
      val g = SynthGraph {
        import synth.proc.graph.Ops._
        import synth.proc.graph._
        import synth.ugen._
        val in    = ScanInFix(numInChannels)
        val gain  = "gain".kr(1f)
        val mute  = "mute".kr(0f)
        val bus   = "bus" .kr(0f)
        val amp   = gain * (1 - mute)
        val mul   = in * amp
        val sig   = if (numInChannels == numOutChannels) mul else Seq.tabulate(numOutChannels)(i => mul.out(i % numOutChannels)): GE
        Out.ar(bus, sig)
      }
      g
    }

    final protected def hasOutput = false

    final class Ctl extends Controls {
      private[this] val mNumChannels = new SpinnerNumberModel(1, 1, 1024, 1)

      def numChannels: Int = mNumChannels.getNumber.intValue()

      val component: Component = new FlowPanel(new Label("N:"), new Spinner(mNumChannels))

      def make[S <: Sys[S]]()(implicit tx: S#Tx): Proc[S] = self.make(this)
    }

    final def mkControls() = new Ctl

    final protected def configure[S <: Sys[S]](proc: Proc[S], controls: Ctl)(implicit tx: S#Tx): Unit = ()
  }

  private object OneToN extends MToN {
    def name = "1-to-N"

    protected def numInputChannels (controls: Ctl): Int = 1
    protected def numOutputChannels(controls: Ctl): Int = controls.numChannels
  }

  private object NToN extends MToN {
    def name = "N-to-N"

    protected def numInputChannels (controls: Ctl): Int = controls.numChannels
    protected def numOutputChannels(controls: Ctl): Int = controls.numChannels
  }
}
trait GlobalProcPreset {
  def name: String

  def mkControls(): GlobalProcPreset.Controls
}