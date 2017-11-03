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
