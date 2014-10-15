package com.github.dunnololda.scageprojects.drivers

import com.github.dunnololda.scage.ScageLib._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object RoadsCreator extends ScageScreenApp("Roads Creator", 800, 600) {
  val ROADS_MODE = 1
  val CROSSROADS_MODE = 2
  val NETWORK_MODE = 3
  
  private var mode = ROADS_MODE
  
  private var _center = windowCenter
  private var _road_start:Option[Vec] = None

  private val road_elements = ArrayBuffer[RoadElement]()
  private var selected_road_element = -1

  private val road_network = mutable.HashMap[Vec, ArrayBuffer[Vec]]()
  private var selected_network_element:Option[Vec] = None

  key(KEY_1, onKeyDown = mode = ROADS_MODE)
  key(KEY_2, onKeyDown = mode = CROSSROADS_MODE)
  key(KEY_3, onKeyDown = mode = NETWORK_MODE)

  key(KEY_W, 100, onKeyDown = _center += Vec(0, 5))
  key(KEY_A, 100, onKeyDown = _center -= Vec(5, 0))
  key(KEY_S, 100, onKeyDown = _center -= Vec(0, 5))
  key(KEY_D, 100, onKeyDown = _center += Vec(5, 0))

  key(KEY_LEFT, onKeyDown = {
    if(road_elements.nonEmpty) {
      selected_road_element += 1
      if(selected_road_element >= road_elements.length) selected_road_element = -1
    } else selected_road_element = -1
  })
  key(KEY_RIGHT, onKeyDown = {
    if(road_elements.nonEmpty) {
      selected_road_element -= 1
      if(selected_road_element <= -2) selected_road_element = road_elements.length-1
    } else selected_road_element = -1
  })

  key(KEY_Q, onKeyDown = {if(keyPressed(KEY_LCONTROL)) stopApp()})

  leftMouse(onBtnDown = m => {
    mode match {
      case ROADS_MODE =>
        _road_start match {
          case Some(from) =>
            road_elements += TwoLinesEachSide(from, nearestPoint(scaledCoord(m)))
            _road_start = None
          case None =>
            _road_start = Some(nearestPoint(scaledCoord(m)))
        }
      case CROSSROADS_MODE =>
        val pos = nearestPoint(scaledCoord(m))
        val four_ends = Set(
          pos + Vec( 30,   0),
          pos + Vec(-30,   0),
          pos + Vec(  0,  30),
          pos + Vec(  0, -30)
        )
        val roads = road_elements.filter {
          case TwoLinesEachSide(from, to) => four_ends.contains(from) || four_ends.contains(to)
          case _ => false
        }.toList
        road_elements += CrossRoad(pos, roads)
      case NETWORK_MODE =>
        val pos = nearestNetworkPoint(scaledCoord(m))
        if(!road_network.contains(pos)) {
          road_network(pos) = ArrayBuffer[Vec]()
        }
        selected_network_element match {
          case Some(elem) => road_network.getOrElseUpdate(elem, ArrayBuffer[Vec]()) += pos
          case None =>
        }
        selected_network_element = Some(pos)
      case _ =>
    }
  })
  rightMouse(onBtnDown = m => {
    _road_start match {
      case Some(from) =>
        _road_start = None
      case None =>
        mode match {
          case ROADS_MODE | CROSSROADS_MODE =>
            if(selected_road_element != -1 && road_elements.nonEmpty && selected_road_element >= 0 && selected_road_element < road_elements.length) {
              road_elements.remove(selected_road_element)
              if(selected_road_element >= road_elements.length) selected_road_element = -1
            }
          case NETWORK_MODE =>
            selected_network_element match {
              case Some(elem) =>
                road_network -= elem
                road_network.foreach(kv => if(kv._2.contains(elem)) kv._2 -= elem)
                selected_network_element = None
              case None =>
            }
          case _ =>
        }
    }
  })

  mouseWheelUp(onWheelUp = m => {
    if(globalScale < 1) globalScale += 0.1f
    else if(globalScale < 4) globalScale += 1
  })
  mouseWheelDown(onWheelDown = m => {
    if(globalScale > 1) globalScale -= 1
    else if(globalScale > 0.1f) globalScale -= 0.1f
  })

  center = _center

  render {
    (-5000 to 5000 by 15).foreach(x => drawLine(Vec(x, -5000), Vec(x, 5000), DARK_GRAY))
    (-5000 to 5000 by 15).foreach(y => drawLine(Vec(-5000, y), Vec(5000, y), DARK_GRAY))
    road_elements.zipWithIndex.foreach {
      case (road, idx) =>
        if(idx == selected_road_element) {
          road.drawSelf(RED)
        } else {
          road.drawSelf(WHITE)
        }
    }
    if(_road_start.nonEmpty) {
      drawLine(_road_start.get, nearestPoint(scaledCoord(mouseCoord)), WHITE)
    }
    road_network.foreach {
      case (elem, connected_to) =>
        val color = if(selected_network_element.exists(se => elem == se)) RED else WHITE
        drawRectCentered(elem, 5, 5, color)
        connected_to.foreach {
          case to =>
            drawLine(elem, to, color)
            val v1 = (elem - to).n.rotateDeg(15)*3
            val v2 = (elem - to).n.rotateDeg(-15)*3
            drawLine(to, to+v1, color)
            drawLine(to, to+v2, color)
        }
    }
  }
  
  interface {
    mode match {
      case ROADS_MODE =>
        print("Mode: Roads", 20, 20, WHITE)
      case CROSSROADS_MODE =>
        print("Mode: Crossroads", 20, 20, WHITE)
      case NETWORK_MODE =>
        print("Mode: Network", 20, 20, WHITE)
      case _ =>
    }
  }

  def nearestPoint(v:Vec):Vec = {
    Vec(v.ix/15*15-5, v.iy/15*15-5)
  }

  def nearestNetworkPoint(v:Vec):Vec = {
    Vec(v.ix/15*15-5+7.5f, v.iy/15*15-5+7.5f)
  }
}

sealed trait RoadElement {
  def drawSelf(color:ScageColor)
}
case class TwoLinesEachSide(from:Vec, to:Vec) extends RoadElement {
  override def drawSelf(color: ScageColor) {
    val n = (to - from).n
    val n_turn_1 = n.rotateDeg(90)
    val n_turn_2 = n.rotateDeg(-90)

    // двойная сплошная по центру
    drawLine(from+n_turn_1, to+n_turn_1, color)
    drawLine(from+n_turn_2, to+n_turn_2, color)

    // пунктирные линии
    drawDashedLine(from+n_turn_1*15, to+n_turn_1*15, 5, color)
    drawDashedLine(from+n_turn_2*15, to+n_turn_2*15, 5, color)

    // границы дороги
    drawLine(from+n_turn_1*30, to+n_turn_1*30, color)
    drawLine(from+n_turn_2*30, to+n_turn_2*30, color)
  }
}

case class CrossRoad(pos:Vec, roads:List[RoadElement]) extends RoadElement {
  val up_end    = pos+Vec(  0,  30)
  val down_end  = pos+Vec(  0, -30)
  val right_end = pos+Vec( 30,   0)
  val left_end  = pos+Vec(-30,   0)
  override def drawSelf(color: ScageColor): Unit = {
    roads.length match {
      case 2 =>
      case 3 =>
      case 4 =>
      case _ =>
    }
    drawRectCentered(pos, 60, 60, color)
  }
}