name := "ways-of-running-spark"
version := "0.1"
scalaVersion := "2.12.10"
val sparkVersion = "3.0.0-preview2"
val hadoopVersion = "3.2.0"

// dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.3"

libraryDependencies ++=
  Seq(
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.apache.hadoop" % "hadoop-common" % hadoopVersion
  )
    .map(_ % Provided) ++ Seq(
    "org.apache.hadoop" % "hadoop-aws" % hadoopVersion,
    "org.rogach" %% "scallop" % "3.2.0",
    "com.typesafe" % "config" % "1.4.0",
    "org.scalatest" %% "scalatest" % "3.0.7" % Test
  )

// https://github.com/sbt/sbt-assembly#-provided-configuration
run in Compile := Defaults
  .runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run))
  .evaluated
