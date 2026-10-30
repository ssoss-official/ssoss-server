package com.ssoss.archfixtures.gatewaynaming.alpha.infrastructure.gateway

import com.ssoss.archfixtures.gatewaynaming.alpha.domain.contract.BetaClient

fun connectBetaClient(): BetaClient = InternalBetaClient.connect()
