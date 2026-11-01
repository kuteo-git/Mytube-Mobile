package com.mytube.app.domain.model

/**
 * A chip in the row above the feed.
 *
 * `videoCount` is carried because the web app uses it to decide what appears:
 * *"the rest are topics that actually have videos, so a chip can never produce
 * an empty grid."* A chip that leads nowhere is the dead button §5 forbids,
 * wearing a category name.
 */
data class Topic(val name: String, val videoCount: Int)
