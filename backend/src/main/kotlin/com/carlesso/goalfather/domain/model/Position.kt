package com.carlesso.goalfather.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Position(val abbr: String) {
    GK("GL"),
    CB("ZG"),
    MF("MC"),
    FW("AT"),
}
