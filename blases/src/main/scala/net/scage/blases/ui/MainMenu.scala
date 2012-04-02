package net.scage.blases.ui

import net.scage.support.Vec
import net.scage.blases.Blases
import net.scage.ScageLib._
import net.scage.handlers.controller2.MultiController
import net.scage.{ScreenApp, Scage, ScageScreenApp}

object MainMenu extends ScreenApp("Blases Main Menu", 640, 480, "Blases") with MultiController {
  backgroundColor = WHITE

  val new_game_button = new Button("New Game", Vec(512, 384) + Vec(-40, 40), 100, MainMenu, {
    Blases.run()
    backgroundColor = WHITE
  })
  val options_button = new Button("Options", Vec(512, 384) + Vec(-40, 0), 100, MainMenu, OptionsMenu.run())
  val help_button = new Button("Help", Vec(512, 384) + Vec(-40, -40), 100, MainMenu, HelpMenu.run())
  val exit_button = new Button("Exit", Vec(512, 384) + Vec(-40, -80), 100, MainMenu, Scage.stopApp())

  interface {
    print("v"+app_version, 20, 20, BLACK)
  }
}
