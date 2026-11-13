package com.streampop;

import java.util.List;

/** One chat message. color = -1 when the sender has no colour. */
public record ChatMsg(String user, int color, List<String> badges, String text, boolean action, long time) {}
