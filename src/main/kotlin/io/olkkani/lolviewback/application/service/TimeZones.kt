package io.olkkani.lolviewback.application.service

import java.time.ZoneId

/**
 * Single source of truth for the "Asia/Seoul" zone used across the application.service
 * package (import mapping, category recommendation scoring, ...). The app runs on UTC
 * containers (see application.yaml `hibernate.jdbc.time_zone: UTC`), so any wall-clock
 * hour/day-of-week extraction MUST go through this constant rather than a locally
 * redeclared `ZoneId.of("Asia/Seoul")`, which could silently drift from this one.
 */
internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
