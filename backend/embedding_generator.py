import pandas as pd
from sentence_transformers import SentenceTransformer
import torch
import csv

# 1. 모델 로드
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model = SentenceTransformer('intfloat/e5-large-v2', device=device)

# 2. CSV 읽기
df = pd.read_csv('C:/ai-bookstore/backend/books_for_embedding.csv', encoding='utf-8')

# 3. title + summary 합치기
texts = ["passage: " + str(row['title']) + " " + str(row['summary']) for _, row in df.iterrows()]

# 4. 벡터 임베딩
embeddings = model.encode(texts, batch_size=32, show_progress_bar=True, normalize_embeddings=True)

# 5. 결과 저장
output_path = 'book_vectors.csv'

with open(output_path, 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['id', 'title', 'summary', 'vector'])

    for idx, (embedding, (_, row)) in enumerate(zip(embeddings, df.iterrows())):
        writer.writerow([
            row['id'],
            row['title'],
            row['summary'],
            ' '.join(map(str, embedding))
        ])

print(f" 벡터 임베딩 완료! 총 {len(embeddings)}개 → {output_path}")
