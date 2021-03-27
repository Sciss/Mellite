# Internal Resources

## notes 27-Mar-2021

These would be "files" whose content is not stored directly in the workspace’s database:

- audio files
- images (once supported)
- binary data?
- ...

Artifacts referring to such resources would use a special scheme like `mllt:` or
`workspace:`. Looking at [existing schemes](https://en.wikipedia.org/wiki/List_of_URI_schemes):

- `blob` - probably not smart to use something that has other meaning in SoundProcesses.js
- `app` - begins with instance identifier, so `app://mellite/sound.aif`; the additional instance
  identifier could make it more cumbersome, e.g. an implementation of `AsyncFileSystemProvider`
  would essentially have to check that first path component always, and it couldn't really do
  anything with a URI that does not use the instance identifier.
  
Let's use `mllt` or `workspace` as scheme. The latter would emphasise that this pertains to
SoundProcesses and not just Mellite. This scheme must be recognised at various points and "resolved" to
a different scheme according to platform (`file` versus `idb`).  Or should it be possible to convert some
into `http`?

We can start with a flat hierarchy, i.e. only top-level resources, and see if we need sub-directories
as well. We can then begin with a `ArtifactLocation.Workspace()`.

We will have to augment `Copy` to deal with resources, i.e. instances of `Artifact` whose value's scheme
is `workspace`. Mellite would then either automatically make copies of these artifacts, or ask the user
for confirmation (and possibly present required disk space, different options for representation?).
What would happen with `InMemory`? In other words, the `AsyncFileSystemProvider` would depend on the
system type? But then how would we distinguish between `Durable` on the desktop and `Durable` based on
binary blob in the browser?

I guess this is resolved, as `Copy` with resource treatment only occurs in Mellite on the desktop. The
interesting question is what happens when using export-as-binary-workspace. We could simply, _if_ workspace
resources are used, create a directory `<ws-name>_assets` next to `<ws-name>.mllt.bin`. Then it will be
up to the user to upload that directory, and we could download the resources through additional XHR calls.
The list of resources could be stored in the binary workspace, so that the loader would immediately know
which files to download from `<ws-name>_assets`. 

Realistically, in the scenario of web export, we are talking about a small number of files, or a small 
total size of files. What happens when there is a huge number of small files? That might be inefficient
with individual XHR for each. In that case, it might be smarter to put all assets together in a `.zip` or
`.jar` and upload that instead. This is an option that we can still pursue later if that issue ever appears.

Now, are the assets downloaded as JavaScript blobs, or stored as "regular" `idb` files? For the first
implementation, assume they are in `idb`.

## images

Slightly orthogonal is the question of how we can support images, which would be a particular case where
internal resources will be useful. For instance, in a `Markdown`, we may want to include internal images,
so that we could include diagrams in `FScape-modules.mllt`, for example. Also, we then may want to support
image decorators for `Widget`.

A related question is whether we should support SVG images as well, which would be particularly useful for
diagrams, and vector icons. For icons in `Widget`, we may first support monochromatic icons based on an
SVG _path_ string. WebLookAndFeel uses svgSalamander for loading and rendering SVG.

## in-memory

It could either refuse to support workspace artifacts, or they would be placed in `/tmp`. When refusing,
and copying from a durable workspace, they could become regular `file` artifacts.

## implementation

We need to find the use of `URI.getPath`, e.g. in SP in `AuralProcImpl`, there is `streamAudioCueToBuffer`
that simply extracts the path. Here we would need to check the scheme and resolve if necessary. Similarly,
in FScape. In Mellite, we have `AudioCueViewImpl` with `new File(snapshot.artifact)`. And so on.
