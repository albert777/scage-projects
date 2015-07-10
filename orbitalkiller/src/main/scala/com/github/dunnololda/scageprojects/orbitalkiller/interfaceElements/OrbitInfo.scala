package com.github.dunnololda.scageprojects.orbitalkiller.interfaceElements

import com.github.dunnololda.scageprojects.orbitalkiller.InterfaceElement
import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._

class OrbitInfo extends InterfaceElement {
  private val strings = Array("")
  override protected def _update(): Unit = {
    strings(0) = s"Параметры орбиты: ${orbitStrInPointWithVelocity(ship.coord, ship.linearVelocity, ship.mass, currentPlanetStates)}"
  }

  override def _data: Seq[String] = strings
}
