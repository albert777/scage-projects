package com.github.dunnololda.scageprojects.orbitalkiller

import java.io.FileOutputStream

import com.github.dunnololda.scage.ScageLibD.{DVec, ScageColor, Vec, addGlyphs, appVersion, max_font_size, messageBounds, print, property, stopApp, _}
import com.github.dunnololda.scage.support.ScageId
import com.github.dunnololda.scageprojects.orbitalkiller.ships.{Satellite1, Ship4, SpaceStation2}

import scala.collection._

//import collection.mutable.ArrayBuffer

case class BodyOrbitRender(bs_coord:DVec,
                           bs_vel:DVec,
                           bs_ang:Double,
                           bs_mass:Double,
                           planet_coord:DVec,
                           planet_vel:DVec,
                           planet_mass:Double,
                           orbit:KeplerOrbit,
                           render:() => Unit) {
  lazy val ellipseOrbit:Option[EllipseOrbit] = orbit match {
    case x: EllipseOrbit => Some(x)
    case _ => None
  }
  lazy val hyperbolaOrbit:Option[HyperbolaOrbit] = orbit match {
    case x: HyperbolaOrbit => Some(x)
    case _ => None
  }
}

sealed trait ViewMode {
  def rusStr:String
}
case object FreeViewMode extends ViewMode        {override def rusStr: String = "свободный"}
case object FixedOnShip extends ViewMode         {override def rusStr: String = "фиксация на корабле"}
case object FixedOnShipAbsolute extends ViewMode {override def rusStr: String = "фиксация на корабле, абсолютная ориентация"}
case object Landing extends ViewMode             {override def rusStr: String = "посадка на планету"}
case object FixedOnOrbit extends ViewMode        {override def rusStr: String = "фиксация на орбите корабля"}

object OrbitalKiller extends ScageScreenAppDMT("Orbital Killer", property("screen.width", 1280), property("screen.height", 768)) {
  val k:Double = 1 // доля секунды симуляции, которая обрабатывается за одну реальную секунду, если не применяется ускорение

  val linear_velocity_error = 0.1
  val angular_velocity_error = 0.0102 // значение подобрано эмпирически при тестах с малым количеством топлива
  val angle_error = 0.1

  // движок делает вызов обработчика примерно 63 раза в секунду, за каждый вызов будет обрабатывать вот такую порцию симуляции
  // то есть, мы хотим, чтобы за одну реальную секунду обрабатывалось k секунд симуляции, поэтому за один такт движка (которых 60 в секунду)
  // будем обрабатывать k/60
  val base_dt:Double = 1.0/63*k

  // какой длины в пикселях на экране будет реальная длина в 1 метр
  /*val zoom:Double = 10*/

  val realtime = (1.0/k).toInt // 1/k соответствует реальному течению времени

  private var _time_multiplier = realtime
  def timeMultiplier = {
    if(_time_multiplier != realtime && ShipsHolder.ships.flatMap(_.engines).exists(_.active)) {
      timeMultiplier_=(realtime)
    }
    _time_multiplier
  }
  def timeMultiplier_=(new_time_multiplier:Int) {
    if(new_time_multiplier > 0) {
      // разрешаем переход на ускоренное/замедленное течение времени только если все двигатели выключены
      if(new_time_multiplier == realtime || ShipsHolder.ships.flatMap(_.engines).forall(!_.active)) {
        _time_multiplier = new_time_multiplier
        ShipsHolder.ships.flatMap(_.engines).filter(_.active).foreach(e => {
          e.workTimeTacts = e.workTimeTacts
        })

      }
    }
  }

  /*def maxTimeMultiplier:Int = {
    /*val a = ships.map(s => math.abs(s.currentState.acc*s.currentState.vel.p)).max
    math.max((0.1*math.pow(351.3011068768212/a, 10.0/7)/5).toInt, 1)*/
    1
  }*/

  val system_evolution = new SystemEvolution(base_dt)
  def tacts:Long = system_evolution.tacts

  def currentBodyState(index:Int):Option[MutableBodyState] = system_evolution.bodyState(index)

  private val system_cache = mutable.HashMap[Long, mutable.Map[Int, MutableBodyState]]()

  def getFutureState(tacts:Long):mutable.Map[Int, MutableBodyState] = {
    if(player_ship.flightMode != Maneuvering) {
      system_cache.getOrElseUpdate(tacts, {
        println("adding to system_cache")
        val system_evolution_copy = system_evolution.copy
        val steps = tacts - system_evolution_copy.tacts
        (1l to steps).foreach(x => {
          system_evolution_copy.bodyState(player_ship.index).foreach(bs => {
            bs.mass = player_ship.currentMass(system_evolution_copy.tacts)
          })
          system_evolution_copy.step()
        })
        system_evolution_copy.allBodyStates
      })
    } else collection.mutable.Map()
  }

  def updateFutureTrajectory(reason:String) {
    if(player_ship.flightMode != Maneuvering) {
      //println(s"updateFutureTrajectory: $reason")
      system_cache.clear()
      _calculate_orbits = true
    }
  }
  
  val sun = new Star(
    ScageId.nextId, "Солнце",
    mass = 1.9891E30,
    coord = DVec(0, 1.496E11),
    radius = 6.9551E8
  )

  system_evolution.addBody(
    sun.currentState,
    (tacts, helper) => {
      DVec.zero
    },
    (tacts, helper) => {
      0.0
    }
  )

  val earth_start_position = DVec.dzero
  val earth_init_velocity = satelliteSpeed(earth_start_position, sun.coord, sun.linearVelocity, sun.mass, G, counterclockwise = true)
  val earth = new PlanetWithAir(
    index = ScageId.nextId, name = "Земля",
    mass = 5.9746E24,
    init_coord = earth_start_position,
    init_velocity = earth_init_velocity,
    //init_ang_vel = 0.0,
    init_ang_vel = 360.0/(24l*60*60),
    radius = 6400000/*6314759.95726045*/,
    orbiting_body = sun, air_free_altitude = 101000,
    T0 = 288,
    L = 0.00288/*0.0065*/,
    P0 = 101325,
    M = 0.02896,
    R = 8.314)

  system_evolution.addBody(
    earth.currentState,
    (tacts, helper) => {
      helper.gravityForceFromTo(sun.index, earth.index)
    },
    (tacts, helper) => {
      0.0
    }
  )

  val moon_start_position = DVec(-269000000, 269000000)
  val moon_init_velocity = satelliteSpeed(moon_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)
  val moon = new Planet(
    ScageId.nextId, "Луна",
    mass = 7.3477E22,
    init_coord = moon_start_position,
    init_velocity = moon_init_velocity,
    init_ang_vel = 360.0/(26l*24*60*60 + 8l*60*60 + 59l*60 + 44),   // период орбиты луны в данной симуляции: 26 д. 8 ч. 59 мин. 44 сек, равен периоду обращения вокруг собственной оси
    radius = 1737000,
    earth, 2000)

  system_evolution.addBody(
    moon.currentState,
    (tacts, helper) => {
      helper.gravityForceFromTo(sun.index, moon.index) +
        helper.gravityForceFromTo(earth.index, moon.index)
    },
    (tacts, helper) => {
      0.0
    }
  )

  system_evolution.addCollisionExclusion(earth.index, moon.index)
  system_evolution.addCollisionExclusion(earth.index, sun.index)
  system_evolution.addCollisionExclusion(moon.index, sun.index)
  val earth_sun_eq_gravity_radius = equalGravityRadius(earth.currentState, sun.currentState)
  val moon_earth_eq_gravity_radius = equalGravityRadius(moon.currentState, earth.currentState)

  // стоим на поверхности Земли
  //val ship_start_position = earth.coord + DVec(500, earth.radius + 3.5)
  //val ship_init_velocity = earth.linearVelocity + (ship_start_position - earth.coord).p*earth.groundSpeedMsec/*DVec.zero*/

  // суборбитальная траектория
  //val ship_start_position = earth.coord + DVec(500, earth.radius + 100000)
  //val ship_init_velocity = speedToHaveOrbitWithParams(ship_start_position, -30000, earth.coord, earth.linearVelocity, earth.mass, G)

  // на круговой орбите в 200 км от поверхности Земли
  val ship_start_position = earth.coord + DVec(-100, earth.radius + 199000)
  val ship_init_velocity = satelliteSpeed(ship_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)/**1.15*/

  // стоим на поверхности Луны
  //val ship_start_position = moon.coord + DVec(500, moon.radius + 3.5)
  //val ship_init_velocity = moon.linearVelocity + (ship_start_position - moon.coord).p*moon.groundSpeedMsec/*DVec.zero*//*satelliteSpeed(ship_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)*1.15*/
  //val ship_init_velocity = -escapeVelocity(ship_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)*1.01

  // на орбите в 1000 км от поверхности Луны
  //val ship_start_position = moon.coord + DVec(0, moon.radius + 1000000)
  //val ship_init_velocity = satelliteSpeed(ship_start_position, moon.coord, moon.linearVelocity, moon.mass, G, counterclockwise = true)
  //val ship_init_velocity = satelliteSpeed(ship_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)*1.15

  val player_ship = new Ship4(ScageId.nextId,
    init_coord = ship_start_position,
    init_velocity = ship_init_velocity,
    init_rotation = 0
  )

  // на круговой орбите в 200 км от поверхности Земли
  val station_start_position = earth.coord + DVec(0, earth.radius + 200000)
  val station_init_velocity = satelliteSpeed(station_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)

  // суборбитальная траектория
  //val station_start_position = earth.coord + DVec(0, earth.radius + 100000)
  //val station_init_velocity = speedToHaveOrbitWithParams(station_start_position, -30000, earth.coord, earth.linearVelocity, earth.mass, G)

  val station = new SpaceStation2(ScageId.nextId,
    init_coord = station_start_position,
    init_velocity = station_init_velocity,
    init_rotation = 180
  )

  // случайная орбита с перигеем от 200 до 1000 км, и апогеем от 0 до 3000 км выше перигея
  val sat1_start_position = earth.coord + DVec(0, 1).rotateDeg(math.random*360)*(earth.radius + 200000 + math.random*800000)
  val sat1_init_velocity = speedToHaveOrbitWithParams(sat1_start_position, math.random*3000000, earth.coord, earth.linearVelocity, earth.mass, G)
  //val sat1_init_velocity = satelliteSpeed(sat1_start_position, earth.coord, earth.linearVelocity, earth.mass, G, counterclockwise = true)
  val sat1 = new Satellite1(ScageId.nextId,
    init_coord = sat1_start_position,
    init_velocity = sat1_init_velocity,
    init_rotation = 45
  )

  val planets = immutable.Map(sun.index   -> sun,
                              earth.index -> earth,
                              moon.index  -> moon)
  val planet_indices:immutable.Set[Int] = planets.keySet

  def planetStates(body_states:Map[Int, MutableBodyState]):Seq[(CelestialBody, MutableBodyState)] = {
    body_states.flatMap(kv => {
      planets.get(kv._1).map(planet => (kv._1, (planet, kv._2)))
    }).values.toSeq.sortBy(_._2.mass)
  }

  val currentPlanetStates:Seq[(CelestialBody, MutableBodyState)] = planetStates(system_evolution.bodyStates(planet_indices))
  def planetByIndex(index:Int):Option[CelestialBody] = planets.get(index)

  def nameByIndex(index:Int):Option[String] = {
    planets.get(index) match {
      case s@Some(x) => s.map(_.name)
      case None => ShipsHolder.shipByIndex(index).map(_.name)
    }
  }

  var _stop_after_number_of_tacts:Long = 0
  var _stop_in_orbit_true_anomaly:Double = 0

  private def nextStep() {
    (1 to timeMultiplier).foreach(step => {
      ShipsHolder.ships.foreach(s => {
        s.beforeStep()
      })
      system_evolution.step()
      ShipsHolder.ships.foreach(s => {
        s.afterStep((tacts*base_dt*1000).toLong)
      })
      if(_stop_after_number_of_tacts > 0) {
        _stop_after_number_of_tacts -= 1
        if (_stop_after_number_of_tacts <= 0) {
          if (timeMultiplier != realtime) {
            timeMultiplier = realtime
          }
          pause()
        }
      }
    })
  }
  nextStep()

  private var view_mode:ViewMode = FreeViewMode

  def viewMode = view_mode
  def viewMode_=(new_view_mode:ViewMode) {
    if(new_view_mode != view_mode) {
      new_view_mode match {
        case FreeViewMode => // свободный
          _center = center
          center = _center
          rotationAngle = 0
          base = DVec.zero
          view_mode = FreeViewMode
        case FixedOnShip => // фиксация на корабле
          center = player_ship.coord + _ship_offset
          base = if (player_ship.coord.norma < 100000) DVec.zero else player_ship.coord
          rotationCenter = player_ship.coord
          rotationAngleDeg = -player_ship.rotation
          view_mode = FixedOnShip
        case Landing => // посадка на планету
          center = player_ship.coord
          rotationCenter = player_ship.coord
          rotationAngleDeg = {
            val nearest_body_coord = if (player_ship.coord.dist2(earth.coord) < player_ship.coord.dist2(moon.coord)) earth.coord else moon.coord
            val vec = player_ship.coord - nearest_body_coord
            if (vec.x >= 0) vec.deg(DVec(0, 1))
            else vec.deg(DVec(0, 1)) * (-1)
          }
          view_mode = Landing
        case FixedOnShipAbsolute => // фиксация на корабле, абсолютная ориентация
          center = player_ship.coord + _ship_offset
          base = if (player_ship.coord.norma < 100000) DVec.zero else player_ship.coord
          rotationAngle = 0
          view_mode = FixedOnShipAbsolute
        case FixedOnOrbit => // в режиме карты зафиксировать центр орбиты в центре экрана
          if (drawMapMode) {
            _center = _center - orbitAroundCelestialInPointWithVelocity(player_ship.coord, player_ship.linearVelocity, player_ship.mass).map(_._2.center * scale).getOrElse(player_ship.coord)
            center = orbitAroundCelestialInPointWithVelocity(player_ship.coord, player_ship.linearVelocity, player_ship.mass).map(_._2.center * scale).getOrElse(player_ship.coord) + _center
            rotationAngle = 0
            view_mode = FixedOnOrbit
          }
        case _ =>
      }
    }
  }

  def satelliteSpeedStrInPoint(coord:DVec, velocity:DVec, mass:Double):String = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, currentPlanetStates) match {
      case Some((planet, planet_state)) =>
        val ss = satelliteSpeed(coord, velocity, planet_state.coord, planet_state.vel, planet_state.mass, G)
        f"${msecOrKmsec(ss.norma)} (velx = ${msecOrKmsec(ss.x)}, vely = ${msecOrKmsec(ss.y)})"
      case None =>
        "N/A"
    }
  }

  def escapeVelocityStrInPoint(coord:DVec, velocity:DVec, mass:Double):String = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, currentPlanetStates) match {
      case Some((planet, planet_state)) =>
        val ss = escapeVelocity(coord, velocity, planet_state.coord, planet_state.vel, planet_state.mass, G)
        f"${msecOrKmsec(ss.norma)} (velx = ${msecOrKmsec(ss.x)}, vely = ${msecOrKmsec(ss.y)})"
      case None =>
        "N/A"
    }
  }

  def curvatureRadiusInPoint(body_state:BodyState):Double = {
    math.abs(body_state.vel.norma2/(body_state.acc * body_state.vel.p))
  }

  def curvatureRadiusStrInPoint(body_state:BodyState):String = {
    s"${mOrKmOrMKm(curvatureRadiusInPoint(body_state))}"
  }

  def orbitStrInPointWithVelocity(coord:DVec, velocity:DVec, radius:Double, mass:Double, planet_states:Seq[(CelestialBody, MutableBodyState)]):String = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, planet_states) match {
      case Some((planet, planet_state)) =>
        val orbit = calculateOrbit(planet_state.mass, planet_state.coord, mass, coord - planet_state.coord, velocity - planet_state.vel, G)
        orbit.strDefinition(planet.name, planetByIndex(planet_state.index).get.radius, planet_state.vel, planet.groundSpeedMsec, planet.g, coord, velocity, radius)
      case None => "N/A"
    }
  }

  def orbitInPointWithVelocity(coord:DVec, velocity:DVec, mass:Double):Option[KeplerOrbit] = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, currentPlanetStates) match {
      case Some((planet, planet_state)) =>
        Some(calculateOrbit(planet_state.mass, planet_state.coord, mass, coord - planet_state.coord, velocity - planet_state.vel, G))
      case None => None
    }
  }

  def orbitAroundCelestialInPointWithVelocity(coord:DVec, velocity:DVec, mass:Double):Option[((CelestialBody, MutableBodyState), KeplerOrbit)] = {
    insideSphereOfInfluenceOfCelestialBody(coord, mass, currentPlanetStates) match {
      case Some((planet, planet_state)) =>
        planetByIndex(planet_state.index).flatMap(planet => {
          Some(((planet, planet_state), calculateOrbit(planet_state.mass, planet_state.coord, mass, coord - planet_state.coord, velocity - planet_state.vel, G)))
        })
      case None => None
    }
  }

  private var disable_interface_drawing = false

  private var _draw_map_mode = false
  def drawMapMode = _draw_map_mode
  def drawMapMode_=(new_mode:Boolean) {
    if(new_mode) {
      _draw_map_mode = true
      orbitAroundCelestialInPointWithVelocity(player_ship.coord, player_ship.linearVelocity, player_ship.mass) match {
        case Some((planet, kepler_orbit)) =>
          kepler_orbit match {
            case ellipse:EllipseOrbit =>
              val b = BoxShape(2*ellipse.a, 2*ellipse.b)
              val aabb = b.aabb(ellipse.center, Vec(-1,0).signedDeg(ellipse.f2-ellipse.f))
              viewMode = FreeViewMode
              globalScale = 750 / (aabb.height * scale)
              _center = ellipse.center*scale
              viewMode = FixedOnOrbit
            case hyperbola:HyperbolaOrbit =>
              val b = BoxShape(2*hyperbola.a, 2*hyperbola.b)
              val aabb = b.aabb(hyperbola.half_center, Vec(1,0).signedDeg(hyperbola.f_minus_center_n))
              viewMode = FreeViewMode
              globalScale = 750 / (aabb.height * scale)
              _center = hyperbola.half_center*scale
              viewMode = FixedOnOrbit
          }
        case None =>
          viewMode = FreeViewMode
          globalScale = 1
          _center = earth.coord*scale
          viewMode = FixedOnOrbit
      }
    } else {
      _draw_map_mode = false
      globalScale = 10
      viewMode = FixedOnShip
    }
  }

  /**
   * Возвращает информацию о небесном теле, в сфере влияния которого находится наш объект (заведомо гораздо меньшей массы).
   * Мы вычисляем это, определяя в сфере Хилла какого небесного тела мы находимся. Потенциальные кандидаты передаются в аргументе
   * planet_state, и они там отсортированы по возрастанию массы. Мы проверяем нахождение в сфере Хилла начиная с самого малого.
   *
   * @param ship_coord - позиция нашего объекта
   * @param ship_mass - масса нашего объекта
   * @param planet_states - информация о небесных телах, в сфере влияния которых потенциально мы можем быть. Это список, и он должен быть
   *                        отсортирован по возрастанию массы. В конце списка должно быть Солнце!
   * @return
   */
  def insideSphereOfInfluenceOfCelestialBody(ship_coord:DVec,
                                             ship_mass:Double,
                                             planet_states:Seq[(CelestialBody, MutableBodyState)]):Option[(CelestialBody, MutableBodyState)] = {
    if(planet_states.isEmpty) None
    else if(planet_states.length == 1) Some(planet_states.head)
    else {
      val x = planet_states.find {
        case (smaller_planet, smaller_planet_state) =>
          ship_coord.dist(smaller_planet_state.coord) <= smaller_planet.half_hill_radius
      }
      if(x.nonEmpty) x else Some(planet_states.last)
    }
  }

  def drawArrow(from1:DVec, to1:DVec, color:ScageColor, scale:Double = globalScale) {
    val arrow11 = to1 + ((from1-to1).n*10/scale).rotateDeg(15)
    val arrow12 = to1 + ((from1-to1).n*10/scale).rotateDeg(-15)
    drawLine(from1, to1, color)
    drawLine(to1, arrow11, color)
    drawLine(to1, arrow12, color)
  }

  private var _show_game_saved_message = false
  def showGameSavedMessage = _show_game_saved_message

  def saveGame() {
    val fos = new FileOutputStream("save.orbitalkiller")
    fos.write(s"time $tacts\n".getBytes)
    currentBodyState(player_ship.index).foreach(x => fos.write(s"${x.saveData}\n".getBytes))
    currentBodyState(station.index).foreach(x => fos.write(s"${x.saveData}\n".getBytes))
    currentBodyState(moon.index).foreach(x => fos.write(s"${x.saveData}\n".getBytes))
    currentBodyState(earth.index).foreach(x => fos.write(s"${x.saveData}\n".getBytes))
    currentBodyState(sun.index).foreach(x => fos.write(s"${x.saveData}\n".getBytes))
    fos.close()
    _show_game_saved_message = true
    val start = System.currentTimeMillis()
    actionStaticPeriodIgnorePause(1000) {
      if(System.currentTimeMillis() - start > 2000) {
        _show_game_saved_message = false
        deleteSelf()
      }
    }
  }

  private var _show_game_loaded_message = false
  def showGameLoadedMessage = _show_game_loaded_message

  private var _show_game_failed_to_load_message = false
  def showGameFailedToLoadMessage = _show_game_failed_to_load_message

  def loadGame() {
    def _parseDVec(str:String):Option[DVec] = {
      val s = str.split(":")
      if(s.length == 2) {
        try {
          Some(DVec(s(0).toDouble, s(1).toDouble))
        } catch {
          case e:Exception => None
        }
      } else None
    }

    def _parseLong(str:String):Option[Long] = {
      try {
        Some(str.toLong)
      } catch {
        case e: Exception => None
      }
    }

    def _parseDouble(str:String):Option[Double] = {
      try {
        Some(str.toDouble)
      } catch {
        case e: Exception => None
      }
    }

    val savefile_lines = io.Source.fromFile("save.orbitalkiller").getLines().toList
    val new_tacts_option = savefile_lines.headOption.flatMap(l => {
      val s = l.split(" ")
      if(s.length == 2)_parseLong(s(1)) else None
    })

    val new_states = (for {
      line <- savefile_lines.drop(1)
      s = line.split(" ")
      if s.length == 7
      index = s(0)
      acc <- _parseDVec(s(1))
      vel <- _parseDVec(s(2))
      coord <- _parseDVec(s(3))
      ang_acc <- _parseDouble(s(4))
      ang_vel <- _parseDouble(s(5))
      ang <- _parseDouble(s(6))
    } yield {
        (index, (acc, vel, coord, ang_acc, ang_vel, ang))
      }).toMap

    for {
      new_tacts     <- new_tacts_option
      ship_state    <- new_states.get("ship")
      station_state <- new_states.get("station")
      moon_state    <- new_states.get("Moon")
      earth_state   <- new_states.get("Earth")
      sun_state     <- new_states.get("Sun")
    } {
      system_evolution.tacts = new_tacts

      def _loadState(currentState:MutableBodyState, new_state:(DVec, DVec, DVec, Double, Double, Double)): Unit = {
        val (acc, vel, coord, ang_acc, ang_vel, ang) = new_state
        currentState.acc = acc
        currentState.vel = vel
        currentState.coord = coord
        currentState.ang_acc = ang_acc
        currentState.ang_vel = ang_vel
        currentState.ang = ang
      }

      _loadState(player_ship.currentState, ship_state)
      _loadState(station.currentState, station_state)
      _loadState(moon.currentState, moon_state)
      _loadState(earth.currentState, earth_state)
      _loadState(sun.currentState, sun_state)
      _show_game_loaded_message = true
    }
    if(_show_game_loaded_message) {
      val start = System.currentTimeMillis()
      actionStaticPeriodIgnorePause(1000) {
        if(System.currentTimeMillis() - start > 2000) {
          _show_game_loaded_message = false
          deleteSelf()
        }
      }
    } else {
      _show_game_failed_to_load_message = true
      val start = System.currentTimeMillis()
      actionStaticPeriodIgnorePause(1000) {
        if(System.currentTimeMillis() - start > 2000) {
          _show_game_failed_to_load_message = false
          deleteSelf()
        }
      }
    }
  }

  val ccw_symbol    = '\u21b6'
  val cw_symbol     = '\u21b7'
  val rocket_symbol = '\u2191'

  preinit {
    addGlyphs(s"$ccw_symbol$cw_symbol$rocket_symbol")
  }

  keyIgnorePause(KEY_RETURN, onKeyDown = {if(player_ship.isAlive) player_ship.launchRocket()})

  keyIgnorePause(KEY_NUMPAD1, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD1)})
  keyIgnorePause(KEY_NUMPAD2, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD2)})
  keyIgnorePause(KEY_NUMPAD3, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD3)})
  keyIgnorePause(KEY_NUMPAD4, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD4)})
  keyIgnorePause(KEY_NUMPAD6, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD6)})
  keyIgnorePause(KEY_NUMPAD7, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD7)})
  keyIgnorePause(KEY_NUMPAD8, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD8)})
  keyIgnorePause(KEY_NUMPAD9, onKeyDown = {if(player_ship.isAlive) player_ship.selectOrSwitchEngineActive(KEY_NUMPAD9)})

  keyIgnorePause(KEY_NUMPAD5, onKeyDown = {
    if(player_ship.isAlive) {
      player_ship.engines.foreach(e => {
        if (e.active || e.power > 0) {
          e.active = false
          e.power = 0
        } else {
          e.workTimeTacts = 0
        }
      })
      player_ship.selected_engine = None
    }
  })

  /*keyIgnorePause(KEY_NUMPAD0, onKeyDown = {
    ship.engines.filterNot(_.active).foreach(e => e.workTimeTacts = 226800)     // 1 hour
    val active_engines = ship.engines.filter(_.active)
    if(active_engines.map(ae => ae.fuelConsumptionPerTact*226800).sum <= ship.fuelMass) {
      active_engines.foreach(e => e.workTimeTacts = 226800)
    } else {
      val fuel_for_every_active_engine = ship.fuelMass / active_engines.length
      active_engines.foreach(e => e.workTimeTacts = (fuel_for_every_active_engine/e.fuelConsumptionPerTact).toLong)
    }
  })*/


  private def repeatTime(code:Int):Long = {
    keyPress(code).map {
      case kp =>
        if(kp.was_pressed && System.currentTimeMillis() - kp.pressed_start_time < 100) 100l
        else 10l
    }.getOrElse(100l)
  }

  keyIgnorePause(KEY_UP,      repeatTime(KEY_UP),    onKeyDown = {
    if(player_ship.isAlive) {
      if (player_ship.flightMode != NearestPlanetVelocity) {
        player_ship.selected_engine.foreach(e => e.powerPercent += 1)
      } else {
        player_ship.vertical_speed_msec += 1
      }
    }
  }, onKeyUp = if(player_ship.isAlive && player_ship.flightMode != NearestPlanetVelocity && player_ship.selected_engine.exists(_.active)) {
    updateFutureTrajectory("KEY_UP")
  })
  keyIgnorePause(KEY_DOWN,    repeatTime(KEY_DOWN),  onKeyDown = {
    if(player_ship.isAlive) {
      if (player_ship.flightMode != NearestPlanetVelocity) {
        player_ship.selected_engine.foreach(e => e.powerPercent -= 1)
      } else {
        player_ship.vertical_speed_msec -= 1
      }
    }
  }, onKeyUp = if(player_ship.isAlive && player_ship.flightMode != NearestPlanetVelocity && player_ship.selected_engine.exists(_.active)) {
    updateFutureTrajectory("KEY_DOWN")
  })
  keyIgnorePause(KEY_RIGHT,   repeatTime(KEY_RIGHT), onKeyDown = {
    if(player_ship.isAlive) {
      if (player_ship.flightMode != NearestPlanetVelocity) {
        player_ship.selected_engine.foreach(e => {
          e.workTimeTacts += InterfaceHolder.timeStepSwitcher.timeStep
          //updateFutureTrajectory()
        })
      } else {
        player_ship.horizontal_speed_msec -= 1
      }
    }
  }, onKeyUp = if(player_ship.isAlive && player_ship.flightMode != NearestPlanetVelocity && player_ship.selected_engine.exists(_.active)) {
    updateFutureTrajectory("KEY_RIGHT")
  })
  keyIgnorePause(KEY_LEFT,    repeatTime(KEY_LEFT),  onKeyDown = {
    if(player_ship.isAlive) {
      if (player_ship.flightMode != NearestPlanetVelocity) {
        player_ship.selected_engine.foreach(e => {
          /*if(e.worktimeTacts > 0) {*/
          e.workTimeTacts -= InterfaceHolder.timeStepSwitcher.timeStep
          //updateFutureTrajectory()
          /*}*/
        })
      } else {
        player_ship.horizontal_speed_msec += 1
      }
    }
  }, onKeyUp = {
    if(player_ship.isAlive && player_ship.flightMode != NearestPlanetVelocity && player_ship.selected_engine.exists(_.active)) {
      updateFutureTrajectory("KEY_LEFT")
    }
  })

  private val engine_keys = List(KEY_UP, KEY_DOWN, KEY_RIGHT, KEY_LEFT)
  def anyEngineKeyPressed = engine_keys.exists(k => keyPressed(k))

  keyIgnorePause(KEY_ADD, 100, onKeyDown = {
    timeMultiplier += realtime
  }/*, onKeyUp = updateFutureTrajectory("KEY_ADD")*/)
  keyIgnorePause(KEY_SUBTRACT, 100, onKeyDown = {
    if(timeMultiplier > realtime) {
      timeMultiplier -= realtime
    }
  }/*, onKeyUp = updateFutureTrajectory("KEY_SUBTRACT")*/)

  keyIgnorePause(KEY_MULTIPLY, 100, onKeyDown = {
    if(timeMultiplier == realtime) {
      timeMultiplier = realtime*50
    } else {
      timeMultiplier += realtime*50
    }
  }/*, onKeyUp = updateFutureTrajectory("KEY_MULTIPLY")*/)
  keyIgnorePause(KEY_DIVIDE, 100, onKeyDown = {
    if (timeMultiplier != realtime) {
      timeMultiplier = realtime
    }
  }/*, onKeyUp = updateFutureTrajectory("KEY_DIVIDE")*/)

  keyIgnorePause(KEY_W, 10, onKeyDown = {if(drawMapMode) _center += DVec(0, 5/globalScale)  else _ship_offset += DVec(0, 5/globalScale)})
  keyIgnorePause(KEY_A, 10, onKeyDown = {if(drawMapMode) _center += DVec(-5/globalScale, 0) else _ship_offset += DVec(-5/globalScale, 0)})
  keyIgnorePause(KEY_S, 10, onKeyDown = {if(drawMapMode) _center += DVec(0, -5/globalScale) else _ship_offset += DVec(0, -5/globalScale)})
  keyIgnorePause(KEY_D, 10, onKeyDown = {if(drawMapMode) _center += DVec(5/globalScale, 0)  else _ship_offset += DVec(5/globalScale, 0)})

  keyIgnorePause(KEY_M, onKeyDown = {drawMapMode = !drawMapMode})

  keyIgnorePause(KEY_SPACE, onKeyDown = {
    if(!drawMapMode) {
      _ship_offset = DVec.zero
    } else {
      drawMapMode = true
    }
  })

  keyIgnorePause(KEY_1, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = FreeFlightMode)
  keyIgnorePause(KEY_2, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = Killrot)
  keyIgnorePause(KEY_3, onKeyDown = if(player_ship.isAlive) {
    if(keyPressed(KEY_LSHIFT) || keyPressed(KEY_RSHIFT)) player_ship.flightMode = OppositeVelocityAligned else player_ship.flightMode = VelocityAligned
  })
  keyIgnorePause(KEY_4, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = CirclularOrbit)
  keyIgnorePause(KEY_5, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = NearestShipVelocity)
  keyIgnorePause(KEY_6, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = NearestShipAligned)
  keyIgnorePause(KEY_7, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = NearestShipAutoDocking)
  keyIgnorePause(KEY_8, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = NearestPlanetVelocity)
  //keyIgnorePause(KEY_9, onKeyDown = if(ship.pilotIsAlive) ship.flightMode = AbsoluteStop)
  keyIgnorePause(KEY_0, onKeyDown = if(player_ship.isAlive) player_ship.flightMode = Maneuvering)

  keyIgnorePause(KEY_P, onKeyDown = switchPause())

  keyIgnorePause(KEY_F1, onKeyDown = {pause(); holdCounters {HelpScreen.run()}})
  keyIgnorePause(KEY_F2, onKeyDown = if(!drawMapMode) viewMode = FixedOnShip else viewMode = FreeViewMode)     // фиксация на корабле, в режиме карты: свободный режим
  keyIgnorePause(KEY_F3, onKeyDown = if(!drawMapMode) viewMode = FixedOnShipAbsolute else viewMode = FixedOnOrbit)     // фиксация на корабле, абсолютная ориентация, в режиме карты: фиксация на орбите
  keyIgnorePause(KEY_F4, onKeyDown = if(!drawMapMode) viewMode = Landing)                       // посадка на планету, если не в режиме карты

  // функционал толком не работает
  //keyIgnorePause(KEY_F5, onKeyDown = saveGame())                          // сохранить текущее состояние системы
  //keyIgnorePause(KEY_F6, onKeyDown = loadGame())                          // загрузить из файла состояние системы

  keyIgnorePause(KEY_I, onKeyDown = {
    disable_interface_drawing = !disable_interface_drawing
    if(disable_interface_drawing) InterfaceHolder.hideAllByUser()
    else InterfaceHolder.showAllByUser()
  })
  keyIgnorePause(KEY_R, onKeyDown = {
    player_ship.rockets_enabled = !player_ship.rockets_enabled
    if(player_ship.rockets_enabled) {
      if(InterfaceHolder.rocketsInfo.isMinimizedByUser) {
        InterfaceHolder.rocketsInfo.showByUser()
      }
    }
  })
  keyIgnorePause(KEY_Q, onKeyDown = {if(keyPressed(KEY_LCONTROL)) stopApp()})

  mouseWheelDownIgnorePause(onWheelDown = m => {
    if(globalScale > 0.01) {
      if(globalScale.toInt >= 200000) globalScale -= 100000
      else if(globalScale.toInt >= 20000) globalScale -= 10000
      else if(globalScale.toInt >= 2000) globalScale -= 1000
      else if(globalScale.toInt >= 200) globalScale -= 100
      else if(globalScale.toInt >= 20) globalScale -= 10
      else if(globalScale.toInt >= 2) globalScale -= 1
      else if((globalScale*10).toInt > 1) globalScale -= 0.1
      else globalScale -= 0.01
      if(globalScale < 0.01) globalScale = 0.01
    }
    println(globalScale)
  })
  mouseWheelUpIgnorePause(onWheelUp = m => {
    val _maxGlobalScale = if(!drawMapMode) 30 else 1000000
    if(globalScale < _maxGlobalScale) {
      if(globalScale < 0.1) globalScale += 0.01
      else if(globalScale < 1) globalScale += 0.1
      else if(globalScale < 10) globalScale +=1
      else if(globalScale < 100) globalScale +=10
      else if(globalScale < 1000) globalScale +=100
      else if(globalScale < 10000) globalScale +=1000
      else if(globalScale < 100000) globalScale +=10000
      else globalScale += 100000
      if(globalScale > _maxGlobalScale) globalScale = _maxGlobalScale
    }
    println(globalScale)
  })


  private var left_up_corner:Option[DVec] = None
  private var right_down_corner:Option[DVec] = None
  private var set_stop_moment = false
  leftMouseIgnorePause(onBtnDown = m => {
    if(drawMapMode) {
      left_up_corner = Some(absCoord(m))
    } else {
      InterfaceHolder.clickInterfaceElem(m, 0)
    }
  }, onBtnUp = m => {
    if(drawMapMode) {
      if(right_down_corner.nonEmpty) {
        for {
          x <- left_up_corner
          y <- right_down_corner
          c = (y - x).n * (y.dist(x) / 2f) + x
          h = math.abs(y.y - x.y)
          if h*globalScale > 10
        } {
          globalScale = math.min(1000000, 750 / h)
          if(viewMode == FixedOnOrbit) {
            _center = c - orbitAroundCelestialInPointWithVelocity(player_ship.coord, player_ship.linearVelocity, player_ship.mass).map(_._2.center * scale).getOrElse(player_ship.coord)
          } else {
            _center = c
          }
        }
      } else {
        if(!InterfaceHolder.clickInterfaceElem(m, 0) && player_ship.isAlive && (keyPressed(KEY_LSHIFT) || keyPressed(KEY_RSHIFT))) {
          set_stop_moment = true
        }
      }
      left_up_corner = None
      right_down_corner = None
    }})
  leftMouseDragIgnorePause(onDrag = m => if(drawMapMode) {
    right_down_corner = Some(absCoord(m))
  })

  rightMouseIgnorePause(onBtnDown = m => {
    if(!InterfaceHolder.clickInterfaceElem(m, 1) && (keyPressed(KEY_LSHIFT) || keyPressed(KEY_RSHIFT))) {
      _stop_after_number_of_tacts = 0
    }
  })

  action {
    nextStep()
  }

  private var _center = player_ship.coord
  private var _ship_offset = DVec.zero
  def shipOffset = _ship_offset
  center = _center
  windowCenter = DVec((windowWidth-1024)+1024/2, windowHeight/2)
  viewMode = FixedOnShip
  globalScale = 10

  val scale = 1e-6

  private def orbitRender(body_index:Int,
                          hyperbola_color:ScageColor,
                          ellipse_color:ScageColor,
                          some_system_state:mutable.Map[Int, MutableBodyState],
                          need_planets:Set[Int] = planet_indices):Option[BodyOrbitRender] = {
    val celestials = some_system_state.filter(kv => need_planets.contains(kv._1)).flatMap(kv => {
      planets.get(kv._1).map(planet => (kv._1, (planet, kv._2)))
    }).values.toSeq.sortBy(_._2.mass)
    // находим наш корабль
    some_system_state.get(body_index) match {
      case Some(bs) =>
        // смотрим, где он находится
        val bs_coord = bs.coord
        val bs_vel = bs.vel
        val bs_mass = bs.mass
        insideSphereOfInfluenceOfCelestialBody(bs_coord, bs_mass, celestials) match {
          case Some((planet, planet_state)) =>
            val planet_state_coord = planet_state.coord
            val planet_state_vel = planet_state.vel
            val planet_state_mass = planet_state.mass
            // корабль находится внутри гравитационного радиуса какой-то планеты (Земли или Луны)
            val orbit = calculateOrbit(
              planet_state_mass,
              planet_state_coord,
              bs_mass,
              bs_coord - planet_state_coord,
              bs_vel - planet_state_vel, G)
            orbit match {
              case h:HyperbolaOrbit =>
                val yy = (-math.acos(-1.0/h.e)+0.1 to math.acos(-1.0/h.e)-0.1 by 0.1).map(true_anomaly => {
                  val r = h.a*(h.e*h.e-1)/(1 + h.e*math.cos(true_anomaly))
                  (h.f + (h.f_minus_center_n*r).rotateRad(true_anomaly))*scale
                }).toList
                if(body_index != player_ship.index) {
                  Some(BodyOrbitRender(bs_coord, bs_vel, bs.ang, bs_mass, planet_state_coord, planet_state_vel, planet_state_mass, h, () => {
                    drawSlidingLines(yy, hyperbola_color)
                  }))
                } else {
                  Some(BodyOrbitRender(bs_coord, bs_vel, bs.ang, bs_mass, planet_state_coord, planet_state_vel, planet_state_mass, h, () => {
                    drawSlidingLines(yy, hyperbola_color)

                    val mouse_point = absCoord(mouseCoord) / scale
                    drawLine(h.f * scale, mouse_point * scale, DARK_GRAY)

                    val ccw = (bs_coord - h.f).perpendicular * (bs_vel - planet_state_vel) >= 0 // летим против часовой?

                    if(_stop_after_number_of_tacts > 0) {
                      drawFilledCircle(h.orbitalPointByTrueAnomalyRad(_stop_in_orbit_true_anomaly)*scale, 3 / globalScale, RED)
                      drawFilledCircle(h.orbitalPointAfterTime(bs_coord, (_stop_after_number_of_tacts*base_dt).toLong, ccw)*scale, 3 / globalScale, GREEN)
                    }

                    val mouse_teta_rad2Pi = h.tetaRad2PiInPoint(mouse_point)
                    val ship_teta_rad2Pi = h.tetaRad2PiInPoint(bs_coord)
                    if(h.tetaRad2PiValid(mouse_teta_rad2Pi)) {
                      val away_from_rp = (bs_coord - h.f) * (bs_vel - planet_state_vel) >= 0 // приближаемся к перигею или удаляемся от него?

                      val mouse_point_further_on_the_way = {
                        if(ccw) {
                          (away_from_rp  && ship_teta_rad2Pi <= mouse_teta_rad2Pi && mouse_teta_rad2Pi <= h.teta_rad_min) ||
                            (!away_from_rp && ((ship_teta_rad2Pi <= mouse_teta_rad2Pi && mouse_teta_rad2Pi <= 360) || (0 <= mouse_teta_rad2Pi && mouse_teta_rad2Pi <= h.teta_rad_min)))
                        } else {
                          (away_from_rp && ship_teta_rad2Pi >= mouse_teta_rad2Pi && mouse_teta_rad2Pi >= h.teta_rad_max) ||
                            (!away_from_rp && ((ship_teta_rad2Pi >= mouse_teta_rad2Pi && mouse_teta_rad2Pi >= 0) || (360 >= mouse_teta_rad2Pi && mouse_teta_rad2Pi >= h.teta_rad_max)))
                        }
                      }

                      if(mouse_point_further_on_the_way) {
                        val orbital_point = h.orbitalPointInPoint(mouse_point)
                        drawFilledCircle(orbital_point*scale, 3 / globalScale, ellipse_color)
                        val flight_time_msec = h.travelTimeOnOrbitMsec(bs_coord, orbital_point, ccw)

                        if (set_stop_moment) {
                          _stop_after_number_of_tacts = (flight_time_msec / 1000 / base_dt).toLong
                          _stop_in_orbit_true_anomaly = mouse_teta_rad2Pi
                          set_stop_moment = false

                          /*val p1 = h.orbitalPointByTrueAnomalyRad(_stop_in_orbit_true_anomaly)
                          println((50 to 300 by 50).map(num_iterations => {
                            val px = h.orbitalPointAfterTime(bs_coord, (_stop_after_number_of_tacts*base_dt).toLong, ccw, num_iterations)
                            mOrKmOrMKm(p1.dist(px))
                          }).mkString(" : "))*/
                        }

                        if(InterfaceHolder.orbParams.calculationOn) {
                          val flight_time = s"${timeStr(flight_time_msec)}"
                          val vnorm = h.orbitalVelocityByTrueAnomalyRad(mouse_teta_rad2Pi)
                          openglLocalTransform {
                            openglMove(orbital_point * scale)
                            print(s"  $flight_time : ${mOrKmOrMKm(h.distanceByTrueAnomalyRad(mouse_teta_rad2Pi) - planet.radius)} : ${msecOrKmsec(vnorm)}", Vec.zero, size = (max_font_size / globalScale).toFloat, ellipse_color)
                          }
                          InterfaceHolder.ship_interfaces.filter(si => !si.isMinimized && !si.monitoring_ship.isCrashed).flatMap(_.monitoring_ship.orbitRender).foreach(x => {
                            x.ellipseOrbit.foreach(e => {
                              val position_after_time = e.orbitalPointAfterTimeCCW(x.bs_coord, flight_time_msec / 1000)
                              drawCircle(position_after_time * scale, earth.radius * scale / 2f / globalScale, YELLOW)
                            })
                          })
                          moon.orbitRender.foreach(x => {
                            x.ellipseOrbit.foreach(e => {
                              val position_after_time = e.orbitalPointAfterTimeCCW(x.bs_coord, flight_time_msec / 1000)
                              drawCircle(position_after_time * scale, moon.radius * scale, YELLOW)
                              drawCircle(position_after_time * scale, moon.half_hill_radius * scale, color = DARK_GRAY)
                            })
                          })
                        }
                      }
                      if(InterfaceHolder.orbParams.calculationOn) {
                        InterfaceHolder.ship_interfaces.filter(si => !si.isMinimized && !si.monitoring_ship.isCrashed).flatMap(_.monitoring_ship.orbitRender).foreach(x => {
                          x.ellipseOrbit.foreach(e => {
                            if (_stop_after_number_of_tacts > 0) {
                              val time_to_stop_sec = (_stop_after_number_of_tacts * base_dt).toLong
                              val position_when_stop_moment = e.orbitalPointAfterTimeCCW(x.bs_coord, time_to_stop_sec)
                              drawCircle(position_when_stop_moment * scale, earth.radius * scale / 2f / globalScale, GREEN)
                            }
                          })
                        })
                        moon.orbitRender.foreach(x => {
                          x.ellipseOrbit.foreach(e => {
                            if (_stop_after_number_of_tacts > 0) {
                              val time_to_stop_sec = (_stop_after_number_of_tacts * base_dt).toLong
                              val position_when_stop_moment = e.orbitalPointAfterTimeCCW(x.bs_coord, time_to_stop_sec)
                              drawCircle(position_when_stop_moment * scale, moon.radius * scale, GREEN)
                              drawCircle(position_when_stop_moment * scale, moon.half_hill_radius * scale, color = DARK_GRAY)
                            }
                          })
                        })
                      }
                    }
                  }))
                }
              case e:EllipseOrbit =>
                if(body_index != player_ship.index) {
                  Some(BodyOrbitRender(bs_coord, bs_vel, bs.ang, bs_mass, planet_state_coord, planet_state_vel, planet_state_mass, e, () => {
                    openglLocalTransform {
                      openglMove(e.center * scale)
                      openglRotateDeg(Vec(-1, 0).signedDeg(e.f2 - e.f))
                      drawEllipse(DVec.zero, e.a * scale, e.b * scale, ellipse_color)
                    }
                  }))
                } else {
                  Some(BodyOrbitRender(bs_coord, bs_vel, bs.ang, bs_mass, planet_state_coord, planet_state_vel, planet_state_mass, e, () => {
                    openglLocalTransform {
                      openglMove(e.center * scale)
                      openglRotateDeg(Vec(-1, 0).signedDeg(e.f2 - e.f))
                      drawEllipse(DVec.zero, e.a * scale, e.b * scale, ellipse_color)
                    }
                    val mouse_point = absCoord(mouseCoord) / scale
                    drawLine(e.f*scale, mouse_point*scale, DARK_GRAY)

                    val orbital_point = e.orbitalPointInPoint(mouse_point)
                    drawFilledCircle(orbital_point*scale, 3 / globalScale, ellipse_color)

                    val ccw = (bs_coord - e.f).perpendicular*(bs_vel - planet_state_vel) >= 0 // летим против часовой?

                    if(_stop_after_number_of_tacts > 0) {
                      drawFilledCircle(e.orbitalPointByTrueAnomalyRad(_stop_in_orbit_true_anomaly)*scale, 3 / globalScale, RED)
                      drawFilledCircle(e.orbitalPointAfterTime(bs_coord, (_stop_after_number_of_tacts*base_dt).toLong, ccw)*scale, 3 / globalScale, GREEN)
                    }
                    val true_anomaly_rad = e.tetaRad2PiInPoint(mouse_point)

                    val flight_time_msec = e.travelTimeOnOrbitMsec(bs_coord, orbital_point, ccw)

                    if(set_stop_moment) {
                      _stop_after_number_of_tacts = (flight_time_msec/1000/base_dt).toLong
                      _stop_in_orbit_true_anomaly = true_anomaly_rad
                      set_stop_moment = false

                      /*val p1 = e.orbitalPointByTrueAnomalyRad(_stop_in_orbit_true_anomaly)
                      println((50 to 300 by 50).map(num_iterations => {
                        val px = e.orbitalPointAfterTime(bs_coord, (_stop_after_number_of_tacts*base_dt).toLong, ccw, num_iterations)
                        mOrKmOrMKm(p1.dist(px))
                      }).mkString(" : "))*/
                    }
                    if(InterfaceHolder.orbParams.calculationOn) {
                      val flight_time_str = s"${timeStr(flight_time_msec)}"
                      val (vt, vr) = e.orbitalVelocityByTrueAnomalyRad(true_anomaly_rad)
                      val vnorm = math.sqrt(vr * vr + vt * vt)
                      openglLocalTransform {
                        openglMove(orbital_point * scale)
                        print(s"  $flight_time_str : ${mOrKmOrMKm(e.distanceByTrueAnomalyRad(true_anomaly_rad) - planet.radius)} : ${msecOrKmsec(vnorm)}", Vec.zero, size = (max_font_size / globalScale).toFloat, ellipse_color)
                      }
                      InterfaceHolder.ship_interfaces.filter(si => !si.isMinimized && !si.monitoring_ship.isCrashed).flatMap(_.monitoring_ship.orbitRender).foreach(x => {
                        x.ellipseOrbit.foreach(e => {
                          val position_after_time = e.orbitalPointAfterTimeCCW(x.bs_coord, flight_time_msec / 1000)
                          drawCircle(position_after_time * scale, earth.radius * scale / 2f / globalScale, YELLOW)
                          if (_stop_after_number_of_tacts > 0) {
                            val time_to_stop_sec = (_stop_after_number_of_tacts * base_dt).toLong
                            val position_when_stop_moment = e.orbitalPointAfterTimeCCW(x.bs_coord, time_to_stop_sec)
                            drawCircle(position_when_stop_moment * scale, earth.radius * scale / 2f / globalScale, GREEN)
                          }
                        })
                      })
                      moon.orbitRender.foreach(x => {
                        x.ellipseOrbit.foreach(e => {
                          val position_after_time = e.orbitalPointAfterTimeCCW(x.bs_coord, flight_time_msec / 1000)
                          drawCircle(position_after_time * scale, moon.radius * scale, YELLOW)
                          drawCircle(position_after_time * scale, moon.half_hill_radius * scale, color = DARK_GRAY)
                          if (_stop_after_number_of_tacts > 0) {
                            val time_to_stop_sec = (_stop_after_number_of_tacts * base_dt).toLong
                            val position_when_stop_moment = e.orbitalPointAfterTimeCCW(x.bs_coord, time_to_stop_sec)
                            drawCircle(position_when_stop_moment * scale, moon.radius * scale, GREEN)
                            drawCircle(position_when_stop_moment * scale, moon.half_hill_radius * scale, color = DARK_GRAY)
                          }
                        })
                      })
                    }
                  }))
                }
            }
          case None => None
        }
      case None => None
    }
  }

  private var _calculate_orbits = false

  private def updateOrbits() {
    //println("updateOrbits")
    if(player_ship.flightMode == Maneuvering || !onPause || !player_ship.engines.exists(_.active)) {
      // если в режиме маневрирования, или не в режиме маневрирования, но не на паузе, или на паузе, но двигатели не работают - рисуем текущее состояние
      player_ship.orbitRender = orbitRender(player_ship.index, player_ship.colorIfPlayerAliveOrRed(YELLOW), player_ship.colorIfPlayerAliveOrRed(YELLOW), system_evolution.allBodyStates)
      InterfaceHolder.ship_interfaces.foreach(si => {
        if(!si.isMinimized && !si.monitoring_ship.isCrashed) {
          si.monitoring_ship.orbitRender = {
            orbitRender(si.monitoring_ship.index, player_ship.colorIfPlayerAliveOrRed(MAGENTA), player_ship.colorIfPlayerAliveOrRed(MAGENTA), system_evolution.allBodyStates)
          }
        }
      })
      moon.orbitRender =  orbitRender(moon.index, player_ship.colorIfPlayerAliveOrRed(GREEN), player_ship.colorIfPlayerAliveOrRed(GREEN), system_evolution.allBodyStates, Set(earth.index, sun.index))
      earth.orbitRender =  orbitRender(earth.index, player_ship.colorIfPlayerAliveOrRed(ORANGE), player_ship.colorIfPlayerAliveOrRed(ORANGE), system_evolution.allBodyStates, Set(sun.index))
    } else {
      // в эту секцию мы попадаем, если мы не в режиме маневрирования, не на паузе, и двигатели работают
      val system_state_when_engines_off = getFutureState(player_ship.engines.map(_.stopMomentTacts).max)
      player_ship.orbitRender = orbitRender(player_ship.index, player_ship.colorIfPlayerAliveOrRed(YELLOW), player_ship.colorIfPlayerAliveOrRed(YELLOW), system_state_when_engines_off)
      InterfaceHolder.ship_interfaces.foreach(si => {
        if(!si.isMinimized && !si.monitoring_ship.isCrashed) {
          si.monitoring_ship.orbitRender = {
            orbitRender(si.monitoring_ship.index, player_ship.colorIfPlayerAliveOrRed(MAGENTA), player_ship.colorIfPlayerAliveOrRed(MAGENTA), system_state_when_engines_off)
          }
        }
      })
      moon.orbitRender =  orbitRender(moon.index, player_ship.colorIfPlayerAliveOrRed(GREEN), player_ship.colorIfPlayerAliveOrRed(GREEN), system_state_when_engines_off, Set(earth.index, sun.index))
      earth.orbitRender =  orbitRender(earth.index, player_ship.colorIfPlayerAliveOrRed(ORANGE), player_ship.colorIfPlayerAliveOrRed(ORANGE), system_state_when_engines_off, Set(sun.index))
    }
    _calculate_orbits = false
  }
  updateOrbits()

  actionDynamicPeriodIgnorePause(1000/timeMultiplier) {
    if(drawMapMode && (!onPause || _calculate_orbits)) {
      updateOrbits()
    }
  }

  actionStaticPeriodIgnorePause(10000) {
    if(OrbitalKiller.timeMultiplier != realtime && OrbitalKiller.timeMultiplier > 1f*OrbitalKiller.timeMultiplier/63*OrbitalKiller.ticks + 20) {
      println("updating timeMultiplier")
      OrbitalKiller.timeMultiplier = (OrbitalKiller.timeMultiplier*1f/63*OrbitalKiller.ticks).toInt
    }
  }

  private def circlesIntersection(p0:DVec, r0:Double, p1:DVec, r1:Double):List[DVec] = {
    val d = p1.dist(p0)
    if(d > r0 + r1 || d < math.abs(r0 - r1)) Nil
    else if(d == r0 + r1) {
      List(p0 + (p1 - p0).n*r0)
    } else {
      val a = (r0*r0 - r1*r1 + d*d)/(2*d)
      val h = math.sqrt(r0*r0 - a*a)
      val DVec(x2, y2) = p0 + a*(p1 - p0)/d
      List(DVec(x2 + h*(p1.y - p0.y)/d, y2 - h*(p1.x - p0.x)/d),
        DVec(x2 - h*(p1.y - p0.y)/d, y2 + h*(p1.x - p0.x)/d))
    }
  }

  private def tangentsFromPointToCircle(p:DVec, c:DVec, r:Double):List[DVec] = {
    circlesIntersection(c, r, p + (c - p)*0.5, c.dist(p)/2)
  }

  def tangentsFromCircleToCircle(p0:DVec, r0:Double, p1:DVec, r1:Double):Option[(DVec, DVec, DVec, DVec)] = {
    if(r0 > r1) tangentsFromCircleToCircle(p1, r1, p0, r0)
    else {
      val l = tangentsFromPointToCircle(p0, p1, r1 - r0)
      if(l.length != 2) None else {
        val List(x1, x2) = l
        val x1p2_n = (x1 - p1).n
        val x2p2_n = (x2 - p1).n

        val b1 = p1 + x1p2_n*r1
        val b2 = p1 + x2p2_n*r1

        val c1 = p0 + x1p2_n*r0
        val c2 = p0 + x2p2_n*r0
        Some((c1, c2, b1, b2))
      }
    }
  }

  def inShadowOfPlanet(coord:DVec):Option[(CelestialBody, MutableBodyState)] = {
    val ship_sun_dist = coord.dist(sun.coord)
    currentPlanetStates.filterNot(_._1.index == sun.index).find {
      case (planet, planet_state) =>
        ship_sun_dist > planet.coord.dist(sun.coord) && (tangentsFromCircleToCircle(planet.coord, planet.radius, sun.coord, sun.radius) match {
          case Some((c1, c2, b1, b2)) =>
            val a1 = (c1 - b1).perpendicular * (coord - b1) > 0
            val a2 = (c2 - b2).perpendicular * (coord - b2) < 0
            a1 && a2
          case None => false
        })
    }
  }

  private def drawSunTangents(planet_coord:DVec, planet_radius:Double, sun_coord:DVec, sun_radius:Double, dist:Double) {
    tangentsFromCircleToCircle(planet_coord, planet_radius, sun_coord, sun_radius) match {
      case Some((c1, c2, b1, b2)) =>
        val a = (c1 - b1).n*dist
        val b = (c2 - b2).n*dist
        drawLine(c1*scale, (c1 + a)*scale, DARK_GRAY)
        drawLine(c2*scale, (c2 + b)*scale, DARK_GRAY)
      case None =>
    }
  }

  render {
      if(drawMapMode) {
        /*val spaces = splitSpace(new Space(our_mutable_system.map(_._1), DVec(OrbitalKiller.earth.radius*2, -OrbitalKiller.earth.radius*2)), 5, 2)
        spaces.foreach(s => {
          drawRectCentered(s.center*scale, s.width*scale, s.height*scale, GRAY)
        })
        println(spaces.filter(_.bodies.length > 1).map(x => s"${x.bodies.length}").mkString(" : "))*/

        drawCircle(sun.coord*scale, sun.radius * scale, WHITE)

        earth.orbitRender.foreach {
          case BodyOrbitRender(bs_coord, _, bs_ang, _, planet_coord, _, _, _, render) =>
            drawCircle(bs_coord*scale, earth.radius * scale, WHITE)
            if(InterfaceHolder.namesSwitcher.showNames) {
              openglLocalTransform {
                openglMove(bs_coord.toVec * scale)
                print(earth.name, Vec.zero, color = WHITE, size = (max_font_size / globalScale).toFloat)
              }
              val v = (planet_coord - bs_coord).n*earth_sun_eq_gravity_radius
              openglLocalTransform {
                openglMove((bs_coord + v).toVec * scale)
                drawFilledCircle(Vec.zero, earth.radius * scale / 2f / globalScale, DARK_GRAY)
                print(" L1", Vec.zero, color = DARK_GRAY, size = (max_font_size / globalScale).toFloat)
              }
              openglLocalTransform {
                openglMove((bs_coord - v).toVec * scale)
                drawFilledCircle(Vec.zero, earth.radius * scale / 2f / globalScale, DARK_GRAY)
                print(" L2", Vec.zero, color = DARK_GRAY, size = (max_font_size / globalScale).toFloat)
              }
            }

            drawCircle(bs_coord*scale, earth.half_hill_radius*scale, color = DARK_GRAY)
            drawLine(bs_coord*scale, bs_coord*scale + DVec(0, earth.radius*scale).rotateDeg(bs_ang), WHITE)
            drawSunTangents(bs_coord, earth.radius, planet_coord, sun.radius, 500000000)
            render()
        }

        moon.orbitRender.foreach {
          case BodyOrbitRender(bs_coord, _, bs_ang, _, planet_coord, _, _, _, render) =>
            drawCircle(bs_coord*scale, moon.radius * scale, WHITE)
            if(InterfaceHolder.namesSwitcher.showNames) {
              openglLocalTransform {
                openglMove(bs_coord.toVec * scale)
                print(moon.name, Vec.zero, color = WHITE, size = (max_font_size / globalScale).toFloat)
              }
              val v = (planet_coord - bs_coord).n*moon_earth_eq_gravity_radius
              openglLocalTransform {
                openglMove((bs_coord + v).toVec * scale)
                drawFilledCircle(Vec.zero, earth.radius * scale / 2f / globalScale, DARK_GRAY)
                print(" L1", Vec.zero, color = DARK_GRAY, size = (max_font_size / globalScale).toFloat)
              }
              openglLocalTransform {
                openglMove((bs_coord - v).toVec * scale)
                drawFilledCircle(Vec.zero, earth.radius * scale / 2f / globalScale, DARK_GRAY)
                print(" L2", Vec.zero, color = DARK_GRAY, size = (max_font_size / globalScale).toFloat)
              }
            }
            //drawCircle(moon.coord*scale, equalGravityRadius(moon.currentState, earth.currentState)*scale, color = DARK_GRAY)
            drawCircle(bs_coord*scale, moon.half_hill_radius*scale, color = DARK_GRAY)
            //drawCircle(moon.coord*scale, soi(moon.mass, earth.coord.dist(moon.coord), earth.mass)*scale, color = DARK_GRAY)
            drawLine(bs_coord*scale, bs_coord * scale + DVec(0, moon.radius * scale).rotateDeg(bs_ang), WHITE)
            drawSunTangents(bs_coord, moon.radius, sun.coord, sun.radius, 40000000)
            render()
        }

        player_ship.orbitRender.foreach {
          case BodyOrbitRender(bs_coord, _, bs_ang, _, planet_coord, _, _, _, render) =>
            drawFilledCircle(bs_coord * scale, earth.radius * scale / 2f / globalScale, WHITE)
            if (InterfaceHolder.namesSwitcher.showNames) {
              openglLocalTransform {
                openglMove(bs_coord.toVec * scale)
                print(player_ship.name, Vec.zero, color = WHITE, size = (max_font_size / globalScale).toFloat)
              }
            }
            render()
        }

        InterfaceHolder.ship_interfaces.foreach(si => {
          if(!si.isMinimized) {
            si.monitoring_ship.orbitRender.foreach {
              case BodyOrbitRender(bs_coord, _, bs_ang, _, planet_coord, _, _, _, render) =>
                val color = if(player_ship.isDead || si.monitoring_ship.isDead) RED else MAGENTA
                drawFilledCircle(bs_coord * scale, earth.radius * scale / 2f / globalScale, color)
                if (InterfaceHolder.namesSwitcher.showNames) {
                  openglLocalTransform {
                    openglMove(bs_coord.toVec * scale)
                    print(si.monitoring_ship.name, Vec.zero, color = color, size = (max_font_size / globalScale).toFloat)
                  }
                }
                if(!si.monitoring_ship.isCrashed) render()
            }
          }
        })

        for {
          x <- left_up_corner
          y <- right_down_corner
        } {
          val c = (y-x).n*(y.dist(x)/2f)+x
          val w = math.abs(y.x - x.x)
          val h = math.abs(y.y - x.y)

          drawRectCentered(c,w,h,DARK_GRAY)
          drawLine(c + DVec(-w/2, -h/2), c + DVec(-w/2, -h/2) + DVec(0, -10/globalScale))
          drawLine(c + DVec(w/2, h/2),   c + DVec(w/2, h/2)   + DVec(0, -10/globalScale))

          drawArrow(c + DVec(0, -h/2) + DVec(0, -5/globalScale), c + DVec(-w/2, -h/2) + DVec(0, -5/globalScale),DARK_GRAY)
          drawArrow(c + DVec(0, -h/2) + DVec(0, -5/globalScale), c + DVec(w/2, -h/2)  + DVec(0, -5/globalScale),DARK_GRAY)

          openglLocalTransform {
            val k = messageBounds(s"${mOrKmOrMKm((w / scale).toInt)}", (max_font_size / globalScale).toFloat)
            openglMove((c + DVec(0, -h / 2) + DVec(0, -15 / globalScale) + DVec(-k.x / 2, -k.y / 2)).toVec)
            print(
              s"${mOrKmOrMKm((w / scale).toInt)}",
              Vec.zero,
              color = DARK_GRAY,
              size = (max_font_size / globalScale).toFloat
            )
          }

          drawLine(c + DVec(w/2, h/2),  c + DVec(w/2, h/2)  + DVec(10/globalScale, 0))
          drawLine(c + DVec(w/2, -h/2), c + DVec(w/2, -h/2) + DVec(10/globalScale, 0))

          drawArrow(c + DVec(w/2, 0) + DVec(5/globalScale, 0), c + DVec(w/2, h/2) + DVec(5/globalScale, 0),DARK_GRAY)
          drawArrow(c + DVec(w/2, 0) + DVec(5/globalScale, 0), c + DVec(w/2, -h/2) + DVec(5/globalScale, 0),DARK_GRAY)

          openglLocalTransform {
            val l = messageBounds(s"${mOrKmOrMKm((h / scale).toInt)}", (max_font_size / globalScale).toFloat)
            openglMove((c + DVec(w / 2, 0) + DVec(10 / globalScale, 0) + DVec(0, -l.y / 2)).toVec)
            print(
              s"${mOrKmOrMKm((h / scale).toInt)}",
              Vec.zero,
              color = DARK_GRAY,
              size = (max_font_size / globalScale).toFloat
            )
          }
        }

        if(left_up_corner.isEmpty) {
          val m = absCoord(mouseCoord)
          val d = (player_ship.coord * scale).dist(m) / scale
          drawArrow(player_ship.coord * scale, m, DARK_GRAY)
          openglLocalTransform {
            openglMove(m)
            print(s"  ${mOrKmOrMKm(d.toLong)}", Vec.zero, size = (max_font_size / globalScale).toFloat, DARK_GRAY)
          }
        }
      } else {
        val m = absCoord(mouseCoord)
        val d = player_ship.coord.dist(m)
        openglLocalTransform {
          openglMove(player_ship.coord - base)
          drawArrow(DVec.zero, m - player_ship.coord, DARK_GRAY)
          openglMove(m - player_ship.coord)
          openglRotateDeg(-rotationAngleDeg)
          print(s"  ${mOrKmOrMKm(d.toLong)}", Vec.zero, size = (max_font_size / globalScale).toFloat, DARK_GRAY)
        }
      }
    /*}*/
  }

  interface {
    if(onPause) print("Пауза", windowCenter.toVec, align = "center", color = WHITE)
    print("F1 - Справка", 20, windowHeight - 40, align = "bottom-left", color = DARK_GRAY)
    print(s"сборка $appVersion", windowWidth - 20, windowHeight - 20, align = "top-right", color = DARK_GRAY)
    print(s"FPS/Ticks $fps/$ticks", windowWidth - 20, windowHeight - 40, align = "top-right", color = DARK_GRAY)
    print(f"Render/Action ${averageRenderTimeMsec*fps/(averageRenderTimeMsec*fps+averageActionTimeMsec*ticks)*100}%.2f%%/${1*averageActionTimeMsec*ticks/(averageRenderTimeMsec*fps+averageActionTimeMsec*ticks)*100}%.2f%%", windowWidth - 20, windowHeight - 60, align = "top-right", color = DARK_GRAY)
    print(f"Render/Action $averageRenderTimeMsec%.2f msec/$averageActionTimeMsec%.2f msec", windowWidth - 20, windowHeight - 80, align = "top-right", color = DARK_GRAY)
    print(s"Render/Action $currentRenderTimeMsec msec/$currentActionTimeMsec msec", windowWidth - 20, windowHeight - 100, align = "top-right", color = DARK_GRAY)

    val a = DVec(windowWidth - 250, 20)
    val b = DVec(windowWidth - 250 + 100, 20)
    drawLine(a,b, DARK_GRAY)
    drawLine(a, a+(a-b).rotateDeg(90).n*5, DARK_GRAY)
    drawLine(b, b+(a-b).rotateDeg(90).n*5, DARK_GRAY)
    print(s"${mOrKmOrMKm((100/globalScale/(if(drawMapMode) scale else 1.0)).toInt)}", b.toVec, DARK_GRAY)

    InterfaceHolder.update()
    InterfaceHolder.draw()
  }

  pause()
}






