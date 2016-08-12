
cancelable in Global := true

enablePlugins(JmhPlugin)

sourceDirectory in Jmh := (sourceDirectory in Test).value
classDirectory in Jmh := (classDirectory in Test).value
dependencyClasspath in Jmh := (dependencyClasspath in Test).value
// rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
compile in Jmh <<= (compile in Jmh) dependsOn (compile in Test)
run in Jmh <<= (run in Jmh) dependsOn (Keys.compile in Jmh)
javaOptions in Jmh ++= Seq("-Xms4g", "-Xmx4g")

scalacOptions ++= Seq("-optimise", "-Yclosure-elim", "-Yinline")

