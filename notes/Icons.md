This documents aids to avoid accidental re-use

# UI elements

| action or element             | shape                                 | location                                   |
| ----------------------------- | ------------------------------------- | ------------------------------------------ |
| hh:mm:ss time type            | `raphael.Shapes.WallClock`            | (audiowidgets) `TimeField`                 |
| time go-to start              | `raphael.Shapes.TransportBegin`       | (audiowidgets) `ActionGoToTime`            |
| time go-to current location   | `raphael.Shapes.TransportBegin`       | (audiowidgets) `ActionGoToTime`            |
| time go-to end                | `raphael.Shapes.TransportEnd`         | (audiowidgets) `ActionGoToTime`            |
| duration                      | `raphael.Shapes.FutureTime`           | (freesound)    `SoundTableViewImpl`        |
| number-of-channels            | `raphael.Shapes.Flickr`               | (freesound)    `SoundTableViewImpl`        |
| view object                   | `raphael.Shapes.View`                 | (mellite)      `GUI`                       |
| view attributes               | `raphael.Shapes.Wrench`               | (mellite)      `GUI`                       |
| add object                    | `raphael.Shapes.Plus`                 | (mellite)      `GUI`                       |
| duplicate object              | `raphael.Shapes.SplitArrows`          | (mellite)      `GUI`                       |
| duplicate object              | `raphael.Shapes.SplitArrows`          | (mellite)      `GUI`                       |
| toggle DSP                    | `raphael.Shapes.Power`                | (mellite)      `PlayToggleButton`          |
| apply/save changes            | `raphael.Shapes.Check`                | (mellite)      `ParamSpecObjView`          |
| abort operation               | `raphael.Shapes.Cross`                | (mellite)      `FScapeObjView`             |
| drag source                   | `melliteg.gui.Shapes.Share`           | (mellite)      `DragSourceButton`          |
| run code                      | `raphael.Shapes.Bolt`                 | (mellite)      `CodeFrameImpl`             |
| compile/build                 | `raphael.Shapes.Hammer`               | (mellite)      `CodeViewImpl`              |
| render/refresh                | `raphael.Shapes.RefreshArrow`         | (mellite)      `MarkdownEditorViewImpl`    |
| navigate backward             | `raphael.Shapes.Backward`             | (mellite)      `MarkdownRenderViewImpl`    |
| navigate forward              | `raphael.Shapes.Forward`              | (mellite)      `MarkdownRenderViewImpl`    |
| edit                          | `raphael.Shapes.Edit`                 | (mellite)      `MarkdownRenderViewImpl`    |
| legal/licensing               | `mellite.gui.Shapes.Justice`          | (mellite)      `FreesoundRetrievalObjView` |
| move (spatially)              | `raphael.Shapes.Hand`                 | (mellite)      `MoveImpl`                  |
| audition                      | `mellite.gui.Shapes.Audition`         | (mellite)      `AuditionImpl`              |
| cursor                        | `mellite.gui.Shapes.Pointer`          | (mellite)      `CursorImpl`                |
| gain                          | `mellite.gui.Shapes.Gain`             | (mellite)      `GainImpl`                  |
| mute                          | `mellite.gui.Shapes.Mute`             | (mellite)      `MuteImpl`                  |
| patch/connect                 | `mellite.gui.Shapes.Patch`            | (mellite)      `PatchImpl`                 |
| resize                        | `mellite.gui.Shapes.Crop`             | (mellite)      `ResizeImpl`                |

# Obj views

| object type                   | shape                                    |
| ----------------------------- | ---------------------------------------- |
| `Action`                      | `raphael.Shapes.Bolt`                    |
| `Code`                        | `raphael.Shapes.Code`                    |
| (generic/unknown)             | `raphael.Shapes.No`                      |
| `String`                      | `raphael.Shapes.Font`                    |
| `Paint`                       | `raphael.Shapes.Paint`                   |
| `Timeline`                    | `raphael.Shapes.Ruler`                   |
| `Grapheme`                    | `raphael.Shapes.LineChart`               |
| `Ensemble`                    | `raphael.Shapes.Cube2`                   |
| `Nuages`                      | `raphael.Shapes.CloudWhite`              |
| `ParamSpec`                   | `raphael.Shapes.Thermometer`             |
| `ArtifactLocation`            | `raphael.Shapes.Location`                |
| `Artifact`                    | `raphael.Shapes.PagePortrait`            |
| `AudioCue`                    | `raphael.Shapes.Music`                   |
| `Proc`                        | `raphael.Shapes.Cogs`                    |
| `Double`                      | `mellite.gui.Shapes.RealNumber`          |
| `Double`                      | `mellite.gui.Shapes.RealNumber`          |
| `DoubleVector`                | `mellite.gui.Shapes.RealNumberVector`    |
| `Int`                         | `mellite.gui.Shapes.IntegerNumber`       |
| `FScape`                      | `mellite.gui.Shapes.Sparks`              |
| `Markdown`                    | `mellite.gui.Shapes.Markdown`            |
| `Long` XXX TODO               | `mellite.gui.Shapes.IntegerNumber`       |
| `Boolean`                     | `mellite.gui.Shapes.BooleanNumber`       |
| `IntVector`                   | `mellite.gui.Shapes.IntegerNumberVector` |
| `FadeSpec`                    | `mellite.gui.Shapes.Aperture`            |
| `proc.Output`                 | `raphael.Shapes.Export`                  |
| `FScape.Output`               | `raphael.Shapes.Export`                  |
| `EnvSegment`                  | `raphael.Shapes.Connect`                 |

# SysSon

| object or action              | shape                                    |
| ----------------------------- | ---------------------------------------- |
| `DataSource`                  | `raphael.Shapes.Database`                |
| `Plot` XXX TODO Coll          | `raphael.Shapes.LineChart`               |
| show table                    | `at.iem.sysson.gui.Spreadsheet`          |
| associate dimensions          | `raphael.Shapes.Clip`                    |
| drag-and-drop                 | `raphael.Shapes.Hand`                    |
| `Matrix`                      | `raphael.Shapes.IconView`                |
| `Sonification`                | `raphael.Shapes.Feed`                    |
