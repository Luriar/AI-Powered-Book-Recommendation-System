from flask import Flask, request, jsonify
from embedding_query import recommend_books, recommend_books_by_profile

app = Flask(__name__)

# 📘 질문 기반 챗봇 추천 API (기존)
@app.post("/api/chat")
def chat():
    data = request.get_json(force=True)
    question = data.get("question", "")
    answer_text = recommend_books(question)
    return jsonify({"answer": answer_text})

# 🟨 NEW: 개인화 추천 API (찜 + 추천 + 선호 카테고리 통합)
@app.post("/api/recommend/by_user_data")
def recommend_by_user_data():
    data = request.get_json(force=True)
    fav_ids = data.get("favorite_book_ids", [])
    rec_ids = data.get("recommended_book_ids", [])
    categories = data.get("preferred_categories", [])
    result_ids = recommend_books_by_profile(fav_ids, rec_ids, categories)
    return jsonify(result_ids)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
