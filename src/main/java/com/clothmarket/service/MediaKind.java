package com.clothmarket.service;

/** What an upload is allowed to contain. Drives type, extension and size checks. */
public enum MediaKind {
    /** Profile pictures: images only. */
    IMAGE,
    /** Marketplace posts: an image or a video. */
    POST_MEDIA,
    /** Chat voice messages: audio only. */
    AUDIO
}
