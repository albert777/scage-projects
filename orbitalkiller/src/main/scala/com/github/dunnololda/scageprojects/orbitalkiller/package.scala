package com.github.dunnololda.scageprojects

import com.github.dunnololda.scage.ScageLib._
import com.github.dunnololda.scage.ScageLib.Vec
import net.phys2d.raw.collide._
import net.phys2d.raw.Body
import net.phys2d.raw.shapes.{Circle, Box, Line}
import scala.Some
import scala.collection.mutable

package object orbitalkiller {
  val G:Float = 20
  val base_dt = 0.01f // 1/60 секунды

  case class M3(row1:(Float, Float, Float), row2:(Float, Float, Float), row3:(Float, Float, Float)) {
    val (a11, a12, a13) = row1
    val (a21, a22, a23) = row2
    val (a31, a32, a33) = row3

    val (a, b, c) = row1
    val (d, e, f) = row2
    val (g, h, k) = row3

    val rows = List(List(a11, a12, a13),
                    List(a21, a22, a23),
                    List(a31, a32, a33))

    val columns = List(List(a11, a21, a31),
                       List(a12, a22, a32),
                       List(a13, a23, a33))
    val det = {
      a11*a22*a33 - a11*a23*a32 - a12*a21*a33 + a12*a23*a31 + a13*a21*a32 - a13*a22*a31
    }

    val t = M3(
      (a11, a21, a31),
      (a12, a22, a32),
      (a13, a23, a33)
    )

    val inv:Option[M3] = {
      if(det == 0) None
      else {
        Some(M3(
          row1 = ((e*k - f*h)/det, -(b*k - c*h)/det, (b*f - c*e)/det),
          row2 = (-(d*k - f*g)/det, (a*k - c*g)/det, -(a*f - c*d)/det),
          row3 = ((d*h - e*g)/det, -(a*h - b*g)/det, (a*e - b*d)/det)
        ))
      }
    }

    def *(m:M3):M3 = {
      val p = for {
        (row, m_row) <- rows.zip(m.columns)
      } yield {
        for {
          (a, b) <- row.zip(m_row)
        } yield a*b
      }
      M3(
        row1 = (p(0)(0), p(0)(1), p(0)(2)),
        row2 = (p(1)(0), p(1)(1), p(1)(2)),
        row3 = (p(2)(0), p(2)(1), p(2)(2))
      )
    }

    def *(v:(Float, Float, Float)):(Float, Float, Float) = {
      val (v1, v2, v3) = v
      (a11*v1 + a12*v2 + a13*v3,
       a21*v1 + a22*v2 + a23*v3,
       a31*v1 + a32*v2 + a33*v3)
    }

    def *(x:Float):M3 = {
      M3(
        (a11*x, a21*x, a31*x),
        (a12*x, a22*x, a32*x),
        (a13*x, a23*x, a33*x)
      )
    }
  }

  case class AABB(center:Vec, width:Float, height:Float) {
    val half_width = width/2
    val half_height = height/2
  }

  def aabbCollision(b1:AABB, b2:AABB):Boolean = {
    val d1 = math.abs(b1.center.x - b2.center.x)
    d1 < b1.half_width + b2.half_width && {
      val d2 = math.abs(b1.center.y - b2.center.y)
      d2 < b1.half_height + b2.half_height
    }
  }

  sealed trait Shape

  case class CircleShape(center:Vec, radius:Float) extends Shape {
    def phys2dBody = {
      val b1 = new Body(new Circle(radius), 1f)
      b1.setPosition(center.x, center.y)
      b1
    }
  }

  case class LineShape(from:Vec, to:Vec) extends Shape {
    val center = from + (to - from)/2f

    /**
     * Get the closest point on the line to a given point
     */
    def closestPoint(point:Vec):Vec = {
      val vec = to - from
      val loc = point - from
      val v = vec.n
      val proj = loc.project(v)
      if(proj.norma2 > vec.norma2) to
      else {
        val proj2 = proj + from
        val other = proj2 - to
        if(other.norma2 > vec.norma2) from
        else proj2
      }
    }

    def distanceSquared(point:Vec):Float = closestPoint(point).dist2(point)

    val vec = to - from
    def dx = vec.x
    def dy = vec.y

    def phys2dBody = {
      val b1 = new Body(new Line(vec.x, vec.y), 1f)
      b1.setPosition(from.x, from.y)
      b1
    }
  }

  case class BoxShape(center:Vec, width:Float, height:Float, rotation:Float) extends Shape {
    val w = width
    val h = height

    val one = center + Vec(-width/2, height/2).rotateDeg(rotation)
    val two = center + Vec(width/2, height/2).rotateDeg(rotation)
    val three = center + Vec(width/2, -height/2).rotateDeg(rotation)
    val four = center + Vec(-width/2, -height/2).rotateDeg(rotation)
    val points = List(one, two, three, four)
    val lines = List(LineShape(one, two), LineShape(two, three), LineShape(three, four), LineShape(four, one))

    def phys2dBody = {
      val b2 = new Body(new Box(w, h), 1f)
      b2.setPosition(center.x, center.y)
      b2.setRotation(rotation/180f*math.Pi.toFloat)
      b2
    }
  }

  case class GeometricContactData(contact_point:Vec, normal:Vec)
  case class Contact(body1:BodyState, body2:BodyState, contact_point:Vec, normal:Vec)

  def maybeCollision(body1:BodyState, body2:BodyState):Option[Contact] = {
    body1.currentShape match {
      case c1:CircleShape =>
        body2.currentShape match {
          case c2:CircleShape =>
            circleCircleCollision(c1, c2).map(gcd => Contact(body1, body2, gcd.contact_point, gcd.normal))
          case l2:LineShape =>
            lineCircleCollision(l2, c1).map(gcd => Contact(body1, body2, gcd.contact_point, -gcd.normal))
          case b2:BoxShape =>
            circleBoxCollision(c1, b2).map(gcd => Contact(body1, body2, gcd.contact_point, gcd.normal))
          case _ => None
        }
      case l1:LineShape =>
        body2.currentShape match {
          case c2:CircleShape =>
            lineCircleCollision(l1, c2).map(gcd => Contact(body2, body1, gcd.contact_point, gcd.normal))
          case b2:BoxShape =>
            lineBoxCollision(l1, b2).map(gcd => Contact(body1, body2, gcd.contact_point, gcd.normal))
          case _ => None
        }
      case b1:BoxShape =>
        body2.currentShape match {
          case c2:CircleShape =>
            boxCircleCollision(b1, c2).map(gcd => Contact(body1, body2, gcd.contact_point, gcd.normal))
          case l2:LineShape =>
            lineBoxCollision(l2, b1).map(gcd => Contact(body1, body2, gcd.contact_point, -gcd.normal))
          case _ => None
        }
      case _ => None
    }
  }

  private val contacts = Array(new net.phys2d.raw.Contact, new net.phys2d.raw.Contact)
  private val circle_circle_collider = new CircleCircleCollider
  private val line_circle_collider = new LineCircleCollider
  private val circle_box_collider = CircleBoxCollider.createCircleBoxCollider()
  private val box_circle_collider = new BoxCircleCollider
  private val line_box_collider = new LineBoxCollider

  def circleCircleCollision(c1:CircleShape, c2:CircleShape):Option[GeometricContactData] = {
    val num_contacts = circle_circle_collider.collide(contacts, c1.phys2dBody, c2.phys2dBody)
    if(num_contacts == 0) None
    else {
      val contact_point = contacts(0).getPosition.toVec
      val normal = contacts(0).getNormal.toVec
      Some(GeometricContactData(contact_point, normal))
    }
  }

  def lineCircleCollision(l:LineShape, c:CircleShape):Option[GeometricContactData] = {
    val num_contacts = line_circle_collider.collide(contacts, l.phys2dBody, c.phys2dBody)
    if(num_contacts == 0) None
    else {
      val contact_point = contacts(0).getPosition.toVec
      val normal = contacts(0).getNormal.toVec
      Some(GeometricContactData(contact_point, normal))
    }
  }

  def circleBoxCollision(c:CircleShape, b:BoxShape):Option[GeometricContactData] = {
    val num_contacts = circle_box_collider.collide(contacts, c.phys2dBody, b.phys2dBody)
    if(num_contacts == 0) None
    else {
      val contact_point = contacts(0).getPosition.toVec
      val normal = contacts(0).getNormal.toVec
      Some(GeometricContactData(contact_point, normal))
    }
  }

  def boxCircleCollision(b:BoxShape, c:CircleShape):Option[GeometricContactData] = {
    val num_contacts = box_circle_collider.collide(contacts, b.phys2dBody, c.phys2dBody)
    if(num_contacts == 0) None
    else {
      val contact_point = contacts(0).getPosition.toVec
      val normal = contacts(0).getNormal.toVec
      Some(GeometricContactData(contact_point, normal))
    }
  }

  def lineBoxCollision(l:LineShape, b:BoxShape):Option[GeometricContactData] = {
    val num_contacts = line_box_collider.collide(contacts, l.phys2dBody, b.phys2dBody)
    if(num_contacts == 0) None
    else {
      num_contacts match {
        case 1 =>
          val contact_point = contacts(0).getPosition.toVec
          val normal = contacts(0).getNormal.toVec
          Some(GeometricContactData(contact_point, normal))
        case 2 =>
          val contact_point = (contacts(0).getPosition.toVec + contacts(1).getPosition.toVec)/2
          val normal = contacts(0).getNormal.toVec
          Some(GeometricContactData(contact_point, normal))
        case _ => None
      }
    }
  }

  case class BodyState(index:String,
                       mass:Float,
                       I:Float,
                       force:Vec,
                       acc:Vec,
                       vel:Vec,
                       coord:Vec,
                       torque:Float,
                       ang_acc:Float,
                       ang_vel:Float,
                       ang:Float,
                       shape: (Vec, Float) => Shape,
                       is_static:Boolean) {
    def currentShape = shape(coord, ang)
  }

  def systemEvolutionFrom(dt: => Float,
                          elasticity:Float, // elasticity or restitution: 0 - inelastic, 1 - perfectly elastic, (va2 - vb2) = -e*(va1 - vb1)
                          force: (Long, BodyState, List[BodyState]) => Vec,
                          torque: (Long, BodyState, List[BodyState]) => Float)
                         (current_state:(Long, List[BodyState])):Stream[(Long, List[BodyState])] = {
    val (time, bodies) = current_state

    val next_time = time + (dt/base_dt).toLong

    val collision_data = mutable.HashMap[String, (Vec, Float)]()
    for {
      b1 <- bodies
      if !b1.is_static && !collision_data.contains(b1.index)
    } {
      val other_bodies = bodies.filterNot(_ == b1)
      val collisions = other_bodies.flatMap {
        case b2 => maybeCollision(b1, b2)
      }
      collisions.foreach {
        case Contact(_ ,b2, contact_point, normal) =>
          val rap = contact_point - b1.coord
          val n = normal.n
          val dv = b1.vel - b2.vel
          val relative_movement = dv*n
          if(relative_movement < 0) {  // If the objects are moving away from each other we dont need to apply an impulse
            collision_data += (b1.index -> (b1.vel, b1.ang_vel))
            collision_data += (b2.index -> (b2.vel, b2.ang_vel))
          } else {
            val ma = b1.mass
            val ia = b1.I

            val va1 = b1.vel
            val wa1 = b1.ang_vel/180f*math.Pi.toFloat // ang_vel in degrees, wa1 must be in radians
            val mb = b2.mass

            val e = elasticity
            if(mb == -1) {  // infinite mass
              val vap1 = va1 + (wa1 * rap.perpendicular)
              val j = -(1+e)*(vap1*n)/(1f/ma + (rap*/n)*(rap*/n)/ia)

              val va2 = va1 + (j * n)/ma
              val wa2 = (wa1 + (rap*/(j * n))/ia)/math.Pi.toFloat*180f  // must be in degrees

              collision_data += (b1.index -> (va2, wa2))
            } else {
              val ib = b2.I
              val rbp = contact_point - b2.coord
              val vb1 = b2.vel
              val wb1 = b2.ang_vel/180f*math.Pi.toFloat  // ang_vel in degrees, wb1 must be in radians
              val vab1 = va1 + (wa1 * rap.perpendicular) - vb1 - (wb1 * rbp.perpendicular)
              val j = -(1+e) * vab1*n/(1f/ma + 1f/mb + (rap*/n)*(rap*/n)/ia + (rbp*/n)*(rbp*/n)/ib)

              val va2 = va1 + (j * n)/ma
              val wa2 = (wa1 + (rap*/(j * n))/ia)/math.Pi.toFloat*180f  // must be in degrees
              collision_data += (b1.index -> (va2, wa2))

              val vb2 = vb1 - (j * n)/mb
              val wb2 = (wb1 - (rbp*/(j * n))/ib)/math.Pi.toFloat*180f  // must be in degrees
              collision_data += (b2.index -> (vb2, wb2))
            }
          }
      }
    }

    val next_bodies = bodies.map { case b1 =>
      if(b1.is_static) b1
      else {
        val other_bodies = bodies.filterNot(_ == b1)

        val next_force = force(time, b1, other_bodies)
        val next_acc = next_force / b1.mass
        val next_vel = collision_data.get(b1.index).map(_._1).getOrElse(b1.vel + next_acc*dt)
        val next_coord = b1.coord + next_vel*dt

        val next_torque = torque(time, b1, other_bodies)
        val next_ang_acc = (next_torque / b1.I)/math.Pi.toFloat*180f  // in degrees
        val next_ang_vel = collision_data.get(b1.index).map(_._2).getOrElse(b1.ang_vel + next_ang_acc*dt)
        val next_ang = (b1.ang + next_ang_vel*dt) % 360f

        b1.copy(
          force = next_force,
          acc = next_acc,
          vel = next_vel,
          coord = next_coord,
          torque = next_torque,
          ang_acc= next_ang_acc,
          ang_vel = next_ang_vel,
          ang = next_ang
        )
      }
    }

    val pewpew = (next_time, next_bodies)
    pewpew #:: systemEvolutionFrom(dt, elasticity, force, torque)(pewpew)
  }

  def gravityForce(body1_coord:Vec, body1_mass:Float, body2_coord:Vec, body2_mass:Float):Vec = {
    (body1_coord - body2_coord).n*G*body1_mass*body2_mass/body1_coord.dist2(body2_coord)
  }

  def satelliteSpeed(body_coord:Vec, planet_coord:Vec, planet_mass:Float):Vec = {
    val from_planet_to_body = body_coord - planet_coord
    from_planet_to_body.n.rotateDeg(90)*math.sqrt(G*planet_mass/from_planet_to_body.norma)
  }

  /**
   *
   * @param force - вектор силы
   * @param force_position_from_mass_center - точка приложения силы относительно центра масс
   * @param sin_angle - синус угла между вектором от центра масс до точки приложения силы и вектором силы
   * @return
   */
  def torque(force:Vec, force_position_from_mass_center:Vec, sin_angle:Float):Float = {
    force.norma*force_position_from_mass_center.norma*sin_angle
  }

  def torque(force:Vec, force_position_from_mass_center:Vec):Float = {
    val xf        = force_position_from_mass_center*force.rotateDeg(90).n
    val sin_angle = xf/force_position_from_mass_center.norma
    torque(force, force_position_from_mass_center, sin_angle)
  }

  def torque(force:Vec, force_position:Vec, center:Vec):Float = {
    val force_position_from_mass_center = force_position - center
    val xf                              = force_position_from_mass_center*force.rotateDeg(90).n
    val sin_angle                       = xf/force_position_from_mass_center.norma
    torque(force, force_position_from_mass_center, sin_angle)
  }

  def maxOption[T](l:Seq[T])(implicit o:Ordering[T]):Option[T] = if(l.isEmpty) None else Some(l.max(o))
}
