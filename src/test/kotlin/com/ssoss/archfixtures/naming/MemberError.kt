package com.ssoss.archfixtures.naming

import com.ssoss.ssossbackend.shared.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MemberError : ErrorCode {
    INSTANCE,
    ;

    override fun getCode(): String = "M0001"

    override fun getStatus(): HttpStatus = HttpStatus.NOT_FOUND

    override fun getMessage(): String = "회원을 찾을 수 없습니다"
}
