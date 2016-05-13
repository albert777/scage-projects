package com.github.dunnololda.scageprojects.orbitalkiller.interface.elements

import com.github.dunnololda.scageprojects.orbitalkiller.{InterfaceElement, OrbitalKiller, _}

class TimeInfo extends InterfaceElement {
  private val strings_without_stop_moment = Array("")
  private val strings_with_stop_moment = Array("", "")
  private var strings = strings_without_stop_moment

  override protected def _update(): Unit = {
    val time_acceleration = f"x${(OrbitalKiller.timeMultiplier * OrbitalKiller.k).toInt} (${1f * OrbitalKiller.timeMultiplier / 63 * OrbitalKiller.ticks}%.2f)"
    if (OrbitalKiller._stop_after_number_of_tacts > 0) {
      strings_with_stop_moment(0) = s"Время: $time_acceleration ${timeStr((OrbitalKiller.tacts * OrbitalKiller.base_dt * 1000).toLong)}"
      if (OrbitalKiller.timeMultiplier != OrbitalKiller.realtime) {
        strings_with_stop_moment(1) = s"Остановка через ${timeStr((OrbitalKiller._stop_after_number_of_tacts * OrbitalKiller.base_dt * 1000).toLong)} (${timeStr((OrbitalKiller._stop_after_number_of_tacts * OrbitalKiller.base_dt * 1000 / (1f * OrbitalKiller.timeMultiplier / 63 * OrbitalKiller.ticks)).toLong)})"
      } else {
        strings_with_stop_moment(1) = s"Остановка через ${timeStr((OrbitalKiller._stop_after_number_of_tacts * OrbitalKiller.base_dt * 1000).toLong)}"
      }
      strings = strings_with_stop_moment
    } else {
      strings_without_stop_moment(0) = s"Время: $time_acceleration ${timeStr((OrbitalKiller.tacts * OrbitalKiller.base_dt * 1000).toLong)}"
      strings = strings_without_stop_moment
    }
  }

  override def data: Seq[String] = strings

  override val shortDescr: String = "T"
}
