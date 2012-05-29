package net.scageprojects.td

import net.scage.ScageScreenApp
import net.scage.ScageLib._
import net.scage.support.{ScageColor, State, Vec}
import net.scage.support.tracer3.{DefaultTrace, Trace, CoordTracer}
import collection.mutable.ArrayBuffer
import net.scage.support.messages.ScageMessage
import Tower._
import Wall._

object TowerDemka extends ScageScreenApp("Tower Demka", 1220, 560) {
  windowTitle += " - "+app_version
  val tracer = CoordTracer.create[Trace with HaveType with HaveHitPoints with SelfRemovable](
    field_from_x = 10,
    field_to_x = 1210,
    field_from_y = 150,
    field_to_y = 550,
    init_N_x = 15,
    init_N_y = 5
  )

  render {
    drawTraceGrid(tracer, DARK_GRAY)
  }

  private val PLACE_TOWER = 0
  private val PLACE_WALL = 1
  private var which_building = 0

  key(KEY_1, onKeyDown = which_building = PLACE_TOWER)
  key(KEY_2, onKeyDown = which_building = PLACE_WALL)

  keyNoPause(KEY_SPACE, onKeyDown = switchPause())

  leftMouse(onBtnDown = {m =>
    val p = tracer.point(m)
    if(p.x > 0 && p.x < tracer.N_x-1 && p.y >= 0 && p.y < tracer.N_y) {
      val traces_in_point = tracer.tracesInPoint(p)
      if(traces_in_point.isEmpty) {
        which_building match {
          case PLACE_TOWER =>
            if(_resource >= tower_price) {
              _resource -= tower_price
              new Tower(p)
            }
          case PLACE_WALL =>
            if(_resource >= wall_price) {
              _resource -= wall_price
              new Wall(p)
            }
          case _ =>
        }
      } else {
        val trace = traces_in_point.head
        if(trace.isBuilding) {
          trace.changeState(null, State("mouse_clicked" -> m))
        }
      }
    }
  })

  rightMouse(onBtnDown = {m =>
    val p = tracer.point(m)
    val traces_in_point = tracer.tracesInPoint(p)
    traces_in_point.withFilter(trace => trace.isTower || trace.isWall).foreach(_.remove())
  })

  private var enemy_amount = property("respawn.amount", 10)
  private val enemy_increase_amount = property("respawn.increase_amount", 2)
  private val respawn_period = property("respawn.period", 30)   // seconds
  private var enemy_first_period = property("respawn.first_period", 15) // seconds
  private var count = enemy_first_period

  init {
    enemy_amount = property("respawn.amount", 10)
    count = enemy_first_period
    wave_number = 0
  }

  private var all_enemies_dead = true
  def allEnemiesDead = all_enemies_dead

  private var wave_number = 0
  def spawnEnemies() {
    wave_number += 1
    all_enemies_dead = false
    val enemies = ArrayBuffer[Enemy]()
    action(500) {
      if(enemies.length < enemy_amount) {
        val start_point = Vec(0, (math.random*tracer.N_y).toInt)
        if(tracer.tracesInPoint(start_point).isEmpty) {
          val start = tracer.pointCenter(start_point)
          val end = Vec((tracer.N_x-1)*tracer.h_x + tracer.h_x/2, start.y)
          enemies += new Enemy(start, end)
        }
      } else {
        deleteSelf()
        action(1000) {
          all_enemies_dead = enemies.forall(_.hp <= 0)
          if(all_enemies_dead) {
            enemy_amount += enemy_increase_amount
            nextWaveCountdown(respawn_period)
            deleteSelf()
          }
        }
      }
    }
  }

  def nextWaveCountdown(period:Int) {
    count = period
      new {
      val action_id:Int = action(1000) {
        count -= 1
        if(count <= 0) {
          spawnEnemies()
          count = period
          delOperations(clear_id, currentOperation)
        }
      }

      val clear_id:Int = clear {
        delOperations(action_id, currentOperation)
      }
    }
  }

  init {
    nextWaveCountdown(enemy_first_period)
  }

  private var _resource = property("resource.initial_amount", 80)
  def resource = _resource
  init {
    _resource = property("resource.initial_amount", 80)
  }

  val resource_from_enemy = property("resource.from_enemy", 5)
  onEvent("Enemy Killed") {
    _resource += resource_from_enemy
  }
  onEventWithArguments("Tower Upgraded") {
    case upgrade_price:Int => _resource -= upgrade_price
  }
  onEventWithArguments("Tower Repaired") {
    case repair_price:Int => _resource -= repair_price
  }

  interface {
    if(all_enemies_dead) print("Next "+enemy_amount+" enemies will spawn in "+count, 10, 10+120, WHITE)
    else print("Wave "+wave_number+" Attack On!!!", 10, 10+120, WHITE)

    print("Build Mode: "+(which_building match {
      case PLACE_TOWER => "TOWER ("+tower_price+")"
      case PLACE_WALL => "WALL ("+wall_price+")"
      case _ =>
    })+" (Press 1 or 2 to change)", 10, 10+80, WHITE)

    print("Hitpoints: "+Base.hp.formatted("%.0f"), 10, 10+40, WHITE)

    print("Resource: "+_resource, 10, 10, WHITE)

    if(onPause) printCentered("PAUSE (Press Space)", windowWidth/2, 20, WHITE)
  }

  /*clear {
    delAllActions()
  }*/
}

trait HaveHitPoints {
  def hp:Float
  protected def hp_=(new_hp:Float)
}

trait SelfHitPoints extends HaveHitPoints {
  private var _hp = 0f
  def hp = _hp
  protected def hp_=(new_hp:Float) {_hp = new_hp}
}

trait SelfRemovable {
  def remove()
}

trait Damageable extends Trace {
  this: HaveHitPoints with SelfRemovable =>
  abstract override def changeState(changer:Trace, s:State) {
    super.changeState(changer, s)
    s.neededKeys {
      case ("damage", damage_amount:Float) =>
        hp -= damage_amount
        if(hp <= 0) {
          remove()
        }
    }
  }
}

trait HaveType {
  def isEnemy:Boolean
  def isBuilding:Boolean
  def isTower:Boolean
  def isWall:Boolean
  def isBase:Boolean
}

trait EnemyType extends HaveType {
  def isEnemy    = true
  def isBuilding = false
  def isTower    = false
  def isWall     = false
  def isBase     = false
}

trait BuildingType extends HaveType {
  def isEnemy    = false
  def isBuilding = true
}

trait TowerType extends BuildingType {
  def isTower    = true
  def isWall     = false
  def isBase     = false
}

trait WallType extends BuildingType {
  def isTower    = false
  def isWall     = true
  def isBase     = false
}

trait BaseType extends BuildingType {
  def isTower    = false
  def isWall     = false
  def isBase     = true
}

import TowerDemka._

trait SelfInsertable {
  this: Trace with HaveType with HaveHitPoints with SelfRemovable =>
  def init_coord:Vec
  tracer.addTrace(init_coord, this)
}