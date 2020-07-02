package de.wayofquality.blended.mill.utils.config

import com.typesafe.config.{Config, ConfigValueFactory}

trait ConfigOptionSetter {

  implicit class RichConfigSetter(config : Config) {

    def setOptionLong(key : String, v : Option[Long]) : Config =
      setOptionAnyRef(key, v.map(_.asInstanceOf[AnyRef]))

    def setOptionString(key : String, v : Option[String]) : Config =
      setOptionAnyRef(key, v)

    def setOptionInt(key : String, v : Option[Int]) : Config =
      setOptionAnyRef(key, v.map(_.asInstanceOf[AnyRef]))

    def setOptionBoolean(key : String, v : Option[Boolean]) : Config =
      setOptionAnyRef(key, v.map(_.asInstanceOf[AnyRef]))

    def setOptionDouble(key : String, v : Option[Double]) : Config =
      setOptionAnyRef(key, v.map(_.asInstanceOf[AnyRef]))

    def setOptionAnyRef(key : String, v : Option[AnyRef]) : Config = v match {
      case None => config
      case Some(x) => config.withValue(key, ConfigValueFactory.fromAnyRef(x))
    }
  }
}
