package com.example.aichat.service;

import com.example.aichat.config.props.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ChunkingService {

    private final RagProperties ragProperties;

    /** 已知 LLM prompt injection 模式，匹配则过滤 */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|directives?|commands?|prompt)" +
            "|you\\s+are\\s+(now\\s+)?(DAN|jailbreak)" +
            "|\\\\[system\\\\]" +
            "|<script[^>]*>" +
            "|system:\\s*(override|ignore))",
            Pattern.DOTALL);

    public ChunkingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    private int getChunkSize() { return ragProperties.getChunk().getSize(); }
    private int getOverlap() { return ragProperties.getChunk().getOverlap(); }

    /** 使用系统默认参数分块 */
    public List<String> split(String text) {
        return split(text, null, null);
    }

    /**
     * 使用指定参数分块。
     * @param chunkSize 分块大小，null/<=0 使用系统默认
     * @param overlap   重叠字符数，null/<0 使用系统默认
     */
    public List<String> split(String text, Integer chunkSize, Integer overlap) {
        int cs = (chunkSize != null && chunkSize > 0) ? chunkSize : getChunkSize();
        int ol = (overlap != null && overlap >= 0) ? overlap : getOverlap();
        return doSplit(text, cs, ol);
    }

    /**
     * 递归字符分割：\n\n → \n → 。 → ； → ，
     * 最后按 chunk_size 硬切，带 overlap
     */
    private List<String> doSplit(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        // 过滤潜在的 prompt injection 模式，用 [已过滤] 替换
        text = INJECTION_PATTERN.matcher(text).replaceAll("[已过滤]");
        String[] separators = {"\n\n", "\n", "。", "；", "，"};
        List<String> segments = new ArrayList<>();
        segments.add(text);

        for (String sep : separators) {
            segments = splitBySeparator(segments, sep, chunkSize);
        }
        return enforceMaxSize(segments, chunkSize, overlap);
    }

    private List<String> splitBySeparator(List<String> segments, String sep, int chunkSize) {
        List<String> result = new ArrayList<>();
        for (String seg : segments) {
            if (seg.length() <= chunkSize) {
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
