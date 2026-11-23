package com.bookstore.backend.service;

import com.bookstore.backend.model.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiSummaryService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 유효한 카테고리 목록
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
            "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
            "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
            "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
            "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
    );

    public void summarizeAndFill(Book book) throws IOException {
        final int MAX_RETRY = 2;
        int attempt = 0;

        while (attempt <= MAX_RETRY) {
            attempt++;

            String content = callDeepSeek(book);
            String category = "기타";
            String summary = content;

            Matcher matcher = Pattern
                    .compile("\\[카테고리\\]:\\s*(.+?)\\n\\[요약\\]:\\s*(.+)", Pattern.DOTALL)
                    .matcher(content);

            if (matcher.find()) {
                category = matcher.group(1).trim();
                summary = matcher.group(2).trim();
            }

            if (VALID_CATEGORIES.contains(category)) {
                book.setCategory(category);
                book.setSummary(summary);
                return;
            } else {
                System.out.printf("⚠️ 잘못된 카테고리 (%s), 재요청 중... (시도 %d/%d)%n", category, attempt, MAX_RETRY);
            }
        }

        // 반복 실패 시 fallback
        book.setCategory("기타");
        book.setSummary("AI 요약 실패 또는 유효 카테고리 확인 실패.");
    }

    // DeepSeek API 요청 분리
    private String callDeepSeek(Book book) throws IOException {
        String prompt = String.format("""
다음은 책의 정보입니다. 아래 “3단계 규칙”에 따라
가장 적절한 카테고리를 **하나만** 고른 뒤, 2-3문장으로 요약해 주세요.

──────────────────────────────────
📌 3단계 규칙
1️⃣ 형식 우선
   - 제목·소개에 ‘소설·시·에세이·만화·잡지’ 등 **형식**이 분명히 언급되면
     → 같은 이름의 카테고리를 선택한다.
2️⃣ 주제(내용) 우선
   - 형식 단서가 없거나 여러 개이면, **내용·전문 분야**와
     아래 “카테고리 정의”를 대조해 가장 가까운 것을 고른다.
3️⃣ 독자층 우선
   - 형식·주제가 겹칠 때는 **주된 독자층**을 본다.
     예) 초등 참고용 과학책 → ‘어린이(초등)’ 선택.

※ 반드시 목록 중 하나만 선택, 임의의 새 카테고리 추가 금지
※ [카테고리] 표기는 정확히 동일하게 써야 함
※ 요약은 번호 없이 자연스러운 한 단락(문장 3개)

──────────────────────────────────
📚 카테고리 목록 및 정의:

- 소설: 허구 서사 전반
- 시: 운문 작품집
- 에세이: 작가 개인적 단상·수필
- 인문: 철학·심리·역사적 통찰을 담은 교양서
- 가정/육아: 양육, 가족생활
- 요리: 조리법, 레시피
- 건강: 의학, 웰빙
- 취미/실용/스포츠: DIY, 운동, 생활 실용서
- 경제/경영: 경제 이론·투자·기업 경영
- 자기계발: 삶의 목표·습관·마인드셋 개선
- 정치/사회: 정치 제도·사회 문제·시사 분석
- 역사/문화: 시대·민속·문화사 연구·평론
- 종교: 신앙, 종교 이론
- 예술/대중문화: 음악·미술·영화·연예
- 중/고등참고서: 중고등학생용 시험 대비서
- 기술/공학: 전문 공학·산업 기술
- 외국어: 영어·일어 등 언어 학습서
- 과학: 자연·공학·의학 이론/실험 교양서
- 취업/수험서: 자격증, 면접, 공무원 시험 등 대비
- 여행: 국내외 여행 정보, 가이드북
- 컴퓨터/IT: 프로그래밍, SW, IT 기술
- 잡지: 정기 간행물
- 청소년: 13~18세 일반 교양·소설·학습서
- 초등참고서: 초등학생용 학습서
- 유아(0~7세): 0~7세 대상 그림책, 놀이책
- 어린이(초등): 초등생 대상 동화, 지식 교양서
- 만화: 만화 형식의 책
- 대학교재: 대학 전공 교재

──────────────────────────────────

📘 책 정보
제목: %s
저자: %s
내용: %s

응답 형식
[카테고리]: (위 목록 중 택 1)
[요약]: (자연스러운 문단, 3문장)
""",
                book.getTitle(),
                book.getAuthor() != null ? book.getAuthor() : "정보 없음",
                (book.getDescription() != null && !book.getDescription().isBlank())
                        ? book.getDescription() : "정보 없음"
        );

        // JSON payload 구성
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", "deepseek-reasoner");
        payload.set("messages", objectMapper.createArrayNode().add(message));

        String json = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                throw new IOException("AI 응답 실패: " + response.code() + "\n내용: " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return root.get("choices").get(0).get("message").get("content").asText();
        }
    }
}
