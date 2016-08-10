import sbt._
import sbt.Keys._

object MyBuild extends com.typesafe.sbt.pom.PomBuild {
  override def settings = super.settings ++ Seq(Keys.checksums := Seq(""))
}
