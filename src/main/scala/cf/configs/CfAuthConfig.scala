package cf.configs

import pureconfig.ConfigReader

case class CfAuthConfig(
    apiKey: String,
    apiSecret: String,
) derives ConfigReader
