package com.example.gajeontiming.chat;

import com.example.gajeontiming.model.InsightModels;

import java.util.List;

/** 대화 한 줄: 내가 보낸 질문 / AI 답변 / "답변 쓰는 중" 표시 */
public class ChatMessage {
    public static final int USER = 0;
    public static final int AI = 1;
    public static final int TYPING = 2;

    public final int type;
    public final String content;
    public final List<InsightModels.Source> sources;   // AI 답변의 참고 자료 (없으면 null)

    public ChatMessage(int type, String content, List<InsightModels.Source> sources) {
        this.type = type;
        this.content = content;
        this.sources = sources;
    }
}
