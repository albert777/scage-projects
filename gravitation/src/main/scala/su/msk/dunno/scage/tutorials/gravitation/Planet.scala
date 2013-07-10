package su.msk.dunno.scage.tutorials.gravitation

import Orbita._
import com.github.dunnololda.scage.ScageLib._

class Planet(init_velocity:Vec, mass:Float, radius:Int, val planet_color:ScageColor)
extends MaterialPoint(init_velocity = init_velocity, mass = mass, radius = radius) {
  //private val PLANET = image("planet1.png", 10, 10, 0, 0, 350, 350)
  render {
    //drawDisplayList(PLANET, coord)
    drawFilledCircle(location, radius, planet_color)

    /*GL11.glPushMatrix();
      GL11.glTranslatef(coord.x, coord.y, 0.0f)
      GL11.glScalef(1.5f/scale, 1.5f/scale, 1)
      print(mass, 0, 0, YELLOW)
      print(coord, 0, -15, YELLOW)
      print(_velocity, 0, -30, YELLOW)
    GL11.glPopMatrix()*/

    drawLine(location, location+_velocity)
  }
}