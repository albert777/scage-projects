package net.scage.blases

import net.scage.ScageLib._
import net.scage.support.{State, Vec}
import net.scage.{Scage, ScageScreenApp}
import net.scage.support.physics.{Physical, ScagePhysics}
import net.scage.support.tracer3.{TraceTrait, Trace, CoordTracer}
import net.scage.support.physics.objects.{StaticLine, DynaBall, StaticPolygon}
import collection.mutable.ArrayBuffer

object Blases extends ScageScreenApp("Blases") {
  val physics = ScagePhysics()
  val tracer = CoordTracer.create[Blase](solid_edges = false)

  val right_edge = new StaticLine(Vec(0,0), Vec(0, window_height))
  val up_edge    = new StaticLine(Vec(0, window_height), Vec(window_width, window_height))
  val left_edge  = new StaticLine(Vec(window_width, window_height), Vec(window_width, 0))
  val down_edge  = new StaticLine(Vec(window_width, 0), Vec(0, 0))

  physics.addPhysicals(right_edge, up_edge, left_edge, down_edge)

  private var current_level = 0
  private val levels = ArrayBuffer[Level](Level1, Level2, Level3)

  private var score = 0
  private[blases] var score_for_level = 10000
  action(1000) {
    if(is_game_started && current_game_status == IN_PLAY) score_for_level -= 50
  }
  
  action {
    physics.step()
    checkGameStatus()
    if(current_game_status != IN_PLAY) {
      score += score_for_level
      pause()
    }
  }
  
  val IN_PLAY = 0
  val WIN     = 1
  val LOSE    = 2
  private var current_game_status = IN_PLAY
  def checkGameStatus() {
    if(is_game_started && tracer.tracesList.isEmpty) current_game_status = LOSE
    if(levels(current_level).isWin) current_game_status = WIN
  }
  
  interface {
    print("Score: "+score,  20, window_height-20, WHITE)
    print(score_for_level,  20, window_height-40, WHITE)
    if(onPause) {
      current_game_status match {
        case WIN => 
          if(current_level < levels.length-1) {
            print("You win! Score for the level:\n"+score_for_level+"\n\nOverall score:\n"+score+"\n\nWanna go to the next level? (Y/N)", windowCenter + Vec(-60, 60), RED)
          } else print("You beat the game!!! Final score:\n"+score+"\n\nWanna play again from the beginning? (Y/N)", windowCenter + Vec(-60, 60), RED)
        case LOSE => print("You lose. Final score:\n"+score+"\n\nWanna play the last level again? (Y/N)", windowCenter + Vec(-60, 60), RED)
        case _ =>
      }
    }
  }

  keyNoPause(KEY_Y, onKeyDown = if(onPause && current_game_status != IN_PLAY) {
    current_game_status match {
      case WIN =>
        if(current_level < levels.length-1) {
          current_level += 1
        } else {
          score = 0
          current_level = 0
        }
      case LOSE =>
        score -= score_for_level
      case _ =>
    }
    restart()
    pauseOff()
  })
  keyNoPause(KEY_N, onKeyDown = if(onPause && (current_game_status != IN_PLAY)) Scage.stopApp())
  
  key(KEY_SPACE, onKeyDown = if(selected_blase != no_selection) selected_blase.velocity = Vec.zero)
  
  render {
    if(!is_game_started) drawLine(levels(current_level).startCoord, (mouseCoord - levels(current_level).startCoord).n*40 + levels(current_level).startCoord, RED)
    else if(selected_blase.id != no_selection.id) drawLine(selected_blase.location, (mouseCoord - selected_blase.location).n*40 + selected_blase.location, RED)
  }

  private val no_selection = new DynaBall(Vec.zero, radius = 20) with TraceTrait {
    def state = State()
    type ChangerType = Trace
    def changeState(changer:Trace, s:State) {}
  }
  private[blases] var selected_blase = no_selection

  leftMouse(onBtnDown = {mouse_coord =>
    if(!is_game_started) {
      val new_blase_position = (mouse_coord - levels(current_level).startCoord).n*50 + levels(current_level).startCoord
      val new_blase = new Blase(new_blase_position)
      new_blase.velocity = (mouse_coord - levels(current_level).startCoord).n*90
      is_game_started = true
    } else if(selected_blase.id == no_selection.id) {
      val blases = tracer.tracesNearCoord(mouse_coord, -1 to 1, condition = {blase => blase.location.dist(mouse_coord) <= 20})
      if(!blases.isEmpty) {
        selected_blase = blases.head
      }
    } else {
      val new_blase_position = (mouse_coord - selected_blase.location).n*50 + selected_blase.location
      val new_blase = new Blase(new_blase_position)
      new_blase.velocity = (mouse_coord - selected_blase.location).n*90
      selected_blase = no_selection
    }
  })

  rightMouse(onBtnDown = {mouse_coord =>
    /*selected_blase = no_selection*/
    if(tracer.tracesList.length > 1) {
      val blases = tracer.tracesNearCoord(mouse_coord, -1 to 1, condition = {blase => blase.location.dist(mouse_coord) <= 20})
      if(!blases.isEmpty) blases.head.burst()
    }
  })

  private var is_game_started = false
  init {
    levels(current_level).load()
    is_game_started = false
    current_game_status = IN_PLAY
    score_for_level = 10000
  }
  
  clear {
    tracer.tracesList.foreach(_.burst())
  }
}

import Blases._

class Blase(init_coord:Vec) extends DynaBall(init_coord, radius = 20) with Trace {
  physics.addPhysical(this)
  tracer.addTrace(coord, this)
  body.setUserData(this)

  def state = State()
  def changeState(changer:Trace, s:State) {}

  private val action_id = action {
    tracer.updateLocation(this, coord)
    coord = this.location
    if(!touchingPoints.isEmpty) velocity = Vec.zero
  }

  def burst() {
    delOperations(action_id, render_id)
    tracer.removeTraces(this)
    physics.removePhysicals(this)
  }
  
  private val render_id = render {
    val color = if(id == selected_blase.id) RED else WHITE
    drawCircle(coord, radius, color)
  }
}

class BurstPolygon(vertices:Vec*) extends StaticPolygon(vertices:_*) {
  physics.addPhysical(this)

  private val action_id = action {
    touchingBodies.foreach{body => {
      val user_data = body.getUserData
      if(user_data != null && user_data.isInstanceOf[Blase]) {
        user_data.asInstanceOf[Blase].burst()
        score_for_level -= 100
      }
    }}
  }

  private val render_id = render {
    drawPolygon(points, RED)
  }

  def remove() {
    physics.removePhysicals(this)
    delOperations(action_id, render_id)
  }
}

class SpeedPolygon(vertices:List[Vec], direction:Vec) {
  private val dir = direction.n*90
  private val (min_x, max_x, min_y, max_y) = vertices.map(vertice => tracer.outsidePoint(tracer.point(vertice))).foldLeft((0, 0, 0, 0)) {
    case ((current_min_x, current_max_x, current_min_y, current_max_y), vertice) =>
      val new_min_x = math.min(current_min_x, vertice.ix)
      val new_max_x = math.max(current_max_x, vertice.ix)
      val new_min_y = math.min(current_min_y, vertice.iy)
      val new_max_y = math.max(current_max_y, vertice.iy)
      (new_min_x, new_max_x, new_min_y, new_max_y)
  }

  private val speeded_blases_ids = ArrayBuffer[Int]()
  private val action_id = action {
    tracer.tracesInPointRange(min_x to max_x, min_y to max_y).filter(blase => containsCoord(blase.location)).foreach(blase => {
      if(!speeded_blases_ids.contains(blase.id)) {
        blase.velocity = dir
        speeded_blases_ids += blase.id
      }
    })
  }

  private val render_id = render {
    drawPolygon(vertices, BLUE)
  }

  private val vertices_zipped = if(vertices.length >= 2) {
    val vertices_shift = vertices.last :: vertices.init
    vertices_shift.zip(vertices)
  } else List[(Vec, Vec)]()
  def containsCoord(coord:Vec):Boolean = {
    def _areLinesIntersect(a:Vec, b:Vec, c:Vec, d:Vec):Boolean = {
      val common = (b.x - a.x)*(d.y - c.y) - (b.y - a.y)*(d.x - c.x)
      if (common == 0) false
      else {
        val rH = (a.y - c.y)*(d.x - c.x) - (a.x - c.x)*(d.y - c.y)
        val sH = (a.y - c.y)*(b.x - a.x) - (a.x - c.x)*(b.y - a.y)

        val r = rH / common
        val s = sH / common

        if(r >= 0 && r <= 1 && s >= 0 && s <= 1) true
        else false
      }
    }
    if(vertices.length < 2) false
    else {
      val a = coord
      val b = Vec(Integer.MAX_VALUE, coord.y)
      val intersections = vertices_zipped.foldLeft(0) {
        case (result, (c, d)) => if(_areLinesIntersect(a, b, c, d)) result + 1 else result
      }
      intersections % 2 != 0
    }
  }

  def remove() {
    delOperations(action_id, render_id)
  }
}

trait Level {
  def load()
  def startCoord:Vec
  def finishCoord:Vec

  def drawStartFinish() {
    drawCircle(startCoord, 20, RED)
    print("Start", (startCoord - Vec(20, 40)), RED)

    drawCircle(finishCoord, 30, GREEN)
    print("Finish", (finishCoord - Vec(20, 40)), GREEN)
  }

  def isWin:Boolean = {
    val winner_blases = tracer.tracesNearCoord(finishCoord, -1 to 1, condition = {blase => blase.location.dist(finishCoord) < 20})
    !winner_blases.isEmpty
  }
}

object Level1 extends Level {
  def load() {
    val first  = new StaticPolygon(Vec(84,  212), Vec(458, 564), Vec(627, 393), Vec(591, 359), Vec(454, 495), Vec(113, 175))
    val second = new StaticPolygon(Vec(76,  85),  Vec(810, 83),  Vec(812,46),   Vec(77,  45))
    val third  = new StaticPolygon(Vec(782, 658), Vec(829, 658), Vec(830,151),  Vec(787, 152))
    val fourth = new StaticPolygon(Vec(562, 281), Vec(615, 256), Vec(644,186),  Vec(568, 150), Vec(536, 223))

    physics.addPhysicals(first, second, third, fourth)

    val burst_polygon = new BurstPolygon(Vec(772, 153), Vec(787, 140), Vec(745, 80), Vec(731, 95))

    val render_id = render {
      currentColor = WHITE
      drawPolygon(first.points)
      drawPolygon(second.points)
      drawPolygon(third.points)
      drawPolygon(fourth.points)

      drawStartFinish()
    }

    clear {
      physics.removePhysicals(first, second, third, fourth)
      burst_polygon.remove()
      delOperation(render_id)
      deleteSelf()
    }
  }

  val startCoord = Vec(271, 564)
  val finishCoord = Vec(350, 300)
}

object Level2 extends Level {
  def load() {
    val first  = new StaticPolygon(Vec(86,  526), Vec(353, 526), Vec(353, 414), Vec(86, 414))
    val second = new StaticPolygon(Vec(625,  715),  Vec(729, 715),  Vec(730, 414),   Vec(625,  414))
    val third  = new StaticPolygon(Vec(227, 311), Vec(502, 311), Vec(502, 280),  Vec(227, 280))
    val fourth = new StaticPolygon(Vec(730, 212), Vec(779, 170), Vec(779, 105),  Vec(682, 105), Vec(682, 170))
    val fifth  = new StaticPolygon(Vec(568, 143), Vec(594, 124), Vec(511, 17),  Vec(487, 38))
    
    physics.addPhysicals(first, second, third, fourth, fifth)
    
    val speed_polygon = new SpeedPolygon(List(Vec(415, 538), Vec(523, 621), Vec(737, 364), Vec(639, 284)), (Vec(737, 364) - Vec(523, 621)))

    val render_id = render {
      currentColor = WHITE
      drawPolygon(first.points)
      drawPolygon(second.points)
      drawPolygon(third.points)
      drawPolygon(fourth.points)
      drawPolygon(fifth.points)

      drawStartFinish()
    }

    clear {
      physics.removePhysicals(first, second, third, fourth, fifth)
      speed_polygon.remove()
      delOperation(render_id)
      deleteSelf()
    }
  }

  val startCoord = Vec(183, 630)
  val finishCoord = Vec(855, 58)
}

object Level3 extends Level {
  def load() {
    val first  = new StaticPolygon(Vec(58,  526), Vec(309, 292), Vec(284, 264), Vec(37, 505))
    val second = new StaticPolygon(Vec(307,  105),  Vec(332, 31),  Vec(249, 36))
    val third  = new StaticPolygon(Vec(432, 365), Vec(468, 365), Vec(468, 130),  Vec(432, 130))
    val fourth = new StaticPolygon(Vec(601, 287), Vec(639, 240), Vec(633, 185),  Vec(582, 178), Vec(571, 232))
    val fifth  = new StaticPolygon(Vec(738, 44), Vec(893, 209), Vec(924, 188),  Vec(763, 25))
    val sixth  = new StaticPolygon(Vec(892, 233), Vec(892, 429), Vec(927, 429),  Vec(927, 233))
    val seventh = new StaticPolygon(Vec(254, 561), Vec(684, 428), Vec(670, 401),  Vec(248, 541))
    val eighth  = new StaticPolygon(Vec(258, 578), Vec(308, 748), Vec(350, 739),  Vec(299, 568))

    physics.addPhysicals(first, second, third, fourth, fifth, sixth, seventh, eighth)

    val speed_polygon = new SpeedPolygon(List(Vec(764, 438), Vec(871, 397), Vec(742, 79), Vec(636, 125)), (Vec(871, 397) - Vec(742, 79)))
    val burst_polygon = new BurstPolygon(Vec(756, 535), Vec(898, 474), Vec(885, 445), Vec(738, 507))

    val render_id = render {
      currentColor = WHITE
      drawPolygon(first.points)
      drawPolygon(second.points)
      drawPolygon(third.points)
      drawPolygon(fourth.points)
      drawPolygon(fifth.points)
      drawPolygon(sixth.points)
      drawPolygon(seventh.points)
      drawPolygon(eighth.points)

      drawStartFinish()
    }

    clear {
      physics.removePhysicals(first, second, third, fourth, fifth, sixth, seventh, eighth)
      speed_polygon.remove()
      burst_polygon.remove()
      delOperation(render_id)
      deleteSelf()
    }
  }

  val startCoord = Vec(153, 609)
  val finishCoord = Vec(410, 608)
}
