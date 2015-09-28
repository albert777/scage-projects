package com.github.dunnololda.scageprojects.lightcycles.tests

import com.github.dunnololda.scage.ScageLib._
import com.github.dunnololda.scage.handlers.controller3.ControllerActorSystem
import com.github.dunnololda.scage.support.messages.ScageMessage
import org.lwjgl.opengl.Display

import scala.collection.mutable.ArrayBuffer

object MTTest extends ScageScreenAppMT("MT Test", 640, 480){
  private val keys_buf = ArrayBuffer[Int]()
  private val keys_buf_len = 30
  private val mouse_buf = ArrayBuffer[Vec]()

  key(KEY_1, 100, onKeyDown = {keys_buf += 1; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_2, onKeyDown = {keys_buf += 2; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_3, onKeyDown = {keys_buf += 3; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_4, onKeyDown = {keys_buf += 4; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_5, onKeyDown = {keys_buf += 5; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_6, onKeyDown = {keys_buf += 6; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_7, onKeyDown = {keys_buf += 7; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_8, onKeyDown = {keys_buf += 8; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_9, onKeyDown = {keys_buf += 9; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  key(KEY_0, onKeyDown = {keys_buf += 0; if(keys_buf.length > keys_buf_len) keys_buf.remove(0)})
  
  key(KEY_Q, onKeyDown = if(keyPressed(KEY_RCONTROL) || keyPressed(KEY_LCONTROL)) stopApp())

  leftMouse(onBtnDown = m => {
    if(coordOnRectCentered(m, Vec(windowWidth/2, 30), 100, 40)) {
      if(windowSize == Vec(640,480)) {
        ControllerActorSystem.shutdownControllerActor()
        Display.destroy()
        ControllerActorSystem.createControllerActor()
        ControllerActorSystem.initGLAndReleaseContext(800, 600, "MT Test")
        Display.makeCurrent()
        ScageMessage.reloadFont()
        ControllerActorSystem.startCheckControls()
      } else if(windowSize == Vec(800,600)) {
        ControllerActorSystem.shutdownControllerActor()
        Display.destroy()
        ControllerActorSystem.createControllerActor()
        ControllerActorSystem.initGLAndReleaseContext(640, 480, "MT Test")
        Display.makeCurrent()
        ScageMessage.reloadFont()
        ControllerActorSystem.startCheckControls()
      }
    } else {
      mouse_buf += m
      if (mouse_buf.length > 10) mouse_buf.remove(0)
    }
  })

  action {
    Thread.sleep(100)
  }

  render {
    print(keys_buf.mkString(" "), Vec(20, windowHeight/2), WHITE)
    mouse_buf.foreach(m => {
      drawFilledCircle(m, 3, WHITE)
    })
    print(s"FPS/Ticks $fps/$ticks", windowWidth - 20, windowHeight - 40, align = "top-right", color = DARK_GRAY)
    print(f"Render/Action ${averageRenderTimeMsec*fps/(averageRenderTimeMsec*fps+averageActionTimeMsec*ticks)*100}%.2f%%/${1*averageActionTimeMsec*ticks/(averageRenderTimeMsec*fps+averageActionTimeMsec*ticks)*100}%.2f%%", windowWidth - 20, windowHeight - 60, align = "top-right", color = DARK_GRAY)
    print(f"Render/Action $averageRenderTimeMsec%.2f msec/$averageActionTimeMsec%.2f msec", windowWidth - 20, windowHeight - 80, align = "top-right", color = DARK_GRAY)
    print(s"Render/Action $currentRenderTimeMsec msec/$currentActionTimeMsec msec", windowWidth - 20, windowHeight - 100, align = "top-right", color = DARK_GRAY)

    drawRectCentered(Vec(windowWidth/2, 30), 100, 40, WHITE)
    if(windowSize == Vec(640,480)) {
      print("800x600", Vec(windowWidth/2, 30), WHITE, align = "center")
    } else if(windowSize == Vec(800,600)) {
      print("640x480", Vec(windowWidth/2, 30), WHITE, align = "center")
    }
  }
}
