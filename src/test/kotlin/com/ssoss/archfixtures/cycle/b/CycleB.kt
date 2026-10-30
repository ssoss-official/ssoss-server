package com.ssoss.archfixtures.cycle.b

import com.ssoss.archfixtures.cycle.a.CycleA

class CycleB(
    private val a: CycleA,
)
