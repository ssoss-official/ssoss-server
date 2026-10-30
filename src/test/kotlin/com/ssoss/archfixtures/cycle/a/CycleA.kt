package com.ssoss.archfixtures.cycle.a

import com.ssoss.archfixtures.cycle.b.CycleB

class CycleA(
    private val b: CycleB,
)
