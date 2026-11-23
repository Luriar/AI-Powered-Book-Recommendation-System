# ─────────────────────────── embedding_query.py ───────────────────────────
import os, re, json, concurrent.futures
import torch, requests, pandas as pd
import time
from torch.nn import functional as F
from sentence_transformers import SentenceTransformer, util
import httpx
import asyncio

# ── 0. 환경 ---------------------------------------------------------------
API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
API_URL  = "https://api.deepseek.com/v1/chat/completions"
MODEL    = "deepseek-chat"

PENALTY, BONUS, THRESH, CUTOFF, TOP_K = 0.15, 0.05, 0.50, 0.65, 5

# ── 1. 디바이스 & 임베딩 --------------------------------------------------
DEVICE       = "cuda" if torch.cuda.is_available() else "cpu"
embed_model  = SentenceTransformer("intfloat/e5-large-v2", device=DEVICE)
dim          = embed_model.get_sentence_embedding_dimension()

#  1-B. 전체 카테고리 → 벡터
all_categories = [
    "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
    "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
    "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
    "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
    "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

with torch.no_grad():
    category_embeddings = embed_model.encode(
        all_categories, convert_to_tensor=True, normalize_embeddings=True
    ).to(DEVICE)

category_vectors = {cat: vec for cat, vec in zip(all_categories, category_embeddings)}

# ── 2. 도서 벡터 ----------------------------------------------------------
#    id, title, summary, vector 컬럼이 있다고 가정
df = pd.read_csv("book_vectors.csv", encoding="utf-8")
book_ids       = df["id"].tolist()
book_titles    = df["title"].tolist()
book_summaries = df["summary"].tolist()
book_vectors   = F.normalize(
    torch.tensor(
        [list(map(float, v.split())) for v in df["vector"]],
        dtype=torch.float32), dim=1
).to(DEVICE)

# ── 3. DeepSeek 래퍼 ------------------------------------------------------
import httpx
import asyncio

async def deepseek(messages: list[dict], temp: float = 0.4, timeout: int = 30) -> str:
    """ asyncio 기반 DeepSeek 비동기 호출"""
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {API_KEY}"
    }
    payload = {
        "model": MODEL,
        "messages": messages,
        "temperature": temp
    }
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(API_URL, headers=headers, json=payload)
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()

# ── 4. 사전 프롬프트 ------------------------------------------------------
YESNO_SYS = {
    "role": "system",
    "content": "질문이 '책 추천' 관련이면 '예', 아니면 '아니오'만."
}

ANALYSIS_SYS = {
    "role": "system",
    "content": "당신은 감정·상황·주제·가중치를 분석하는 전문가입니다."
}

ANALYSIS_PROMPT_TMPL = (
    '사용자 질문: "{q}"\n\n'
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
    "- 예시(감정 중심): {{\"감정\":\"우울\",\"상황\":\"기분 전환이 필요할 때\",\"주제\":\"자기계발\",\"가중치\":{{\"감정\":0.70,\"상황\":0.15,\"주제\":0.15}}}}\n"
    "- 예시(주제 중심): {{\"감정\":\"호기심\",\"상황\":\"여행 계획 중\",\"주제\":\"여행 가이드\",\"가중치\":{{\"감정\":0.10,\"상황\":0.10,\"주제\":0.80}}}}\n"
    "JSON 이외의 다른 텍스트는 포함하지 마세요."
)

# ── 5-A.  질문 기반 추천 -----------------------------------------------
def recommend_books(question: str) -> str:
    q = (question or "").strip()
    if not q:
        return "질문을 입력해 주세요."

    print(f"[Recommendation Request] \"{q}\"")
    start_time = time.time()

    async def process():
        #  DeepSeek 2개 비동기 호출
        fut_yesno = asyncio.create_task(deepseek([YESNO_SYS, {"role": "user", "content": q}], temp=0.0))
        fut_anal  = asyncio.create_task(deepseek(
            [ANALYSIS_SYS, {"role": "user", "content": ANALYSIS_PROMPT_TMPL.format(q=q)}],
            temp=0.25
        ))
        yesno_resp = await fut_yesno
        anal_resp  = await fut_anal

        if not yesno_resp.strip().lower().startswith("예"):
            return "죄송하지만, 이 질문은 도서 추천과 직접 관련이 없어요!"

        try:
            info = json.loads(re.search(r"\{.*\}", anal_resp, re.S).group(0))
        except Exception:
            info = {}

        emotion   = info.get("감정", "")
        situation = info.get("상황", "")
        topic     = info.get("주제", "")
        weights   = info.get("가중치", {}) or {"감정": 1/3, "상황": 1/3, "주제": 1/3}

        parsed_keywords = [k for k in (emotion, situation, topic) if k]
        print(f"→ Parsed Keywords: {parsed_keywords}")

        #  Embedding 연산 (동기 그대로)
        embedding_start = time.time()
        em_inputs  = [q, emotion, situation, topic]
        embs_batch = embed_model.encode(
            em_inputs, batch_size=4,
            convert_to_tensor=True, normalize_embeddings=True
        ).to(DEVICE)

        embs = {
            "query": embs_batch[0],
            "감정":  embs_batch[1] if emotion   else torch.zeros(dim, device=DEVICE),
            "상황":  embs_batch[2] if situation else torch.zeros(dim, device=DEVICE),
            "주제":  embs_batch[3] if topic     else torch.zeros(dim, device=DEVICE)
        }

        sims = {k: util.cos_sim(v, book_vectors)[0] for k, v in embs.items()}
        base_score = (
            0.30 * sims["query"]
            + weights.get("감정", 0) * sims["감정"]
            + weights.get("상황", 0) * sims["상황"]
            + weights.get("주제", 0) * sims["주제"]
        )
        penalty_factor = torch.ones(len(book_vectors), device=DEVICE)
        for k in ("감정", "상황", "주제"):
            penalty_factor -= PENALTY * (sims[k] < THRESH).float()
        penalty_factor.clamp_min_(0.0)
        total_score = base_score * penalty_factor
        top_scores, top_indices = torch.topk(total_score, k=TOP_K)
        if top_scores[0].item() < CUTOFF:
            return "관련성 높은 책을 찾지 못했습니다."

        print(f"→ Embedding + Matching Completed in {int((time.time() - embedding_start)*1000)} ms")

        books_block = "\n\n".join(
            f"제목: {book_titles[i]}\n요약: {book_summaries[i]}"
            for i in top_indices.tolist()
        )

        #  마지막 추천 문구 생성도 async
        rec_msg = await deepseek(
            [
                {"role": "system", "content": "당신은 감정 기반 책 추천 전문가입니다."},
                {"role": "user",
                 "content": (
                     f"사용자 감정: {emotion}\n"
                     f"상황: {situation}\n"
                     f"주제: {topic}\n\n"
                     "아래 후보 중 하나를 골라 존댓말을 사용하여 따뜻한 추천 문구 작성해주세요.\n"
                     "첫 시작줄은 반드시 \"추천도서 : xxx\" 형식으로 시작해주세요.\n\n"
                     f"{books_block}"
                 )}
            ],
            temp=0.7
        )

        total_time = int((time.time() - start_time) * 1000)
        print(f"→ Response returned in {total_time} ms")
        return rec_msg.strip()

    return asyncio.run(process())

# ── 5-B.  프로필 기반 추천 --------------------------------------
def recommend_books_by_profile(favorite_ids: list[int],
                               recommended_ids: list[int],
                               preferred_categories: list[str]) -> list[int]:
    """
    (1) favorite_ids + recommended_ids 벡터 평균
    (2) preferred_categories 가중치 부여
    (3) TOP_K(5) id 반환
    """
    # --- ① 좋아요·추천 책 벡터 ------------------------------------------
    sel = df[df["id"].isin(set(favorite_ids) | set(recommended_ids))]
    if sel.empty:
        # 좋아요/추천이 전혀 없으면 무작위 베스트셀러 전략 등으로 대체 가능
        sel_vec = None
    else:
        sel_vec = F.normalize(
            torch.tensor(
                [list(map(float, v.split())) for v in sel["vector"]],
                dtype=torch.float32), dim=1
        ).to(DEVICE).mean(dim=0, keepdim=True)  # (1, dim)

    # --- ② 선호 카테고리 벡터 -----------------------------------------
    cat_vecs = [
        category_vectors[c] for c in preferred_categories if c in category_vectors
    ]
    if cat_vecs:
        cat_vec = torch.stack(cat_vecs).mean(dim=0, keepdim=True)  # (1, dim)
    else:
        cat_vec = None

    if sel_vec is None and cat_vec is None:
        return []

    # --- ③ 최종 유저 벡터 --------------------------------------------
    if sel_vec is not None and cat_vec is not None:
        user_vec = F.normalize(sel_vec + BONUS * cat_vec, p=2, dim=1)  # (1, dim)
    else:
        user_vec = sel_vec or cat_vec                                       # (1, dim)

    scores   = torch.matmul(user_vec, book_vectors.T)[0]  # (N,)
    top_idx  = torch.topk(scores, k=TOP_K).indices.tolist()
    return df.iloc[top_idx]["id"].tolist()

# ── 6. CLI 테스트 ---------------------------------------------------------
if __name__ == "__main__":
    print("안녕하세요! 😄 어떤 도서를 추천해드릴까요? (빈 줄로 종료)\n")
    try:
        while True:
            q = input("사용자 > ").strip()
            if not q:
                break
            print("\n챗봇 > " + recommend_books(q) + "\n")
    except (KeyboardInterrupt, EOFError):
        pass
# ─────────────────────────────── End ──────────────────────────────
