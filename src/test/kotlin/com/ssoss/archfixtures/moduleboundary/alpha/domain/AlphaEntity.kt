package com.ssoss.archfixtures.moduleboundary.alpha.domain

import com.ssoss.archfixtures.moduleboundary.beta.application.BetaService

class AlphaEntity(
    private val betaService: BetaService,
)
