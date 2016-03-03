package com.github.dunnololda.scageprojects.orbitalkiller.switchers

import com.github.dunnololda.scageprojects.orbitalkiller.InterfaceSwitcher

class NamesSwitcher extends InterfaceSwitcher {
  override def strVariants: Array[String] = Array("Non", "Noff")
  selected_variant = 1
  def showNames:Boolean = selectedVariant == 0
}
