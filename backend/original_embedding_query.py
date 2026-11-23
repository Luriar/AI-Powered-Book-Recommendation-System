import os
# ─────────────────────────── embedding_query.py ───────────────────────────
import re, json, torch, requests, concurrent.futures, pandas as pd
from sentence_transformers import SentenceTransformer, util

# 0. 환경 세팅 --------------------------------------------------------------
API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
API_URL  = "https://api.deepseek.com/v1/chat/completions"
MODEL    = "deepseek-chat"

PENALTY  = 0.15    # 요소(감정/상황/주제) 미매칭 시 감점
BONUS    = 0.05    # 키워드 1개 매칭당 가산점
THRESH   = 0.50    # “매칭” 판정 기준
CUTOFF   = 0.65    # Top-1 종합점수 이하면 추천 중단

embed_model = SentenceTransformer("intfloat/e5-large-v2")
dim         = embed_model.get_sentence_embedding_dimension()

# 사전 계산된 벡터 ----------------------------------------------------------
df            = pd.read_csv("book_vectors.csv")
book_titles    = df["title"].tolist()
book_summaries = df["summary"].tolist()
book_vectors   = torch.tensor([list(map(float, v.split())) for v in df["vector"]],
                              dtype=torch.float32)

# DeepSeek 래퍼 -------------------------------------------------------------
def deepseek(messages, temp: float = 0.4) -> str:
    resp = requests.post(
        API_URL,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {API_KEY}"
        },
        json={"model": MODEL, "messages": messages, "temperature": temp},
        timeout=60
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"].strip()

# 키워드 추출 ---------------------------------------------------------------
### 🔄 MOD ── source_type 파라미터 추가 & 감정일 때 ‘극복’ 키워드 유도
def extract_keywords(text: str, source_type: str, max_k: int = 5) -> list[str]:
    """text → 핵심 키워드 리스트(DeepSeek)"""
    if not text:
        return []

    if source_type == "emotion":
        prompt = (
            f"감정: {text}\n\n"
            f"이 감정을 극복(완화)하거나 긍정적으로 전환하는 데 도움이 되는 "
            f"핵심 키워드 {max_k}개를 쉼표(,)로 구분해 주세요. "
            f"다른 설명 없이 키워드만 적어주세요."
        )
    else:  # situation / topic
        prompt = (
            f"문장: {text}\n\n"
            f"이 문장과 밀접한 핵심 키워드 {max_k}개를 쉼표(,)로 구분해 주세요. "
            f"다른 설명 없이 키워드만 적어주세요."
        )

    out = deepseek(
        [
            {"role": "system", "content": "당신은 도서 키워드 추출 전문가입니다."},
            {"role": "user",   "content": prompt}
        ],
        temp=0.3
    )

    keywords = [w.strip() for w in re.split(r"[,\n]", out) if w.strip()][:max_k]
    return keywords

# ─────────────────────────── 메인 루프 ───────────────────────────
print("안녕하세요~!😄 어떤 도서를 추천해드릴까요?\n(빈 줄 입력 시 종료)\n")

try:
    while True:
        user_query = input().strip()
        if not user_query:
            break

        # 1-A) 질문 유형 빠른 검증 --------------------------------------
        ok = deepseek(
            [
                {"role": "system",
                 "content": "질문이 '책 추천' 관련이면 '예', 아니면 '아니오'만."},
                {"role": "user", "content": user_query}
            ],
            temp=0.0
        ).lower().startswith("예")

        if not ok:
            print("\n 죄송하지만, 이 질문은 도서 추천과 직접 관련이 없어요!\n")
            continue

        # 1-B) 감정·상황·주제 + 가중치 분석 --------------------------
        analysis_prompt = (
            f"사용자 질문: \"{user_query}\"\n\n"
            "아래 4가지를 **JSON만**으로 답하세요.\n"
            "1) 감정 (한 단어)\n"
            "2) 상황 (짧은 문장)\n"
            "3) 주제 (핵심 주제)\n"
            "4) 가중치: 감정·상황·주제 중요도 (합 = 1)\n\n"
            "판정 규칙:\n"
            "- 사용자가 특정 감정을 명시·암시하면 **감정**이 핵심입니다. (감정 ≥ 0.6)\n"
            "- 사용자가 구체적 상황을 강조하면 **상황**이 핵심입니다. (상황 ≥ 0.6)\n"
            "- 사용자가 특정 분야·장르·테마를 찾으면 **주제**가 핵심입니다. (주제 ≥ 0.7)\n"
            "- 두 요소 이상이 핵심이면 중요도를 합리적으로 배분하되, 반드시 총합은 1.\n"
            "- 예시(감정 중심):\n"
            "{\n"
            "  \"감정\": \"우울\",\n"
            "  \"상황\": \"기분 전환이 필요할 때\",\n"
            "  \"주제\": \"자기계발\",\n"
            "  \"가중치\": {\"감정\": 0.70, \"상황\": 0.15, \"주제\": 0.15}\n"
            "}\n"
            "- 예시(주제 중심):\n"
            "{\n"
            "  \"감정\": \"호기심\",\n"
            "  \"상황\": \"여행 계획 중\",\n"
            "  \"주제\": \"여행 가이드\",\n"
            "  \"가중치\": {\"감정\": 0.10, \"상황\": 0.10, \"주제\": 0.80}\n"
            "}\n"
            "JSON 이외의 다른 텍스트는 포함하지 마세요."
        )

        raw = deepseek(
            [
                {"role": "system",
                 "content": "당신은 감정·상황·주제·가중치를 분석하는 전문가입니다."},
                {"role": "user", "content": analysis_prompt}
            ]
        )

        try:
            info      = json.loads(re.search(r"\{.*\}", raw, re.S).group(0))
            emotion   = info.get("감정", "")
            situation = info.get("상황", "")
            topic     = info.get("주제", "")
            weights   = info.get("가중치", {})
        except Exception:
            emotion = situation = topic = ""
            weights = {}

        for k in ("감정", "상황", "주제"):
            weights.setdefault(k, 1 / 3)
        s = sum(weights.values()) or 1
        weights = {k: v / s for k, v in weights.items()}

        print("\nDeepSeek 분석 결과:")
        print(f"감정: {emotion or '(없음)'}")
        print(f"상황: {situation or '(없음)'}")
        print(f"주제: {topic or '(없음)'}")
        print("가중치: " + ", ".join(f"{k}={v:.2f}" for k, v in weights.items()) + "\n")

        # 1-C) 가중치 최댓값 요소에서 키워드 추출 ----------------------
        score_key    = max(weights, key=weights.get)            # 감정 / 상황 / 주제
        source_text  = {"감정": emotion, "상황": situation, "주제": topic}.get(score_key, "")
        bonus_weight = weights[score_key]

        kw_core = extract_keywords(source_text, score_key, max_k=5)  # 5개
        ### 🔄 MOD ── “기준 키워드 + 추출 키워드 5개 = 총 6개”
        keywords = [source_text] + [w for w in kw_core if w.lower() != source_text.lower()]
        keywords = keywords[:6]  # 중복 제거 후 최대 6개
        print(f"키워드 추출 기준 → {score_key} (가중치 {bonus_weight:.2f})")
        print(f"최종 사용 키워드: {', '.join(keywords) if keywords else '(없음)'}\n")
        # -------------------------------------------------------------------

        # 2) 텍스트 임베딩 -------------------------------------------------
        texts = {"query": user_query, "감정": emotion,
                 "상황": situation, "주제": topic}

        with concurrent.futures.ThreadPoolExecutor() as exe:
            embs = {
                k: (torch.tensor(
                        exe.submit(embed_model.encode, v,
                                   normalize_embeddings=True).result())
                    if v else torch.zeros(dim))
                for k, v in texts.items()
            }

        # 3) 요소별 cos-sim ----------------------------------------------
        sims = {
            "query": util.cos_sim(embs["query"], book_vectors)[0],
            "감정":  util.cos_sim(embs["감정"],  book_vectors)[0] if emotion   else torch.zeros(len(book_vectors)),
            "상황":  util.cos_sim(embs["상황"],  book_vectors)[0] if situation else torch.zeros(len(book_vectors)),
            "주제":  util.cos_sim(embs["주제"],  book_vectors)[0] if topic     else torch.zeros(len(book_vectors)),
        }

        # (1) 임베딩 기반 기본 점수 -------------------------------------
        base_score = (
            0.3 * sims["query"]
            + weights["감정"] * sims["감정"]
            + weights["상황"] * sims["상황"]
            + weights["주제"] * sims["주제"]
        )

        # (2) 패널티 -----------------------------------------------------
        penalty_factor = torch.ones(len(book_vectors))
        for k in ("감정", "상황", "주제"):
            penalty_factor -= PENALTY * (sims[k] < THRESH).float()
        penalty_factor.clamp_min_(0.0)

        # (3) 키워드 매칭 보너스 ---------------------------------------
        keyword_bonus = torch.zeros(len(book_vectors))
        if keywords:
            # 키워드별 정규식 컴파일
            patterns = [re.compile(re.escape(k), re.I) for k in keywords]

            for i, (title, summary) in enumerate(zip(book_titles, book_summaries)):
                # 도서별로 겹치는 키워드 개수 집계
                matches = sum(
                    1 for p in patterns
                    if p.search(title) or p.search(summary)
                )
                if matches:                         # n개 겹치면 n × BONUS × 가중치
                    keyword_bonus[i] += BONUS * matches * bonus_weight

        # (4) 최종 스코어 ----------------------------------------------
        total_score = (base_score + keyword_bonus) * penalty_factor

        ranked      = torch.topk(total_score, k=5)
        top_indices = ranked.indices.tolist()
        top_scores  = ranked.values.tolist()

        if top_scores[0] < CUTOFF:
            print("관련성 높은 책을 찾을 수 없습니다.\n")
            continue

        print("추천 책 (종합 유사도 + 세부 유사도):")
        for idx, sc in zip(top_indices, top_scores):
            print(f"- {book_titles[idx]} | 종합: {sc:.3f} "
                  f"(q:{sims['query'][idx]:.3f} / "
                  f"감:{sims['감정'][idx]:.3f} / "
                  f"상:{sims['상황'][idx]:.3f} / "
                  f"주:{sims['주제'][idx]:.3f} / "
                  f"보너스:{keyword_bonus[idx]:.3f})")
        print()

        # 5) DeepSeek 추천 문구 ----------------------------------------
        books_block = "\n\n".join(
            f"제목: {book_titles[i]}\n요약: {book_summaries[i]}"
            for i in top_indices
        )

        rec_msg = deepseek(
            [
                {"role": "system",
                 "content": "당신은 감정 기반 책 추천 전문가입니다."},
                {"role": "user",
                 "content": (
                     f"사용자 감정: {emotion}\n"
                     f"상황: {situation}\n"
                     f"주제: {topic}\n\n"
                     "아래 후보 중 하나를 골라 존댓말을 사용하여 따뜻한 추천 문구 작성:\n\n"
                     f"{books_block}"
                 )}
            ],
            temp=0.7
        )

        print(rec_msg, "\n")
        print("다른 책을 추천받고 싶으시면 자유롭게 질문해주세요!\n")

except (KeyboardInterrupt, EOFError):
    pass
# ────────────────────────────── End ─────────────────────────────