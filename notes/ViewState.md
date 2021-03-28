# View State

## notes 27-Mar-2021

We want to be able to preserve view/editor state, such as window locations, but also for example
zoom level, timeline position, sonogram boost.

The question is where that state is stored and how. Performance questions aside, it would make
sense to store it as regular objects in an objects attribute map. To not pollute the user's
navigation of the attribute map, one could use a single `Tag` element as an additional hierarchical
level, and populate that in turn with the editor state as key-value pairs.

Alternatively, we could store a binary blob (introduce a new opaque blob type). This may be more
performant and use up less space, on the other hand, from the overall philosophy's point of view,
it's kind of sad not allowing code to interact with the view state.

The same sadness would be to store the state entirely outside the workspace, i.e. parallel to the
workspace in a different map like the event-store we used to have in an earlier version.
The advantage of this would be that a workspace could be copied without editor state, that it
could still be locked as read-only, etc.

---

_If_ we store in a `Tag` with the object, there must be a way to reduce the number of writes,
and to store alternatively if the workspace is read-only (at least it would be surprising if in
that case you closed a window, re-opened it, and state was lost). Alternatively, it could be
an explicit action: "store state"?

I imagine the user could check a flag (menu item checkbox) to preserve state or not. If enabled,
the state would only be stored when the view is closed, and only the state that changes. Sure,
workspaces can grow quite a bit in disk space, but disk space is also increasingly cheap, and
copying from workspace to workspace, there could be user options to filter out the editor state
(preserve "privacy" so to say).

### Examples

Timeline editor:

- window bounds
- sonogram boost
- timeline model: position, visible, selection (?), virtual, ...
- vertical viewport position
- not: tool
- not: selected objects (expensive!)
- global procs collapsed/expanded

Selection is already an open question - should we store the timeline selection at all?

Tag entries:

- `view-bounds`, `IntVector`
- `sonogram-boost`, `DoubleObj`
- `timeline-pos`, `LongObj`

etc.

(Yes, it would be nice if `Expr.Const` was not automatically an `Obj` that requires an `id`...)

Code editor:

- cursor position 
- selection? (IntelliJ does).

Grapheme editor:

- vertical axis bounds (once implemented)
- ...

AudioCue editor:

- view type: sonogram, waveform, etc. (once implemented)
- meters on/off (once implemented)

Attribute Map:

- column widths
- sorting order
