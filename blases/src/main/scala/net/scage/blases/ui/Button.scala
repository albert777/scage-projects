package net.scage.blases.ui

import net.scage.handlers.Renderer
import net.scage.ScageLib._
import net.scage.blases.Relatives._
import net.scage.{Screen, Scage}
import net.scage.support.{ScageColor, Vec}
import net.scage.handlers.controller2.MultiController
import net.scage.blases.IntersectablePolygon

class Button(message: => String,
             var coord: Vec,
             width: Int,
             screen: Screen with MultiController,
             onBtnPressed: => Any,
             var color:ScageColor = BLACK,
             var visible:Boolean = true) extends IntersectablePolygon {
  val intersectableVertices = List(rVec(coord) + Vec(-5, 20),
      rVec(coord) + Vec(-5 + width, 20),
      rVec(coord) + Vec(-5 + width, -10),
      rVec(coord) + Vec(-5, -10))

  screen.interface {
    if(visible) {
      print(message, rVec(coord), color)
    }
  }

  screen.leftMouseNoPause(onBtnDown = {m => if(visible && containsCoord(m)) onBtnPressed})
}
