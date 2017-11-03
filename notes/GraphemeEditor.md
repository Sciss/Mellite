- there should be a view factory and a view type - `GraphemeObjView`
- a grapheme is not necessarily a break-point function in the sense that
  there is a logical vertical axis that all views could relate to
- for example, a grapheme view could simply be a succession of sound files,
  or strings
- any vertical positioning must therefore be optional and configurable
- multiple successive elements might want to be shows as a 'compound' view,
  e.g. as a break-point-function we want it to be editable as a connected polygon
- how should 'hidden' elements be shown or accessed? i.e. elements which share
  their time position with other elements
- we have `ParamSpec` to define an axis; where would that be picked up from or stored?
- it could be that we want to see the linear series, even of numbers, without vertical
  interpretation
  
# The scale versus scale-invariant problem

In the long term the renderers should be able to address this problem. It means that
the rendering of object might need to take a screen space that combines static sizes
(e.g. rendering a tooltip or label) with scale-variant sizes (e.g. the width of a
span on a timeline). That problem manifests itself in the fact that we can't simply
drop things into a spatial index, because the space is a linear combination
(usually sum) of two values that don't relate to each other.

A simple trick could be to track "worst case insets" in a priority-queue, so we can
always add them to repaint-actions.

# Predecessor/successor context

- we only need to store that in the views if it would affect, for example, it's calculation
  of insets. but anyway, now we update without notification, so it would not work
- can we construct a case where passing that information straight to the paint methods
  (and eventually mouse/keyboard interaction) is a severe disadvantage? if _not_, that would
  save a lot of book keeping
  
Let's see:

- __Numbers:__ No context needed; they need to render their vertical position with a fixed-size knob.
- __Proc.Output:__ (I don't think currently supported); no context needed
- __Grapheme:__ (i.e. nested); the necessity would depend on its own head/last elements?
- __AudioCue:__ Perhaps successor? If it does not rely on the clip rectangle of the `Graphics2D`,
  it would need that
- __Action:__ No context needed
- __Folder:__ would extend to all its elements? Or we don't support it

Only the hypothetical `CurveSegment` would require the predecessor information; this is kind of
a strange case, because the curve-segments only "works" with certain kind of predecessor elements,
e.g. a number or another curve segment. In other words, giving the preceding `numFrames` would not
even help, we would need the entire view.

I wonder, if we should not rather give up the isolated curve segment, and introduce something analogous
to `Grapheme` dedicated to curve-segments as values. You could then still store such a curve sequence
within a grapheme. What would happen if we put curve segments back into `Nuages` control? How would they
mix with scalar values? Would we just drop them in favour of curve segments with `step` function?
Let's not forget that we use `Proc.Output` there as well.

Let's stick to `Grapheme` for now. Let's remember how we used to draw envelopes in SwingOSC:
The _predecessor_ actually draws towards the target state. The target state itself would draw
the handle at the nominal level, and only if it itself has a successor, it would draw the curve.
In other words, we actually _can_ rely on successor-information-only, no need to extend the context in
both ways. But we should remove `numFrames` and replace it with `succ: Option[GraphemeObjView[S]]`.
