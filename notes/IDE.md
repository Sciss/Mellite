# IntelliJ platform

core: vfs, psi, ...; no UI
platform: actions, UI, text editor, ...
lang: language-independent code features, highlighting, navigation
other: debugging, testing, vcs

idea.platform.prefix - VM options / property that defines what plugins are loaded

format as 'regular' plugin xml file

application info file

build/scripts/dist.gant
(probably that's gradle now?)

layers
app > project > module > virtual files

component: life cycle part
(e.g. per app, or per project, or per module)

