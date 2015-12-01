package com.github.dunnololda.scageprojects.orbitalkiller.interfaceElements

import com.github.dunnololda.scage.support.ScageColor
import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._
import com.github.dunnololda.scageprojects.orbitalkiller._

class LinearVelocityInfo extends InterfaceElement {
  private val strings = Array(f"Линейная скорость: ${ship.velocityStr}")
  override protected def _update(): Unit = {
    strings(0) = f"Линейная скорость: ${ship.velocityStr}"
  }

  override def data: Seq[String] = strings
  override val color = ScageColor.CYAN

  override val shortDescr: String = "LV"
}
