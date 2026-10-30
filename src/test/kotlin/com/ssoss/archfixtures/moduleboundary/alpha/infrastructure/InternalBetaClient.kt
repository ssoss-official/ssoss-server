package com.ssoss.archfixtures.moduleboundary.alpha.infrastructure

import com.ssoss.archfixtures.moduleboundary.beta.entrypoint.BetaInternalGateway

class InternalBetaClient(
    private val betaInternalGateway: BetaInternalGateway,
)
