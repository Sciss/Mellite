# Notes from 02-May-2019

We want to be able to have a smooth work flow in which selections in the timeline can be
subject to FScape processing, such that the selected input region, or two / multiple input
regions are patched into attributes of a program, the program is run, and the result
replaces (for example) the inputs. This needs to accommodate various scenarios and be
implemented such that we can perform these operations with minimum ceremony.

For instance, say we want to apply a foreground/background filter; so we have two inputs
and their order matters. We want to replace the "background" input by a version filtered
by the "foreground".

A rather manual approach, yet still with some forms of automation would be to create
a widget that allowed a drop from a timeline drag; that would have to be able to discern
the objects dropped, along with their time spans and the timeline model parameters
(selection span, perhaps position).

In this variant, we should perhaps standardise a "take-and-drag" button for views,
located in their top left position, as is already the case for audio cue view.

Obviously we would then have to special-case the drop widget so it can parse that bit,
including parsing the type of object which is difficult (`Proc` with `AudioCue.Obj`
in its attribute map at a conventional key)... This is all very easy for a general Scala
program and thus `Action`. For in terms of `Ex` and `Control`?

------

We should also be forward looking in terms of merging with Eisenkraut, where naturally
we would implement the 'process' menu items as FScape programs. Sadly there is no 
sub-typing possibility for `DataFlavor`, so we can't just define a general trait for
moving objects and then have more specific flavours for timeline DnD, folder DnD, etc.
So in that sense, we would probably choose a variant where we can add custom menu items
to the timeline view. But the programs behind that item could nevertheless be called
with something akin to the envisioned timeline DnD.

How could parsing this DnD object look like? For just an object, it would be easier,
we could look at `Attr` etc. I guess we should introduce a glue API for the possible
drags supported, and then have a select operation like on OSC reception? Then we could
even have dynamical matching at the cost of type safety? Like:

```
val tlPos = Var[Long]
val tlSel = Var[SpanLike]
val tlObj = Var[Option[Proc]]
val tr = DnD.select("/timeline", tlPos, tlSel, tlObj)
```

This does nt work well, because the API cannot expanded, and the `tlObj` formulation is
not good. Instead, if we use a form of `collect[A]` as we did (?) with `Folder` (or was
that the Patterns project?), we can add operations to an opaque `A` at later points,
and we do not need to select stuff we're not interested in.

In Patterns we have `Attribute[Folder].collect[A: Obj.Aux]: Pat[A]`. Here, `Folder` is
an empty `case class Folder()` and we are actually processing the attribute map
reference. `Obj.Aux` requires that an input `stm.Obj` is provided. This is going to be
a bit of a DRY case... perhaps at some point we should merge `Ex` and `Pat` within the
same library.

The question is if we can transform

```
def extract[S <: Sys[S]](obj: stm.Obj[S])(implicit tx: S#Tx): Option[A]
```

into something more general, like

```
def extract[S <: Sys[S], Repr[~ <: Sys[~]](obj: Repr[S])(implicit tx: S#Tx): Option[A]
```

With a match like `case b: BooleanObj[S]` we get a compiler warning that `S` is erased.
So we need either a different `Extractor` or a useful upper bound (which does not exist).
We can introduce an upper bound for dragging, on top of `timeline.DnD.Drag`, without
problems.

So we have already these different APIs: Patterns:

```
implicit class Factory(name: String) {
  def attr[A: Obj.Aux]: Attribute[A] = Attribute(key = name, default = None)
}
```

Expr:

```
implicit class StringToExAttr(x: String) {
  def attr[A](implicit bridge: Attr.Bridge[A]): Attr[A] = Attr(x)
}
```

`Obj.Aux` is described by the `extract` method above. `Attr.Bridge` in contrast by

```
def cellView[S <: Sys[S]](obj: stm.Obj[S], key: String)(implicit tx: S#Tx): CellView.Var[S, Option[A]]
```

In other words, we diverted here to be able to update attributes (bidirectional).

----------

Again:

```
object Drop {
  trait Selector[A] extends Aux {
    def select[S <: Sys[S]](obj: Drop[S]) /* (implicit tx: S#Tx) */ : Option[A]
  }
}

trait Drop[S <: Sys[S]]   // top type

object DnD {
  def select[A: Drop.Selector]: (Trig, Ex[A])
  
  // note: cannot combine Trig and Ex!
  // trait Select[A] extends Trig with Ex[A]
}
```

Can we do with losing `S` in `A`, like have a representation of `Proc` where we can still
obtain the audio cue attribute?

```
trait mellite.TimelineDrop[S <: Sys[S]] extends Drop[S]

object DnD.Timeline {
  implicit def selector: Drop.Selector[DnD.Timeline] = ...
  
  implicit class ops(t: DnD.Timeline) {
    def sampleRate: Ex[Double]
    def visible   : Ex[Span]
    def position  : Ex[Long]
    def selection : Ex[SpanOrVoid]
    def bounds    : Ex[SpanLike]
    def virtual   : Ex[Span]
  }
}
trait DnD.Timeline {
  def sampleRate: Double
  def visible   : Span
  def position  : Long
  def selection : SpanOrVoid
  def bounds    : SpanLike
  def virtual   : Span
}

val (tr, tl) = DnD.select[DnD.Timeline]
val timed = tl.selectedObjects.collect[Proc]
// or?
// val timed = tl.selectedObjects[Proc]
```

We can easily give "default" values for all of these.
(might be interesting _at a later point_, to have `modifiableOption` as well, yielding for example
`visible: Model[Span]`). That's the more easy part. Now for selected objects (indeed, should we
provide a bridge to `proc.Timeline` as well?), which come in the form of `TimelineObjView`. If we
ignore the track positioning, that's just a handle for the span and the placed object.

Here, `timed` would be perhaps `Ex[Seq[(SpanLike, Proc)]]`, or `(Ex[Seq[Spanlike]], Ex[Seq[Proc]])`.
Probably the former, and we can split them if needed through `unzip`? Or we duplicate the notion
of `Timed`, so `Ex[Seq[Timed]]`, the annoying them being that we need then extension methods for all
of the methods of `Ex[Timed]` but for a sequence. The advantage of `selectedObjects[Filter]` is that
we keep the spans together with the filtered objects. Still we could do the same with
`Ex[Seq[Timed]]`, i.e. add a `collect` method. Then `Timed` would have a type parameter.

```
val cueOpt: Ex[Option[AudioCue]] = timed.value.attr[AudioCue]("sig")
```

No! Because now we have this sequence problem which is less severe in patterns. We would have
`cueOpt: Ex[Seq[Option[AudioCue]]]` :( Can we go from `Ex[Seq[A]]` to `Pat[A]` ?

```
val p1: Ex[Option[Timed[Proc]]] = timed.applyOption(0)
val p2: Ex[Option[Timed[Proc]]] = timed.applyOption(1)
```

Again stuck, unless we cheat an introduce a `Timed.Empty` (which we could do).

```
val p1: Ex[Timed[Proc]] = timed.applyOption(0).getOrElse(Timed.Empty)
val p2: Ex[Timed[Proc]] = timed.applyOption(1).getOrElse(Timed.Empty)
```

(we could add `def apply(index: Ex[Int], default: Ex[A]): Ex[A]`)

With `Timed[+A <: Obj]` we'd have `def Empty: Timed[Obj]` or `def Empty: Timed[Nothing]`.

--------------

```
val cueOpt1: Ex[Option[AudioCue]] = p1.value.attr[AudioCue]("sig")
val cueOpt2: Ex[Option[AudioCue]] = p2.value.attr[AudioCue]("sig")
```

again we could have a silly `AudioCue.Empty`. Thinking about it... why can't we have
`flatMap` where, like `FlatMap[A, B](in: Ex[Option[A]], Ex[Option[B]]) extends Ex[Option[B]]`,
and likewise `Map` ? What was the problem in `Pat`? We need one internal monad like
`Seq` or `Option`; but that's exactly what we have here.; so should it not be possible to write

```
val cueOpt1: Ex[Option[AudioCue]] = timed.applyOption(0).flatMap(_.value.attr[AudioCue]("sig"))
```

? I don't see why this can't be done. In any case, we need an 'empty' element for `AudioCue` at
some point, so we could as well add `Timed.Empty` and implement `map` and `flatMap` later.

--------

So let's assume we get to the one or or two input audio cue's. So we have a `Runner` for FScape,
initiate that, either automatically or through a UI, and upon completion, we create a new audio cue
object (T.B.D. -- see previous discussions on Koerper); and assuming that we know how to do that,
we need to delete and insert object from and into the timeline.

We will want to "latch" the data from drop to render-finish, i.e. region to remove and insertion
position. Something like

```
val run     = Runner("fsc")
val stopped = ??? : Trig
val trOk    = tr.filter(!running) // this was always implied above
val tlL     = trOk.latch(tl)
val p1L     = trOk.latch(p1)

val cue     = AudioCue.make(stopped) // T.B.D.
stopped ---> tlL.timeline.remove(p1L)
stopped ---> tlL.timeline.insert(???)
```

Another possibility would be to introduce edits, and assembly an edit operation which is
triggered (probably a better idea, a bit more "functional" than imperative).

```
val edit = Edit("bla")(
  tlL.timeline.remove(p1L)
  tlL.timeline.insert(???)
)
stopped ---> edit
```

> This will become a general question for `.attr` as well, should we be able to undo the edits
in the UI, and if so, where is the undoable edit recorded. Probably the widgets should open an
edit context, and the attr update would automatically detect if it is present or not.

> We should also be able to dispose the cue / underlying artifact if the edit is undone and
disposed (history purged because window closes, or new edit appended).

Stuff like `timeline.remove` could as well be a `Trig` (for execution outside undo context) and a
reference to an undoable edit.

## "Objects"

What does `Ex[Obj]` mean? Do we hold a reference an an actual `stm.Obj[S]` somewhere? If we _do_,
we can imagine an `Attr.Bridge[Obj]` without problem.

The "normal" way we use expression graphs is to assume that `S =:= InMemory`; so what happens if we 
drop a `TimelineView` where `S =:= Durable`, or in general, where that `S` does not match the `S` of
the expression graph?

We could only imagine that `Ex.Context` actually holds both systems and a bridge. Then the next problem
is the proliferation of `stm.Cursor`, because if we allow in-memory `step` transactions, we no longer
can fall back to an outer durable transaction?

So let's say, we can only populate `TimelineView#selectedObjects` if there is a fall back and its main
system matches the system of the dragged object.

__No, wrong!__ We do use the full `S`, it's just that so far we have used `Ref` for state; in the future,
we should perhaps better use `S#I#Var`? In this sense, we designed `Ex` differently than Patterns. On the other
hand, `IEvent` and `ITargets` are already tuned for in-memory operation, so we won't gain anything from such
change, except perhaps that wrapping a graph inside an Akka Stream stage logic might benefit from dropping
the transactional context altogether (using `Plain`).

So we can simply check if the DnD system equals the own system (`universe.workspace.system`).

-----------

# map, flatMap

The following is showing a problem (08-May-2019)

```
obj.map(_.attr[String]("name")).headOption
```

It throws a NPE because `attr` needs to register a listener on `It` (the object iteration variable), so when
the outer `obj` is empty, then `It` is uninitialised. I already thought this went to smooth...

Which objects do require calls to `.value` in their initialization?

- `Latch` (could be moved)
- controls, such as `OscNode`; we should forbid controls to be created within a `.map` graph,
  this again speaks for wrapping in `Graph {}` so we can control the builder.

Looking at `ExpandedImpl`, there is the uninitialised `Ref`, and actually calling `fire` should _not_ be done
in value update. This should be the "deal" for mapping -- no events are fired from the iterator, we solely rely
on `.value` calls bottom up.

The question is, is `Obj.Attr` an outlier, or can it be fixed to avoid the `.value` call?
It's that `Ex[Obj]` is kind of hollow. The problem does not occur in Patterns, because due to the `reset`
functionality, we never assume eager initialization.

The safer but more involved way would be to have the inner stuff as an unexpanded `Graph`, and to re-expand it
for each push of the outer value. More involved, because it means again to distinguish between source elements
that are inside and outside the graph. Take for example this:

```
val foo: Ex[Int] = ???
val seq: Ex[Seq[Int]] = ???

seq.map(bar => bar + foo)
```

This will (or not?) cause problems when `foo` is expanded inside the closure.
The main issue is clean up (`dispose`). While controls are registered with the graph builder, which in turn
can expand them, collect their `IControl` instances, and then free them, this is not the case for expressions.
So in the above example, the "closure" compiles to `BinaryOp(BinaryOp.Plus, It, foo)`, and the bin-op adds
event listeners to register changes to `It` and `foo` (which in turn might also be expanded inside the closure and
thus register more listeners). The result is something like `targets.add(fooEx.changed, binOpEx.changed)`, and
`targets` comes from the source, so might be _outside_ the closure's graph (if we even want to think about having
multiple instances of `Context`). 

In summary, it would be much easier if we tracked _all called to `expand`_ that result in `Disposable` objects,
for example by adding a `final def expand` to `Ex`, forcing the `Lazy` approach. Then it's simple to dispose
everything that comes out of a sub-graph.

Where would sub-graphs and iteration be used?

- `Seq#map`, `Seq#flatMap` (`Seq#foldLeft`, `Seq#sortWith`) -- they would work very similar to each other
- `Option#map`, `Option#flatMap` -- kind of "sad" for the performance? but would work
- `If`-`Then`-`Else` etc. although I suspect we won't need this much, as simply selection between two expressions
  should be fine

-------------

A hypothetical next step program:

```
val tgt     = DropTarget()
val drop    = tgt.select[TimelineView]
val tlv     = drop.value
val timed   = tlv.selectedObjects

val actOpt  = for {
  t1 <- timed.applyOption(0)
  t2 <- timed.applyOption(1)
  c1 <- t1.value.attr[AudioCue]("sig")
  c2 <- t2.value.attr[AudioCue]("sig")
} yield {
  val s1 = t1.span
  val s2 = t2.span
  val test = s1 union s2
  
  PrintLn(test.toStr)
}

drop.received ---> actOpt

tgt
```

So we would need `map` and `flatMap` on `Option[Ex[_]]`, as well as `---> Option[Act]`.

Let's go one step back (not yet require different result types of `flatMap`):

```
val tgt     = DropTarget()
val drop    = tgt.select[TimelineView]
val tlv     = drop.value
val timed   = tlv.selectedObjects

val textOpt = for {
  t1 <- timed.applyOption(0)
  t2 <- timed.applyOption(1)
  c1 <- t1.value.attr[AudioCue]("sig")
  c2 <- t2.value.attr[AudioCue]("sig")
} yield {
  val s1 = t1.span
  val s2 = t2.span
  val test = s1 union s2
  test.toStr
}

drop.received ---> PrintLn(textOpt.getOrElse("no match"))

tgt
```
