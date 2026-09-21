package cf.configs

import pureconfig.ConfigReader

case class CfAuthConfig(
    key: String,
    secret: String,
) derives ConfigReader
