/*
 *  Config.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.file.File

final case class Config(open        : List[File]    = Nil,
                        autoRun     : List[String]  = Nil,
                        headless    : Boolean       = false,
                        bootAudio   : Boolean       = false,
                        logFrame    : Boolean       = true,
                        launcherPort: Int           = -1,
                        prefix      : String        = "default",
                       )