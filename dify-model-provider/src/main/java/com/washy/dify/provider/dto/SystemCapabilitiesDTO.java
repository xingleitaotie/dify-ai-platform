package com.washy.dify.provider.dto;

import lombok.Data;

@Data
public class SystemCapabilitiesDTO {
    private SystemCapabilityDTO chat;
    private SystemCapabilityDTO embedding;
    private SystemCapabilityDTO rerank;
    private SystemCapabilityDTO stt;
    private SystemCapabilityDTO tts;
    private SystemCapabilityDTO vision;
}