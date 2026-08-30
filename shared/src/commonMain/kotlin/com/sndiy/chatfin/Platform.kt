package com.sndiy.chatfin

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
