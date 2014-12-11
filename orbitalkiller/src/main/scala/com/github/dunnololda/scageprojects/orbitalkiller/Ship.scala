package com.github.dunnololda.scageprojects.orbitalkiller

import OrbitalKiller._
import com.github.dunnololda.scage.ScageLib._

trait Ship {
  var selected_engine:Option[Engine] = None
  def isSelectedEngine(e:Engine):Boolean = {
    selected_engine.exists(x => x == e)
  }

  def engines:List[Engine]
  def engines_mapping:Map[Int, Engine]
  def switchEngineActive(engine_code:Int) {
    timeMultiplier = realtime
    engines_mapping.get(engine_code).foreach(e => e.switchActive())
  }

  def mass:Double

  def currentState:BodyState

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

  def currentTorque(time:Long, bs:BodyState):Double = {
    engines.filter(e => e.active && time < e.stopMomentTacts).foldLeft(0.0) {
      case (sum, e) => sum + e.torque
    }
  }

  def switchEngine(e:Engine) {
    e.switchActive()
    updateFutureTrajectory()
  }

  def activateOnlyTheseEngines(engines_to_activate:Engine*) {
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
    if(e.active) RED else WHITE
  }

  def engineActiveSize(e:Engine):Double = {
    10.0*e.power/e.max_power
  }

  def drawEngine(e:Engine, center:DVec, width:Double, height:Double, is_vertical:Boolean) {
    drawRectCentered(center.toVec, width.toFloat, height.toFloat, color = engineColor(e))
    if(e.active && e.power > 0) {
      if(is_vertical) {
        drawFilledRectCentered(center.toVec, width.toFloat, engineActiveSize(e).toFloat, color = engineColor(e))
      } else {
        drawFilledRectCentered(center.toVec, engineActiveSize(e).toFloat, height.toFloat, color = engineColor(e))
      }
      if(globalScale > 2) print(s"${e.powerPercent}% : ${e.worktimeTacts}", center.toVec, size = max_font_size/globalScale)
      if(isSelectedEngine(e)) drawRectCentered(center.toVec, width.toFloat+2, height.toFloat+2, color = engineColor(e))
    }
  }

  def preserveAngularVelocity(ang_vel_deg:Double)
  def enterOrbit()

  def preserveAngle(angle_deg:Double) {
    if(rotation != angle_deg) {
      if(rotation > angle_deg) {
        if(rotation - angle_deg > 50) preserveAngularVelocity(-5)
        else if(rotation - angle_deg > 10) preserveAngularVelocity(-2)
        else if(rotation - angle_deg > 1) preserveAngularVelocity(-1)
        else if(rotation - angle_deg > 0.1f) preserveAngularVelocity(-0.1f)
        else preserveAngularVelocity(0)
      } else if(rotation < angle_deg) {
        if(rotation - angle_deg < -50) preserveAngularVelocity(5)
        else if(rotation - angle_deg < -10) preserveAngularVelocity(2)
        else if(rotation - angle_deg < -1) preserveAngularVelocity(1)
        else if(rotation - angle_deg < -0.1f) preserveAngularVelocity(0.1f)
        else preserveAngularVelocity(0)
      }
    }
  }

  private var flight_mode = 1
  def flightMode = flight_mode
  def flightMode_=(new_flight_mode:Int) {
    flight_mode = new_flight_mode
    //if(flight_mode != 1) timeMultiplier = 1
  }
  def flightModeStr:String = flight_mode match {
    case 1 => "свободный"
    case 2 => "запрет вращения"
    case 3 => "ориентация по осям"
    case 4 => "ориентация по траектории"
    case 5 => "ориентация против траектории"
    case 6 => "выход на круговую орбиту"
    case _ => ""
  }
}
