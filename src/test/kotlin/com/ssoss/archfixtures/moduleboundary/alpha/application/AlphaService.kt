package com.ssoss.archfixtures.moduleboundary.alpha.application

import com.ssoss.archfixtures.moduleboundary.beta.application.BetaService

class AlphaService(
    private val betaService: BetaService,
)
