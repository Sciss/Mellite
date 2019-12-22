package de.sciss.mellite.impl

import de.sciss.file._
import de.sciss.kollflitz.ISeq
import de.sciss.lucre.swing.LucreSwing.defer
import de.sciss.mellite.Mellite.executionContext
import de.sciss.mellite.{GUI, Mellite, WebBrowser}
import de.sciss.synth.proc.Code
import dotterweide.build.{Module, Version}
import dotterweide.editor.controller.LookUpTypeAction
import dotterweide.editor.{Action, Editor}
import dotterweide.ide.DocUtil
import dotterweide.languages.scala.ScalaLanguage
import dotterweide.languages.scala.node.ScalaType
import dotterweide.node.NodeType

import scala.concurrent.Future
import scala.swing.{Dialog, GridPanel, Label, ProgressBar, Swing}
import scala.util.{Failure, Success}

object ApiBrowser {
  def openBase(code: Option[Code]): Unit =
    open(code, mkBasePath(code))

  /** @param code   the code object being edited
    * @param path   relative path inside the base directory of the API docs
    */
  def open(code: Option[Code], path: String): Unit =
    lookUpRef match {
      case Some(lookUp) => lookUp.open(code, path)
      case None =>
        if (!lookUpRefBusy) {
          def finish(docModule: Module): Unit = {
            val lookUp  = new LookUpDocumentation(docModule)
            lookUpRef   = Some(lookUp)
            lookUp.open(code, path)
          }

          val bestMod = mkDocModule()
          // if we find a local copy of the current version,
          // don't bother reading the online meta data
          if (isDocReady(bestMod)) {
            finish(bestMod)
          } else {
            val futMeta   = DocUtil.findModuleVersions(bestMod)
            lookUpRefBusy = true
            futMeta.onComplete { tr =>
              defer {
                lookUpRefBusy = false

                def checkVersions(versions: ISeq[Version]): Unit = {
                  val vOpt = versions.find(_ <= bestMod.version).orElse {
                    val v1 = versions.headOption
                    v1 match {
                      case Some(v2) =>
                        println(s"No matching documentation found. Using newer version $v2")
                      case None =>
                        println(s"No documentation found in maven meta data.")
                    }
                    v1
                  }
                  vOpt.foreach { v =>
                    if (v < bestMod.version) println(s"No current documentation found. Using older version $v")
                    val docModule = bestMod.copy(version = v)
                    finish(docModule)
                  }
                }

                tr match {
                  case Success(meta) =>
                    checkVersions(meta.versions)

                  case Failure(ex) =>
                    println(s"Could not resolve documentation meta data (${DocUtil.mkJavadocMetaDataUrl(bestMod)}):")
                    val msg = if (ex.getMessage == null) "" else s": ${ex.getMessage}"
                    println(s"${ex.getClass.getName}$msg")
                    // ex.printStackTrace()

                    // see if we have any local docs yet
                    val p = mkDocBaseDirParent(bestMod)
                    val versions = p.children(_.isDirectory).flatMap { vDir =>
                      Version.parse(vDir.name).toOption
                    }
                    checkVersions(versions)
                }
              }
            }
          }
        }
    }

  def lookUpDocAction(code: Code, ed: Editor, language: ScalaLanguage): Action =
    new LookUpDocAction(Some(code), ed, language)

  // ---- private ----

  private class LookUpDocAction(code: Option[Code], ed: Editor, language: ScalaLanguage)
    extends LookUpTypeAction(
      document  = ed.document,
      terminal  = ed.terminal,
      data      = ed.data,
      adviser   = language.adviser // doc.language.adviser
    )(ed.async, ed.platform) {

    override protected def run(tpeOpt: Option[NodeType]): Unit =
      tpeOpt match {
        case Some(tpe: ScalaType) =>
          // println(tpe)
          tpe.scalaDocPath() match {
            case Some(path) =>
              open(code, path)

            case None =>
              println(s"No scala-doc path for ${tpe.presentation}")
              openBase(code)
          }
        case _ =>
          // super.run(tpeOpt)
          openBase(code)
      }
  }

  private def melliteVersion: Version = {
    // Version.parse does not handle SNAPSHOT at the moment
    val v0  = Mellite.version
    val v   = if (v0.endsWith("-SNAPSHOT")) v0.substring(0, v0.length - 9) else v0
    Version.parse(v).get
  }

  private def mkDocModule(version: Version = melliteVersion): Module = {
    val scalaVersion = Version.parse(de.sciss.mellite.BuildInfo.scalaVersion).get
    Module("de.sciss", s"mellite-unidoc_${scalaVersion.binaryCompatible}", version)
  }

  // used only on the EDT
  private[this] var lookUpRef     = Option.empty[LookUpDocumentation]
  private[this] var lookUpRefBusy = false

  private def mkBasePath(code: Option[Code]): String = {
    val baseSymbol  = code.fold("de.sciss.synth.proc")(_.tpe.docBaseSymbol)
    val s           = baseSymbol.replace('.', '/')
    val i           = s.lastIndexOf('/')
    val s1          = s.substring(i + 1)
    val isPkg       = s1.isEmpty || s1.charAt(0).isLower
    val path        = if (isPkg) s + "/index.html" else s + ".html"
    path
  }

  private def mkDocBaseDirParent(docModule: Module): File =
    DocUtil.defaultUnpackDirBase(Mellite.cacheDir / "api", docModule.groupId, docModule.artifactId)

  private def mkDocBaseDir(docModule: Module): File =
    DocUtil.defaultUnpackDir(Mellite.cacheDir / "api", docModule)

  private def mkDocReadyFile(docModule: Module): File =
    new File(mkDocBaseDir(docModule), "ready")

  private def isDocReady(docModule: Module): Boolean =
    mkDocReadyFile(docModule).isFile

  private class LookUpDocumentation(docModule: Module) {
    //      private[this] val docModule   = Module("de.sciss", s"scalacollider-unidoc_${language.scalaVersion.binaryCompatible}", Version(1,28,0))
    //      private[this] val docModule   = Module("de.sciss", s"mellite-unidoc_${language.scalaVersion.binaryCompatible}", Version(2,31,0))
    private[this] val baseDir     = mkDocBaseDir  (docModule)
    private[this] val ready       = mkDocReadyFile(docModule)
    private[this] var styleSet    = false

    private def prepareJar(): Future[Unit] =
      if (ready.isFile) {
        if (!styleSet) {
          styleSet = true
          DocUtil.setScalaCssStyle(dark = GUI.isDarkSkin, baseDir = baseDir)
        }
        Future.successful(())
      } else {
        val (dl, futRes)    = DocUtil.downloadAndExtract(docModule, target = baseDir, darkCss = GUI.isDarkSkin)
        val progress        = new ProgressBar
        val progressDialog  = new Dialog(null /* frame */) {
          title = "Look up Documentation"
          contents = new GridPanel(2, 1) {
            vGap    = 2
            border  = Swing.EmptyBorder(2, 4, 2, 4)
            contents += new Label("Downloading API documentation...")
            contents += progress
          }
          pack().centerOnScreen()
          open()
        }
        dl.onChange { pr =>
          Swing.onEDT(progress.value = (pr.relative * 100).toInt)
        }
        futRes.onComplete(_ => Swing.onEDT(progressDialog.dispose()))
        futRes
      }

//    def openBase(code: Option[Code]): Unit =
//      open(code, mkBasePath(code))

    def open(code: Option[Code], path: String): Unit = {
      prepareJar().onComplete {
        case Success(_) =>
          ready.createNewFile()
          val i     = path.indexOf('#')
          val s     = if (i < 0) path else path.substring(0, i)
          val f0    = new File(baseDir, s)

          def fallback(): String = {
            if (!path.contains("/Main.html")) {
              println(s"No doc for $path, using fallback")
            }
            mkBasePath(code)
          }

          val path1 = if (f0.exists()) path else {
            // for case classes, there is no doc for companion;
            // in this case, try again with the class name, dropping the $
            val j = s.indexOf("$.html")
            if (j < 0) fallback() else {
              val s1 = s.substring(0, j) + ".html"
              val f1 = new File(baseDir, s1)
              if (f1.exists()) s1 else fallback()
            }
          }

          // XXX TODO --- `toURI` will escape the hash symbol; we should use URIs throughout
          //                val docURI = (baseDir / path).toURI
          val docURI = "file://" + new File(baseDir, path1).getPath
          // println(docURI)
          WebBrowser.instance.openURI(docURI)

        case Failure(ex) =>
          val msg = Option(ex.getMessage).getOrElse(ex.getClass.getSimpleName)
          ex.printStackTrace()
          println(s"Failed to download and extract javadoc jar: $msg")
      }
    }
  }
}
