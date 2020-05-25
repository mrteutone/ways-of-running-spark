import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

case class ConfigFileProperties(startDate: String,
                                inputPath: String,
                                sparkProperties: Map[String, String])

object ConfigFileProperties {
  def apply(configFile: String = "application.conf"): ConfigFileProperties = {
    val config = ConfigFactory.load(configFile)
    val startDate = config.getString("startDate")
    val inputPath = config.getString("inputPath")

    // Only necessary if Spark job runs as process (for testing) instead of being submitted to a cluster.
    val sparkProperties = config
      .getConfig("spark")
      .entrySet()
      .asScala
      .map(k => s"spark.${k.getKey}" -> k.getValue.unwrapped().toString)
      .toMap

    ConfigFileProperties(
      startDate = startDate,
      inputPath = inputPath,
      sparkProperties = sparkProperties
    )
  }
}
