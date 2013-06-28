package com.github.dunnololda.scageprojects.simpleshooter

import com.github.dunnololda.scage.ScageLib._
import collection.mutable.ArrayBuffer
import com.github.dunnololda.cli.Cli

object MapCreator extends ScageScreenApp(s"Simple Shooter Map Creator v$appVersion", game_window_width, game_window_height) with Cli {
  programDescription = s"Simple Shooter Map Creator $appVersion"
  commandLineArgsAndParse(("m", "map", "optional map file name to edit or the name for new map. Default: map.ss", true, false))
  private val map_name = property("map", "map.ss")

  private var loaded_map = loadMap(map_name)

  private var from:Option[Vec] = None
  private val walls = ArrayBuffer[Wall](loaded_map.walls:_*)
  private var walls_counter = 0

  private val safe_zones = ArrayBuffer[List[Vec]](loaded_map.safe_zones:_*)
  private val new_safe_zone = ArrayBuffer[Vec]()
  private var safe_zones_counter = 0

  private val chance_modificators = ArrayBuffer[MutableChanceModificator](loaded_map.chance_modificators.map(x => x.toMutable):_*)
  private val new_chance_modificator_area = ArrayBuffer[Vec]()
  private var chance_modificators_counter = 0

  private var mode = 0  // 0 - walls, 1 - safe zones, 2 - chance modificators


  private var _center:Vec = Vec.zero

  private var _map_changed = false
  private def isMapChanged:Boolean = _map_changed

  private def setModificationValue(new_value:Float) {
    if(chance_modificators.length > 0 && chance_modificators_counter >= 0 && chance_modificators_counter < chance_modificators.length)
    chance_modificators(chance_modificators_counter).value = new_value
    _map_changed = true
  }

  key(KEY_2, onKeyDown = setModificationValue(2))
  key(KEY_3, onKeyDown = setModificationValue(3))
  key(KEY_4, onKeyDown = setModificationValue(4))
  key(KEY_5, onKeyDown = setModificationValue(5))
  key(KEY_6, onKeyDown = setModificationValue(6))
  key(KEY_7, onKeyDown = setModificationValue(7))
  key(KEY_8, onKeyDown = setModificationValue(8))
  key(KEY_9, onKeyDown = setModificationValue(9))

  key(KEY_Z, onKeyDown = {
    mode match {
      case 0 =>
        mode = 1
        new_safe_zone.clear()
      case 1 =>
        mode = 2
        new_chance_modificator_area.clear()
      case 2 =>
        mode = 0
      case _ =>
        from = None
    }
  })

  private var dir = Vec.zero
  key(KEY_W, 100, onKeyDown = dir += Vec(0, 1))
  key(KEY_A, 100, onKeyDown = dir += Vec(-1, 0))
  key(KEY_S, 100, onKeyDown = dir += Vec(0, -1))
  key(KEY_D, 100, onKeyDown = dir += Vec(1, 0))

  key(KEY_F1, onKeyDown = {
    walls.clear()
    walls ++= loaded_map.walls
    walls_counter = 0
    safe_zones.clear()
    safe_zones ++= loaded_map.safe_zones
    safe_zones_counter = 0
    chance_modificators.clear()
    chance_modificators ++= loaded_map.chance_modificators.map(x => x.toMutable)
    chance_modificators_counter = 0
    _map_changed = false
  })
  key(KEY_F5, onKeyDown = {
    if(isMapChanged) {
      saveMap(map_name, walls, safe_zones, chance_modificators.map(x => x.toImmutable))
      loaded_map = GameMap(walls.toList, safe_zones.toList, chance_modificators.map(x => x.toImmutable).toList)
      _map_changed = false
    }
  })

  leftMouse(onBtnDown = m => {
    val sm = scaledCoord(m)
    if(isCoordInsideMap(sm)) {
      mode match {
        case 0 =>
          from match {
            case Some(f) =>
              walls += Wall(f, sm)
              _map_changed = true
              from = None
            case None => from = Some(sm)
          }
        case 1 =>
          if(new_safe_zone.length > 0) {
            if(sm.dist2(new_safe_zone.head) < body_radius*body_radius) {
              if(new_safe_zone.length < 3) {
                new_safe_zone.clear()
              } else {
                new_safe_zone += new_safe_zone.head
                safe_zones += new_safe_zone.toList
                new_safe_zone.clear()
                _map_changed = true
              }
            } else new_safe_zone += sm
          } else new_safe_zone += sm
        case 2 =>
          if(new_chance_modificator_area.length > 0) {
            if(sm.dist2(new_chance_modificator_area.head) < body_radius*body_radius) {
              if(new_chance_modificator_area.length < 3) {
                new_chance_modificator_area.clear()
              } else {
                new_chance_modificator_area += new_chance_modificator_area.head
                chance_modificators += MutableChanceModificator(2, new_chance_modificator_area.toList)
                new_chance_modificator_area.clear()
                _map_changed = true
              }
            } else new_chance_modificator_area += sm
          } else new_chance_modificator_area += sm
        case _ =>
      }
    }
  })

  rightMouse(onBtnDown = m => {
    mode match {
      case 0 =>
        if(walls.length > 0 && walls_counter >= 0 && walls_counter < walls.length) {
          walls.remove(walls_counter)
          _map_changed = true
          if(walls.length > 0) walls_counter -= 1
          else walls_counter = 0
        }
      case 1 =>
        if(safe_zones.length > 0 && safe_zones_counter >= 0 && safe_zones_counter < safe_zones.length) {
          safe_zones.remove(safe_zones_counter)
          _map_changed = true
          if(safe_zones.length > 0) safe_zones_counter -= 1
          else safe_zones_counter = 0
        }
      case 2 =>
        if(chance_modificators.length > 0 && chance_modificators_counter >= 0 && chance_modificators_counter < chance_modificators.length) {
          chance_modificators.remove(chance_modificators_counter)
          _map_changed = true
          if(chance_modificators.length > 0) chance_modificators_counter -= 1
          else chance_modificators_counter = 0
        }
      case _ =>
    }
  })

  mouseWheelDown(m => {
    mode match {
      case 0 =>
        if(walls.length > 0) {
          walls_counter -= 1
          if(walls_counter < 0) walls_counter = walls.length-1
        }
      case 1 =>
        if(safe_zones.length > 0) {
          safe_zones_counter -= 1
          if(safe_zones_counter < 0) safe_zones_counter = safe_zones.length-1
        }
      case 2 =>
        if(chance_modificators.length > 0) {
          chance_modificators_counter -= 1
          if(chance_modificators_counter < 0) chance_modificators_counter = chance_modificators.length-1
        }
      case _ =>
    }
  })

  mouseWheelUp(m => {
    mode match {
      case 0 =>
        if(walls.length > 0) {
          walls_counter += 1
          if(walls_counter >= walls.length) walls_counter = 0
        }
      case 1 =>
        if(safe_zones.length > 0) {
          safe_zones_counter += 1
          if(safe_zones_counter >= safe_zones.length) safe_zones_counter = 0
        }
      case 2 =>
        if(chance_modificators.length > 0) {
          chance_modificators_counter += 1
          if(chance_modificators_counter >= chance_modificators.length) chance_modificators_counter = 0
        }
      case _ =>
    }
  })

  action {
    if(dir.notZero) {
      val new_center = _center + dir.n*body_radius
      if(isCoordInsideMap(new_center)) _center = new_center
      dir = Vec.zero
    }
  }

  center = _center

  render {
    drawSlidingLines(map_edges, DARK_GRAY)
    (-map_width/2 to map_width/2 by body_radius*2).foreach(x => drawLine(Vec(x, -map_height/2), Vec(x, map_height/2), DARK_GRAY))
    (-map_height/2 to map_height/2 by body_radius*2).foreach(y => drawLine(Vec(-map_width/2, y), Vec(map_width/2, y), DARK_GRAY))
    drawFilledCircle(_center, 2, GREEN)
    walls.zipWithIndex.foreach {
      case (w, i) =>
        if(mode == 0 && i == walls_counter) drawLine(w.from, w.to, RED)
        else drawLine(w.from, w.to, WHITE)
    }
    safe_zones.zipWithIndex.foreach {
      case (sz, idx) =>
        if(mode == 1 && idx == safe_zones_counter) drawSlidingLines(sz, RED)
        else drawSlidingLines(sz, GREEN)
    }
    chance_modificators.zipWithIndex.foreach {
      case (cm, idx) =>
        if(mode == 2 && idx == chance_modificators_counter) {
          drawSlidingLines(cm.area, RED)
          print(cm.value, cm.area_center, RED, align = "center")
        } else {
          drawSlidingLines(cm.area, YELLOW)
          print(cm.value, cm.area_center, YELLOW, align = "center")
        }
    }
    mode match {
      case 0 =>
        from match {
          case Some(f) =>
            drawFilledCircle(f, 2, WHITE)
            drawLine(f, scaledCoord(mouseCoord), WHITE)
          case None =>
        }
      case 1 =>
        drawSlidingLines(new_safe_zone.toList ::: scaledCoord(mouseCoord) :: Nil, GREEN)
      case 2 =>
        drawSlidingLines(new_chance_modificator_area.toList ::: scaledCoord(mouseCoord) :: Nil, YELLOW)
      case _ =>
    }
  }

  interface {
    print(fps, 20, windowHeight-20, WHITE)
    val mode_str = mode match {
      case 0 => "Добавляем стены"
      case 1 => "Добавляем мертвяк"
      case 2 => "Добавляем изменятор вероятности"
      case _ =>
    }
    print(mode_str, 20, 80, GREEN)
    print(f"Центр: x = ${_center.x/10f}%.2f м, y = ${_center.y/10f}%.2f м", 20, 50, GREEN)
    val s = if(isMapChanged) {
      s"Карта: $map_name. Сохранить: F5. Восстановить сохраненную карту: F1. Добавление стен/мертвяка: Z"
    } else s"Карта: $map_name. Сохранено"
    print(s, 20, 20, GREEN)
  }

  dispose {
    if(isMapChanged) saveMap(map_name, walls, safe_zones, chance_modificators.map(x => x.toImmutable))
  }
}
