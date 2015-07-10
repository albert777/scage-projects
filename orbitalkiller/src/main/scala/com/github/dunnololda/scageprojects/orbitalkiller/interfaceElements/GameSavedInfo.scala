package com.github.dunnololda.scageprojects.orbitalkiller.interfaceElements

import com.github.dunnololda.scageprojects.orbitalkiller.{OrbitalKiller, InterfaceElement}

class GameSavedInfo extends InterfaceElement {
  override def minimizable:Boolean = false

  private val rows1 = List("Игра сохранена")
  private var strings:List[String] = Nil
  override protected def _update(): Unit = {
    if(OrbitalKiller.showGameSavedMessage) strings = rows1
    else strings = Nil
  }
  override protected def _data: Seq[String] = strings
}
