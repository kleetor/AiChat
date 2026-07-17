package com.example.aichat.service;

import com.example.aichat.config.props.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChunkingService {

    private final RagProperties ragProperties;

    public ChunkingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    private int getChunkSize() { return ragProperties.getChunk().getSize(); }
    private int getOverlap() { return ragProperties.getChunk().getOverlap(); }

    /**
     * 递归字符分割：\n\n → \n → 。 → ； → ，
     * 最后按 chunk_size 硬切，带 overlap
     */
    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] separators = {"\n\n", "\n", "。", "；", "，"};
        List<String> segments = new ArrayList<>();
        segments.add(text);

        for (String sep : separators) {
            segments = splitBySeparator(segments, sep);
        }
        return enforceMaxSize(segments, getChunkSize(), getOverlap());
    }

    private List<String> splitBySeparator(List<String> segments, String sep) {
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() <= getChunkSize()) {
                result.add(seg);
            } else {
                for (String part : seg.split(Pattern.quote(sep))) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed);
                }
            }
        }
        return result;
    }

    private List<String> enforceMaxSize(List<String> segments, int maxLen, int overlapLen) {
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() <= maxLen) {
                if (!seg.isBlank()) result.add(seg);
            } else {
                int start = 0;
                while (start < seg.length()) {
                    int end = Math.min(start + maxLen, seg.length());
                    result.add(seg.substring(start, end));
                    start = end - overlapLen;
                }
            }
        }
        return result;
    }
}
