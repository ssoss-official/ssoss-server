package com.ssoss.archfixtures.gatewaynaming.alpha.infrastructure.gateway

import com.ssoss.archfixtures.gatewaynaming.alpha.domain.contract.BetaClient

class InternalBetaClient : BetaClient {
    override fun call() {
    }

    companion object {
        fun connect(): InternalBetaClient = InternalBetaClient()
    }
}
