package com.kharcha.app.core.util

import java.util.UUID

/** A new unique id. Made on the phone, so two phones never collide when their data is merged. */
fun newId(): String = UUID.randomUUID().toString()
