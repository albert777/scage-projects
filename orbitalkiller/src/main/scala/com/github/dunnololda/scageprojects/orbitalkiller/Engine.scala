package com.github.dunnololda.scageprojects.orbitalkiller

import OrbitalKiller._
import com.github.dunnololda.scage.ScageLib._

case class Engine(position:Vec, force_dir:Vec, max_power:Float, power_step:Float, ship:Ship) {
  private var worktime_tacts = 0l
  private var stop_moment_tacts = 0l

  def worktimeTacts = worktime_tacts
  def worktimeTacts_=(new_worktime_tacts:Long) {
    worktime_tacts = new_worktime_tacts
    stop_moment_tacts = tacts + worktime_tacts*timeMultiplier
  }

  def stopMomentTacts = stop_moment_tacts

  private var _power:Float = 1f
  def power = _power
  def power_=(new_power:Float) {
    if(new_power >= 0 && new_power < max_power) {
      _power = new_power
    }
  }

  def force = force_dir*power
  def torque = -force*/position

  private var is_active:Boolean = false
  def active = is_active
  def active_=(bool:Boolean) {
    if(is_active != bool) {
      is_active = bool
      if(is_active) {
        //_power = 1f
        if(worktime_tacts == 0) {
          worktimeTacts = 10
        } else worktimeTacts = worktimeTacts
        ship.selected_engine = Some(this)
      } else {
        ship.selected_engine = ship.engines.filter(_.active).lastOption
      }
      updateFutureTrajectory()
    }
  }
  def switchActive() {
    if(!is_active) {
      is_active = true
      //_power = 1f
      if(worktime_tacts == 0) {
        worktimeTacts = 10
      } else worktimeTacts = worktimeTacts
      ship.selected_engine = Some(this)
      updateFutureTrajectory()
    } else {
      if (!ship.isSelectedEngine(this)) ship.selected_engine = Some(this)
      else {
        is_active = false
        ship.selected_engine = ship.engines.filter(_.active).lastOption
        updateFutureTrajectory()
      }
    }
  }

  action {
    if(is_active) {
      if(worktime_tacts > 0) worktime_tacts -= 1
      else active = false
    }
  }
}
