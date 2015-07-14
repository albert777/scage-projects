package com.github.dunnololda.scageprojects.orbitalkiller.interfaceElements

import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._
import com.github.dunnololda.scageprojects.orbitalkiller._

class PilotStateInfo extends InterfaceElement {
  private val strings = Array(ship.pilotStateStr)
  override val shortDescr: String = "P"

  override def data: Seq[String] = strings

  override protected def _update(): Unit = {
    strings(0) = ship.pilotStateStr
  }
}