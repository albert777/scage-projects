package com.github.dunnololda.scageprojects

import com.github.dunnololda.scage.ScageLib._
import com.github.dunnololda.simplenet.{State => NetState}
import collection.mutable.ArrayBuffer
import scala.collection.mutable
import java.io.FileOutputStream

package object simpleshooter {
  val host = "fzeulf.netris.ru"
  val port = 10000
  val speed = 0.332f  // px / 10msec = 33.2 px / sec = 3.32 m/sec = 12 km/h
  val bullet_speed = 12f  // 12 px / 10msec = 1200 px / sec = 120 m/sec
  val bullet_count = 50  // 60 meters = 600 px = 0.5 sec = 50*10msec
  val bullet_damage = 100
  val map_width = 1024
  val map_height = 768
  val body_radius = 10  // 10px is 1 meter
  val bullet_size = 3
  val pov_distance = 600
  val pov_angle = 50
  val fire_pace = 100 // 100 msec/bullet = 60000 msec / 600 bullet = 600 bullet/min -> AK
  val audibility_radius = 60
  val reload_time = 5000  // 5 sec to swap frames
  val magazine = 30 // 30 rounds in AK's magazine
  val max_bullets = 90  // three magazines

  def vec(message:NetState, x:String, y:String):Vec = {
    Vec(message.value[Float](x).get, message.value[Float](y).get)
  }

  case class Client(id:Long, var coord:Vec, var health:Int, var wins:Int, var deaths:Int, var visible:Boolean) {
    def netState:NetState = NetState("id" -> id,
      "x"  -> coord.x,
      "y"  -> coord.y,
      "hp" -> health,
      "w"  -> wins,
      "d"  -> deaths,
      "v" -> visible)
  }

  def client(message:NetState):Client = {
    Client(id = message.value[Long]("id").get,
      coord = vec(message, "x", "y"),
      health = message.value[Int]("hp").get,
      wins = message.value[Int]("w").get,
      deaths = message.value[Int]("d").get,
      visible = message.value[Boolean]("v").get
    )
  }

  case class ClientData(up:Boolean, left:Boolean, down:Boolean, right:Boolean, shoots:List[Vec])

  def clientData(message:NetState):ClientData = {
    ClientData(
      up = message.valueOrDefault("up", false),
      left = message.valueOrDefault("left", false),
      down = message.valueOrDefault("down", false),
      right = message.valueOrDefault("right", false),
      shoots = message.valueOrDefault[List[NetState]]("shoots", Nil).map(x => vec(x, "x", "y"))

    )
  }

  case class ServerData(you:Client, others:List[Client], your_bullets:List[Vec], other_bullets:List[Vec])

  def serverData(message:NetState):ServerData = {
    val you = client(message.value[NetState]("you").get)
    val others = message.value[List[NetState]]("others").getOrElse(Nil).map(m => client(m))
    val your_bullets = message.value[List[NetState]]("your_bullets").getOrElse(Nil).map(m => vec(m, "x", "y"))
    val other_bullets = message.value[List[NetState]]("other_bullets").getOrElse(Nil).map(m => vec(m, "x", "y"))
    ServerData(you, others, your_bullets, other_bullets)
  }

  case class Bullet(dir:Vec, shooter:Client, var coord:Vec, var count:Int) {
    def netState = NetState("x" -> coord.x, "y" -> coord.y)
  }

  case class TacticServerPlayer(
                          id:Long,
                          number:Int,
                          team:Int,
                          number_in_team:Int,
                          private var _coord:Vec,
                          private var _pov:Vec,
                          var health:Int,
                          var wins:Int,
                          var deaths:Int,
                          var visible:Boolean) {
    def coord = _coord
    def coord_=(new_coord:Vec) {
      _coord = new_coord
      _pov_area = povTriangle(_coord, _pov, pov_distance, pov_angle)
    }

    val ds = ArrayBuffer[Vec]()
    def pov = _pov
    def pov_=(new_pov:Vec) {
      _pov = new_pov
      _pov_area = povTriangle(_coord, _pov, pov_distance, pov_angle)
    }

    var last_bullet_shot = 0l

    private var _pov_area = povTriangle(_coord, _pov, pov_distance, pov_angle)
    def povArea = _pov_area

    private var _bullets = max_bullets
    private var reload_start_time = 0l

    def shootBullet(bullet_id:Long, dir:Vec, body_radius:Float, bullet_count:Int):TacticBullet = {
      _bullets -= 1
      if(_bullets % magazine == 0) reload_start_time = System.currentTimeMillis()
      last_bullet_shot = System.currentTimeMillis()
      val init_coord = coord + dir*(body_radius+1)
      TacticBullet(bullet_id, dir, this, init_coord, init_coord, bullet_count)
    }
    def bullets = _bullets
    def replenishAmmo() {_bullets = max_bullets}
    def isReloading:Boolean = {
      System.currentTimeMillis() - reload_start_time < reload_time
    }
    def canShoot:Boolean = {
      health > 0 && _bullets > 0 && !isReloading && System.currentTimeMillis() - last_bullet_shot > fire_pace
    }

    def isDead = health <= 0
    def isAlive = health > 0

    def netState:NetState = NetState(
      "id" -> id,
      "n" -> number,
      "t" -> team,
      "nit" -> number_in_team,
      "x"  -> _coord.x,
      "y"  -> _coord.y,
      "ds"  -> ds.toList.map(d => NetState("x" -> d.x, "y" -> d.y)),
      "px" -> _pov.x,
      "py" -> _pov.y,
      "pa"  -> _pov_area.toList.map(d => NetState("x" -> d.x, "y" -> d.y)),
      "bs" -> _bullets,
      "r" -> isReloading,
      "hp" -> health,
      "w"  -> wins,
      "d"  -> deaths,
      "v" -> visible)
  }

  case class TacticClientPlayer(
     id:Long,
     number:Int,
     team:Int,
     number_in_team:Int,
     coord:Vec,
     destinations:List[Vec],
     pov:Vec,
     pov_area:List[Vec],
     bullets:Int,
     is_reloading:Boolean,
     health:Int,
     wins:Int,
     deaths:Int,
     visible:Boolean
  ) {
    def isDead = health <= 0
  }

  def tacticClient(message:NetState):TacticClientPlayer = {
    TacticClientPlayer(
      id = message.value[Long]("id").get,
      number = message.value[Int]("n").get,
      team = message.value[Int]("t").get,
      number_in_team = message.value[Int]("nit").get,
      coord = vec(message, "x", "y"),
      destinations = message.value[List[NetState]]("ds").getOrElse(Nil).map(m => vec(m, "x", "y")),
      pov = vec(message, "px", "py"),
      pov_area = message.value[List[NetState]]("pa").getOrElse(Nil).map(m => vec(m, "x", "y")),
      bullets = message.value[Int]("bs").get,
      is_reloading = message.value[Boolean]("r").get,
      health = message.value[Int]("hp").get,
      wins = message.value[Int]("w").get,
      deaths = message.value[Int]("d").get,
      visible = message.value[Boolean]("v").get
    )
  }

  case class TacticClientData(player_num:Int, destination:Option[Vec], pov: Option[Vec], clear_destinations:Boolean)

  def tacticClientData(message:NetState):TacticClientData = {
    TacticClientData(
      player_num = message.value[Int]("pn").get,
      destination = message.value[NetState]("d").map(x => vec(x, "x", "y")),
      pov = message.value[NetState]("pov").map(x => vec(x, "x", "y")),
      clear_destinations = message.value[Boolean]("cleardest").getOrElse(false)
    )
  }

  case class TacticServerData(
                               yours:List[TacticClientPlayer],
                               others:List[TacticClientPlayer],
                               your_bullets:List[TacticClientBullet],
                               other_bullets:List[TacticClientBullet],
                               receive_moment:Long
  )

  def tacticServerData(message:NetState, receive_moment:Long):TacticServerData = {
    val yours = message.value[List[NetState]]("yours").getOrElse(Nil).map(m => tacticClient(m))
    val others = message.value[List[NetState]]("others").getOrElse(Nil).map(m => tacticClient(m))
    val your_bullets = message.value[List[NetState]]("your_bullets").getOrElse(Nil).map(m => tacticClientBullet(m))
    val other_bullets = message.value[List[NetState]]("other_bullets").getOrElse(Nil).map(m => tacticClientBullet(m))
    TacticServerData(yours, others, your_bullets, other_bullets, receive_moment)
  }

  case class Wall(from:Vec, to:Vec) {
    def netState = NetState("fromx" -> from.x, "fromy" -> from.y, "tox" -> to.x, "toy" -> to.y)
  }

  def wall(message:NetState):Wall = {
    Wall(Vec(message.value[Float]("fromx").get, message.value[Float]("fromy").get), Vec(message.value[Float]("tox").get, message.value[Float]("toy").get))
  }

  def serverWalls(message:NetState):List[Wall] = {
    message.value[List[NetState]]("walls").get.map(x => wall(x))
  }

  case class TacticBullet(id:Long, dir:Vec, shooter:TacticServerPlayer, var prev_coord:Vec, var coord:Vec, var count:Int) {
    def netState = NetState(
      "id" -> id,
      "pid" -> shooter.id,
      "pn" -> shooter.number,
      "pt" -> shooter.team,
      "x" -> coord.x,
      "y" -> coord.y
    )
  }

  case class TacticClientBullet(id:Long, player_id:Long, player_number:Int, player_team:Int, coord:Vec)

  def tacticClientBullet(message:NetState):TacticClientBullet = {
    TacticClientBullet(
      id = message.value[Long]("id").get,
      player_id = message.value[Long]("pid").get,
      player_number = message.value[Int]("pn").get,
      player_team = message.value[Int]("pt").get,
      coord = vec(message, "x", "y"))
  }

  case class TacticGame(game_id:Int,
                        players:mutable.HashMap[Long, List[TacticServerPlayer]] = mutable.HashMap[Long, List[TacticServerPlayer]](),  // client_id -> list of players
                        bullets:ArrayBuffer[TacticBullet] = ArrayBuffer[TacticBullet]())

  case class GameInfo(game_id:Int, players:Int) {
    def netState = NetState(
      "gid" -> game_id,
      "ps" -> players
    )
  }

  def gameInfo(message:NetState):GameInfo = {
    GameInfo(message.value[Int]("gid").get, message.value[Int]("ps").get)
  }

  def gamesList(message:NetState):List[GameInfo] = {
    message.value[List[NetState]]("gameslist").getOrElse(Nil).map(m => gameInfo(m))
  }

  case class GameMap(walls:List[Wall] = Nil, safe_zones:List[List[Vec]] = Nil) {
    def isInsideSafeZone(coord:Vec):Boolean = safe_zones.exists(
      sz => coordOnArea(coord, sz)
    )

    def isEmpty:Boolean = walls.isEmpty && safe_zones.isEmpty

    def netState = NetState(
      "ws" -> walls.map(w => w.netState).toList,
      "szs" -> safe_zones.map(sz => sz.map(p => NetState("x" -> p.x, "y" -> p.y)))
    )
  }

  def saveMap(map_name:String, walls:Seq[Wall], safe_zones:Seq[Seq[Vec]]) {
    val fos = new FileOutputStream(map_name)
    if(walls.nonEmpty) {
      fos.write("walls\n".getBytes)
      walls.foreach(w => {
        fos.write(s"${w.from.x} ${w.from.y} ${w.to.x} ${w.to.y}\n".getBytes)
      })
    }
    if(safe_zones.nonEmpty) {
      fos.write("safe zones\n".getBytes)
      safe_zones.foreach(sz => {
        fos.write(sz.map(p => s"${p.x} ${p.y}").mkString("", " ", "\n").getBytes)
      })
    }
    fos.close()
  }

  def loadMap(map_name:String):GameMap = {
    def tryFloat(str:String):Boolean = {
      try {
        str.toFloat
        true
      } catch {
        case e:Exception => false
      }
    }

    val walls = ArrayBuffer[Wall]()
    val safe_zones = ArrayBuffer[List[Vec]]()
    var mode = ""
    try {
      for {
        line <- io.Source.fromFile(map_name).getLines()
        if !line.startsWith("#")
      } {
        if(line == "walls") mode = "walls"
        else if(line == "safe zones") mode = "safe zones"
        else {
          mode match {
            case "walls" =>
              val coords = line.split(" ")
              if(coords.length == 4 && coords.forall(c => tryFloat(c))) {
                walls += Wall(Vec(coords(0).toFloat, coords(1).toFloat), Vec(coords(2).toFloat, coords(3).toFloat))
              }
            case "safe zones" =>
              val coords = line.split(" ")
              if(coords.length % 2 == 0 && coords.forall(c => tryFloat(c))) {
                val new_safe_zone = ArrayBuffer[Vec]()
                coords.grouped(2).foreach {
                  case Array(x, y) => new_safe_zone += Vec(x.toFloat, y.toFloat)
                  case _ =>
                }
                safe_zones += new_safe_zone.toList
              }
            case _ =>
          }
        }
      }
      GameMap(walls.toList, safe_zones.toList)
    } catch {
      case e:Exception => GameMap(Nil, Nil)
    }
  }

  def gameMap(message:NetState):GameMap = {
    GameMap(
      walls = message.value[List[NetState]]("ws").getOrElse(Nil).map(m => wall(m)),
      safe_zones = message.value[List[List[NetState]]]("szs").getOrElse(Nil).map(m => m.map(p => vec(p, "x", "y")))
    )
  }

  def isCoordNearWall(coord:Vec, wall:Wall, body_radius:Float):Boolean = {
    val one = (wall.to - wall.from).rotateDeg(135).n*body_radius + wall.from
    val two = (wall.to - wall.from).rotateDeg(-135).n*body_radius + wall.from
    val three = (wall.from - wall.to).rotateDeg(135).n*body_radius + wall.to
    val four = (wall.from - wall.to).rotateDeg(-135).n*body_radius + wall.to
    val area = List(one, two, three, four)
    coordOnArea(coord, area)
  }

  def isCoordCorrect(coord:Vec, body_radius:Float, walls:Seq[Wall]):Boolean = {
    walls.forall(w => !isCoordNearWall(coord, w, body_radius))
  }

  def randomCoord(width:Float, height:Float, body_radius:Float, walls:Seq[Wall]):Vec = {
    val coord = Vec(math.random*width, math.random*height)
    if (isCoordCorrect(coord, body_radius, walls)) coord
    else randomCoord(width, height, body_radius, walls)
  }

  def randomCoordNear(near:Vec, radius:Float, body_radius:Float, walls:Seq[Wall]):Vec = {
    val dir = Vec(math.random, math.random).n
    val coord = near + dir*radius
    if (isCoordCorrect(coord, body_radius, walls)) coord
    else randomCoordNear(near, radius, body_radius, walls)
  }

  def randomCoordInsideArea(area:List[Vec], body_radius:Float, walls:Seq[Wall]):Vec = {
    val area_center = Vec(area.map(_.x).sum/area.length, area.map(_.y).sum/area.length)
    val random_corner = area((math.random*area.length).toInt)
    area_center + (random_corner - area_center).n*(math.random*random_corner.dist(area_center))
  }

  def respawnCoord(team:Int, map_width:Float, map_height:Float, body_radius:Float, map:GameMap):Vec = {
    if(map.safe_zones.length == 0) {
      randomCoord(map_width, map_height, body_radius, map.walls)
    } else if(map.safe_zones.length == 0) {
      randomCoordInsideArea(map.safe_zones.head, body_radius, map.walls)
    } else {
      val team1_area = map.safe_zones(0)
      val team2_area = map.safe_zones(1)
      team match {
        case 1 => randomCoordInsideArea(team1_area, body_radius, map.walls)
        case 2 => randomCoordInsideArea(team2_area, body_radius, map.walls)
        case _ => randomCoordInsideArea(team1_area, body_radius, map.walls)
      }
    }
  }

  def isCoordVisible(coord:Vec, from:Vec, povArea:List[Vec], walls:Seq[Wall]):Boolean = {
    coordOnArea(coord, povArea) && walls.forall(w => {
      !areLinesIntersect(from, coord, w.from, w.to)
    })
  }

  def isCoordVisibleOrAudible(coord:Vec, from:Vec, povArea:List[Vec], is_moving:Boolean, audibility_radius:Float, walls:Seq[Wall]):Boolean = {
    is_moving && coord.dist2(from) <= audibility_radius * audibility_radius || coordOnArea(coord, povArea) && walls.forall(w => {
      !areLinesIntersect(from, coord, w.from, w.to)
    })
  }

  def isPathCorrect(from:Vec, to:Vec, body_radius:Float, walls:Seq[Wall]):Boolean = {
    walls.forall(w => !areLinesIntersect(from, to, w.from, w.to)) && isCoordCorrect(to, body_radius, walls)
  }

  def isBodyHit(from:Vec, to:Vec, body_position:Vec, body_radius:Float):Boolean = {
    val a = body_position + (to - from).rotateDeg(90)
    val b = body_position + (to - from).rotateDeg(-90)
    areLinesIntersect(from ,to, a, b)
  }

  def isCoordVisible(coord:Vec, from:Vec, walls:Seq[Wall]):Boolean = {
    walls.forall(w => {
      !areLinesIntersect(from, coord, w.from, w.to)
    })
  }

  def isWallVisible(wall:Wall, from:Vec, other_walls:Seq[Wall]):Boolean = {
    val middle = (wall.to - wall.from).n*(wall.to.dist(wall.from)/2) + wall.from
    isCoordVisible(wall.from, from, other_walls) || isCoordVisible(middle, from, other_walls) || isCoordVisible(wall.to, from, other_walls)
  }

  def outsideCoord(coord:Vec, width:Float, height:Float):Vec = {
    def checkC(c:Float, from:Float, to:Float):Float = {
      val dist = to - from
      if(c >= to) checkC(c - dist, from, to)
      else if(c < from) checkC(c + dist, from, to)
      else c
    }
    val x = checkC(coord.x, 0, width)
    val y = checkC(coord.y, 0, height)
    Vec(x, y)
  }

  def povTriangle(coord:Vec, pov:Vec, distance:Float, angle:Float):List[Vec] = {
    val axis = pov*distance
    val one = coord + axis.rotateDeg(angle)
    val two = coord + axis.rotateDeg(-angle)
    List(coord, one, two, coord)
  }

  def messageArea(message:Any, coord:Vec, printer:ScageMessage = ScageMessage):List[Vec] = {
    val Vec(w, h) = messageBounds(message)
    val Vec(x, y) = coord
    List(Vec(x-w/2, y+h/2), Vec(x+w/2, y+h/2), Vec(x+w/2, y-h/2), Vec(x-w/2, y-h/2))
  }

  def createMenuItems(menu_items:List[(String, Vec,  ScageColor, () => Any)],
                      printer:ScageMessage = ScageMessage):List[(String, Vec, List[Vec], ScageColor, () => Any)] = {
    menu_items.map {
      case (title, coord, color, action) => (title, coord, messageArea(title, coord, printer), color, action)
    }
  }
}
