package cf.configs

import pureconfig.ConfigReader

case class HttpServerConfig(host: String, port: Int) derives ConfigReader
