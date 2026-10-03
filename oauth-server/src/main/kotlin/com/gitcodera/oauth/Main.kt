package com.gitcodera.oauth

import java.util.concurrent.CountDownLatch

fun main() {
    val config = OAuthConfig.fromEnvironment(System.getenv())
    val vault = TokenVault(config.databaseUrl, config.encryptionKey)
    vault.removeExpired()
    val oauthServer = OAuthServer(config, vault)
    oauthServer.start()
    Runtime.getRuntime().addShutdownHook(Thread {
        oauthServer.stop()
    })
    println("GitCoderA OAuth callback server listening on port ${config.port}.")
    CountDownLatch(1).await()
}
