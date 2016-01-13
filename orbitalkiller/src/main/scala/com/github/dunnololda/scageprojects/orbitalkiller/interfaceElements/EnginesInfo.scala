package com.github.dunnololda.scageprojects.orbitalkiller.interfaceElements

import com.github.dunnololda.scage.support.ScageColor
import com.github.dunnololda.scageprojects.orbitalkiller._
import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._

class EnginesInfo extends InterfaceElement {
  private var engines_work_time_msec:Double = 0
  def addWorkTime(time_msec:Double): Unit = {
    engines_work_time_msec += time_msec
  }

  private val strings_when_engines_nonactive = Array("")
  private val strings_when_engines_active = Array("", "")
  private var strings = strings_when_engines_nonactive

  override protected def _update(): Unit = {
    var engines_active = false
    val engines_str = s"${
      if(ship.pilotIsAlive) {
        ship.engines.filter(e => e.active || ship.isSelectedEngine(e)).map(e => {
          if (ship.isSelectedEngine(e)) {
            if (e.active) {
              engines_active = true
              f"[r${e.index}: ${e.power / 1000}%.1f кН (${e.workTimeStr})]"
            } else {
              f"[g${e.index}: ${e.power / 1000}%.1f кН (${e.workTimeStr})]"
            }
          } else {
            if (e.active) {
              engines_active = true
              f"[o${e.index}: ${e.power / 1000}%.1f кН (${e.workTimeStr})]"
            } else {
              f"${e.index}: ${e.power / 1000}%.1f кН (${e.workTimeStr})"
            }
          }
        }).mkString(", ")
      } else {
        ship.engines.map(e => {
          f"${e.index}: ${e.power / 1000}%.1f кН (${e.workTimeStr})"
        }).mkString(", ")
      }
    }${val x = ship.engines.filter(e => e.active).map(e => e.fuelConsumptionPerTact*e.power/e.max_power).sum/base_dt; if(x != 0) f" ($x%.3f кг/сек)" else ""}"
    if(engines_str.isEmpty) {
      strings_when_engines_nonactive(0) = s"Двигательная установка: ${if (engines_active) "[rактивирована]" else "отключена"}. Общее время работы двигателей: ${timeStr(engines_work_time_msec.toLong)}"
      strings = strings_when_engines_nonactive
    } else {
      strings_when_engines_active(0) = s"Двигательная установка: ${if (engines_active) "[rактивирована]" else "отключена"}. Общее время работы двигателей: ${timeStr(engines_work_time_msec.toLong)}"
      strings_when_engines_active(1) = engines_str
      strings = strings_when_engines_active
    }
  }
  override def data: Seq[String] = strings

  override def color:ScageColor = {
    if(isMinimized && ship.engines.exists(_.active)) ScageColor.RED else ScageColor.YELLOW
  }

  override val shortDescr: String = "En"
}
