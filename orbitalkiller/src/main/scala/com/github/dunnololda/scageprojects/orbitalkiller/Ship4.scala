package com.github.dunnololda.scageprojects.orbitalkiller

import com.github.dunnololda.scage.ScageLibD._
import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._

class Ship4(index:String,
            init_coord:DVec,
            init_velocity:DVec = DVec.dzero,
            init_rotation:Double = 0.0) extends PolygonShip(index, init_coord, init_velocity, init_rotation) {
  val mass = 10*1000.0

  val points:List[DVec] = List(
    DVec(-30.0, 10.0),
    DVec(-10.0, 50.0),
    DVec(-10.0, 90.0),
    DVec(10.0, 90.0),
    DVec(10.0, 50.0),
    DVec(30.0, 10.0),
    DVec(30.0, -30.0),
    DVec(-30.0, -30.0)
  )

  val draw_points = points :+ points.head

  val four = Engine(position = Vec(-30.0, 0.0), force_dir = Vec(1.0, 0.0),   max_power = 1000000, default_power_percent = 1, this)
  val six = Engine(position = Vec(30.0, 0.0), force_dir = Vec(-1.0, 0.0),    max_power = 1000000, default_power_percent = 1, this)
  val seven = Engine(position = Vec(-10.0, 80.0), force_dir = Vec(1.0, 0.0), max_power = 10000,   default_power_percent = 100, this)
  val nine = Engine(position = Vec(10.0, 80.0), force_dir = Vec(-1.0, 0.0),  max_power = 10000,   default_power_percent = 100, this)
  val eight = Engine(position = Vec(0.0, 90.0), force_dir = Vec(0.0, -1.0),  max_power = 1000000, default_power_percent = 1, this)
  val two = Engine(position = Vec(0.0, -30.0), force_dir = Vec(0.0, 1.0),    max_power = 1000000, default_power_percent = 1, this)

  val engines = List(four, six, seven, nine, eight, two)

  val engines_mapping = Map(
    KEY_NUMPAD4 -> four,
    KEY_NUMPAD6 -> six,
    KEY_NUMPAD7 -> seven,
    KEY_NUMPAD9 -> nine,
    KEY_NUMPAD8 -> eight,
    KEY_NUMPAD2 -> two
  )

  private def howManyTacts(to:Double, from:Double, a:Double, dt:Double):(Int, Double) = {
    val tacts = ((to - from)/(a*dt)).toInt + 1
    val result_to = from + tacts*a*dt
    //println(s"$from -> $to : $result_to : $tacts")
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

  private def maxPossiblePowerForLinearMovement(max_power:Double, force_dir:Double, mass:Double, to:Double, from:Double, max_diff:Double):Double = {
    (99 to 1 by -1).find {
      case percent =>
        val power = max_power*0.01*percent
        val force = force_dir*power
        val acc = force / mass
        val (_, result_to) = howManyTacts(to, from, acc, dt)
        math.abs(to - result_to) < max_diff
    }.map(percent => max_power*0.01*percent).getOrElse(max_power*0.01)
  }

  private def maxPossiblePowerForRotation(max_power:Double, force_dir:DVec, position:DVec, I:Double, to:Double, from:Double, max_diff:Double):Double = {
    (99 to 1 by -1).find {
      case percent =>
        val power = max_power*0.01*percent
        val torque = (-force_dir*power)*/position
        val ang_acc = (torque / I).toDeg
        val (_, result_to) = howManyTacts(to, from, ang_acc, dt)
        math.abs(to - result_to) < max_diff
    }.map(percent => max_power*0.01*percent).getOrElse(max_power*0.01)
  }

  private var last_correction_or_check_moment:Long = 0l

  override def preserveAngularVelocity(ang_vel_deg: Double) {
    val difference = angularVelocity - ang_vel_deg
    if(difference > 0.01) {
      val power = maxPossiblePowerForRotation(seven.max_power, seven.force_dir, seven.position, currentState.I, ang_vel_deg, angularVelocity, 0.01)
      seven.power = power
      eight.power = power
      val ang_acc = (seven.torque / currentState.I).toDeg
      val (tacts, _) = howManyTacts(ang_vel_deg, angularVelocity, ang_acc, dt)
      activateOnlyTheseEngines(seven, eight)
      seven.workTimeTacts = tacts
      eight.workTimeTacts = tacts
    } else if(difference < -0.01) {
      val power = maxPossiblePowerForRotation(nine.max_power, nine.force_dir, nine.position, currentState.I, ang_vel_deg, angularVelocity, 0.01)
      nine.power = power
      eight.power = power
      val ang_acc = (nine.torque / currentState.I).toDeg
      val (tacts, _) = howManyTacts(ang_vel_deg, angularVelocity, ang_acc, dt)
      activateOnlyTheseEngines(nine, eight)
      nine.workTimeTacts = tacts
      eight.workTimeTacts = tacts
    }
    last_correction_or_check_moment = OrbitalKiller.tacts
  }

  override def preserveVelocity(vel: DVec) {
    val n = DVec(0, 1).rotateDeg(rotation).n
    val p = n.p*(-1)

    val ship_velocity_n = linearVelocity*n  // from
    val ss_n = vel*n                         // to

    if(ship_velocity_n - ss_n > 0.1) {
      val power = maxPossiblePowerForLinearMovement(eight.max_power, eight.force_dir.y, mass, ss_n, ship_velocity_n, 0.1)

      eight.power = power
      val acc = (eight.force / mass).y
      val (tacts, result_to) = howManyTacts(ss_n, ship_velocity_n, acc, dt)
      /*println("===========================")
      println(s"$ship_velocity_n -> $ss_n : $tacts : $result_to : $power")*/
      eight.active = true
      eight.workTimeTacts = tacts
    } else if(ship_velocity_n - ss_n < -0.1) {
      val power = maxPossiblePowerForLinearMovement(two.max_power, two.force_dir.y, mass, ss_n, ship_velocity_n, 0.1)
      two.power = power
      val acc = (two.force / mass).y
      val (tacts, result_to) = howManyTacts(ss_n, ship_velocity_n, acc, dt)
      /*println("===========================")
      println(s"$ship_velocity_n -> $ss_n : $tacts : $result_to : $power")*/
      two.active = true
      two.workTimeTacts = tacts
    }

    val ship_velocity_p = p*linearVelocity
    val ss_p = p*vel

    if(ship_velocity_p - ss_p > 0.1) {
      val power = maxPossiblePowerForLinearMovement(six.max_power, six.force_dir.x, mass, ss_p, ship_velocity_p, 0.1)
      six.power = power
      val acc = (six.force / mass).x
      val (tacts, result_to) = howManyTacts(ss_p, ship_velocity_p, acc, dt)
      /*println(s"$ship_velocity_p -> $ss_p : $tacts : $result_to : $power")
      println("===========================")*/
      six.active = true
      six.workTimeTacts = tacts
    } else if(ship_velocity_p - ss_p < -0.1) {
      val power = maxPossiblePowerForLinearMovement(four.max_power, four.force_dir.x, mass, ss_p, ship_velocity_p, 0.1)
      four.power = power
      val acc = (four.force / mass).x
      val (tacts, result_to) = howManyTacts(ss_p, ship_velocity_p, acc, dt)
      /*println(s"$ship_velocity_p -> $ss_p : $tacts : $result_to : $power")
      println("===========================")*/
      four.active = true
      four.workTimeTacts = tacts
    }
    last_correction_or_check_moment = OrbitalKiller.tacts
  }

  action {
    flightMode match {
      case 1 => // свободный режим
      case 2 => // запрет вращения
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          if (math.abs(angularVelocity) < 0.01) flightMode = 1
          else preserveAngularVelocity(0)
        }
      case 3 => // ориентация по осям
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          if (math.abs(rotation) < 0.1) flightMode = 2
          else preserveAngle(0)
        }
      case 4 => // ориентация по траектории
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          val angle = linearVelocity.mydeg(DVec(0, 1))
          if (math.abs(rotation - angle) < 0.1) flightMode = 2
          else preserveAngle(angle)
        }
      case 5 => // ориентация против траектории
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          val angle = correctAngle(linearVelocity.mydeg(DVec(0, 1)) + 180)
          if (math.abs(rotation - angle) < 0.1) flightMode = 2
          else preserveAngle(angle)
        }
      case 6 => // выход на орбиту
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          if (math.abs(angularVelocity) < 0.01) {
            insideSphereOfInfluenceOfCelestialBody(coord, mass, currentPlanetStates) match {
              case Some((planet, planet_state)) =>
                val ss = satelliteSpeed(coord, linearVelocity, planet_state.coord, planet_state.vel, planet_state.mass, G)
                if (linearVelocity.dist(ss) > 0.1) {
                  preserveVelocity(ss)
                } else flightMode = 1
              case None =>
                flightMode = 1
            }
          } else preserveAngularVelocity(0)
        }
      case 7 => // уравнять скорость с ближайшим кораблем
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          if (math.abs(angularVelocity) < 0.01) {
            otherShipsNear.headOption match {
              case Some(s) =>
                val ss = s.linearVelocity
                if (linearVelocity.dist(ss) > 0.1) {
                  preserveVelocity(ss)
                } else {
                  flightMode = 1
                }
              case None =>
                flightMode = 1
            }
          } else preserveAngularVelocity(0)
        }
      case 8 => // уравнять скорость с ближайшей планетой
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 0) {
          if (math.abs(angularVelocity) < 0.01) {
            (for {
              planet_state <- currentPlanetStates.sortBy(_.coord.dist(coord)).headOption
              planet <- planetByIndex(planet_state.index)
            } yield (planet_state, planet)) match {
              case Some((planet_state, planet)) =>
                val need_velocity_l = List({
                  val ship_vertical_speed = (linearVelocity - planet_state.vel) * (coord - planet_state.coord).n
                  if (math.abs(ship_vertical_speed) > 0.3) {
                    println(math.abs(ship_vertical_speed))
                    Some((planet_state.vel * (coord - planet_state.coord).n) * (coord - planet_state.coord).n)
                  } else {
                    None
                  }
                }, {
                  if (planet_state.coord.dist(coord) - planet.radius < 10000) {
                    val ship_above_ground_velocity = (linearVelocity - planet_state.vel) * (coord - planet_state.coord).p
                    if (math.abs(ship_above_ground_velocity - planet.groundSpeedMsec) > 0.3) {
                      Some((planet_state.vel*(coord - planet_state.coord).p)*(coord - planet_state.coord).p)
                    } else {
                      None
                    }
                  } else {
                    None
                  }
                }).flatten
                if (need_velocity_l.nonEmpty) preserveVelocity(need_velocity_l.sum)
                else flightMode = 1
              case None =>
                flightMode = 1
            }
          } else preserveAngularVelocity(0)
        }
      case 9 => // остановиться
        if(allEnginesInactive || OrbitalKiller.tacts - last_correction_or_check_moment > 1000) {
          if (math.abs(angularVelocity) < 0.01) {
            if (linearVelocity.dist(DVec.dzero) > 0.1) {
              preserveVelocity(DVec.dzero)
            } else flightMode = 1
          } else preserveAngularVelocity(0)
        }
      case _ =>
    }
  }

  render {
    if(renderingEnabled) {
      if(!drawMapMode) {
        openglLocalTransform {
          openglMove(coord - base)
          drawFilledCircle(DVec.zero, 2, GREEN)                                 // mass center

          drawArrow(DVec.zero, linearVelocity.n * 100, CYAN)              // current velocity
          drawArrow(DVec.zero, linearAcceleration.n * 100, ORANGE)        // current acceleration
          drawArrow(Vec.zero, (earth.coord - coord).n * 100, YELLOW)      // direction to earth
          drawArrow(Vec.zero, (moon.coord - coord).n * 100, GREEN)        // direction to moon

          openglRotateDeg(rotation)
          drawSlidingLines(draw_points, WHITE)

          engines.foreach {
            case e =>
              e.force_dir match {
                case DVec(0, -1) => drawEngine(e, e.position + DVec(0, 2.5),  10, 5,  is_vertical = false)
                case DVec(0, 1)  => drawEngine(e, e.position + DVec(0, -2.5), 10, 5,  is_vertical = false)
                case DVec(-1, 0) => drawEngine(e, e.position + DVec(2.5, 0),  5,  10, is_vertical = true)
                case DVec(1, 0)  => drawEngine(e, e.position + DVec(-2.5, 0), 5,  10, is_vertical = true)
                case _ =>
              }
          }
        }
      }
    }
  }
}
