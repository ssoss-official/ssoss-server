package com.ssoss.archfixtures.layering.domain

import com.ssoss.archfixtures.layering.infrastructure.MemberJdbcTemplate

class Member(
    private val jdbcTemplate: MemberJdbcTemplate,
)
