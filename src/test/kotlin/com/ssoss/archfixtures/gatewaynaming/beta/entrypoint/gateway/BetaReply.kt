package com.ssoss.archfixtures.gatewaynaming.beta.entrypoint.gateway

data class BetaReply(
    val name: String,
) {
    companion object {
        fun of(name: String): BetaReply = BetaReply(name)
    }
}
