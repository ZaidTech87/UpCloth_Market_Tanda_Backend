package com.clothmarket.dto;

import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

@Data
public class CallSignal {
    private String type;          // call-invite, call-answer, call-reject, call-end, ice-candidate
    private Long fromUserId;      // ALWAYS overwritten server-side from the authenticated session
    private Long toUserId;
    private String fromUserName;  // overwritten server-side on call-invite
    // set server-side on call-invite (caller's avatar). Holds the stored R2 object key and is
    // converted to a short-lived signed URL when the signal is serialized.
    @JsonSerialize(using = MediaUrlSerializer.class)
    private String profileImage;
    private String callType;      // "audio" or "video"
    private Object payload;       // SDP offer/answer or ICE candidate JSON
}