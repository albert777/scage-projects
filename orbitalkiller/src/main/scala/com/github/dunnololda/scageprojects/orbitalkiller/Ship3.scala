package com.github.dunnololda.scageprojects.orbitalkiller

import com.github.dunnololda.scage.ScageLib._
import OrbitalKiller._

class Ship3(
             index:String,
             init_coord:Vec,
             init_velocity:Vec = Vec.zero,
             init_rotation:Float = 0f
             ) extends PolygonShip(index, init_coord, init_velocity, init_rotation) {
  val mass:Float = 1

  val points:List[Vec] = List(
    Vec(-10.0, 70.0),
    Vec(10.0, 70.0),
    Vec(10.0, 30.0),
    Vec(50.0, 10.0),
    Vec(10.0, 10.0),
    Vec(10.0, -10.0),
    Vec(50.0, -30.0),
    Vec(10.0, -30.0),
    Vec(10.0, -70.0),
    Vec(-10.0, -70.0),
    Vec(-10.0, -30.0),
    Vec(-50.0, -30.0),
    Vec(-10.0, -10.0),
    Vec(-10.0, 10.0),
    Vec(-50.0, 10.0),
    Vec(-10.0, 30.0)
  )

  val eight = Engine(position = Vec(0.0, 70.0),    force_dir = Vec(0.0, -1.0), max_power = 10, power_step = 0.1f, this)
  val two   = Engine(position = Vec(0.0, -70.0),   force_dir = Vec(0.0, 1.0),  max_power = 10, power_step = 0.1f, this)
  val four  = Engine(position = Vec(-10.0, 0.0),   force_dir = Vec(1.0, 0.0),  max_power = 10, power_step = 0.1f, this)
  val six   = Engine(position = Vec(10.0, 0.0),    force_dir = Vec(-1.0, 0.0), max_power = 10, power_step = 0.1f, this)
  val seven = Engine(position = Vec(-40.0, 10.0),  force_dir = Vec(0.0, 1.0),  max_power = 10, power_step = 0.1f, this)
  val nine  = Engine(position = Vec(40.0, 10.0),   force_dir = Vec(0.0, 1.0),  max_power = 10, power_step = 0.1f, this)
  val one   = Engine(position = Vec(-40.0, -30.0), force_dir = Vec(0.0, 1.0),  max_power = 10, power_step = 0.1f, this)
  val three = Engine(position = Vec(40.0, -30.0),  force_dir = Vec(0.0, 1.0),  max_power = 10, power_step = 0.1f, this)

  val engines = List(eight, two, four, six, seven, nine, one, three)

  val engines_mapping = Map(
    KEY_NUMPAD8 -> eight,
    KEY_NUMPAD2 -> two,
    KEY_NUMPAD4 -> four,
    KEY_NUMPAD6 -> six,
    KEY_NUMPAD7 -> seven,
    KEY_NUMPAD9 -> nine,
    KEY_NUMPAD1 -> one,
    KEY_NUMPAD3 -> three
  )

  private def howManyTacts(to:Float, from:Float, a:Float, dt:Float):(Int, Float) = {
    val tacts = ((to - from)/(a*dt)).toInt + 1
    val result_to = from + tacts*a*dt
    (tacts, result_to)
    /*if(a == 0) tacts
    else if(a > 0) {
      if(from >= to) tacts
      else howManyTacts(to, from + a*dt, a, dt, tacts+1)
    } else {
      if(from <= to) tacts
      else howManyTacts(to, from + a*dt, a, dt, tacts+1)
    }*/
  }

  private def maxPossiblePowerForLinearMovement(max_power:Float, power_step:Float, force_dir:Float, mass:Float, to:Float, from:Float, max_diff:Float):Float = {
    (max_power to 0 by -power_step).find {
      case pp =>
        val force = force_dir*pp
        val acc = force / mass
        val (_, result_to) = howManyTacts(to, from, acc, dt)
        math.abs(to - result_to) < max_diff
    }.getOrElse(1f)
  }
  
  private def maxPossiblePowerForRotation(max_power:Float, power_step:Float, force_dir:Vec, position:Vec, I:Float, to:Float, from:Float, max_diff:Float):Float = {
    (max_power to 0f by -power_step).find {
      case pp =>
        val torque = (-force_dir*pp)*/position
        val ang_acc = (torque / I).toDeg
        val (_, result_to) = howManyTacts(to, from, ang_acc, dt)
        math.abs(to - result_to) < max_diff
    }.getOrElse(1f)
  }

  override def preserveAngularVelocity(ang_vel_deg:Float) {
    val difference = angularVelocity - ang_vel_deg
    if(difference > 0.01f) {
      val power = maxPossiblePowerForRotation(seven.max_power, seven.power_step, seven.force_dir, seven.position, currentState.I, ang_vel_deg, angularVelocity, 0.01f)
      seven.power = power
      eight.power = power
      val ang_acc = (seven.torque / currentState.I).toDeg
      val (tacts, _) = howManyTacts(ang_vel_deg, angularVelocity, ang_acc, dt)
      activateOnlyTheseEngines(seven, eight)
      seven.worktimeTacts = tacts
      eight.worktimeTacts = tacts
    } else if(difference < -0.01f) {
      val power = maxPossiblePowerForRotation(nine.max_power, nine.power_step, nine.force_dir, nine.position, currentState.I, ang_vel_deg, angularVelocity, 0.01f)
      nine.power = power
      eight.power = power
      val ang_acc = (nine.torque / currentState.I).toDeg
      val (tacts, _) = howManyTacts(ang_vel_deg, angularVelocity, ang_acc, dt)
      activateOnlyTheseEngines(nine, eight)
      nine.worktimeTacts = tacts
      eight.worktimeTacts = tacts
    }
  }

  def enterOrbit() {
    insideGravitationalRadiusOfCelestialBody(coord) match {
      case Some(body) =>
        val ss = satelliteSpeed(coord, body.coord, body.linearVelocity, body.mass, G)
        val n = Vec(0, 1).rotateDeg(rotation).n
        val p = n.p*(-1)

        val ship_velocity_n = linearVelocity*n  // from
        val ss_n = ss*n                         // to

        if(ship_velocity_n > ss_n) {
          val power = maxPossiblePowerForLinearMovement(eight.max_power, eight.power_step, eight.force_dir.y, mass, ss_n, ship_velocity_n, 0.1f)

          eight.power = power
          val acc = (eight.force / mass).y
          val (tacts, result_to) = howManyTacts(ss_n, ship_velocity_n, acc, dt)
          /*println("===========================")
          println(s"$ship_velocity_n -> $ss_n : $tacts : $result_to : $power")*/
          eight.active = true
          eight.worktimeTacts = tacts
        } else if(ship_velocity_n < ss_n) {
          val power = maxPossiblePowerForLinearMovement(two.max_power, two.power_step, two.force_dir.y, mass, ss_n, ship_velocity_n, 0.1f)
          two.power = power
          val acc = (two.force / mass).y
          val (tacts, result_to) = howManyTacts(ss_n, ship_velocity_n, acc, dt)
          /*println("===========================")
          println(s"$ship_velocity_n -> $ss_n : $tacts : $result_to : $power")*/
          two.active = true
          two.worktimeTacts = tacts
        }

        val ship_velocity_p = p*linearVelocity
        val ss_p = p*ss

        if(ship_velocity_p > ss_p) {
          val power = maxPossiblePowerForLinearMovement(six.max_power, six.power_step, six.force_dir.x, mass, ss_p, ship_velocity_p, 0.1f)
          six.power = power
          val acc = (six.force / mass).x
          val (tacts, result_to) = howManyTacts(ss_p, ship_velocity_p, acc, dt)
          /*println(s"$ship_velocity_p -> $ss_p : $tacts : $result_to : $power")
          println("===========================")*/
          six.active = true
          six.worktimeTacts = tacts
        } else if(ship_velocity_p < ss_p) {
          val power = maxPossiblePowerForLinearMovement(four.max_power, four.power_step, four.force_dir.x, mass, ss_p, ship_velocity_p, 0.1f)
          four.power = power
          val acc = (four.force / mass).x
          val (tacts, result_to) = howManyTacts(ss_p, ship_velocity_p, acc, dt)
          /*println(s"$ship_velocity_p -> $ss_p : $tacts : $result_to : $power")
          println("===========================")*/
          four.active = true
          four.worktimeTacts = tacts
        }
      case None =>
    }
  }

  action {
    if(allEnginesInactive) {
      flightMode match {
        case 1 => // свободный режим
        case 2 => // запрет вращения
          if(math.abs(angularVelocity) < 0.01f) flightMode = 1
          else preserveAngularVelocity(0)
        case 3 => // ориентация по осям
          if(math.abs(rotation) < 0.1f) flightMode = 2
          else preserveAngle(0)
        case 4 => // ориентация по траектории
          val angle = linearVelocity.mydeg(Vec(0,1))
          if(math.abs(rotation - angle) < 0.1f) flightMode = 2
          else preserveAngle(angle)
        case 5 => // ориентация против траектории
          val angle = correctAngle(linearVelocity.mydeg(Vec(0,1)) + 180)
          if(math.abs(rotation - angle) < 0.1f) flightMode = 2
          else preserveAngle(angle)
        case 6 => // выход на орбиту
          if(math.abs(angularVelocity) < 0.01f) {
            insideGravitationalRadiusOfCelestialBody(coord).foreach {
              case body =>
                val ss = satelliteSpeed(coord, body.coord, body.linearVelocity, body.mass, G)
                if(linearVelocity.dist(ss) > 0.1) {
                  enterOrbit()
                } else flightMode = 1
            }
          } else preserveAngularVelocity(0)
        case _ =>
      }
    }
  }

  render {
    openglLocalTransform {
      openglMove(coord)
      openglRotateDeg(rotation)
      drawSlidingLines(points :+ points.head, WHITE)

      engines.foreach {
        case e =>
          e.force_dir match {
            case Vec(0, -1) => drawEngine(e, e.position + Vec(0, 2.5f),  10, 5,  is_vertical = false)
            case Vec(0, 1)  => drawEngine(e, e.position + Vec(0, -2.5f), 10, 5,  is_vertical = false)
            case Vec(-1, 0) => drawEngine(e, e.position + Vec(2.5f, 0),  5,  10, is_vertical = true)
            case Vec(1, 0)  => drawEngine(e, e.position + Vec(-2.5f, 0), 5,  10, is_vertical = true)
            case _ =>
          }
      }
    }

    drawFilledCircle(coord, 2, GREEN)                             // mass center
    drawLine(coord, coord + linearVelocity.n*100, CYAN)           // current velocity
    drawLine(coord, coord + (earth.coord - coord).n*100, YELLOW)    // direction to sun
    drawLine(coord, coord + (moon.coord - coord).n*100, GREEN)   // direction to earth
  }
}
