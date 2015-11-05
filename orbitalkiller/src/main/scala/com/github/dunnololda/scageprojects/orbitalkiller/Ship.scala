package com.github.dunnololda.scageprojects.orbitalkiller

import OrbitalKiller._
import com.github.dunnololda.scage.ScageLibD._

import scala.collection.mutable.ArrayBuffer

trait Ship {
  def index:String
  var selected_engine:Option[Engine] = None

  def isSelectedEngine(e:Engine):Boolean = {
    selected_engine.exists(x => x == e)
  }

  def switchEngineSelected(engine_code:Int) {
    engines_mapping.get(engine_code).foreach(e => if(isSelectedEngine(e)) selected_engine = None else selected_engine = Some(e))
  }

  def engines:List[Engine]
  def engines_mapping:Map[Int, Engine]
  def switchEngineActive(engine_code:Int) {
    //timeMultiplier = realtime
    engines_mapping.get(engine_code).foreach(e => e.switchActive())
  }

  def switchEngineActiveOrSelect(engine_code:Int) {
    //timeMultiplier = realtime
    engines_mapping.get(engine_code).foreach(e => if(e.active) {
      if(selected_engine.exists(_ == e)) e.switchActive()
      else selected_engine = Some(e)
    } else e.switchActive())
  }

  def mass:Double

  def fuelMass:Double
  def fuelMass_=(m:Double):Unit

  def initState:BodyState
  def currentState:MutableBodyState

  def linearAcceleration = currentState.acc

  def linearVelocity = currentState.vel

  def coord = currentState.coord

  def angularAcceleration = currentState.ang_acc

  def angularVelocity = currentState.ang_vel

  def rotation = currentState.ang

  def currentReactiveForce(time:Long, bs:BodyState):DVec = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(DVec.dzero) {
      case (sum, e) => sum + e.force.rotateDeg(bs.ang)
    }
  }

  def currentReactiveForce(time:Long, bs:MutableBodyState):DVec = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(DVec.dzero) {
      case (sum, e) => sum + e.force.rotateDeg(bs.ang)
    }
  }

  def fuelConsumptionPerTact:Double = {
    engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) => sum + e.fuelConsumptionPerTact
    }
  }

  def fuelMassWhenEnginesOff:Double = {
    fuelMass - engines.filter(e => e.active).map(e => e.workTimeTacts*e.fuelConsumptionPerTact).sum
  }

  def fuelMassWhenEnginesOffWithoutEngine(ee:Engine):Double = {
    fuelMass - engines.filter(e => e.active && e != ee).map(e => e.workTimeTacts*e.fuelConsumptionPerTact).sum
  }

  def currentMass(time:Long, bs:BodyState):Double = {
    mass - engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) =>
        sum + e.fuelConsumptionPerTact * (math.min(time, e.stopMomentTacts) - (e.stopMomentTacts - e.workTimeTacts))
    }
  }

  def currentMass(time:Long):Double = {
    mass - engines.filter(e => e.active).foldLeft(0.0) {
      case (sum, e) =>
        sum + e.fuelConsumptionPerTact * (math.min(time, e.stopMomentTacts) - (e.stopMomentTacts - e.workTimeTacts))
    }
  }

  def currentTorque(time:Long, bs:BodyState):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def currentTorque(time:Long, bs:MutableBodyState):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def currentTorque(time:Long):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def activateOnlyTheseEngines(engines_to_activate:Engine*) {
    //timeMultiplier = realtime
    engines_to_activate.foreach(_.active = true)
    engines.withFilter(e => e.active && !engines_to_activate.contains(e)).foreach(_.active = false)
  }

  def activateOnlyOneEngine(e:Engine) {
    activateOnlyTheseEngines(e)
  }

  def allEnginesInactive:Boolean = {
    engines.forall(!_.active)
  }

  def engineColor(e:Engine):ScageColor = {
    if(pilot_is_dead || e.active) RED else WHITE
  }

  def engineActiveSize(e:Engine):Double = {
    10.0*e.power/e.max_power
  }

  def drawEngine(e:Engine, center:DVec, width:Double, height:Double, is_vertical:Boolean) {
    drawRectCentered(center, width, height, color = engineColor(e))
    if(e.active && e.power > 0) {
      if(is_vertical) {
        drawFilledRectCentered(center, width, engineActiveSize(e), color = engineColor(e))
      } else {
        drawFilledRectCentered(center, engineActiveSize(e), height, color = engineColor(e))
      }
      //if(globalScale > 2) print(s"${e.powerPercent}% : ${e.workTimeTacts}", center.toVec, size = (max_font_size/globalScale).toFloat)
      if(isSelectedEngine(e)) drawRectCentered(center, width+2, height+2, color = engineColor(e))
    }
  }

  def preserveAngularVelocity(ang_vel_deg:Double)
  def preserveVelocity(vel:DVec)

  /**
   * rotation и angle_deg оба в диапазоне 0 - 360
   * @param angle_deg - угол между ориентацией корабля и вектором DVec(0, 1), который необходимо поддерживать
   */
  def preserveAngle(angle_deg:Double) {
    if(rotation != angle_deg) {
      if(rotation > angle_deg) {
        if(rotation - angle_deg < angle_deg - rotation + 360) {
          val diff = rotation - angle_deg
          if (diff > 50) preserveAngularVelocity(-10)
          else if (diff > 10) preserveAngularVelocity(-5)
          else if (diff > 1) preserveAngularVelocity(-1.8)
          else if (diff > 0.1) preserveAngularVelocity(-0.2)
          else preserveAngularVelocity(0)
        } else {
          val diff = angle_deg - rotation + 360
          if (diff > 50) preserveAngularVelocity(10)
          else if (diff > 10) preserveAngularVelocity(5)
          else if (diff > 1) preserveAngularVelocity(1.8)
          else if (diff > 0.1) preserveAngularVelocity(0.2)
          else preserveAngularVelocity(0)
        }
      } else if(rotation < angle_deg) {
        if(rotation - angle_deg > angle_deg - rotation - 360) {
          val diff = rotation - angle_deg
          if(diff < -50) preserveAngularVelocity(10)
          else if(diff < -10) preserveAngularVelocity(5)
          else if(diff < -1) preserveAngularVelocity(1.8)
          else if(diff < -0.1) preserveAngularVelocity(0.2)
          else preserveAngularVelocity(0)
        } else {
          val diff = angle_deg - rotation - 360
          if(diff < -50) preserveAngularVelocity(-10)
          else if(diff < -10) preserveAngularVelocity(-5)
          else if(diff < -1) preserveAngularVelocity(-1.8)
          else if(diff < -0.1) preserveAngularVelocity(-0.2)
          else preserveAngularVelocity(0)
        }
      }
    }
  }

  private var flight_mode = 1
  def flightMode = flight_mode
  def flightMode_=(new_flight_mode:Int) {
    val prev_flight_mode = flight_mode
    flight_mode = new_flight_mode
    if(flight_mode == 0) {
      engines.filterNot(_.active).foreach(e => e.workTimeTacts = 226800)     // 1 hour
      val active_engines = engines.filter(_.active)
      if(active_engines.map(ae => ae.fuelConsumptionPerTact*226800).sum <= fuelMass) {
        active_engines.foreach(e => e.workTimeTacts = 226800)
      } else {
        val fuel_for_every_active_engine = fuelMass / active_engines.length
        active_engines.foreach(e => e.workTimeTacts = (fuel_for_every_active_engine/e.fuelConsumptionPerTact).toLong)
      }
    } else {
      if(flightMode == 1) {
        engines.foreach(e => e.active = false)
      }
      if(prev_flight_mode == 0) {
        engines.foreach(e => e.workTimeTacts = 0)
      }
      if(flightMode == 8) {
        vertical_speed_msec = 0
        horizontal_speed_msec = 0
      }
    }
  }

  def vertical_speed_msec:Int = 0
  def vertical_speed_msec_=(x:Int) {}
  def horizontal_speed_msec:Int = 0
  def horizontal_speed_msec_=(x:Int) {}

  def flightModeStr:String = flight_mode match {
    case 1 => "свободный"
    case 2 => "запрет вращения"
    case 3 => "ориентация по осям"
    case 4 => "ориентация по траектории"
    case 5 => "ориентация против траектории"
    case 6 => "выход на круговую орбиту"
    case 7 => "уравнять скорость с кораблем"
    case 8 => s"уравнять скорость с ближайшей планетой: ${msecOrKmsec(vertical_speed_msec)}, ${msecOrKmsec(horizontal_speed_msec)}"
    case 9 => "остановиться"
    case 0 => "маневрирование"
    case _ => ""
  }

  def otherShipsNear:List[Ship] = ships.filter(s => s.index != ship.index/* && ship.coord.dist(s.coord) < 100000*/).sortBy(s => ship.coord.dist(s.coord))

  private val pilot_mass = 75
  private val pilot_position = DVec(0, 69)
  private var pilot_average_g:Double = 0.0
  private val pilot_gs = ArrayBuffer[(Double, Long)]()

  private var pilot_is_dead = false
  private var pilot_death_reason = ""
  def pilotIsDead = pilot_is_dead
  def pilotIsAlive = !pilot_is_dead

  private var ship_removed = false
  def shipRemoved = ship_removed

  private val g = OrbitalKiller.earth.mass*G/(OrbitalKiller.earth.radius*OrbitalKiller.earth.radius)

  def updateShipState(time_msec:Long): Unit = {
    if(!pilot_is_dead) {
      val dvel = currentState.dvel.norma
      if (dvel > 10) { // crash tolerance = 10 m/s
        pilot_is_dead = true
        pilot_death_reason = f"Корабль уничтожен в результате столкновения (${dvel/OrbitalKiller.base_dt/g}%.2fg)"
        flightMode = 1
        if(dvel > 100) {
          OrbitalKiller.mutable_system.find(_._1.index == index).foreach(x => {
            println("removed ship from mutable system")
            OrbitalKiller.mutable_system -= x
            ship_removed = true
          })
        }
      } else {
        val reactive_force = currentReactiveForce(0, currentState)
        val centrifugial_force = if (angularVelocity == 0) DVec.zero else pilot_mass * math.pow(angularVelocity.toRad, 2) * pilot_position.rotateDeg(rotation)
        val pilot_acc = (reactive_force / mass + centrifugial_force / pilot_mass + currentState.dacc).norma
        pilot_gs += ((pilot_acc / g, time_msec))
        if (time_msec - pilot_gs.head._2 >= 1000) {
          pilot_average_g = pilot_gs.map(_._1).sum / pilot_gs.length
          pilot_gs.clear()
        }
      }
    }
  }

  def pilotStateStr:String = {
    if(!pilot_is_dead) {
      if (pilot_average_g < 0.1) "Пилот в состоянии невесомости"
      else if (pilot_average_g <= 1) f"Пилот испытывает силу тяжести $pilot_average_g%.1fg"
      else f"Пилот испытывает перегрузку $pilot_average_g%.1fg"
    } else {
      pilot_death_reason
    }
  }
}
