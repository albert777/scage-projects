package com.github.dunnololda.scageprojects.orbitalkiller.ships

import com.github.dunnololda.scage.ScageLibD._
import com.github.dunnololda.scage.support.{ScageId, DVec}
import com.github.dunnololda.scageprojects.orbitalkiller.OrbitalKiller._
import com.github.dunnololda.scageprojects.orbitalkiller._

class Rocket1(index:Int,
              init_coord:DVec,
              init_velocity:DVec = DVec.dzero,
              init_rotation:Double = 0.0) extends PolygonShip(index, "Доброта", init_coord, init_velocity, init_rotation) {
  private val _payload:Double = 196
  private var _fuel_mass:Double = 4
  def mass:Double = _payload + _fuel_mass
  override def fuelMass: Double = _fuel_mass
  override def fuelMass_=(m: Double): Unit = {_fuel_mass = m}

  val is_manned = false

  lazy val engine_size:Double = 0.5*0.1

  lazy val points:List[DVec] = List(
    DVec(1.0, -20.0),
    DVec(2.0, -21.0),
    DVec(2.0, -18.0),
    DVec(1.0, -17.0),
    DVec(1.0, 18.0),
    DVec(0.0, 21.0),
    DVec(-1.0, 18.0),
    DVec(-1.0, -17.0),
    DVec(-2.0, -18.0),
    DVec(-2.0, -21.0),
    DVec(-1.0, -20.0)
  ).map(_*0.1)

  lazy val convex_parts = List(
    PolygonShape(List(DVec(-0.2, -2.0), DVec(-0.2, -2.1000001), DVec(-0.1, -2.0)), Nil),
    PolygonShape(List(DVec(-0.2, -2.0), DVec(0.1, -2.0), DVec(0.1, -1.8000001), DVec(-0.2, -1.8000001)), Nil),
    PolygonShape(List(DVec(0.1, -2.0), DVec(0.2, -2.1000001), DVec(0.2, -2.0)), Nil),
    PolygonShape(List(DVec(0.1, -2.0), DVec(0.2, -2.0), DVec(0.2, -1.8000001), DVec(0.1, -1.8000001)), Nil),
    PolygonShape(List(DVec(0.1, -1.7), DVec(0.1, -1.8000001), DVec(0.2, -1.8000001)), Nil),
    PolygonShape(List(DVec(-0.2, -1.8000001), DVec(-0.1, -1.8000001), DVec(-0.1, -1.7)), Nil),
    PolygonShape(List(DVec(-0.1, -1.8000001), DVec(0.1, -1.8000001), DVec(0.1, 1.8000001), DVec(-0.1, 1.8000001)), Nil),
    PolygonShape(List(DVec(-0.1, 1.8000001), DVec(0.1, 1.8000001), DVec(0.0, 2.1000001)), Nil)
  )

  val wreck_parts = List(
    PolygonShape(List(DVec(-0.2, -2.1000001), DVec(-0.1, -2.0), DVec(0.1, -1.9), DVec(-0.2, -1.8000001)), Nil),
    PolygonShape(List(DVec(-0.1, -2.0), DVec(0.1, -2.0), DVec(0.2, -1.9), DVec(0.1, -1.7), DVec(0.1, -1.9)), Nil),
    PolygonShape(List(DVec(0.1, -2.0), DVec(0.2, -2.1000001), DVec(0.2, -1.9)), Nil),
    PolygonShape(List(DVec(0.1, -1.7), DVec(0.2, -1.9), DVec(0.2, -1.8000001)), Nil),
    PolygonShape(List(DVec(-0.2, -1.8000001), DVec(0.1, -1.9), DVec(0.0, -1.7), DVec(-0.1, -1.7)), Nil),
    PolygonShape(List(DVec(0.1, -1.3000001), DVec(0.0, -1.4), DVec(0.0, -1.7), DVec(0.1, -1.9)), Nil),
    PolygonShape(List(DVec(-0.1, -1.7), DVec(0.0, -1.7), DVec(0.0, -1.4), DVec(0.1, -1.3000001), DVec(0.1, -1.1), DVec(-0.1, -1.3000001)), Nil),
    PolygonShape(List(DVec(-0.1, -1.3000001), DVec(0.1, -1.1), DVec(0.1, -0.90000004), DVec(-0.1, -0.8)), Nil),
    PolygonShape(List(DVec(-0.1, -0.8), DVec(0.1, -0.90000004), DVec(0.1, -0.7), DVec(-0.1, -0.7)), Nil),
    PolygonShape(List(DVec(-0.1, -0.7), DVec(0.1, -0.5), DVec(-0.1, -0.3)), Nil),
    PolygonShape(List(DVec(-0.1, -0.7), DVec(0.1, -0.7), DVec(0.1, -0.5)), Nil),
    PolygonShape(List(DVec(-0.1, -0.3), DVec(0.1, -0.5), DVec(0.1, -0.2)), Nil),
    PolygonShape(List(DVec(-0.1, -0.3), DVec(0.1, -0.2), DVec(0.1, -0.1), DVec(-0.1, 0.0)), Nil),
    PolygonShape(List(DVec(-0.1, 0.0), DVec(0.1, -0.1), DVec(0.0, 0.3)), Nil),
    PolygonShape(List(DVec(-0.1, 0.0), DVec(0.0, 0.3), DVec(0.0, 0.6), DVec(-0.1, 0.4)), Nil),
    PolygonShape(List(DVec(0.0, 0.3), DVec(0.1, -0.1), DVec(0.1, 0.5), DVec(0.0, 0.5)), Nil),
    PolygonShape(List(DVec(-0.1, 0.4), DVec(0.0, 0.6), DVec(0.0, 0.5), DVec(0.1, 0.5), DVec(0.1, 0.7), DVec(-0.1, 0.8)), Nil),
    PolygonShape(List(DVec(-0.1, 0.8), DVec(0.1, 0.7), DVec(0.1, 1.0), DVec(-0.1, 1.1)), Nil),
    PolygonShape(List(DVec(-0.1, 1.1), DVec(0.1, 1.0), DVec(0.1, 1.2), DVec(-0.1, 1.3000001)), Nil),
    PolygonShape(List(DVec(-0.1, 1.3000001), DVec(0.1, 1.2), DVec(0.1, 1.5), DVec(0.0, 1.5)), Nil),
    PolygonShape(List(DVec(-0.1, 1.3000001), DVec(0.0, 1.5), DVec(-0.1, 1.6)), Nil),
    PolygonShape(List(DVec(-0.1, 1.6), DVec(0.0, 1.5), DVec(0.1, 1.5), DVec(0.1, 1.7)), Nil),
    PolygonShape(List(DVec(-0.1, 1.6), DVec(0.1, 1.7), DVec(-0.1, 1.7)), Nil),
    PolygonShape(List(DVec(-0.1, 1.8000001), DVec(0.0, 1.7), DVec(0.0, 2.1000001)), Nil),
    PolygonShape(List(DVec(-0.1, 1.8000001), DVec(-0.1, 1.7), DVec(0.0, 1.7)), Nil),
    PolygonShape(List(DVec(0.0, 1.7), DVec(0.1, 1.7), DVec(0.1, 1.8000001), DVec(0.0, 2.1000001)), Nil)
  )

  val docking_points = Nil

  val two = new Engine("2", position = DVec(0.0, -20.0)*0.1, force_dir = DVec(0.0, 1.0), max_power = 200000, default_power_percent = 1, fuel_consumption_per_sec_at_full_power = 4, this)

  val engines = List(two)

  val engines_mapping = Map(
    KEY_NUMPAD2 -> two
  )

  def preserveVelocity(vel:DVec) {}
  def preserveAngularVelocity(ang_vel_deg: Double) {}

  override protected def drawShip(): Unit = {
    if(!drawMapMode) {
      if(pilotIsAlive) {
        openglLocalTransform {
          openglMove(coord - base)

          /*val pa = (earth.coord - coord).n*(coord.dist(earth.coord) - earth.radius) + (earth.coord - coord).p*70000
          val pb = (earth.coord - coord).n*(coord.dist(earth.coord) - earth.radius) + (earth.coord - coord).p*(-70000)
          drawLine(pa, pb, WHITE)*/

          openglRotateDeg(rotation)

          // ниже алгоритм рисует линии корпуса корабля темносерым или белым в зависимости, в тени эта линия или нет
          /*val cur_draw_lines =  curDrawLines
          val cur_sun_coord = sun.coord
          draw_points.zipWithIndex.sliding(2).foreach {
            case List((p1, p1idx), (p2, p2idx)) =>
              val curP1 = coord + p1.rotateDeg(rotation)
              val curP1InShadow = inShadowOfPlanet(curP1).nonEmpty || cur_draw_lines.filterNot(x => x(0)._1 == curP1 || x(1)._1 == curP1).exists(x => {
                val res = areLinesIntersect(curP1, cur_sun_coord, x(0)._1, x(1)._1)
                res
              })
              val curP2 = coord + p2.rotateDeg(rotation)
              val curP2InShadow = inShadowOfPlanet(curP1).nonEmpty || cur_draw_lines.filterNot(x => x(0)._1 == curP2 || x(1)._1 == curP2).exists(x => {
                areLinesIntersect(curP2, cur_sun_coord, x(0)._1, x(1)._1)
              })
              if(!curP1InShadow && !curP2InShadow) {
                drawLine(p1, p2, colorIfAliveOrRed(WHITE))
              } else {
                drawLine(p1, p2, colorIfAliveOrRed(DARK_GRAY))
              }
              /*print(s"$p1idx", p1.toVec, color = WHITE, size = (max_font_size / globalScale).toFloat)
              print(s"$p2idx", p2.toVec, color = WHITE, size = (max_font_size / globalScale).toFloat)*/
          }*/

          drawSlidingLines(draw_points, colorIfPlayerAliveOrRed(WHITE))

          engines.foreach {
            case e => drawEngine(e)
          }
        }
      } else {
        if(shipIsCrashed) {
          ship_parts.foreach(mbs => {
            val mbs_points = mbs.shape.asInstanceOf[PolygonShape].points
            openglLocalTransform {
              openglMove(mbs.coord - base)
              /*mbs.contacts.foreach(x => {
              if(x.a.index.contains("part") && x.b.index.contains("part")) {
                drawFilledCircle(x.contact_point - mbs.coord, 0.3, YELLOW)
                drawLine(x.contact_point - mbs.coord, x.contact_point - mbs.coord + x.normal.n, YELLOW)
                drawCircle(x.contact_point - mbs.coord, x.separation, YELLOW)
              }
            })*/
              openglRotateDeg(mbs.ang)
              drawSlidingLines(mbs_points :+ mbs_points.head, colorIfPlayerAliveOrRed(RED))
            }
          })
        } else {
          openglLocalTransform {
            openglMove(coord - base)
            openglRotateDeg(rotation)
            drawSlidingLines(draw_points, colorIfPlayerAliveOrRed(WHITE))
          }
        }
      }
    }
  }

  override def afterStep(time_msec:Long): Unit = {
    super.afterStep(time_msec)
    println(msecOrKmsec((linearVelocity - ship.linearVelocity).norma))
  }
}
